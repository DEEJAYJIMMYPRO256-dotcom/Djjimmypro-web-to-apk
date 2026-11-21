package com.example.dj.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ClassicCrimson
import com.example.ui.theme.ClassicGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMint
import com.example.ui.theme.StudioConsole
import com.example.ui.theme.StudioObsidian
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class DeckId(val label: String, val numberLabel: String) {
  A("DECK 1", "1"),
  B("DECK 2", "2"),
}

enum class StemType(val shortLabel: String, val fullName: String) {
  VOCALS("VOX", "Vocals"),
  DRUMS("DRM", "Drums"),
  BASS("BAS", "Bass"),
  MELODY("MEL", "Melody"),
}

enum class StemIsolationPreset(val label: String) {
  FULL("FULL MIX"),
  INSTRUMENTAL("NO VOICE"),
  ACAPELLA("VOX ONLY"),
  DRUMS_ONLY("DRUMS ONLY"),
  BASS_ONLY("BASS ONLY"),
}

enum class ProModeTab(val label: String) {
  DJ("DJ"),
  FX("FX"),
  STEMS("Stems"),
  SAMPLER("Sampler"),
  MASTER("Master"),
}

enum class LibraryCrate(val label: String) {
  ALL("ALL"),
  TITLE("Title"),
  ARTISTS("Artists"),
  GENRES("Genres"),
  HARMONIC("Harmonic"),
  LOCAL("Local Files"),
}

enum class LibraryBottomTab(val label: String) {
  FILES("Files"),
  BROWSE("Browse"),
  PREPARE("Prepare"),
  HISTORY("History"),
}

enum class LibrarySortColumn {
  INDEX,
  TITLE,
  ARTIST,
  BPM,
  KEY,
  LENGTH,
}

enum class SamplerSound(val slot: Int, val title: String, val colorHex: Long) {
  SIRENI(1, "Sireni", 0xFF0091EA),
  CROWD(2, "Crowd", 0xFFAA00FF),
  APPLAUSE(3, "Applause", 0xFF00C853),
  WHISTLE(4, "Whistle", 0xFFFF6D00),
}

data class StemChannelState(
  val level: Float = 1.0f,
  val isMuted: Boolean = false,
  val isSolo: Boolean = false,
)

enum class EqBand {
  LOW,
  MID,
  HIGH,
}

data class EqState(
  val low: Float = 1.0f,
  val mid: Float = 1.0f,
  val high: Float = 1.0f,
)

enum class StemFxType(val label: String, val multilineLabel: String) {
  VOCAL_ECHO("VOCAL ECHO", "VOCAL\nECHO"),
  INST_ECHO("INST ECHO", "INST\nECHO"),
  INST_BRAKER("INST BRAKER", "INST\nBRAKER"),
  DRUMS_ECHO("DRUMS ECHO", "DRUMS\nECHO"),
}

data class DeckEffectsState(
  val filter: Float = 0f, // -1f (LPF) .. 0f (Bypass) .. +1f (HPF)
  val echo: Float = 0f, // 0f .. 1f
  val flanger: Float = 0f, // 0f .. 1f
  val vocalEcho: Boolean = false,
  val instEcho: Boolean = false,
  val instBrake: Boolean = false,
  val drumsEcho: Boolean = false,
) {
  fun isStemFxActive(type: StemFxType): Boolean =
    when (type) {
      StemFxType.VOCAL_ECHO -> vocalEcho
      StemFxType.INST_ECHO -> instEcho
      StemFxType.INST_BRAKER -> instBrake
      StemFxType.DRUMS_ECHO -> drumsEcho
    }
}

data class HotCue(
  val slot: Int,
  val progress: Float,
  val colorHex: Long,
  val label: String? = null,
)

object SeratoHotCueDefaults {
  data class SlotMeta(val slot: Int, val label: String, val colorHex: Long, val defaultArmedColor: Boolean)

