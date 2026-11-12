package com.example.dj.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.ClassicCrimson
import com.example.ui.theme.ClassicGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMint
import com.example.ui.theme.StudioConsole
import com.example.ui.theme.StudioObsidian
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class DeckId(val label: String) {
  A("DECK A"),
  B("DECK B"),
}

enum class StemType(val shortLabel: String, val fullName: String) {
  VOCALS("VOX", "Vocals"),
  DRUMS("DRM", "Drums"),
  BASS("BAS", "Bass"),
  MELODY("MEL", "Melody"),
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

data class DeckEffectsState(
  val filter: Float = 0f, // -1f (LPF) .. 0f (Bypass) .. +1f (HPF)
  val echo: Float = 0f, // 0f .. 1f
  val flanger: Float = 0f, // 0f .. 1f
)

data class HotCue(
  val slot: Int,
  val progress: Float,
  val colorHex: Long,
)

data class LoopState(
  val isActive: Boolean = false,
  val beats: Float = 4f,
  val startProgress: Float = 0f,
  val endProgress: Float = 0f,
)

enum class PitchRange(val maxOffset: Float, val label: String) {
  PCT_8(0.08f, "±8%"),
  PCT_16(0.16f, "±16%"),
  PCT_50(0.50f, "±50%"),
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
      EXPONENTIAL -> {
        val left = (1f - x) * (1f - x)
        val right = x * x
        left to right
      }
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
  DARK_MODE("Dark Mode", StudioObsidian, StudioConsole, ElectricCyan, AmberOrange),
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
      DARK_MODE -> CLASSIC_ANALOG
      CLASSIC_ANALOG -> NEON_FUTURE
      NEON_FUTURE -> DARK_MODE
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
  val waveformPeaks: List<Float> = generateWaveform(id, 64),
  val beatGrid: List<Float> = computeBeatGrid(bpm, durationSeconds),
) {
  fun computeAutoGainTrim(): Float {
    if (waveformPeaks.isEmpty()) return 1.0f
    val sumSq = waveformPeaks.fold(0f) { acc, v -> acc + v * v }
    val rms = sqrt(sumSq / waveformPeaks.size).coerceAtLeast(0.12f)
    return (0.62f / rms).coerceIn(0.45f, 1.55f)
  }

  fun snapToBeatGrid(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (beatGrid.isEmpty()) return p
    return beatGrid.minByOrNull { abs(it - p) } ?: p
  }

  companion object {
    fun generateWaveform(seed: String, count: Int = 64): List<Float> {
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
) {
  val effectiveBpm: Float
    get() = (track.bpm * (1f + tempoOffset)).coerceIn(40f, 240f)

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
  val eqPresets: List<EqPreset> = DefaultTracks.defaultEqPresets,
  val sessionHistory: List<SessionHistoryItem> = emptyList(),
  val masterPeakLeft: Float = 0f,
  val masterPeakRight: Float = 0f,
  val isRecording: Boolean = false,
  val lastRecordingPath: String? = null,
  val autoMixEnabled: Boolean = false,
  val autoMixDurationSeconds: Int = 8,
  val isAutoMixing: Boolean = false,
  val mixerTheme: MixerTheme = MixerTheme.DARK_MODE,
  val powerSavingMode: Boolean = false,
  val connectedMidiDevice: String? = null,
  val shortcutFeedback: KeyboardShortcutFeedback? = null,
  val showShortcutsDialog: Boolean = false,
)

object DefaultTracks {
  val tracks: List<DjTrack> =
    listOf(
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
      DjTrack(
        id = "track_subway_echoes",
        title = "Subway Echoes",
        artist = "Metro Nine",
        bpm = 128f,
        musicalKey = "Dm",
        genre = "Melodic Techno",
        durationSeconds = 248,
        baseFreqHz = 293.66f,
      ),
      DjTrack(
        id = "track_solar_flare",
        title = "Solar Flare",
        artist = "Aether Club",
        bpm = 122f,
        musicalKey = "F",
        genre = "Progressive",
        durationSeconds = 204,
        baseFreqHz = 174.61f,
      ),
      DjTrack(
        id = "track_velvet_groove",
        title = "Velvet Groove",
        artist = "Nadia Ray",
        bpm = 96f,
        musicalKey = "Gm",
        genre = "Hip-Hop / Soul",
        durationSeconds = 192,
        baseFreqHz = 196f,
      ),
      DjTrack(
        id = "track_chrome_static",
        title = "Chrome Static",
        artist = "Volt District",
        bpm = 132f,
        musicalKey = "Em",
        genre = "Electro Breaks",
        durationSeconds = 224,
        baseFreqHz = 329.63f,
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
