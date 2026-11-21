package com.example.dj.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.SamplerSound
import com.example.dj.model.StemType
import com.example.dj.model.TrimGainMath
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Low-latency AudioTrack PCM engine supporting:
 * 1) Real-time 4-stem synthesis for built-in tracks,
 * 2) Real-time Stereo Mid-Side + 3-Band Stem Separation DSP (Vocals/Instrumental removal) for decoded local audio files,
 * 3) 3-Band EQ, LPF/HPF Filter, Echo, Flanger, Crossfader Curve, L/R peak metering, 4-slot DJ Sampler, and WAV recording.
 */
class StemAudioEngine {
  private val sampleRate = 22050
  private val bufferSamples = 512
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var engineJob: Job? = null
  private var audioTrack: AudioTrack? = null

  @Volatile private var deckAState: DeckState? = null
  @Volatile private var deckBState: DeckState? = null
  @Volatile private var deckALocalFallbackOnly: Boolean = false
  @Volatile private var deckBLocalFallbackOnly: Boolean = false
  @Volatile private var crossfaderPos: Float = 0.5f
  @Volatile private var crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER

  private val decodedBuffers = ConcurrentHashMap<DeckId, PcmTrackBuffer>()
  private val pcmCursorFrames = ConcurrentHashMap<DeckId, Double>().apply {
    put(DeckId.A, 0.0)
    put(DeckId.B, 0.0)
  }

  private val _masterPeaks = MutableStateFlow(0f to 0f)
  val masterPeaks: StateFlow<Pair<Float, Float>> = _masterPeaks.asStateFlow()

  private val _deckPeaks = MutableStateFlow(0f to 0f)
  val deckPeaks: StateFlow<Pair<Float, Float>> = _deckPeaks.asStateFlow()

  private val _deckInputLevels = MutableStateFlow(0f to 0f)
  val deckInputLevels: StateFlow<Pair<Float, Float>> = _deckInputLevels.asStateFlow()

  private val dspA = DeckDspState(sampleRate)
  private val dspB = DeckDspState(sampleRate)
  private val samplerDsp = SamplerDsp(sampleRate)

  private val recordLock = Any()
  private var recordingFile: RandomAccessFile? = null
  private var recordedDataBytes: Long = 0L

  fun setDecodedBuffer(deckId: DeckId, buffer: PcmTrackBuffer?) {
    if (buffer == null) {
      decodedBuffers.remove(deckId)
    } else {
      decodedBuffers[deckId] = buffer
      pcmCursorFrames[deckId] = 0.0
    }
  }

  fun hasDecodedBuffer(deckId: DeckId): Boolean = decodedBuffers.containsKey(deckId)

  fun seekDeck(deckId: DeckId, progress: Float) {
    val buf = decodedBuffers[deckId] ?: return
    val frame = (progress.coerceIn(0f, 1f) * buf.frameCount).toDouble()
    pcmCursorFrames[deckId] = frame
  }

  fun triggerSampler(sound: SamplerSound) {
    samplerDsp.trigger(sound)
    if (engineJob == null) start()
  }

  fun updateMixState(
    deckA: DeckState,
    deckB: DeckState,
    crossfader: Float,
    curve: CrossfaderCurve,
    deckAHasLocalFallbackOnly: Boolean,
    deckBHasLocalFallbackOnly: Boolean,
  ) {
    deckAState = deckA
    deckBState = deckB
    crossfaderPos = crossfader
    crossfaderCurve = curve
    deckALocalFallbackOnly = deckAHasLocalFallbackOnly && !decodedBuffers.containsKey(DeckId.A)
    deckBLocalFallbackOnly = deckBHasLocalFallbackOnly && !decodedBuffers.containsKey(DeckId.B)

    val shouldRun =
      (deckA.isPlaying && !deckALocalFallbackOnly) ||
        (deckB.isPlaying && !deckBLocalFallbackOnly) ||
        samplerDsp.isActive()
    if (shouldRun && engineJob == null) {
      start()
    } else if (!shouldRun && engineJob != null && recordingFile == null) {
      _masterPeaks.value = 0f to 0f
      _deckPeaks.value = 0f to 0f
      _deckInputLevels.value = 0f to 0f
    }
  }