  val slots: List<SlotMeta> =
    listOf(
      SlotMeta(1, "START", 0xFFB91C1C, true),
      SlotMeta(2, "INTRO", 0xFFD96B11, true),
      SlotMeta(3, "BUILD", 0xFF164BB8, true),
      SlotMeta(4, "DROP", 0xFFAE2276, true),
      SlotMeta(5, "BREAK", 0xFF198737, true),
      SlotMeta(6, "CUE 6", 0xFF0E7490, false),
      SlotMeta(7, "CUE 7", 0xFF7C3AED, false),
      SlotMeta(8, "CUE 8", 0xFFCA8A04, false),
    )

  fun metaForSlot(slot: Int): SlotMeta = slots.getOrElse(slot - 1) { slots[0] }
}

data class LoopState(
  val isActive: Boolean = false,
  val beats: Float = 4f,
  val startProgress: Float = 0f,
  val endProgress: Float = 0f,
)

enum class PitchRange(val maxOffset: Float, val label: String) {
  PCT_8(0.08f, "±8"),
  PCT_16(0.16f, "±16"),
  PCT_50(0.50f, "±50"),
  ;

  fun next(): PitchRange =
    when (this) {
      PCT_8 -> PCT_16
      PCT_16 -> PCT_50
      PCT_50 -> PCT_8
    }
}

enum class CrossfaderCurve(val label: String) {
  LINEAR("Linear"),
  EXPONENTIAL("Exponential"),
  CONSTANT_POWER("Constant Power"),
  ;

  fun next(): CrossfaderCurve =
    when (this) {
      LINEAR -> EXPONENTIAL
      EXPONENTIAL -> CONSTANT_POWER
      CONSTANT_POWER -> LINEAR
    }

  fun computeGains(crossfader: Float): Pair<Float, Float> {
    val x = crossfader.coerceIn(0f, 1f)
    return when (this) {
      LINEAR -> (1f - x) to x
      EXPONENTIAL -> ((1f - x) * (1f - x)) to (x * x)
      CONSTANT_POWER -> {
        val angle = x * (Math.PI / 2.0)
        kotlin.math.cos(angle).toFloat() to kotlin.math.sin(angle).toFloat()
      }
    }
  }
}

enum class MixerTheme(
  val label: String,
  val background: Color,
  val consoleSurface: Color,
  val deckAAccent: Color,
  val deckBAccent: Color,
) {
  SERATO_PRO(
    "Serato Pro",
    Color(0xFF06080D),
    Color(0xFF0E131D),
    Color(0xFF18B0FF),
    Color(0xFFFF2E93),
  ),
  DARK_MODE("Dark Mode", StudioObsidian, StudioConsole, ElectricCyan, Color(0xFFFF7A00)),
  CLASSIC_ANALOG(
    "Classic Analog",
    Color(0xFF12100E),
    Color(0xFF1D1814),
    ClassicGold,
    ClassicCrimson,
  ),
  NEON_FUTURE(
    "Neon Future",
    Color(0xFF090714),
    Color(0xFF130F26),
    NeonMint,
    NeonMagenta,
  ),
  ;

  fun next(): MixerTheme =
    when (this) {
      SERATO_PRO -> DARK_MODE
      DARK_MODE -> CLASSIC_ANALOG
      CLASSIC_ANALOG -> NEON_FUTURE
      NEON_FUTURE -> SERATO_PRO
    }
}

data class DjTrack(
  val id: String,
  val title: String,
  val artist: String,
  val bpm: Float,
  val musicalKey: String,
  val genre: String,
  val durationSeconds: Int,
  val baseFreqHz: Float,
  val localUri: String? = null,
  val waveformPeaks: List<Float> = generateWaveform(id, 72),
  val beatGrid: List<Float> = computeBeatGrid(bpm, durationSeconds),
) {
  fun computeAutoGainTrim(): Float {
    if (waveformPeaks.isEmpty()) return 1.0f
    val sumSq = waveformPeaks.fold(0f) { acc, v -> acc + v * v }
    val rms = sqrt(sumSq / waveformPeaks.size).coerceAtLeast(0.12f)
    return (0.62f / rms).coerceIn(0.45f, 1.55f)
  }

  fun formattedDuration(): String {
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    return String.format("%02d:%02d", mins, secs)
  }

  companion object {
    fun generateWaveform(seed: String, count: Int = 72): List<Float> {
      val hash = abs(seed.hashCode()).coerceAtLeast(1)
      return List(count) { i ->
        val waveA = abs(sin((i * 0.37) + (hash % 19)))
        val waveB = abs(sin((i * 0.89) + (hash % 7)))
        (0.22 + 0.78 * (0.6 * waveA + 0.4 * waveB)).toFloat().coerceIn(0.18f, 1.0f)
      }
    }

    fun computeBeatGrid(bpm: Float, durationSeconds: Int): List<Float> {
      val safeBpm = bpm.coerceIn(60f, 200f)
      val safeDuration = durationSeconds.coerceAtLeast(10)
      val totalBeats = ((safeBpm / 60f) * safeDuration).roundToInt().coerceIn(8, 2000)
      return List(totalBeats) { idx -> (idx.toFloat() / totalBeats.toFloat()).coerceIn(0f, 1f) }
    }
  }
}

