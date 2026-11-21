package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.StemChannelState
import com.example.dj.model.StemIsolationPreset
import com.example.dj.model.StemType
import com.example.dj.ui.DjScreen
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.StemDeconstructionBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
class StemDeconstructionToggleTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `viewModel isolates and mutes individual stems independently per deck`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Initially all 4 stems audible on both decks
    StemType.entries.forEach { stem ->
      assertTrue(vm.uiState.value.deckA.isStemAudible(stem))
      assertTrue(vm.uiState.value.deckB.isStemAudible(stem))
      assertEquals(1f, vm.uiState.value.deckA.effectiveStemGain(stem), 0.001f)
    }

    // Mute Drums and Bass on Deck A (keeps Vocals and Melody audible, Deck B untouched)
    vm.toggleStemMute(DeckId.A, StemType.DRUMS)
    vm.toggleStemMute(DeckId.A, StemType.BASS)
    val deckA = vm.uiState.value.deckA
    assertTrue(deckA.isStemAudible(StemType.VOCALS))
    assertFalse(deckA.isStemAudible(StemType.DRUMS))
    assertFalse(deckA.isStemAudible(StemType.BASS))
    assertTrue(deckA.isStemAudible(StemType.MELODY))
    assertEquals(0f, deckA.effectiveStemGain(StemType.DRUMS), 0.001f)
    assertTrue(vm.uiState.value.deckB.isStemAudible(StemType.DRUMS))

    // Isolate Vocals on Deck B (silences Drums, Bass, Melody)
    vm.toggleStemSolo(DeckId.B, StemType.VOCALS)
    val deckB = vm.uiState.value.deckB
    assertTrue(deckB.isStemAudible(StemType.VOCALS))
    assertFalse(deckB.isStemAudible(StemType.DRUMS))
    assertFalse(deckB.isStemAudible(StemType.BASS))
    assertFalse(deckB.isStemAudible(StemType.MELODY))

    // Multi-stem isolation: also solo Drums on Deck B -> Vocals + Drums audible
    vm.toggleStemSolo(DeckId.B, StemType.DRUMS)
    val deckBMulti = vm.uiState.value.deckB
    assertTrue(deckBMulti.isStemAudible(StemType.VOCALS))
    assertTrue(deckBMulti.isStemAudible(StemType.DRUMS))
    assertFalse(deckBMulti.isStemAudible(StemType.BASS))
    assertFalse(deckBMulti.isStemAudible(StemType.MELODY))

    // Reset all stems on Deck A & B
    vm.applyStemPreset(DeckId.A, StemIsolationPreset.FULL)
    vm.applyStemPreset(DeckId.B, StemIsolationPreset.FULL)
    StemType.entries.forEach { stem ->
      assertTrue(vm.uiState.value.deckA.isStemAudible(stem))
      assertTrue(vm.uiState.value.deckB.isStemAudible(stem))
    }
  }

  @Test
  fun `StemDeconstructionBar toggles mute and isolate across all 4 stem buttons`() {
    var deck by mutableStateOf(DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0]))

    composeTestRule.setContent {
      StemDeconstructionBar(
        deck = deck,
        accentColor = Color(0xFF18B0FF),
        onToggleMute = { stem ->
          val cur = deck.stems[stem]!!
          deck = deck.copy(stems = deck.stems + (stem to cur.copy(isMuted = !cur.isMuted, isSolo = false)))
        },
        onToggleSolo = { stem ->
          val cur = deck.stems[stem]!!
          deck = deck.copy(stems = deck.stems + (stem to cur.copy(isSolo = !cur.isSolo, isMuted = false)))
        },
        onResetAll = {
          deck = deck.copy(stems = StemType.entries.associateWith { StemChannelState() })
        },
      )
    }
    composeTestRule.waitForIdle()

    // All 4 buttons visible with ON state
    composeTestRule.onNodeWithTag("stem_toggle_VOCALS_A").assertIsDisplayed().assertTextContains("Vocals")
    composeTestRule.onNodeWithTag("stem_toggle_DRUMS_A").assertIsDisplayed().assertTextContains("Drums")
    composeTestRule.onNodeWithTag("stem_toggle_BASS_A").assertIsDisplayed().assertTextContains("Bass")
    composeTestRule.onNodeWithTag("stem_toggle_MELODY_A").assertIsDisplayed().assertTextContains("Melody")
    composeTestRule.onNodeWithTag("stem_deconstruct_status_A").assertTextContains("4/4 STEMS ON")

    // Default MUTE mode: tap Vocals & Bass -> muted
    composeTestRule.onNodeWithTag("stem_toggle_VOCALS_A").performClick()
    composeTestRule.onNodeWithTag("stem_toggle_BASS_A").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deck.stems[StemType.VOCALS]!!.isMuted)
    assertTrue(deck.stems[StemType.BASS]!!.isMuted)
    composeTestRule.onNodeWithTag("stem_toggle_VOCALS_A").assertTextContains("MUTED")
    composeTestRule.onNodeWithTag("stem_deconstruct_status_A").assertTextContains("MUTED: VOX,BAS")

    // Switch to ISOLATE mode and isolate Drums
    composeTestRule.onNodeWithTag("stem_mode_isolate_A").performClick()
    composeTestRule.onNodeWithTag("stem_toggle_DRUMS_A").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deck.stems[StemType.DRUMS]!!.isSolo)
    composeTestRule.onNodeWithTag("stem_toggle_DRUMS_A").assertTextContains("ISO")
    composeTestRule.onNodeWithTag("stem_toggle_MELODY_A").assertTextContains("SILENT")
    composeTestRule.onNodeWithTag("stem_deconstruct_status_A").assertTextContains("ISO: DRM")

    // Long-press in ISOLATE mode mutes Melody directly
    composeTestRule.onNodeWithTag("stem_toggle_MELODY_A").performTouchInput { longClick() }
    composeTestRule.waitForIdle()
    assertTrue(deck.stems[StemType.MELODY]!!.isMuted)

    // Reset restores all 4 stems ON
    composeTestRule.onNodeWithTag("stem_deconstruct_reset_A").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("stem_deconstruct_status_A").assertTextContains("4/4 STEMS ON")
    StemType.entries.forEach { stem -> assertTrue(deck.isStemAudible(stem)) }
  }

  @Test
  @Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
  fun `DjScreen shows 4-button stem deconstruction toggles on both Deck A and Deck B`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent { DjScreen(viewModel = vm) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("stem_deconstruct_bar_A").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_VOCALS_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_DRUMS_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_BASS_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_MELODY_A").assertIsDisplayed()

    composeTestRule.onNodeWithTag("stem_deconstruct_bar_B").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_VOCALS_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stem_toggle_MELODY_B").assertIsDisplayed()
  }
}
