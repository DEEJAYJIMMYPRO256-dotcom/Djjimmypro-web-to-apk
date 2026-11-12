package com.example.dj.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.StemType
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
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
 * Low-latency AudioTrack PCM synthesizer and stem/FX DSP mixer for dual-deck playback,
 * real-time 4-stem isolation, 3-band EQ, Filter/Echo/Flanger FX, peak metering, and WAV capture.
 */
class StemAudioEngine {
  private val sampleRate = 22050
  private val bufferSamples = 512
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var engineJob: Job? = null
  private var audioTrack: AudioTrack? = null

  @Volatile private var deckAState: DeckState? = null
  @Volatile private var deckBState: DeckState? = null
  @Volatile private var deckALocalAudio: Boolean = false
  @Volatile private var deckBLocalAudio: Boolean = false
  @Volatile private var crossfaderPos: Float = 0.5f
  @Volatile private var crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER

  private val _masterPeaks = MutableStateFlow(0f to 0f)
  val masterPeaks: StateFlow<Pair<Float, Float>> = _masterPeaks.asStateFlow()

  private val dspA = DeckDspState(sampleRate)
  private val dspB = DeckDspState(sampleRate)

  private val recordLock = Any()
  private var recordingFile: RandomAccessFile? = null
  private var recordedDataBytes: Long = 0L