data class DeckState(
  val deckId: DeckId,
  val track: DjTrack,
  val isPlaying: Boolean = false,
  val playheadProgress: Float = 0f,
  val tempoOffset: Float = 0f,
  val pitchRange: PitchRange = PitchRange.PCT_8,
  val trimGain: Float = 1.0f,
  val channelVolume: Float = 1.0f,
  val stems: Map<StemType, StemChannelState> =
    StemType.entries.associateWith { StemChannelState() },
  val eq: EqState = EqState(),
  val effects: DeckEffectsState = DeckEffectsState(),
  val hotCues: Map<Int, HotCue> = emptyMap(),
  val quantizeEnabled: Boolean = true,
  val scratchMode: Boolean = false,
  val keyLockEnabled: Boolean = true,
  val loop: LoopState = LoopState(),
  val jogRotationDeg: Float = 0f,
  val quickIsolateEnabled: Boolean = false,
  val preQuickIsolateVocalLevel: Float? = null,
  val preQuickIsolateFilter: Float? = null,
  val keyShiftSemitones: Int = 0,
  val isKeySynced: Boolean = false,
  val isAutoSynced: Boolean = false,
  val livePeakIntensity: Float = 0f,
  val inputSignalLevel: Float = 0f,
) {
  companion object {
    const val QUICK_ISOLATE_HPF = 0.18f
  }

  val keyPitchMultiplier: Float
    get() = 2.0.pow(keyShiftSemitones / 12.0).toFloat()

  val effectiveMusicalKey: String
    get() =
      if (keyShiftSemitones == 0) track.musicalKey
      else CamelotKeyMath.transposeMusicalKey(track.musicalKey, keyShiftSemitones)

  val isQuickIsolateActive: Boolean
    get() =
      quickIsolateEnabled &&
        (stems[StemType.VOCALS]?.isMuted == true) &&
        (stems[StemType.VOCALS]?.level ?: 1f) <= 0.01f &&
        effects.filter > 0.05f

  val effectiveBpm: Float
    get() = (track.bpm * (1f + tempoOffset)).coerceIn(40f, 240f)

  val isVoiceRemoved: Boolean
    get() {
      val vox = stems[StemType.VOCALS] ?: return false
      val hasInst =
        listOf(StemType.DRUMS, StemType.BASS, StemType.MELODY).any {
          val s = stems[it]
          s != null && !s.isMuted && s.level > 0.05f
        }
      return vox.isMuted && hasInst
    }

  val isInstrumentalRemoved: Boolean
    get() {
      val vox = stems[StemType.VOCALS] ?: return false
      val allInstOff =
        listOf(StemType.DRUMS, StemType.BASS, StemType.MELODY).all {
          val s = stems[it]
          s == null || s.isMuted || s.level <= 0.05f
        }
      return (!vox.isMuted && vox.level > 0.05f && (vox.isSolo || allInstOff))
    }

  fun effectiveStemGain(stem: StemType): Float {
    val state = stems[stem] ?: return 1f
    if (state.isMuted) return 0f
    val hasAnySolo = stems.values.any { it.isSolo }
    if (hasAnySolo && !state.isSolo) return 0f
    return state.level.coerceIn(0f, 1f)
  }

  fun isStemAudible(stem: StemType): Boolean = effectiveStemGain(stem) > 0.01f

  fun formattedElapsed(): String {
    val sec = (playheadProgress * track.durationSeconds).coerceAtLeast(0f)
    val mins = (sec / 60f).toInt()
    val remSec = sec - mins * 60f
    return String.format("%02d:%04.1f", mins, remSec)
  }

  fun formattedRemaining(): String {
    val rem = ((1f - playheadProgress) * track.durationSeconds).coerceAtLeast(0f)
    val mins = (rem / 60f).toInt()
    val remSec = rem - mins * 60f
    return String.format("-%02d:%04.1f", mins, remSec)
  }

  fun snapToBeatGrid(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (!quantizeEnabled) return p
    val totalBeats = ((effectiveBpm / 60f) * track.durationSeconds).coerceAtLeast(1f)
    val nearestBeat = (p * totalBeats).roundToInt()
    return (nearestBeat / totalBeats).coerceIn(0f, 1f)
  }

  fun nextCueCountdown(): String? {
    val upcoming =
      hotCues.values
        .filter { it.progress > playheadProgress + 0.002f }
        .minByOrNull { it.progress } ?: return null
    val deltaSeconds = (upcoming.progress - playheadProgress) * track.durationSeconds
    val beatsRemaining = (deltaSeconds * (effectiveBpm / 60f)).roundToInt().coerceAtLeast(1)
    val bars = beatsRemaining / 4
    val beats = beatsRemaining % 4
    return "NEXT CUE: ${bars}B ${beats}b"
  }
}