  private fun start() {
    if (engineJob != null) return
    val minBufBytes =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    val track =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        )
        .setBufferSizeInBytes(maxOf(minBufBytes, bufferSamples * 4 * 2))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    audioTrack = track
    track.play()

    engineJob =
      scope.launch {
        val pcmBuffer = ShortArray(bufferSamples * 2)
        val byteBuffer = ByteArray(bufferSamples * 4)

        while (isActive) {
          val a = deckAState
          val b = deckBState
          val (gainA, gainB) = crossfaderCurve.computeGains(crossfaderPos)
          val bufA = decodedBuffers[DeckId.A]
          val bufB = decodedBuffers[DeckId.B]

          var peakL = 0f
          var peakR = 0f
          var peakDeckA = 0f
          var peakDeckB = 0f
          var inputLevelA = 0f
          var inputLevelB = 0f

          for (i in 0 until bufferSamples) {
            var leftA = 0f
            var rightA = 0f
            if (a != null && a.isPlaying && !deckALocalFallbackOnly) {
              val (l, r) =
                if (bufA != null) {
                  processDecodedDeck(DeckId.A, a, bufA, dspA)
                } else {
                  val mono = synthesizeDeck(a, dspA)
                  mono to mono
                }
              if (dspA.lastInputPeak > inputLevelA) inputLevelA = dspA.lastInputPeak
              val deckScaleA = a.channelVolume
              peakDeckA = maxOf(peakDeckA, abs(l * deckScaleA), abs(r * deckScaleA))
              val scaleA = gainA * deckScaleA
              leftA = l * scaleA
              rightA = r * scaleA
            }

            var leftB = 0f
            var rightB = 0f
            if (b != null && b.isPlaying && !deckBLocalFallbackOnly) {
              val (l, r) =
                if (bufB != null) {
                  processDecodedDeck(DeckId.B, b, bufB, dspB)
                } else {
                  val mono = synthesizeDeck(b, dspB)
                  mono to mono
                }
              if (dspB.lastInputPeak > inputLevelB) inputLevelB = dspB.lastInputPeak
              val deckScaleB = b.channelVolume
              peakDeckB = maxOf(peakDeckB, abs(l * deckScaleB), abs(r * deckScaleB))
              val scaleB = gainB * deckScaleB
              leftB = l * scaleB
              rightB = r * scaleB
            }

            val samplerSample = samplerDsp.nextSample()
            val mixL = (leftA * 0.96f + leftB * 0.90f + samplerSample).coerceIn(-0.99f, 0.99f)
            val mixR = (rightA * 0.90f + rightB * 0.96f + samplerSample).coerceIn(-0.99f, 0.99f)

            peakL = maxOf(peakL, abs(mixL))
            peakR = maxOf(peakR, abs(mixR))

            val sL = (mixL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            val sR = (mixR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            pcmBuffer[i * 2] = sL
            pcmBuffer[i * 2 + 1] = sR

            val bIdx = i * 4
            byteBuffer[bIdx] = (sL.toInt() and 0xFF).toByte()
            byteBuffer[bIdx + 1] = ((sL.toInt() shr 8) and 0xFF).toByte()
            byteBuffer[bIdx + 2] = (sR.toInt() and 0xFF).toByte()
            byteBuffer[bIdx + 3] = ((sR.toInt() shr 8) and 0xFF).toByte()
          }

          val fallbackA =
            if (a?.isPlaying == true && deckALocalFallbackOnly) {
              gainA * a.trimGain * a.channelVolume * 0.72f
            } else 0f
          val fallbackB =
            if (b?.isPlaying == true && deckBLocalFallbackOnly) {
              gainB * b.trimGain * b.channelVolume * 0.72f
            } else 0f
          _masterPeaks.value =
            maxOf(peakL, (fallbackA + fallbackB).coerceAtMost(0.95f)) to
              maxOf(peakR, (fallbackA + fallbackB).coerceAtMost(0.95f))
          _deckPeaks.value =
            peakDeckA.coerceIn(0f, 1f) to peakDeckB.coerceIn(0f, 1f)
          _deckInputLevels.value =
            inputLevelA.coerceIn(0f, 1f) to inputLevelB.coerceIn(0f, 1f)

          synchronized(recordLock) {
            recordingFile?.let { raf ->
              runCatching {
                raf.write(byteBuffer)
                recordedDataBytes += byteBuffer.size
              }
            }
          }

          track.write(pcmBuffer, 0, pcmBuffer.size)
        }
      }
  }

  private fun processDecodedDeck(
    deckId: DeckId,
    deck: DeckState,
    buf: PcmTrackBuffer,
    dsp: DeckDspState,
  ): Pair<Float, Float> {
    val totalFrames = buf.frameCount.coerceAtLeast(1)
    var cursor = pcmCursorFrames[deckId] ?: 0.0
    if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
      val startFrame = deck.loop.startProgress * totalFrames
      val endFrame = deck.loop.endProgress * totalFrames
      if (cursor < startFrame || cursor >= endFrame) {
        cursor = startFrame.toDouble()
      }
    }
    val idx = cursor.toInt().coerceIn(0, totalFrames - 1)
    val sourceL = buf.stereoSamples[idx * 2] / 32768f
    val sourceR = buf.stereoSamples[idx * 2 + 1] / 32768f

    // Pre-mixer Input Gain Trim stage (before stems, EQ, pitch, FX, and faders)
    dsp.lastInputPeak = maxOf(abs(sourceL * deck.trimGain), abs(sourceR * deck.trimGain)).coerceIn(0f, 1f)
    val rawL = TrimGainMath.applyPreMixerTrim(sourceL, deck.trimGain)
    val rawR = TrimGainMath.applyPreMixerTrim(sourceR, deck.trimGain)

    val speed = (1f + deck.tempoOffset).coerceIn(0.35f, 1.85f).toDouble()
    cursor += speed
    if (cursor >= totalFrames) cursor -= totalFrames
    pcmCursorFrames[deckId] = cursor

    // Real-Time Serato-style Stereo Mid-Side + 3-Band Stem Separation
    val hasAnySolo = deck.stems.values.any { it.isSolo }
    fun stemGain(type: StemType): Float {
      val s = deck.stems[type] ?: return 1f
      if (s.isMuted) return 0f
      if (hasAnySolo && !s.isSolo) return 0f
      return s.level
    }

    val voxGain = stemGain(StemType.VOCALS)
    val drumGain = stemGain(StemType.DRUMS)
    val bassGain = stemGain(StemType.BASS)
    val melGain = stemGain(StemType.MELODY)

    val mid = (rawL + rawR) * 0.5f
    val side = (rawL - rawR) * 0.5f

    // 1. Sub-Bass (<220Hz on Mid)
    dsp.stemLpf220 += 0.062f * (mid - dsp.stemLpf220)
    val bassBand = dsp.stemLpf220

    // 2. High Percussion / Hi-Hats (>4.2kHz)
    val midAboveBass = mid - bassBand
    dsp.stemLpf3800 += 0.65f * (midAboveBass - dsp.stemLpf3800)
    val drumAirBand = midAboveBass - dsp.stemLpf3800

    // 3. Lead Vocal Center Band (300Hz .. 3.8kHz on Mid, excluding stereo side instruments)
    val vocalBand = dsp.stemLpf3800

    // 4. Stereo Melody / Instrumental Side + low-level harmonic backing
    val melodyL = side + (vocalBand * 0.18f)
    val melodyR = -side + (vocalBand * 0.18f)

    // Kick drum transient resides in bassBand + mid punch
    val kickPunch = bassBand * 0.35f
    val rawDrums = (drumAirBand + kickPunch) * drumGain * deck.eq.high
    val rawVocals = vocalBand * voxGain * deck.eq.mid
    val rawInstL = (bassBand * bassGain * deck.eq.low) + (melodyL * melGain * deck.eq.mid)
    val rawInstR = (bassBand * bassGain * deck.eq.low) + (melodyR * melGain * deck.eq.mid)

    val fxDrums = dsp.applyStemEcho(rawDrums, deck.effects.drumsEcho, dsp.drumsEchoBuf, isVocal = false)
    val fxVocals = dsp.applyStemEcho(rawVocals, deck.effects.vocalEcho, dsp.vocalEchoBuf, isVocal = true)
    val brakeGain = dsp.stepInstBrakeGain(deck.effects.instBrake)
    val fxInstL = dsp.applyInstEcho(rawInstL * brakeGain, deck.effects.instEcho, channel = 0)
    val fxInstR = dsp.applyInstEcho(rawInstR * brakeGain, deck.effects.instEcho, channel = 1)

    val outL = fxInstL + fxDrums + fxVocals
    val outR = fxInstR + fxDrums + fxVocals

    val (shiftedL, shiftedR) =
      if (deck.keyShiftSemitones != 0) {
        dsp.applyPitchShiftStereo(outL, outR, deck.keyPitchMultiplier)
      } else {
        dsp.applyPitchShiftStereo(outL, outR, 1f)
      }

    val fxMono = dsp.applyEffects((shiftedL + shiftedR) * 0.5f, deck)
    val diff = fxMono - ((shiftedL + shiftedR) * 0.5f)
    return (shiftedL + diff).coerceIn(-0.98f, 0.98f) to (shiftedR + diff).coerceIn(-0.98f, 0.98f)
  }

