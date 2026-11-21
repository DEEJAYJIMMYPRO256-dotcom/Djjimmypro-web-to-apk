package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.dj.audio.BpmSyncEngine
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.MixerTheme
import com.example.dj.model.ProModeTab
import com.example.dj.model.SamplePadBank
import com.example.dj.model.SamplePadCatalog
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.SamplePadsEffectsPanel
import com.example.dj.ui.components.SeratoDeckCard
import kotlin.math.abs
import kotlin.math.floor
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
class SamplePadsAndLoopSyncTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `all 65 sample pad assets are bundled in assets samples directory`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val bundled = app.assets.list("samples")?.toSet() ?: emptySet()
    assertEquals(65, SamplePadCatalog.allPads.size)
    SamplePadCatalog.allPads.forEach { pad ->
      assertTrue(
        "Missing bundled audio asset samples/${pad.assetFileName}",
        pad.assetFileName in bundled,
      )
    }
  }

  @Test
  fun `tapping sample pad toggles ON then OFF and supports multi-pad, loop, and cut-all`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)
    val sirenPad = SamplePadCatalog.allPads.first { it.id == "fx_siren" }
    val hornPad = SamplePadCatalog.allPads.first { it.id == "fx_air_horn" }

    assertTrue(vm.uiState.value.activeSamplePadIds.isEmpty())

    // First tap turns Siren ON
    vm.toggleSamplePad(sirenPad)
    assertTrue(sirenPad.id in vm.uiState.value.activeSamplePadIds)

    // Second tap on same pad turns Siren OFF
    vm.toggleSamplePad(sirenPad)
    assertFalse(sirenPad.id in vm.uiState.value.activeSamplePadIds)

    // Multi-pad activation + natural completion + ALL OFF
    vm.toggleSamplePad(sirenPad)
    vm.toggleSamplePad(hornPad)
    assertEquals(setOf(sirenPad.id, hornPad.id), vm.uiState.value.activeSamplePadIds)

    vm.onSamplePadCompleted(sirenPad.id)
    assertEquals(setOf(hornPad.id), vm.uiState.value.activeSamplePadIds)

    vm.stopAllSamplePads()
    assertTrue(vm.uiState.value.activeSamplePadIds.isEmpty())

    // Bank switching, loop toggle, and gain adjustment
    vm.selectSampleBank(SamplePadBank.DRUMS_909)
    assertEquals(SamplePadBank.DRUMS_909, vm.uiState.value.selectedSampleBank)
    vm.toggleSamplePadLoop()
    assertTrue(vm.uiState.value.samplePadLoopEnabled)
    vm.setSamplePadVolume(0.65f)
    assertEquals(0.65f, vm.uiState.value.samplePadVolume, 0.001f)
  }

  @Test
  fun `autoSync aligns BPM and beat phase cleanly and toggles active state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    // Put Deck B mid-beat and Deck A slightly off phase at 0.31 progress
    vm.scrubDeck(DeckId.B, 0.25f)
    vm.scrubDeck(DeckId.A, 0.31f)
    assertFalse(vm.uiState.value.deckA.isAutoSynced)

    vm.syncDeck(DeckId.A)
    val syncedA = vm.uiState.value.deckA
    val deckB = vm.uiState.value.deckB
    assertTrue(syncedA.isAutoSynced)
    assertEquals(deckB.effectiveBpm, syncedA.effectiveBpm, 0.05f)

    // Verify beat phase alignment (difference between fractional beat phases is ~0)
    val beatsA = (syncedA.playheadProgress * syncedA.track.durationSeconds) * (syncedA.effectiveBpm / 60f)
    val beatsB = (deckB.playheadProgress * deckB.track.durationSeconds) * (deckB.effectiveBpm / 60f)
    val phaseA = beatsA - floor(beatsA)
    val phaseB = beatsB - floor(beatsB)
    val phaseDiff = minOf(abs(phaseA - phaseB), 1f - abs(phaseA - phaseB))
    assertTrue("Expected aligned beat phase (phaseDiff=$phaseDiff)", phaseDiff < 0.03f)

    // Preserved local track region rather than jumping to Deck B's exact playhead
    assertTrue(abs(syncedA.playheadProgress - 0.31f) < 0.02f)

    // Tapping Auto-Sync again when already synced unlocks Auto-Sync
    vm.syncDeck(DeckId.A)
    assertFalse(vm.uiState.value.deckA.isAutoSynced)
  }

  @Test
  fun `dedicated 4-beat and 8-beat auto-loop buttons plus half and double toggles update loop span`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)
    vm.scrubDeck(DeckId.A, 0.2f)

    // Dedicated 4-beat auto-loop
    vm.triggerAutoLoop(DeckId.A, 4f)
    val loop4 = vm.uiState.value.deckA.loop
    assertTrue(loop4.isActive)
    assertEquals(4f, loop4.beats, 0.001f)
    val span4 = loop4.endProgress - loop4.startProgress
    assertTrue(span4 > 0f)

    // Dedicated 8-beat auto-loop doubles span cleanly
    vm.triggerAutoLoop(DeckId.A, 8f)
    val loop8 = vm.uiState.value.deckA.loop
    assertTrue(loop8.isActive)
    assertEquals(8f, loop8.beats, 0.001f)
    assertEquals(span4 * 2f, loop8.endProgress - loop8.startProgress, 0.0005f)

    // Halve loop: 8 -> 4 -> 2
    vm.halveLoop(DeckId.A)
    assertEquals(4f, vm.uiState.value.deckA.loop.beats, 0.001f)
    vm.halveLoop(DeckId.A)
    assertEquals(2f, vm.uiState.value.deckA.loop.beats, 0.001f)

    // Double loop: 2 -> 4
    vm.doubleLoop(DeckId.A)
    assertEquals(4f, vm.uiState.value.deckA.loop.beats, 0.001f)

    // Tapping 4-beat button again toggles loop OFF
    vm.triggerAutoLoop(DeckId.A, 4f)
    assertFalse(vm.uiState.value.deckA.loop.isActive)
  }

  @Test
  fun `SamplePadsEffectsPanel toggles individual pad ON and OFF on tap in Compose UI`() {
    var bank by mutableStateOf(SamplePadBank.FX_SIRENS)
    var activeIds by mutableStateOf(emptySet<String>())
    var loop by mutableStateOf(false)
    var vol by mutableStateOf(0.9f)

    composeTestRule.setContent {
      SamplePadsEffectsPanel(
        selectedBank = bank,
        activePadIds = activeIds,
        loopEnabled = loop,
        volume = vol,
        mixerTheme = MixerTheme.SERATO_PRO,
        onSelectBank = { bank = it },
        onTogglePad = { pad ->
          activeIds = if (pad.id in activeIds) activeIds - pad.id else activeIds + pad.id
        },
        onToggleLoop = { loop = !loop },
        onStopAll = { activeIds = emptySet() },
        onVolumeChange = { vol = it },
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("SAMPLES PADS EFFECTS").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sample_pads_active_badge").assertTextContains("TAP ON/OFF")

    // Tap Siren pad ON
    composeTestRule.onNodeWithTag("sample_pad_fx_siren").performClick()
    composeTestRule.waitForIdle()
    assertTrue("fx_siren" in activeIds)
    composeTestRule.onNodeWithTag("sample_pads_active_badge").assertTextContains("1 ON")

    // Tap Siren pad again -> turns OFF
    composeTestRule.onNodeWithTag("sample_pad_fx_siren").performClick()
    composeTestRule.waitForIdle()
    assertFalse("fx_siren" in activeIds)
    composeTestRule.onNodeWithTag("sample_pads_active_badge").assertTextContains("TAP ON/OFF")

    // Switch to 909 Drums bank and toggle 909 Kick ON
    composeTestRule.onNodeWithTag("sample_bank_DRUMS_909").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sample_pad_drum_909_kick").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue("drum_909_kick" in activeIds)

    // ALL OFF button clears active pads
    composeTestRule.onNodeWithTag("sample_pads_stop_all").performClick()
    composeTestRule.waitForIdle()
    assertTrue(activeIds.isEmpty())
  }

  @Test
  fun `SeratoDeckCard renders Auto-Sync and 4-beat 8-beat half double loop buttons`() {
    var deck by mutableStateOf(DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0]))

    composeTestRule.setContent {
      SeratoDeckCard(
        deck = deck,
        mixerTheme = MixerTheme.SERATO_PRO,
        eqPresets = DefaultTracks.defaultEqPresets,
        activeProTab = ProModeTab.DJ,
        onTogglePlay = {},
        onCue = {},
        onSync = { deck = deck.copy(isAutoSynced = !deck.isAutoSynced) },
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
        onToggleLoop = { deck = deck.copy(loop = deck.loop.copy(isActive = !deck.loop.isActive)) },
        onCycleLoopBeats = {},
        onTriggerAutoLoop = { beats ->
          deck = deck.copy(loop = deck.loop.copy(isActive = true, beats = beats))
        },
        onHalveLoop = {
          deck = deck.copy(loop = deck.loop.copy(beats = (deck.loop.beats * 0.5f).coerceAtLeast(0.25f)))
        },
        onDoubleLoop = {
          deck = deck.copy(loop = deck.loop.copy(beats = (deck.loop.beats * 2f).coerceAtMost(16f)))
        },
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sync_button_A").assertTextContains("AUTO-SYNC").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deck.isAutoSynced)
    composeTestRule.onNodeWithTag("sync_button_A").assertTextContains("SYNCED")

    composeTestRule.onNodeWithTag("loop_4_button_A").assertTextContains("4 BEAT").performClick()
    composeTestRule.waitForIdle()
    assertEquals(4f, deck.loop.beats, 0.001f)

    composeTestRule.onNodeWithTag("loop_8_button_A").assertTextContains("8 BEAT").performClick()
    composeTestRule.waitForIdle()
    assertEquals(8f, deck.loop.beats, 0.001f)

    composeTestRule.onNodeWithTag("loop_half_button_A").assertTextContains("1/2x").performClick()
    composeTestRule.waitForIdle()
    assertEquals(4f, deck.loop.beats, 0.001f)

    composeTestRule.onNodeWithTag("loop_double_button_A").assertTextContains("2x").performClick()
    composeTestRule.waitForIdle()
    assertEquals(8f, deck.loop.beats, 0.001f)
  }
}
