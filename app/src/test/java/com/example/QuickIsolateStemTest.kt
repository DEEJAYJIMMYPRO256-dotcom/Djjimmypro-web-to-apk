package com.example

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.StemType
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.StemsControlPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickIsolateStemTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `quick isolate mode zeroes vocals level and enables subtle HPF when muting vocals`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Standard mute (Quick Isolate OFF) keeps level unchanged and filter at 0
    vm.toggleStemMute(DeckId.A, StemType.VOCALS)
    assertTrue(vm.uiState.value.deckA.stems[StemType.VOCALS]!!.isMuted)
    assertEquals(1f, vm.uiState.value.deckA.stems[StemType.VOCALS]!!.level, 0.001f)
    assertEquals(0f, vm.uiState.value.deckA.effects.filter, 0.001f)
    vm.toggleStemMute(DeckId.A, StemType.VOCALS)

    // Enable Quick Isolate mode and mute Vocals
    vm.toggleQuickIsolateMode(DeckId.A)
    assertTrue(vm.uiState.value.deckA.quickIsolateEnabled)

    vm.toggleStemMute(DeckId.A, StemType.VOCALS)
    val isolated = vm.uiState.value.deckA
    assertTrue(isolated.stems[StemType.VOCALS]!!.isMuted)
    assertEquals(0f, isolated.stems[StemType.VOCALS]!!.level, 0.001f)
    assertEquals(DeckState.QUICK_ISOLATE_HPF, isolated.effects.filter, 0.001f)
    assertTrue(isolated.isQuickIsolateActive)

    // Tapping Vocals mute again restores vocal level and filter
    vm.toggleStemMute(DeckId.A, StemType.VOCALS)
    val restored = vm.uiState.value.deckA
    assertFalse(restored.stems[StemType.VOCALS]!!.isMuted)
    assertEquals(1f, restored.stems[StemType.VOCALS]!!.level, 0.001f)
    assertEquals(0f, restored.effects.filter, 0.001f)
    assertFalse(restored.isQuickIsolateActive)
  }

  @Test
  fun `StemsControlPanel UI toggles Quick Isolate and triggers karaoke vocal mute`() {
    val deckState =
      androidx.compose.runtime.mutableStateOf(
        DeckState(deckId = DeckId.A, track = DefaultTracks.tracks.first())
      )

    composeTestRule.setContent {
      val deck = deckState.value
      StemsControlPanel(
        deck = deck,
        accentColor = Color(0xFF18B0FF),
        onStemLevelChange = { _, _ -> },
        onToggleMute = { stem ->
          val current = deck.stems[stem]!!
          deckState.value =
            if (stem == StemType.VOCALS && deck.quickIsolateEnabled) {
              if (!current.isMuted) {
                deck.copy(
                  stems = deck.stems + (stem to current.copy(level = 0f, isMuted = true)),
                  effects = deck.effects.copy(filter = DeckState.QUICK_ISOLATE_HPF),
                )
              } else {
                deck.copy(
                  stems = deck.stems + (stem to current.copy(level = 1f, isMuted = false)),
                  effects = deck.effects.copy(filter = 0f),
                )
              }
            } else {
              deck.copy(stems = deck.stems + (stem to current.copy(isMuted = !current.isMuted)))
            }
        },
        onToggleSolo = {},
        onToggleQuickIsolate = {
          deckState.value = deck.copy(quickIsolateEnabled = !deck.quickIsolateEnabled)
        },
      )
    }

    composeTestRule.onNodeWithTag("stem_quick_isolate_A").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deckState.value.quickIsolateEnabled)

    composeTestRule.onNodeWithTag("stem_mute_VOX_A").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deckState.value.isQuickIsolateActive)
    assertEquals(0f, deckState.value.stems[StemType.VOCALS]!!.level, 0.001f)
    assertEquals(DeckState.QUICK_ISOLATE_HPF, deckState.value.effects.filter, 0.001f)
    composeTestRule.onNodeWithTag("stem_quick_isolate_status_A").assertIsDisplayed()
  }
}
