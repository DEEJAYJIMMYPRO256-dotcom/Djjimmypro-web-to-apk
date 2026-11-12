package com.example

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import com.example.dj.audio.BpmSyncEngine
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.input.DjShortcutCommand
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.DeckId
import com.example.dj.model.StemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DjKeyboardShortcutsTest {

  private fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent {
    return KeyEvent(
      AndroidKeyEvent(0L, 0L, AndroidKeyEvent.ACTION_DOWN, keyCode, 0, metaState)
    )
  }

  @Test
  fun `crossfader nudge left and right map to fine and fast steps`() {
    val left = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_DPAD_LEFT))
    assertEquals(DjShortcutCommand.NudgeCrossfader(-DjKeyboardShortcuts.FINE_NUDGE_STEP), left)

    val right = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
    assertEquals(DjShortcutCommand.NudgeCrossfader(DjKeyboardShortcuts.FINE_NUDGE_STEP), right)

    val fastLeft =
      DjKeyboardShortcuts.mapKeyEvent(
        keyDown(AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.META_SHIFT_ON)
      )
    assertEquals(DjShortcutCommand.NudgeCrossfader(-DjKeyboardShortcuts.FAST_NUDGE_STEP), fastLeft)
  }

  @Test
  fun `crossfader instant cuts and center snap map properly`() {
    val cutA = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_LEFT_BRACKET))
    assertTrue(cutA is DjShortcutCommand.SetCrossfader && cutA.value == 0f)

    val cutB = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_RIGHT_BRACKET))
    assertTrue(cutB is DjShortcutCommand.SetCrossfader && cutB.value == 1f)

    val center = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_C))
    assertTrue(center is DjShortcutCommand.SetCrossfader && center.value == 0.5f)

    val curve = DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_X))
    assertEquals(DjShortcutCommand.CycleCrossfaderCurve, curve)
  }

  @Test
  fun `deck transport and stem mute shortcuts map properly`() {
    assertEquals(
      DjShortcutCommand.TogglePlay(DeckId.A),
      DjKeyboardShortcuts.mapKeyEvent(keyDown(AndroidKeyEvent.KEYCODE_SPACE)),
    )
    assertEquals(
      DjShortcutCommand.TogglePlay(DeckId.B),
      DjKeyboardShortcuts.mapKeyEvent(
        keyDown(AndroidKeyEvent.KEYCODE_SPACE, AndroidKeyEvent.META_SHIFT_ON)
      ),
    )
    assertEquals(
      DjShortcutCommand.ToggleStemMute(DeckId.A, StemType.BASS),
      DjKeyboardShortcuts.mapKeyEvent(
        keyDown(AndroidKeyEvent.KEYCODE_3, AndroidKeyEvent.META_SHIFT_ON)
      ),
    )
  }

  @Test
  fun `crossfader curves compute expected center gains`() {
    val (linA, linB) = CrossfaderCurve.LINEAR.computeGains(0.5f)
    assertEquals(0.5f, linA, 0.01f)
    assertEquals(0.5f, linB, 0.01f)

    val (cpA, cpB) = CrossfaderCurve.CONSTANT_POWER.computeGains(0.5f)
    assertEquals(0.707f, cpA, 0.01f)
    assertEquals(0.707f, cpB, 0.01f)
  }

  @Test
  fun `bpm sync handles octave matching`() {
    val offset = BpmSyncEngine.calculateSyncOffset(124f, 62f, maxRange = 0.08f)
    assertEquals(0f, offset, 0.001f)
  }
}