  private fun synthesizeDeck(
    deck: DeckState,
    dsp: DeckDspState,
  ): Float {
    val bpm = deck.effectiveBpm
    val beatsPerSec = bpm / 60f
    dsp.beatPhase += beatsPerSec / sampleRate
    if (dsp.beatPhase >= 1f) {
      dsp.beatPhase -= 1f
      dsp.beatIndex = (dsp.beatIndex + 1) % 16
    }

    val tempoPitchFactor = if (deck.keyLockEnabled) 1f else (1f + deck.tempoOffset).coerceIn(0.5f, 1.5f)
    val baseFreq = deck.track.baseFreqHz * tempoPitchFactor * deck.keyPitchMultiplier

    val hasAnySolo = deck.stems.values.any { it.isSolo }
    fun stemGain(type: StemType): Float {
      val s = deck.stems[type] ?: return 1f
      if (s.isMuted) return 0f
      if (hasAnySolo && !s.isSolo) return 0f
      return s.level
    }

    val kickEnv = exp(-dsp.beatPhase * 14f)
    val kickFreq = 52f + 95f * exp(-dsp.beatPhase * 28f)
    dsp.kickPhase += (2.0 * PI * kickFreq / sampleRate).toFloat()
    val kick = sin(dsp.kickPhase.toDouble()).toFloat() * kickEnv * 0.48f

    val hatPhase = (dsp.beatPhase * 2f) % 1f
    val hatEnv = exp(-hatPhase * 32f)
    dsp.noiseSeed = (dsp.noiseSeed * 1664525 + 1013904223)
    val noise = ((dsp.noiseSeed shr 16) and 0x7FFF) / 32768f - 0.5f
    val hihatRaw = noise * hatEnv * 0.22f

    val bassNote = if (dsp.beatIndex % 4 == 3) baseFreq * 0.56f else baseFreq * 0.5f
    dsp.bassPhase += (2.0 * PI * bassNote / sampleRate).toFloat()
    val bassEnv = (1f - (dsp.beatPhase * 0.65f)).coerceIn(0.2f, 1f)
    val bassRaw = sin(dsp.bassPhase.toDouble()).toFloat() * 0.32f * bassEnv

    val arpMult =
      when (dsp.beatIndex % 4) {
        0 -> 1.0f
        1 -> 1.2f
        2 -> 1.5f
        else -> 1.8f
      }
    dsp.melodyPhase += (2.0 * PI * baseFreq * arpMult / sampleRate).toFloat()
    val melodyRaw = sin(dsp.melodyPhase.toDouble()).toFloat() * 0.18f

    dsp.vocalPhase += (2.0 * PI * baseFreq * 2.0f / sampleRate).toFloat()
    val vocalRaw =
      (sin(dsp.vocalPhase.toDouble()) * 0.6 + sin(dsp.vocalPhase * 1.5) * 0.3).toFloat() * 0.16f

    // Pre-mixer Input Gain Trim stage (scales raw audio input before stems/EQ/FX/faders)
    val rawPreMixer = (kick + hihatRaw + bassRaw + melodyRaw + vocalRaw) * 0.65f
    dsp.lastInputPeak = abs(rawPreMixer * deck.trimGain).coerceIn(0f, 1f)
    val inputGain = TrimGainMath.clamp(deck.trimGain)

    val brakeGain = dsp.stepInstBrakeGain(deck.effects.instBrake)
    val drumsSample =
      dsp.applyStemEcho(
        ((kick * deck.eq.low + hihatRaw * deck.eq.high) * inputGain) * stemGain(StemType.DRUMS),
        deck.effects.drumsEcho,
        dsp.drumsEchoBuf,
        isVocal = false,
      )
    val instRaw =
      (((bassRaw * inputGain) * deck.eq.low * stemGain(StemType.BASS)) +
        ((melodyRaw * inputGain) * deck.eq.mid * stemGain(StemType.MELODY))) * brakeGain
    val instSample = dsp.applyInstEcho(instRaw, deck.effects.instEcho, channel = 0)
    val vocalSample =
      dsp.applyStemEcho(
        (vocalRaw * inputGain) * deck.eq.mid * stemGain(StemType.VOCALS),
        deck.effects.vocalEcho,
        dsp.vocalEchoBuf,
        isVocal = true,
      )

    val rawMix = TrimGainMath.applyPreMixerTrim((drumsSample + instSample + vocalSample) * 0.65f, 1.0f)
    return dsp.applyEffects(rawMix, deck)
  }

