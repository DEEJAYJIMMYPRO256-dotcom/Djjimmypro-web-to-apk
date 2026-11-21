package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.MixerTheme
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
class CrossfaderBalanceTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `viewModel crossfader clamps position and adjusts Deck A vs Deck B audio balance`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Default center = 0.5f, Constant Power -> ~0.707 / 0.707
    assertEquals(0.5f, vm.uiState.value.crossfader, 0.001f)
    val (centerA, centerB) = vm.uiState.value.crossfaderCurve.computeGains(vm.uiState.value.crossfader)
    assertEquals(0.707f, centerA, 0.01f)
    assertEquals(0.707f, centerB, 0.01f)

    // Full left -> Deck A 100%, Deck B 0%
    vm.setCrossfader(-0.25f)
    assertEquals(0f, vm.uiState.value.crossfader, 0.001f)
    val (leftA, leftB) = vm.uiState.value.crossfaderCurve.computeGains(vm.uiState.value.crossfader)
    assertEquals(1f, leftA, 0.001f)
    assertEquals(0f, leftB, 0.001f)

    // Full right -> Deck A 0%, Deck B 100%
    vm.setCrossfader(1.5f)
    assertEquals(1f, vm.uiState.value.crossfader, 0.001f)
    val (rightA, rightB) = vm.uiState.value.crossfaderCurve.computeGains(vm.uiState.value.crossfader)
    assertEquals(0f, rightA, 0.001f)
    assertEquals(1f, rightB, 0.001f)

    // Cycle to Linear at 0.25 -> Deck A 75%, Deck B 25%
    vm.cycleCrossfaderCurve()
    assertEquals(CrossfaderCurve.LINEAR, vm.uiState.value.crossfaderCurve)
    vm.setCrossfader(0.25f)
    val (linA, linB) = vm.uiState.value.crossfaderCurve.computeGains(vm.uiState.value.crossfader)
    assertEquals(0.75f, linA, 0.001f)
    assertEquals(0.25f, linB, 0.001f)
  }

  @Test
  fun `HorizontalCrossfader supports drag, cut buttons, semantics progress, and live balance display`() {
    var xfade by mutableFloatStateOf(0.5f)
    var curve by mutableStateOf(CrossfaderCurve.CONSTANT_POWER)

    composeTestRule.setContent {
      CrossfaderMixerCard(
        crossfader = xfade,
        crossfaderCurve = curve,
        peakLeft = 0.6f,
        peakRight = 0.5f,
        autoMixEnabled = false,
        autoMixDurationSeconds = 8,
        isAutoMixing = false,
        mixerTheme = MixerTheme.SERATO_PRO,
        shortcutFeedback = null,
        onCrossfaderChange = { xfade = it },
        onCycleCurve = { curve = curve.next() },
        onToggleAutoMix = {},
        onCycleAutoMixDuration = {},
        onOpenShortcuts = {},
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("crossfader_balance_A").assertTextContains("D1 71%")
    composeTestRule.onNodeWithTag("crossfader_balance_B").assertTextContains("D2 71%")
    composeTestRule.onNodeWithTag("crossfader_balance_bar").assertIsDisplayed()

    // Cut A -> D1 100%, D2 0%
    composeTestRule.onNodeWithTag("crossfader_cut_a_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0f, xfade, 0.001f)
    composeTestRule.onNodeWithTag("crossfader_balance_A").assertTextContains("D1 100%")
    composeTestRule.onNodeWithTag("crossfader_balance_B").assertTextContains("D2 0%")

    // Swipe right on custom horizontal crossfader moves balance toward Deck 2
    composeTestRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()
    assertTrue("Expected swipe right to move crossfader toward Deck B ($xfade)", xfade > 0.7f)

    // Swipe left moves balance back toward Deck 1
    composeTestRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()
    assertTrue("Expected swipe left to move crossfader toward Deck A ($xfade)", xfade < 0.3f)

    // Semantics SetProgress + Curve cycling updates Linear 50% / 50%
    composeTestRule.onNodeWithTag("crossfader_curve_button").performClick()
    composeTestRule.onNodeWithTag("crossfader_slider").performSemanticsAction(SemanticsActions.SetProgress) {
      it(0.5f)
    }
    composeTestRule.waitForIdle()
    assertEquals(CrossfaderCurve.LINEAR, curve)
    composeTestRule.onNodeWithTag("crossfader_balance_A").assertTextContains("D1 50%")
    composeTestRule.onNodeWithTag("crossfader_balance_B").assertTextContains("D2 50%")
  }

  @Test
  @Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
  fun `DjScreen displays horizontal crossfader between Deck 1 and Deck 2 in portrait`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent { DjScreen(viewModel = vm) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("horizontal_crossfader_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("crossfader_slider").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w844dp-h390dp-land")
  fun `DjScreen displays horizontal crossfader between decks in landscape`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent { DjScreen(viewModel = vm) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("layout_landscape_dual_deck").assertIsDisplayed()
    composeTestRule.onNodeWithTag("horizontal_crossfader_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("crossfader_slider").performScrollTo().assertIsDisplayed()
  }
}