data class BeatPosition(
  val bar: Int,
  val beatInBar: Int,
  val phase: Float,
  val totalBeatIndex: Int,
) {
  val formattedBarBeat: String
    get() = "B$bar.$beatInBar"
}

object BeatSyncedWaveformMath {
  fun beatPosition(
    playheadProgress: Float,
    effectiveBpm: Float,
    durationSeconds: Int,
  ): BeatPosition {
    val safeProgress = playheadProgress.coerceIn(0f, 1f)
    val safeBpm = effectiveBpm.coerceIn(40f, 240f)
    val safeDuration = durationSeconds.coerceAtLeast(1)
    val totalBeats = (safeBpm / 60f) * safeDuration
    val exactBeat = safeProgress * totalBeats
    val beatIdx = exactBeat.toInt().coerceAtLeast(0)
    val phase = (exactBeat - beatIdx).coerceIn(0f, 0.9999f)
    val bar = (beatIdx / 4) + 1
    val beatInBar = (beatIdx % 4) + 1
    return BeatPosition(
      bar = bar,
      beatInBar = beatInBar,
      phase = phase,
      totalBeatIndex = beatIdx,
    )
  }

  fun sampleEnvelope(peaks: List<Float>, progress: Float): Float {
    if (peaks.isEmpty()) return 0f
    if (peaks.size == 1) return peaks[0].coerceIn(0f, 1f)
    val exactIdx = (progress.coerceIn(0f, 1f) * (peaks.size - 1)).coerceIn(0f, (peaks.size - 1).toFloat())
    val idx0 = exactIdx.toInt()
    val idx1 = (idx0 + 1).coerceAtMost(peaks.size - 1)
    val frac = exactIdx - idx0
    return (peaks[idx0] * (1f - frac) + peaks[idx1] * frac).coerceIn(0f, 1f)
  }

  fun computeFallbackPeakIntensity(deck: DeckState): Float {
    if (!deck.isPlaying) return 0f
    val env = sampleEnvelope(deck.track.waveformPeaks, deck.playheadProgress).coerceAtLeast(0.18f)
    val beat = beatPosition(deck.playheadProgress, deck.effectiveBpm, deck.track.durationSeconds)
    val beatPunch = kotlin.math.exp((-beat.phase * 4.2f).toDouble()).toFloat()
    val activeStemRatio =
      StemType.entries.sumOf { deck.effectiveStemGain(it).toDouble() }.toFloat() /
        StemType.entries.size.toFloat()
    val eqDrive = ((deck.eq.low + deck.eq.mid + deck.eq.high) / 3f).coerceIn(0.15f, 1.4f)
    val raw = env * (0.58f + 0.42f * beatPunch) * deck.trimGain * deck.channelVolume * activeStemRatio * eqDrive
    return raw.coerceIn(0.02f, 1f)
  }
}