  fun startRecording(outputFile: File): Boolean {
    synchronized(recordLock) {
      if (recordingFile != null) return false
      return runCatching {
        outputFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(outputFile, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44))
        recordingFile = raf
        recordedDataBytes = 0L
        if (engineJob == null) start()
        true
      }.getOrDefault(false)
    }
  }

  fun stopRecording(): Long {
    synchronized(recordLock) {
      val raf = recordingFile ?: return 0L
      val dataSize = recordedDataBytes
      runCatching {
        raf.seek(0)
        val totalFileSize = dataSize + 36L
        val byteRate = sampleRate * 2 * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLe(header, 4, totalFileSize.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLe(header, 16, 16)
        writeShortLe(header, 20, 1)
        writeShortLe(header, 22, 2)
        writeIntLe(header, 24, sampleRate)
        writeIntLe(header, 28, byteRate)
        writeShortLe(header, 32, 4)
        writeShortLe(header, 34, 16)
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLe(header, 40, dataSize.toInt())
        raf.write(header)
        raf.close()
      }
      recordingFile = null
      recordedDataBytes = 0L
      return dataSize
    }
  }

  private fun writeIntLe(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
  }

  private fun writeShortLe(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
  }

  fun release() {
    stopRecording()
    engineJob?.cancel()
    engineJob = null
    runCatching {
      audioTrack?.stop()
      audioTrack?.release()
    }
    audioTrack = null
    scope.cancel()
  }

  private class SamplerDsp(private val sampleRate: Int) {
    @Volatile private var activeSound: SamplerSound? = null
    @Volatile private var remainingSamples: Int = 0
    private var totalSamples: Int = 1
    private var phase: Float = 0f
    private var noiseSeed: Int = 4242

    fun isActive(): Boolean = remainingSamples > 0

    fun trigger(sound: SamplerSound) {
      activeSound = sound
      totalSamples = (sampleRate * 0.75f).toInt()
      remainingSamples = totalSamples
      phase = 0f
    }

    fun nextSample(): Float {
      val sound = activeSound ?: return 0f
      if (remainingSamples <= 0) return 0f
      val progress = 1f - (remainingSamples.toFloat() / totalSamples.toFloat())
      remainingSamples--
      val env = (1f - progress) * (1f - progress)
      return when (sound) {
        SamplerSound.SIRENI -> {
          val freq = 620f + 420f * sin(progress * 4.0 * PI).toFloat()
          phase += (2.0 * PI * freq / sampleRate).toFloat()
          sin(phase.toDouble()).toFloat() * env * 0.35f
        }
        SamplerSound.CROWD -> {
          noiseSeed = noiseSeed * 1664525 + 1013904223
          val noise = ((noiseSeed shr 16) and 0x7FFF) / 32768f - 0.5f
          noise * sin(progress * PI).toFloat() * 0.38f
        }
        SamplerSound.APPLAUSE -> {
          noiseSeed = noiseSeed * 1664525 + 1013904223
          val noise = ((noiseSeed shr 16) and 0x7FFF) / 32768f - 0.5f
          val clapBurst = if (((progress * 14f).toInt() % 2) == 0) 1f else 0.35f
          noise * env * clapBurst * 0.45f
        }
        SamplerSound.WHISTLE -> {
          val f1 = 2650f
          val f2 = 2820f
          phase += (2.0 * PI * f1 / sampleRate).toFloat()
          val w = sin(phase.toDouble()).toFloat() * 0.6f + sin((phase * (f2 / f1)).toDouble()).toFloat() * 0.4f
          w * env * 0.28f
        }
      }
    }
  }

  private class DeckDspState(private val sampleRate: Int) {
    var lastInputPeak = 0f
    var beatPhase = 0f
    var beatIndex = 0
    var kickPhase = 0f
    var bassPhase = 0f
    var melodyPhase = 0f
    var vocalPhase = 0f
    var noiseSeed = 1337

    var stemLpf220 = 0f
    var stemLpf3800 = 0f

    private var lpfState = 0f
    private var hpfPrevIn = 0f
    private var hpfState = 0f

    private val echoBuf = FloatArray((sampleRate * 0.32f).toInt())
    private var echoIdx = 0

    val vocalEchoBuf = FloatArray((sampleRate * 0.28f).toInt())
    private var vocalEchoIdx = 0
    val drumsEchoBuf = FloatArray((sampleRate * 0.18f).toInt())
    private var drumsEchoIdx = 0
    private val instEchoBufL = FloatArray((sampleRate * 0.24f).toInt())
    private val instEchoBufR = FloatArray((sampleRate * 0.24f).toInt())
    private var instEchoIdxL = 0
    private var instEchoIdxR = 0
    private var instBrakeLevel = 1f

    private val flangeBuf = FloatArray((sampleRate * 0.02f).toInt())
    private var flangeIdx = 0
    private var lfoPhase = 0f

    private val pitchBufL = FloatArray((sampleRate * 0.044f).toInt())
    private val pitchBufR = FloatArray((sampleRate * 0.044f).toInt())
    private var pitchWriteIdx = 0
    private var pitchPhase = 0f

    fun stepInstBrakeGain(enabled: Boolean): Float {
      val target = if (enabled) 0.04f else 1.0f
      val rate = if (enabled) 0.00018f else 0.0012f
      instBrakeLevel += (target - instBrakeLevel) * rate
      return instBrakeLevel.coerceIn(0f, 1f)
    }

    fun applyStemEcho(input: Float, enabled: Boolean, buf: FloatArray, isVocal: Boolean): Float {
      val idx = if (isVocal) vocalEchoIdx else drumsEchoIdx
      val delayed = buf[idx]
      val writeSample = if (enabled) input + delayed * 0.52f else delayed * 0.25f
      buf[idx] = writeSample
      val nextIdx = (idx + 1) % buf.size
      if (isVocal) vocalEchoIdx = nextIdx else drumsEchoIdx = nextIdx
      return if (enabled) (input * 0.85f + delayed * 0.65f) else input
    }

    fun applyInstEcho(input: Float, enabled: Boolean, channel: Int): Float {
      val buf = if (channel == 0) instEchoBufL else instEchoBufR
      val idx = if (channel == 0) instEchoIdxL else instEchoIdxR
      val delayed = buf[idx]
      buf[idx] = if (enabled) input + delayed * 0.48f else delayed * 0.2f
      val nextIdx = (idx + 1) % buf.size
      if (channel == 0) instEchoIdxL = nextIdx else instEchoIdxR = nextIdx
      return if (enabled) (input * 0.85f + delayed * 0.6f) else input
    }

    fun applyPitchShiftStereo(inL: Float, inR: Float, pitchRatio: Float): Pair<Float, Float> {
      val n = pitchBufL.size
      pitchBufL[pitchWriteIdx] = inL
      pitchBufR[pitchWriteIdx] = inR
      if (abs(pitchRatio - 1f) < 0.002f) {
        pitchWriteIdx = (pitchWriteIdx + 1) % n
        return inL to inR
      }

      val p1 = pitchPhase
      val p2 = (p1 + 0.5f) % 1f
      val maxDelay = (n - 3).toFloat()

      fun readTap(buf: FloatArray, p: Float): Float {
        val delay = p * maxDelay
        val intDelay = delay.toInt()
        val frac = delay - intDelay
        val idx0 = ((pitchWriteIdx - intDelay) % n + n) % n
        val idx1 = ((idx0 - 1) % n + n) % n
        return buf[idx0] * (1f - frac) + buf[idx1] * frac
      }

      val w1 = 0.5f - 0.5f * cos(2.0 * PI * p1).toFloat()
      val w2 = 1f - w1
      val outL = readTap(pitchBufL, p1) * w1 + readTap(pitchBufL, p2) * w2
      val outR = readTap(pitchBufR, p1) * w1 + readTap(pitchBufR, p2) * w2

      pitchWriteIdx = (pitchWriteIdx + 1) % n
      pitchPhase = ((pitchPhase + (1f - pitchRatio) / maxDelay) % 1f + 1f) % 1f
      return outL to outR
    }

    fun applyEffects(input: Float, deck: DeckState): Float {
      var x = input
      val f = deck.effects.filter
      if (f < -0.05f) {
        val alpha = (1f + f).coerceIn(0.04f, 0.95f)
        lpfState += alpha * (x - lpfState)
        x = lpfState
      } else if (f > 0.05f) {
        val alpha = (1f - f * 0.85f).coerceIn(0.10f, 0.98f)
        hpfState = alpha * (hpfState + x - hpfPrevIn)
        hpfPrevIn = x
        x = hpfState
      }

      val echoWet = deck.effects.echo
      val delayedEcho = echoBuf[echoIdx]
      echoBuf[echoIdx] = x + delayedEcho * 0.42f
      echoIdx = (echoIdx + 1) % echoBuf.size
      if (echoWet > 0.01f) {
        x = x * (1f - echoWet * 0.35f) + delayedEcho * echoWet * 0.65f
      }

      val flangeWet = deck.effects.flanger
      flangeBuf[flangeIdx] = x
      lfoPhase += (2.0 * PI * 0.65 / sampleRate).toFloat()
      val modSamples =
        ((0.5f + 0.5f * sin(lfoPhase.toDouble()).toFloat()) * (flangeBuf.size - 2)).toInt()
      val readIdx = (flangeIdx - modSamples + flangeBuf.size) % flangeBuf.size
      val flanged = flangeBuf[readIdx]
      flangeIdx = (flangeIdx + 1) % flangeBuf.size
      if (flangeWet > 0.01f) {
        x = x * (1f - flangeWet * 0.4f) + flanged * flangeWet * 0.6f
      }

      return x.coerceIn(-0.98f, 0.98f)
    }
  }
}
