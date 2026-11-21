package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.BeatSyncedWaveformMath
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.MixerTheme
import com.example.dj.model.StemType
import com.example.dj.ui.DjScreen
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.BeatSyncedWaveformDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h844dp-port")
class BeatSyncedWaveformTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `BeatSyncedWaveformMath tracks exact bar beat phase and stem-aware peak intensity`() {
    // 120 BPM over 120s = 240 beats total -> 0.5 progress = beat 120 = Bar 31 Beat 1
    val midPos = BeatSyncedWaveformMath.beatPosition(0.5f, 120f, 120)
    assertEquals(120, midPos.totalBeatIndex)
    assertEquals(31, midPos.bar)
    assertEquals(1, midPos.beatInBar)
    assertEquals(0f, midPos.phase, 0.01f)
    assertEquals("B31.1", midPos.formattedBarBeat)

    // +1 beat (1 / 240) -> Bar 31 Beat 2
    val nextBeat = BeatSyncedWaveformMath.beatPosition(121f / 240f, 120f, 120)
    assertEquals(31, nextBeat.bar)
    assertEquals(2, nextBeat.beatInBar)

    // Paused deck has 0 peak intensity; playing deck produces >0 peak intensity that drops when stems mute
    val pausedDeck = DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0], isPlaying = false)
    assertEquals(0f, BeatSyncedWaveformMath.computeFallbackPeakIntensity(pausedDeck), 0.001f)

    val playingDeck = pausedDeck.copy(isPlaying = true, playheadProgress = 0.25f)
    val fullPeak = BeatSyncedWaveformMath.computeFallbackPeakIntensity(playingDeck)
    assertTrue("Expected positive peak when playing ($fullPeak)", fullPeak > 0.1f)

    val twoMutedStems =
      playingDeck.copy(
        stems =
          playingDeck.stems +
            (StemType.DRUMS to playingDeck.stems[StemType.DRUMS]!!.copy(isMuted = true)) +
            (StemType.BASS to playingDeck.stems[StemType.BASS]!!.copy(isMuted = true))
      )
    val reducedPeak = BeatSyncedWaveformMath.computeFallbackPeakIntensity(twoMutedStems)
    assertTrue("Expected reduced peak after muting stems ($reducedPeak < $fullPeak)", reducedPeak < fullPeak)
  }

  @Test
  fun `BeatSyncedWaveformDisplay tracks live progress, beat position, peak meter, and overview tap-seek`() {
    var deck by
      mutableStateOf(
        DeckState(
          deckId = DeckId.A,
          track = DefaultTracks.tracks[0].copy(bpm = 120f, durationSeconds = 120),
          isPlaying = false,
          playheadProgress = 0f,
          livePeakIntensity = 0f,
        )
      )

    composeTestRule.setContent {
      BeatSyncedWaveformDisplay(
        deck = deck,
        accentColor = MixerTheme.SERATO_PRO.deckAAccent,
        beatMatchDeltaBpm = 1.0f,
        onScratch = { delta ->
          deck = deck.copy(playheadProgress = (deck.playheadProgress + delta).coerceIn(0f, 1f))
        },
        zoomLevel = 2.0f,
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("waveform_beat_pos_A").assertIsDisplayed().assertTextContains("B1.1")
    composeTestRule.onNodeWithTag("waveform_progress_label_A").assertTextContains("0%", substring = true)
    composeTestRule.onNodeWithTag("waveform_peak_label_A").assertTextContains("PK 0%")
    composeTestRule.onNodeWithTag("waveform_peak_meter_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_beat_led_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_canvas_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_progress_bar_A").assertIsDisplayed()

    // Simulate playing at 50% audio progress with 84% live peak intensity
    deck = deck.copy(isPlaying = true, playheadProgress = 0.5f, livePeakIntensity = 0.84f)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("waveform_beat_pos_A").assertTextContains("B31.1")
    composeTestRule.onNodeWithTag("waveform_progress_label_A").assertTextContains("50%", substring = true)
    composeTestRule.onNodeWithTag("waveform_peak_label_A").assertTextContains("PK 84%")

    // Tap near 75% on overview progress bar seeks playhead forward
    composeTestRule.onNodeWithTag("waveform_progress_bar_A").performTouchInput {
      click(Offset(width * 0.75f, height / 2f))
    }
    composeTestRule.waitForIdle()
    assertEquals(0.75f, deck.playheadProgress, 0.05f)
    composeTestRule.onNodeWithTag("waveform_progress_label_A").assertTextContains("75%", substring = true)
  }

  @Test
  fun `DjScreen displays beat-synced waveform progress and peak intensity for both decks`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    composeTestRule.setContent { DjScreen(viewModel = vm) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("waveform_telemetry_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_peak_meter_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_progress_bar_A").assertIsDisplayed()

    composeTestRule.onNodeWithTag("waveform_telemetry_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_peak_meter_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_progress_bar_B").assertIsDisplayed()
  }
}