object TrimGainMath {
  const val MIN_TRIM = 0.2f
  const val MAX_TRIM = 1.8f
  const val UNITY_TRIM = 1.0f

  fun clamp(trimGain: Float): Float = trimGain.coerceIn(MIN_TRIM, MAX_TRIM)

  fun toDecibels(trimGain: Float): Float {
    val safe = clamp(trimGain)
    return (20.0 * kotlin.math.log10(safe.toDouble())).toFloat()
  }

  fun formatDb(trimGain: Float): String {
    val db = toDecibels(trimGain)
    if (abs(db) < 0.05f) return "0.0 dB"
    return String.format(java.util.Locale.US, "%+.1f dB", db)
  }

  /**
   * Scales an audio sample by the channel's input gain trim before it reaches
   * the mixer section (stems, EQ, FX, channel fader, crossfader), with smooth
   * soft-knee saturation above 0.88f to preserve analog-style headroom.
   */
  fun applyPreMixerTrim(sample: Float, trimGain: Float): Float {
    val scaled = sample * clamp(trimGain)
    val mag = abs(scaled)
    if (mag <= 0.88f) return scaled
    val excess = mag - 0.88f
    val softLimited = 0.88f + 0.11f * kotlin.math.tanh((excess / 0.11f).toDouble()).toFloat()
    return if (scaled >= 0f) softLimited else -softLimited
  }

  /**
   * Pre-fader, pre-mixer input level estimation driven solely by track envelope,
   * beat punch, and input trim gain (unaffected by channelVolume or crossfader).
   */
  fun computeFallbackInputLevel(deck: DeckState): Float {
    if (!deck.isPlaying) return 0f
    val env =
      BeatSyncedWaveformMath.sampleEnvelope(deck.track.waveformPeaks, deck.playheadProgress)
        .coerceAtLeast(0.18f)
    val beat =
      BeatSyncedWaveformMath.beatPosition(
        deck.playheadProgress,
        deck.effectiveBpm,
        deck.track.durationSeconds,
      )
    val beatPunch = kotlin.math.exp((-beat.phase * 4.2f).toDouble()).toFloat()
    val raw = env * (0.60f + 0.40f * beatPunch) * clamp(deck.trimGain)
    return raw.coerceIn(0.02f, 1f)
  }
}

data class EqPreset(
  val id: Int = 0,
  val name: String,
  val low: Float,
  val mid: Float,
  val high: Float,
)

data class SessionHistoryItem(
  val id: Int,
  val trackTitle: String,
  val artist: String,
  val deckName: String,
  val playedAtMs: Long,
)

data class KeyboardShortcutFeedback(
  val label: String,
  val timestampMs: Long,
)

