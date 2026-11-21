package com.example

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.CamelotKeyMath
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.MixerTheme
import com.example.dj.model.ProModeTab
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.SeratoDeckCard
import kotlin.math.pow
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
class KeySyncTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `camelot key math computes shortest semitone shift and transposes keys accurately`() {
    assertEquals(5, CamelotKeyMath.calculateKeySyncSemitones("Am", "Dm"))
    assertEquals("Dm", CamelotKeyMath.transposeMusicalKey("Am", 5))

    assertEquals(-5, CamelotKeyMath.calculateKeySyncSemitones("Dm", "Am"))
    assertEquals("Am", CamelotKeyMath.transposeMusicalKey("Dm", -5))

    assertEquals(3, CamelotKeyMath.calculateKeySyncSemitones("Am", "Cm"))
    assertEquals("Cm", CamelotKeyMath.transposeMusicalKey("Am", 3))

    assertEquals(-3, CamelotKeyMath.calculateKeySyncSemitones("Am", "F#m"))
    assertEquals("F#m", CamelotKeyMath.transposeMusicalKey("Am", -3))

    // Relative major/minor (8A <-> 8B) requires 0 semitones
    assertEquals(0, CamelotKeyMath.calculateKeySyncSemitones("Am", "C"))
  }

  @Test
  fun `keySyncDeck adjusts playback pitch to match other deck harmonic key and toggles back`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Default: Deck A is Am (8A), Deck B is Dm (7A)
    assertEquals("Am", vm.uiState.value.deckA.track.musicalKey)
    assertEquals("Dm", vm.uiState.value.deckB.track.musicalKey)

    vm.keySyncDeck(DeckId.A)
    val syncedA = vm.uiState.value.deckA
    assertTrue(syncedA.isKeySynced)
    assertEquals(5, syncedA.keyShiftSemitones)
    assertEquals("Dm", syncedA.effectiveMusicalKey)
    assertEquals(2.0.pow(5.0 / 12.0).toFloat(), syncedA.keyPitchMultiplier, 0.001f)

    // Tapping Key Sync again resets to original track key and normal pitch
    vm.keySyncDeck(DeckId.A)
    val resetA = vm.uiState.value.deckA
    assertFalse(resetA.isKeySynced)
    assertEquals(0, resetA.keyShiftSemitones)
    assertEquals("Am", resetA.effectiveMusicalKey)
    assertEquals(1.0f, resetA.keyPitchMultiplier, 0.001f)

    // Deck B Key Sync to Am
    vm.keySyncDeck(DeckId.B)
    val syncedB = vm.uiState.value.deckB
    assertTrue(syncedB.isKeySynced)
    assertEquals(-5, syncedB.keyShiftSemitones)
    assertEquals("Am", syncedB.effectiveMusicalKey)
  }

  @Test
  fun `SeratoDeckCard Key Sync button updates displayed effective harmonic key`() {
    val deckState =
      mutableStateOf(
        DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0]) // Am (8A)
      )
    val otherDeckKey = "Cm" // 5A (+3 semitones)

    composeTestRule.setContent {
      val deck = deckState.value
      SeratoDeckCard(
        deck = deck,
        mixerTheme = MixerTheme.SERATO_PRO,
        eqPresets = DefaultTracks.defaultEqPresets,
        activeProTab = ProModeTab.DJ,
        onTogglePlay = {},
        onCue = {},
        onSync = {},
        onTapTempo = {},
        onOpenLibrary = {},
        onPickLocalFile = {},
        onStemLevelChange = { _, _ -> },
        onToggleStemMute = {},
        onToggleStemSolo = {},
        onApplyStemPreset = {},
        onEqBandChange = { _, _ -> },
        onApplyEqPreset = {},
        onEffectsChange = { _, _, _ -> },
        onTrimGainChange = {},
        onTempoOffsetChange = {},
        onCyclePitchRange = {},
        onRotateJogWheel = {},
        onHotCueTap = {},
        onHotCueLongPress = {},
        onToggleQuantize = {},
        onToggleScratchMode = {},
        onToggleKeyLock = {},
        onToggleLoop = {},
        onCycleLoopBeats = {},
        otherDeckKey = otherDeckKey,
        onKeySync = {
          if (deck.isKeySynced) {
            deckState.value = deck.copy(isKeySynced = false, keyShiftSemitones = 0)
          } else {
            val shift = CamelotKeyMath.calculateKeySyncSemitones(deck.track.musicalKey, otherDeckKey)
            deckState.value = deck.copy(isKeySynced = true, keyShiftSemitones = shift)
          }
        },
      )
    }

    composeTestRule.onNodeWithTag("key_sync_button_A").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertTrue(deckState.value.isKeySynced)
    assertEquals(3, deckState.value.keyShiftSemitones)
    assertEquals("Cm", deckState.value.effectiveMusicalKey)
    composeTestRule.onNodeWithTag("effective_key_badge_A").assertTextContains("Cm", substring = true)
  }
}