  fun updateMixState(
    deckA: DeckState,
    deckB: DeckState,
    crossfader: Float,
    curve: CrossfaderCurve,
    deckAHasLocalAudio: Boolean,
    deckBHasLocalAudio: Boolean,
  ) {
    deckAState = deckA
    deckBState = deckB
    crossfaderPos = crossfader
    crossfaderCurve = curve
    deckALocalAudio = deckAHasLocalAudio
    deckBLocalAudio = deckBHasLocalAudio

    val shouldRun =
      (deckA.isPlaying && !deckAHasLocalAudio) || (deckB.isPlaying && !deckBHasLocalAudio)
    if (shouldRun && engineJob == null) {
      start()
    } else if (!shouldRun && engineJob != null && recordingFile == null) {
      _masterPeaks.value = 0f to 0f
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

          var peakL = 0f
          var peakR = 0f

          for (i in 0 until bufferSamples) {
            val sampleA =
              if (a != null && a.isPlaying && !deckALocalAudio) {
                synthesizeDeck(a, dspA) * gainA * a.trimGain * a.channelVolume
              } else {
                0f
              }
            val sampleB =
              if (b != null && b.isPlaying && !deckBLocalAudio) {
                synthesizeDeck(b, dspB) * gainB * b.trimGain * b.channelVolume
              } else {
                0f
              }

            // Slight stereo width offset between decks for L/R meter differentiation
            val mixL = (sampleA * 0.98f + sampleB * 0.90f).coerceIn(-0.99f, 0.99f)
            val mixR = (sampleA * 0.90f + sampleB * 0.98f).coerceIn(-0.99f, 0.99f)

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

          // Smooth peak decay; if playing local audio only, reflect approximate gain meter
          val fallbackA = if (a?.isPlaying == true && deckALocalAudio) gainA * a.channelVolume * 0.72f else 0f
          val fallbackB = if (b?.isPlaying == true && deckBLocalAudio) gainB * b.channelVolume * 0.72f else 0f
          val displayPeakL = maxOf(peakL, (fallbackA + fallbackB).coerceAtMost(0.95f))
          val displayPeakR = maxOf(peakR, (fallbackA + fallbackB).coerceAtMost(0.95f))
          _masterPeaks.value = displayPeakL to displayPeakR

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

  private fun synthesizeDeck(deck: DeckState, dsp: DeckDspState): Float {
    val bpm = deck.effectiveBpm
    val beatsPerSec = bpm / 60f
    dsp.beatPhase += beatsPerSec / sampleRate
    if (dsp.beatPhase >= 1f) {
      dsp.beatPhase -= 1f
      dsp.beatIndex = (dsp.beatIndex + 1) % 16
    }

    val pitchFactor = if (deck.keyLockEnabled) 1f else (1f + deck.tempoOffset).coerceIn(0.5f, 1.5f)
    val baseFreq = deck.track.baseFreqHz * pitchFactor

    val hasAnySolo = deck.stems.values.any { it.isSolo }
    fun stemGain(type: StemType): Float {
      val s = deck.stems[type] ?: return 1f
      if (s.isMuted) return 0f
      if (hasAnySolo && !s.isSolo) return 0f
      return s.level
    }

    // 1. Drums: Punchy kick on beat + offbeat hi-hat
    val kickEnv = exp(-dsp.beatPhase * 14f)
    val kickFreq = 52f + 95f * exp(-dsp.beatPhase * 28f)
    dsp.kickPhase += (2.0 * PI * kickFreq / sampleRate).toFloat()
    val kick = sin(dsp.kickPhase.toDouble()).toFloat() * kickEnv * 0.48f

    val hatPhase = (dsp.beatPhase * 2f) % 1f
    val hatEnv = exp(-hatPhase * 32f)
    dsp.noiseSeed = (dsp.noiseSeed * 1664525 + 1013904223)
    val noise = ((dsp.noiseSeed shr 16) and 0x7FFF) / 32768f - 0.5f
    val hihat = noise * hatEnv * 0.22f * deck.eq.high

    val drumsSample = (kick * deck.eq.low + hihat) * stemGain(StemType.DRUMS)

    // 2. Bass: Octave-down rhythmic synth bassline
    val bassNote = if (dsp.beatIndex % 4 == 3) baseFreq * 0.56f else baseFreq * 0.5f
    dsp.bassPhase += (2.0 * PI * bassNote / sampleRate).toFloat()
    val bassEnv = (1f - (dsp.beatPhase * 0.65f)).coerceIn(0.2f, 1f)
    val bassSample =
      sin(dsp.bassPhase.toDouble()).toFloat() * 0.32f * bassEnv * deck.eq.low * stemGain(StemType.BASS)

    // 3. Melody: Arpeggiated chord synth
    val arpMult =
      when (dsp.beatIndex % 4) {
        0 -> 1.0f
        1 -> 1.2f
        2 -> 1.5f
        else -> 1.8f
      }
    dsp.melodyPhase += (2.0 * PI * baseFreq * arpMult / sampleRate).toFloat()
    val melWave = sin(dsp.melodyPhase.toDouble()).toFloat() * 0.18f
    val melodySample = melWave * deck.eq.mid * stemGain(StemType.MELODY)

    // 4. Vocals: Smooth formant-modulated harmonic pad
    dsp.vocalPhase += (2.0 * PI * baseFreq * 2.0f / sampleRate).toFloat()
    val voxWave =
      (sin(dsp.vocalPhase.toDouble()) * 0.6 + sin(dsp.vocalPhase * 1.5) * 0.3).toFloat() * 0.16f
    val vocalSample = voxWave * deck.eq.mid * stemGain(StemType.VOCALS)

    val rawMix = (drumsSample + bassSample + melodySample + vocalSample) * 0.65f
    return dsp.applyEffects(rawMix, deck)
  }

  fun startRecording(outputFile: File): Boolean {
    synchronized(recordLock) {
      if (recordingFile != null) return false
      return runCatching {
        outputFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(outputFile, "rw")
        raf.setLength(0)
        // Write 44-byte WAV header placeholder
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
        val byteRate = sampleRate * 2 * 2 // stereo 16-bit
        val header = ByteArray(44)
        // RIFF
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLe(header, 4, totalFileSize.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLe(header, 16, 16)
        writeShortLe(header, 20, 1) // PCM
        writeShortLe(header, 22, 2) // Stereo
        writeIntLe(header, 24, sampleRate)
        writeIntLe(header, 28, byteRate)
        writeShortLe(header, 32, 4)
        writeShortLe(header, 34, 16)
        // data
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

  private class DeckDspState(private val sampleRate: Int) {
    var beatPhase = 0f
    var beatIndex = 0
    var kickPhase = 0f
    var bassPhase = 0f
    var melodyPhase = 0f
    var vocalPhase = 0f
    var noiseSeed = 1337

    private var lpfState = 0f
    private var hpfPrevIn = 0f
    private var hpfState = 0f

    private val echoBuf = FloatArray((sampleRate * 0.32f).toInt())
    private var echoIdx = 0

    private val flangeBuf = FloatArray((sampleRate * 0.02f).toInt())
    private var flangeIdx = 0
    private var lfoPhase = 0f

    fun applyEffects(input: Float, deck: DeckState): Float {
      var x = input

      // 1-pole LPF / HPF filter
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

      // Echo delay line
      val echoWet = deck.effects.echo
      val delayedEcho = echoBuf[echoIdx]
      echoBuf[echoIdx] = x + delayedEcho * 0.42f
      echoIdx = (echoIdx + 1) % echoBuf.size
      if (echoWet > 0.01f) {
        x = x * (1f - echoWet * 0.35f) + delayedEcho * echoWet * 0.65f
      }

      // LFO-modulated Flanger
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