data class DjUiState(
  val deckA: DeckState = DeckState(DeckId.A, DefaultTracks.tracks[0]),
  val deckB: DeckState = DeckState(DeckId.B, DefaultTracks.tracks[1]),
  val crossfader: Float = 0.5f,
  val crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER,
  val libraryTracks: List<DjTrack> = DefaultTracks.tracks,
  val prepareTrackIds: Set<String> = emptySet(),
  val eqPresets: List<EqPreset> = DefaultTracks.defaultEqPresets,
  val sessionHistory: List<SessionHistoryItem> = emptyList(),
  val masterPeakLeft: Float = 0f,
  val masterPeakRight: Float = 0f,
  val isRecording: Boolean = false,
  val lastRecordingPath: String? = null,
  val autoMixEnabled: Boolean = false,
  val autoMixDurationSeconds: Int = 8,
  val isAutoMixing: Boolean = false,
  val mixerTheme: MixerTheme = MixerTheme.SERATO_PRO,
  val powerSavingMode: Boolean = false,
  val connectedMidiDevice: String? = null,
  val shortcutFeedback: KeyboardShortcutFeedback? = null,
  val showShortcutsDialog: Boolean = false,
  val activeProTab: ProModeTab = ProModeTab.DJ,
  val selectedCrate: LibraryCrate = LibraryCrate.ALL,
  val bottomLibraryTab: LibraryBottomTab = LibraryBottomTab.BROWSE,
  val librarySearchQuery: String = "",
  val librarySortColumn: LibrarySortColumn = LibrarySortColumn.INDEX,
  val librarySortAscending: Boolean = true,
  val isRefreshingLibrary: Boolean = false,
  val lastSamplerTriggered: SamplerSound? = null,
  val selectedSampleBank: SamplePadBank = SamplePadBank.FX_SIRENS,
  val activeSamplePadIds: Set<String> = emptySet(),
  val samplePadLoopEnabled: Boolean = false,
  val samplePadVolume: Float = 0.9f,
  val lastSamplePadTriggeredId: String? = null,
  val waveformZoom: Float = 2.0f,
  val isWaveformNeutral: Boolean = true,
  val masterTappedBpm: Float? = null,
  val masterTapSource: String? = null,
  val masterTapCount: Int = 0,
  val lastMasterTapAtMs: Long = 0L,
) {
  val effectiveMasterBpm: Float
    get() =
      masterTappedBpm
        ?: when {
          deckA.isPlaying && !deckB.isPlaying -> deckA.effectiveBpm
          deckB.isPlaying && !deckA.isPlaying -> deckB.effectiveBpm
          deckA.isPlaying && deckB.isPlaying -> (deckA.effectiveBpm + deckB.effectiveBpm) / 2f
          crossfader <= 0.5f -> deckA.effectiveBpm
          else -> deckB.effectiveBpm
        }
}

object WaveformZoomConfig {
  val steps: List<Float> = listOf(1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 6.0f, 8.0f)
}

object DefaultTracks {
  val tracks: List<DjTrack> =
    listOf(
      DjTrack(
        id = "track_shu",
        title = "Shu!",
        artist = "Diamond Platnumz",
        bpm = 128f,
        musicalKey = "Am",
        genre = "Bongo Flava",
        durationSeconds = 204,
        baseFreqHz = 220f,
      ),
      DjTrack(
        id = "track_sukari",
        title = "Sukari",
        artist = "Zuchu",
        bpm = 126f,
        musicalKey = "Dm",
        genre = "Bongo Flava",
        durationSeconds = 252,
        baseFreqHz = 293.66f,
      ),
      DjTrack(
        id = "track_hodi",
        title = "Hodi",
        artist = "Mbosso",
        bpm = 122f,
        musicalKey = "Gm",
        genre = "Afropop",
        durationSeconds = 220,
        baseFreqHz = 196f,
      ),
      DjTrack(
        id = "track_single_again",
        title = "Single Again",
        artist = "Harmonize",
        bpm = 124f,
        musicalKey = "Dm",
        genre = "Bongo Flava",
        durationSeconds = 210,
        baseFreqHz = 293.66f,
      ),
      DjTrack(
        id = "track_unaweza",
        title = "Unaweza",
        artist = "Marioo",
        bpm = 120f,
        musicalKey = "Cm",
        genre = "Amapiano",
        durationSeconds = 230,
        baseFreqHz = 261.63f,
      ),
      DjTrack(
        id = "track_safari",
        title = "Safari",
        artist = "Bien",
        bpm = 118f,
        musicalKey = "Fm",
        genre = "Afro-Soul",
        durationSeconds = 222,
        baseFreqHz = 174.61f,
      ),
      DjTrack(
        id = "track_nakuja",
        title = "Nakuja",
        artist = "Jay Melody",
        bpm = 122f,
        musicalKey = "Gm",
        genre = "Bongo Flava",
        durationSeconds = 208,
        baseFreqHz = 196f,
      ),
      DjTrack(
        id = "track_mapenzi",
        title = "Mapenzi",
        artist = "Classic Jay",
        bpm = 126f,
        musicalKey = "Dm",
        genre = "Club Mix",
        durationSeconds = 215,
        baseFreqHz = 293.66f,
      ),
      DjTrack(
        id = "track_midnight_pulse",
        title = "Midnight Pulse",
        artist = "Kairo Vance",
        bpm = 124f,
        musicalKey = "Am",
        genre = "Deep House",
        durationSeconds = 216,
        baseFreqHz = 220f,
      ),
      DjTrack(
        id = "track_neon_horizon",
        title = "Neon Horizon",
        artist = "Sora & Lex",
        bpm = 126f,
        musicalKey = "C",
        genre = "Tech House",
        durationSeconds = 232,
        baseFreqHz = 261.63f,
      ),
    )

