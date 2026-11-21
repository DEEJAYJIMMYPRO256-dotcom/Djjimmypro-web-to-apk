package com.example.dj.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.example.dj.model.DeckId
import com.example.dj.model.StemType

data class ShortcutDefinition(
  val keys: String,
  val category: String,
  val description: String,
)

sealed interface DjShortcutCommand {
  data class NudgeCrossfader(val delta: Float) : DjShortcutCommand
  data class SetCrossfader(val value: Float, val label: String) : DjShortcutCommand
  data object CycleCrossfaderCurve : DjShortcutCommand
  data class TogglePlay(val deckId: DeckId) : DjShortcutCommand
  data class CueDeck(val deckId: DeckId) : DjShortcutCommand
  data class SyncDeck(val deckId: DeckId) : DjShortcutCommand
  data class TriggerHotCue(val deckId: DeckId, val slot: Int) : DjShortcutCommand
  data class ToggleStemMute(val deckId: DeckId, val stem: StemType) : DjShortcutCommand
  data object ToggleAutoMix : DjShortcutCommand
  data object ToggleShortcutsHelp : DjShortcutCommand
}

object DjKeyboardShortcuts {
  const val FINE_NUDGE_STEP = 0.05f
  const val FAST_NUDGE_STEP = 0.15f

  val catalog: List<ShortcutDefinition> =
    listOf(
      ShortcutDefinition("Space / Q", "Decks", "Play / Pause Deck A"),
      ShortcutDefinition("P / Shift+Space", "Decks", "Play / Pause Deck B"),
      ShortcutDefinition("C / Shift+C", "Decks", "Cue Deck A (Shift+C = Deck B)"),
      ShortcutDefinition("S / Shift+S / K", "Decks", "Sync BPM & Phase (S = Deck A, Shift+S or K = Deck B)"),
      ShortcutDefinition("← / ,", "Crossfader", "Nudge crossfader left toward Deck A (-5%)"),
      ShortcutDefinition("→ / .", "Crossfader", "Nudge crossfader right toward Deck B (+5%)"),
      ShortcutDefinition("Shift + ← / →", "Crossfader", "Fast crossfader sweep (±15%)"),
      ShortcutDefinition("[", "Crossfader", "Instant cut full left to Deck A (0%)"),
      ShortcutDefinition("]", "Crossfader", "Instant cut full right to Deck B (100%)"),
      ShortcutDefinition("\\", "Crossfader", "Snap crossfader to Center (50% equal mix)"),
      ShortcutDefinition("X", "Crossfader", "Cycle Crossfader Curve (Linear / Exp / Const Power)"),
      ShortcutDefinition("1 – 4", "Hot Cues", "Trigger or set Deck A Hot Cues 1–4"),
      ShortcutDefinition("7 – 0", "Hot Cues", "Trigger or set Deck B Hot Cues 1–4"),
      ShortcutDefinition("Shift + 1..4", "Stems", "Mute/Unmute Deck A Vocals, Drums, Bass, Melody"),
      ShortcutDefinition("M", "Mixer", "Toggle Auto-Mix crossfade"),
      ShortcutDefinition("? / F1", "Mixer", "Show / Hide Keyboard Shortcuts guide"),
    )

  fun mapNativeKeyEvent(nativeEvent: android.view.KeyEvent): DjShortcutCommand? =
    mapKeyEvent(KeyEvent(nativeEvent))

  fun mapKeyEvent(event: KeyEvent): DjShortcutCommand? {
    if (event.type != KeyEventType.KeyDown) return null
    val shift = event.isShiftPressed
    val step = if (shift) FAST_NUDGE_STEP else FINE_NUDGE_STEP

    return when (event.key) {
      // Crossfader nudge left/right
      Key.DirectionLeft,
      Key.Comma -> DjShortcutCommand.NudgeCrossfader(-step)
      Key.DirectionRight,
      Key.Period -> DjShortcutCommand.NudgeCrossfader(step)

      // Crossfader instant cuts & center
      Key.LeftBracket -> DjShortcutCommand.SetCrossfader(0.0f, "CUT DECK A (0%)")
      Key.RightBracket -> DjShortcutCommand.SetCrossfader(1.0f, "CUT DECK B (100%)")
      Key.Backslash -> DjShortcutCommand.SetCrossfader(0.5f, "CENTER XFADE (50%)")

      // Crossfader curve
      Key.X -> DjShortcutCommand.CycleCrossfaderCurve

      // Deck transport
      Key.Spacebar ->
        if (shift) DjShortcutCommand.TogglePlay(DeckId.B)
        else DjShortcutCommand.TogglePlay(DeckId.A)
      Key.Q -> DjShortcutCommand.TogglePlay(DeckId.A)
      Key.P -> DjShortcutCommand.TogglePlay(DeckId.B)

      // Cue
      Key.C ->
        if (shift) DjShortcutCommand.CueDeck(DeckId.B)
        else DjShortcutCommand.CueDeck(DeckId.A)

      // Sync
      Key.S ->
        if (shift) DjShortcutCommand.SyncDeck(DeckId.B)
        else DjShortcutCommand.SyncDeck(DeckId.A)
      Key.K -> DjShortcutCommand.SyncDeck(DeckId.B)

      // Deck A Hot Cues / Stem Mutes
      Key.One ->
        if (shift) DjShortcutCommand.ToggleStemMute(DeckId.A, StemType.VOCALS)
        else DjShortcutCommand.TriggerHotCue(DeckId.A, 1)
      Key.Two ->
        if (shift) DjShortcutCommand.ToggleStemMute(DeckId.A, StemType.DRUMS)
        else DjShortcutCommand.TriggerHotCue(DeckId.A, 2)
      Key.Three ->
        if (shift) DjShortcutCommand.ToggleStemMute(DeckId.A, StemType.BASS)
        else DjShortcutCommand.TriggerHotCue(DeckId.A, 3)
      Key.Four ->
        if (shift) DjShortcutCommand.ToggleStemMute(DeckId.A, StemType.MELODY)
        else DjShortcutCommand.TriggerHotCue(DeckId.A, 4)

      // Deck B Hot Cues
      Key.Seven -> DjShortcutCommand.TriggerHotCue(DeckId.B, 1)
      Key.Eight -> DjShortcutCommand.TriggerHotCue(DeckId.B, 2)
      Key.Nine -> DjShortcutCommand.TriggerHotCue(DeckId.B, 3)
      Key.Zero -> DjShortcutCommand.TriggerHotCue(DeckId.B, 4)

      // Auto-Mix & Help
      Key.M -> DjShortcutCommand.ToggleAutoMix
      Key.F1 -> DjShortcutCommand.ToggleShortcutsHelp
      Key.Slash -> if (shift) DjShortcutCommand.ToggleShortcutsHelp else null

      else -> null
    }
  }
}
