package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.DeckId
import com.example.dj.model.ProModeTab
import com.example.dj.ui.DjScreen
import com.example.dj.ui.DjViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasterTapTempoHudTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `viewModel aggregates tap tempo across Deck A and Deck B into instantaneous Master BPM`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Default active reference is Deck A (128 BPM)
    assertNull(vm.uiState.value.masterTappedBpm)
    assertEquals(128.0f, vm.uiState.value.effectiveMasterBpm, 0.01f)

    // Single tap on Deck A + single tap 500ms later on Deck B -> aggregated 120.00 BPM ("A+B")
    vm.tapTempo(DeckId.A, nowMs = 1_000L)
    assertNull(vm.uiState.value.masterTappedBpm)
    assertEquals("DECK 1", vm.uiState.value.masterTapSource)

    vm.tapTempo(DeckId.B, nowMs = 1_500L)
    assertEquals(120.0f, vm.uiState.value.effectiveMasterBpm, 0.01f)
    assertEquals(120.0f, vm.uiState.value.masterTappedBpm ?: 0f, 0.01f)
    assertEquals("A+B", vm.uiState.value.masterTapSource)

    // Direct tap on Master HUD at +500ms keeps 120.00 BPM unified reference
    vm.tapMasterTempo(nowMs = 2_000L)
    assertEquals(120.0f, vm.uiState.value.effectiveMasterBpm, 0.01f)
    assertEquals("A+B", vm.uiState.value.masterTapSource)

    // Reset returns to live deck BPM
    vm.resetMasterTapTempo()
    assertNull(vm.uiState.value.masterTappedBpm)
    assertNull(vm.uiState.value.masterTapSource)
    assertEquals(128.0f, vm.uiState.value.effectiveMasterBpm, 0.01f)

    // Direct taps on Master HUD alone compute Master BPM with "MASTER" badge
    vm.tapMasterTempo(nowMs = 5_000L)
    vm.tapMasterTempo(nowMs = 5_400L) // 400ms -> 150 BPM
    assertEquals(150.0f, vm.uiState.value.effectiveMasterBpm, 0.01f)
    assertEquals("MASTER", vm.uiState.value.masterTapSource)

    // Fresh Deck A sequence after >2s gap (480ms intervals -> 125.00 BPM)
    vm.tapTempo(DeckId.A, nowMs = 10_000L)
    vm.tapTempo(DeckId.A, nowMs = 10_480L)
    assertEquals(125.0f, vm.uiState.value.deckA.track.bpm, 0.05f)
    assertEquals(125.0f, vm.uiState.value.effectiveMasterBpm, 0.05f)
    assertEquals("DECK 1", vm.uiState.value.masterTapSource)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
  fun `Master Tap Tempo HUD stays persistent in top app bar across tabs and updates on tap`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent {
      DjScreen(viewModel = vm)
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("master_tap_tempo_hud").assertIsDisplayed()
    composeTestRule.onNodeWithTag("master_bpm_readout").assertIsDisplayed().assertTextContains("128.00 BPM")
    composeTestRule.onNodeWithTag("master_tap_source_badge").assertTextContains("AUTO")

    // Switch ProMode tabs and verify HUD remains persistent
    composeTestRule.onNodeWithTag("pro_tab_${ProModeTab.FX.name}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("master_tap_tempo_hud").assertIsDisplayed()

    composeTestRule.onNodeWithTag("pro_tab_${ProModeTab.STEMS.name}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("master_tap_tempo_hud").assertIsDisplayed()

    // Tap Deck A and Deck B tap tempo buttons -> HUD updates live with aggregated A+B badge
    composeTestRule.onNodeWithTag("tap_tempo_A").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tap_tempo_B").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("master_tap_source_badge").assertTextContains("A+B")
    composeTestRule.onNodeWithTag("master_tap_tempo_reset").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("master_tap_source_badge").assertTextContains("AUTO")
  }

  @Test
  @Config(sdk = [36], qualifiers = "w844dp-h390dp-land")
  fun `Master Tap Tempo HUD renders inline in landscape top app bar`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent {
      DjScreen(viewModel = vm)
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("layout_landscape_dual_deck").assertIsDisplayed()
    composeTestRule.onNodeWithTag("master_tap_tempo_hud").assertIsDisplayed()
    composeTestRule.onNodeWithTag("master_bpm_readout").assertIsDisplayed().assertTextContains("128.00 BPM")
    composeTestRule.onNodeWithTag("master_tap_tempo_button").assertIsDisplayed()
  }
}