  val defaultEqPresets: List<EqPreset> =
    listOf(
      EqPreset(id = 1, name = "Flat", low = 1.0f, mid = 1.0f, high = 1.0f),
      EqPreset(id = 2, name = "Dance", low = 1.25f, mid = 0.90f, high = 1.20f),
      EqPreset(id = 3, name = "Hip-Hop", low = 1.35f, mid = 1.05f, high = 0.95f),
      EqPreset(id = 4, name = "Vocal", low = 0.75f, mid = 1.30f, high = 1.10f),
    )
}

object CamelotKeyMath {
  private val keyMap =
    mapOf(
      "Am" to "8A",
      "C" to "8B",
      "Em" to "9A",
      "G" to "9B",
      "Bm" to "10A",
      "D" to "10B",
      "F#m" to "11A",
      "A" to "11B",
      "C#m" to "12A",
      "E" to "12B",
      "G#m" to "1A",
      "B" to "1B",
      "D#m" to "2A",
      "F#" to "2B",
      "Bbm" to "3A",
      "Db" to "3B",
      "Fm" to "4A",
      "Ab" to "4B",
      "Cm" to "5A",
      "Eb" to "5B",
      "Gm" to "6A",
      "Bb" to "6B",
      "Dm" to "7A",
      "F" to "7B",
    )

  private val reverseKeyMap = keyMap.entries.associate { (k, v) -> v to k }

  fun toCamelot(musicalKey: String): String = keyMap[musicalKey] ?: "8A"

  fun compatibleCamelotKeys(camelotKey: String): Set<String> {
    val num = camelotKey.dropLast(1).toIntOrNull() ?: return setOf(camelotKey)
    val letter = camelotKey.lastOrNull() ?: 'A'
    val prev = if (num == 1) 12 else num - 1
    val next = if (num == 12) 1 else num + 1
    val otherLetter = if (letter == 'A') 'B' else 'A'
    return setOf("$num$letter", "$prev$letter", "$next$letter", "$num$otherLetter")
  }

  fun isCompatible(referenceMusicalKey: String, candidateMusicalKey: String): Boolean {
    val refCam = toCamelot(referenceMusicalKey)
    val candCam = toCamelot(candidateMusicalKey)
    return candCam in compatibleCamelotKeys(refCam)
  }

  fun transposeMusicalKey(musicalKey: String, semitones: Int): String {
    if (semitones == 0) return musicalKey
    val camelot = toCamelot(musicalKey)
    val num = camelot.dropLast(1).toIntOrNull() ?: return musicalKey
    val letter = camelot.lastOrNull() ?: 'A'
    val shiftedNum = ((num - 1 + semitones * 7) % 12 + 12) % 12 + 1
    return reverseKeyMap["$shiftedNum$letter"] ?: musicalKey
  }

  /**
   * Computes the shortest semitone shift (-5..+6) so [sourceMusicalKey] matches
   * the target deck's Camelot harmonic key (same key or exact relative major/minor).
   */
  fun calculateKeySyncSemitones(sourceMusicalKey: String, targetMusicalKey: String): Int {
    val srcNum = toCamelot(sourceMusicalKey).dropLast(1).toIntOrNull() ?: return 0
    val dstNum = toCamelot(targetMusicalKey).dropLast(1).toIntOrNull() ?: return 0
    if (srcNum == dstNum) return 0
    for (dist in 1..6) {
      if (((srcNum - 1 + dist * 7) % 12 + 12) % 12 + 1 == dstNum) return dist
      if (dist < 6 && ((srcNum - 1 - dist * 7) % 12 + 12) % 12 + 1 == dstNum) return -dist
    }
    return 0
  }
}
