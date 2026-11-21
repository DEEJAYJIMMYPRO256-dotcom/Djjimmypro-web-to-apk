package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.MixerTheme
import com.example.dj.model.TrimGainMath
import com.example.dj.ui.DjScreen
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.CrossfaderMixerCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
class ChannelGainTrimTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `TrimGainMath scales pre-mixer signal, formats dB, and computes pre-fader input level`() {
    assertEquals("0.0 dB", TrimGainMath.formatDb(1.0f))
    assertEquals("-6.0 dB", TrimGainMath.formatDb(0.5f))
    assertEquals("+5.1 dB", TrimGainMath.formatDb(1.8f))

    // Linear below 0.88 headroom knee, soft-limited below 1.0 above knee
    assertEquals(0.4f, TrimGainMath.applyPreMixerTrim(0.5f, 0.8f), 0.001f)
    val hotSample = TrimGainMath.applyPreMixerTrim(0.85f, 1.8f)
    assertTrue("Expected soft-limited headroom < 1.0f ($hotSample)", hotSample in 0.88f..0.995f)

    // Pre-mixer input level stays active even when channelVolume = 0f (pre-fader metering)
    val mutedFaderPlayingDeck =
      DeckState(
        deckId = DeckId.A,
        track = DefaultTracks.tracks[0],
        isPlaying = true,
        playheadProgress = 0.25f,
        trimGain = 1.0f,
        channelVolume = 0f,
      )
    val unityInput = TrimGainMath.computeFallbackInputLevel(mutedFaderPlayingDeck)
    assertTrue("Expected non-zero pre-mixer input even with channel fader at 0 ($unityInput)", unityInput > 0.1f)

    val boostedInput =
      TrimGainMath.computeFallbackInputLevel(mutedFaderPlayingDeck.copy(trimGain = 1.6f))
    val attenuatedInput =
      TrimGainMath.computeFallbackInputLevel(mutedFaderPlayingDeck.copy(trimGain = 0.4f))
    assertTrue("Expected boosted trim to raise pre-mixer input ($boostedInput > $unityInput)", boostedInput > unityInput)
    assertTrue("Expected attenuated trim to lower pre-mixer input ($attenuatedInput < $unityInput)", attenuatedInput < unityInput)
  }

  @Test
  fun `DjViewModel clamps per-channel trim gain, updates pre-mixer input level, and resets to unity`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    vm.setChannelVolume(DeckId.A, 0f)
    vm.togglePlay(DeckId.A)
    vm.setTrimGain(DeckId.A, 1.55f)
    val boostedState = vm.uiState.value.deckA
    assertEquals(1.55f, boostedState.trimGain, 0.001f)
    assertTrue("Expected live pre-mixer input level to update while playing", boostedState.inputSignalLevel > 0.2f)

    // Clamps to MIN_TRIM..MAX_TRIM
    vm.setTrimGain(DeckId.A, 3.0f)
    assertEquals(TrimGainMath.MAX_TRIM, vm.uiState.value.deckA.trimGain, 0.001f)
    vm.setTrimGain(DeckId.B, 0.05f)
    assertEquals(TrimGainMath.MIN_TRIM, vm.uiState.value.deckB.trimGain, 0.001f)

    // Reset returns to 1.0f unity
    vm.resetTrimGain(DeckId.A)
    assertEquals(TrimGainMath.UNITY_TRIM, vm.uiState.value.deckA.trimGain, 0.001f)
  }

  @Test
  fun `CrossfaderMixerCard renders CH1 and CH2 gain trim knobs with drag, semantics, and unity reset`() {
    var trimA by mutableFloatStateOf(1.0f)
    var trimB by mutableFloatStateOf(1.0f)

    composeTestRule.setContent {
      CrossfaderMixerCard(
        crossfader = 0.5f,
        crossfaderCurve = CrossfaderCurve.CONSTANT_POWER,
        peakLeft = 0.5f,
        peakRight = 0.5f,
        autoMixEnabled = false,
        autoMixDurationSeconds = 8,
        isAutoMixing = false,
        mixerTheme = MixerTheme.SERATO_PRO,
        shortcutFeedback = null,
        onCrossfaderChange = {},
        onCycleCurve = {},
        onToggleAutoMix = {},
        onCycleAutoMixDuration = {},
        onOpenShortcuts = {},
        deckATrimGain = trimA,
        deckBTrimGain = trimB,
        deckAInputLevel = (0.5f * trimA).coerceIn(0f, 1f),
        deckBInputLevel = (0.5f * trimB).coerceIn(0f, 1f),
        onDeckATrimChange = { trimA = it },
        onDeckBTrimChange = { trimB = it },
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("mixer_input_trim_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mixer_trim_knob_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("input_level_meter_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("input_level_meter_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mixer_trim_db_A").assertTextContains("0.0 dB")
    composeTestRule.onNodeWithTag("mixer_trim_db_B").assertTextContains("0.0 dB")

    // Drag up on CH 1 trim knob boosts gain
    composeTestRule.onNodeWithTag("mixer_trim_knob_A").performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    assertTrue("Expected swipe up to increase CH 1 trim ($trimA)", trimA > 1.05f)

    // Drag down on CH 2 trim knob attenuates gain
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()
    assertTrue("Expected swipe down to decrease CH 2 trim ($trimB)", trimB < 0.95f)

    // Tap dB badge resets CH 1 to 0.0 dB unity
    composeTestRule.onNodeWithTag("mixer_trim_db_A").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1.0f, trimA, 0.001f)
    composeTestRule.onNodeWithTag("mixer_trim_db_A").assertTextContains("0.0 dB")

    // Semantics SetProgress + double-tap reset on CH 2
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").performSemanticsAction(SemanticsActions.SetProgress) {
      it(1.6f)
    }
    composeTestRule.waitForIdle()
    assertEquals(1.6f, trimB, 0.001f)
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").performTouchInput { doubleClick() }
    composeTestRule.waitForIdle()
    assertEquals(1.0f, trimB, 0.001f)
  }

  @Test
  fun `DjScreen synchronizes mixer gain trim knobs with both deck channels`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent { DjScreen(viewModel = vm) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("mixer_trim_knob_A").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("mixer_trim_knob_A").performSemanticsAction(SemanticsActions.SetProgress) {
      it(1.45f)
    }
    composeTestRule.onNodeWithTag("mixer_trim_knob_B").performSemanticsAction(SemanticsActions.SetProgress) {
      it(0.50f)
    }
    composeTestRule.waitForIdle()

    assertEquals(1.45f, vm.uiState.value.deckA.trimGain, 0.001f)
    assertEquals(0.50f, vm.uiState.value.deckB.trimGain, 0.001f)
    composeTestRule.onNodeWithTag("mixer_trim_db_A").assertTextContains("+3.2 dB")
    composeTestRule.onNodeWithTag("mixer_trim_db_B").assertTextContains("-6.0 dB")
  }
}
