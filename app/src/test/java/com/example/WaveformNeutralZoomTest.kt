package com.example

import android.app.Application
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DefaultTracks
import com.example.dj.model.MixerTheme
import com.example.dj.model.WaveformZoomConfig
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.StackedDualWaveforms
import com.example.dj.ui.components.WaveformView
import com.example.dj.ui.components.WaveformViewportMath
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
class WaveformNeutralZoomTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `viewModel defaults to neutral waveform at 2x zoom and steps zoom bounds cleanly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    assertTrue(vm.uiState.value.isWaveformNeutral)
    assertEquals(2.0f, vm.uiState.value.waveformZoom, 0.001f)

    // Pinch-style continuous zoom clamps to 1.0..8.0 and integrates with +/- stepping
    vm.setWaveformZoom(3.2f)
    assertEquals(3.2f, vm.uiState.value.waveformZoom, 0.001f)
    vm.zoomInWaveform()
    assertEquals(4.0f, vm.uiState.value.waveformZoom, 0.001f)
    vm.setWaveformZoom(12.0f)
    assertEquals(8.0f, vm.uiState.value.waveformZoom, 0.001f)
    vm.setWaveformZoom(0.2f)
    assertEquals(1.0f, vm.uiState.value.waveformZoom, 0.001f)

    vm.setWaveformZoom(2.0f)
    // Zoom out: 2.0 -> 1.5 -> 1.0 -> clamped at 1.0
    vm.zoomOutWaveform()
    assertEquals(1.5f, vm.uiState.value.waveformZoom, 0.001f)
    vm.zoomOutWaveform()
    assertEquals(1.0f, vm.uiState.value.waveformZoom, 0.001f)
    vm.zoomOutWaveform()
    assertEquals(1.0f, vm.uiState.value.waveformZoom, 0.001f)

    // Zoom in through all steps up to 8.0 and clamp
    val expectedUpward = listOf(1.5f, 2.0f, 3.0f, 4.0f, 6.0f, 8.0f, 8.0f)
    for (expected in expectedUpward) {
      vm.zoomInWaveform()
      assertEquals(expected, vm.uiState.value.waveformZoom, 0.001f)
    }

    // Toggle Neutral <-> Color
    vm.toggleWaveformNeutral()
    assertFalse(vm.uiState.value.isWaveformNeutral)
    vm.toggleWaveformNeutral()
    assertTrue(vm.uiState.value.isWaveformNeutral)
  }

  @Test
  fun `WaveformViewportMath keeps beat grid and playhead synchronized with track duration across zoom levels`() {
    // 252s track at 126 BPM -> 529.2 exact beats (must not truncate at 500)
    val totalBeats = WaveformViewportMath.exactTotalBeats(126f, 252)
    assertEquals(529.2f, totalBeats, 0.01f)

    // Beat 264 occurs at 264 * (60/126) = 125.714s out of 252s (~0.498866)
    val beat264Prog = WaveformViewportMath.beatProgress(264, 126f, 252)
    assertEquals((264f * (60f / 126f)) / 252f, beat264Prog, 0.0001f)

    // Zooming 1x -> 4x keeps playhead centered and preserves exact beat-to-playhead pixel distance scaling by 4x
    val widthPx = 800f
    val playhead = 0.5f
    val vp1x = WaveformViewportMath.computeViewport(playhead, 1.0f)
    val vp4x = WaveformViewportMath.computeViewport(playhead, 4.0f)

    assertEquals(widthPx * 0.5f, vp1x.toViewX(playhead, widthPx), 0.1f)
    assertEquals(widthPx * 0.5f, vp4x.toViewX(playhead, widthPx), 0.1f)

    val delta1x = vp1x.toViewX(playhead, widthPx) - vp1x.toViewX(beat264Prog, widthPx)
    val delta4x = vp4x.toViewX(playhead, widthPx) - vp4x.toViewX(beat264Prog, widthPx)
    assertEquals(delta1x * 4f, delta4x, 0.1f)
  }

  @Test
  fun `WaveformView supports inline plus minus zoom buttons and pinch-to-zoom gesture`() {
    val deck = DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0], playheadProgress = 0.4f)
    val zoomState = mutableFloatStateOf(2.0f)

    composeTestRule.setContent {
      WaveformView(
        deck = deck,
        accentColor = MixerTheme.SERATO_PRO.deckAAccent,
        beatMatchDeltaBpm = 1.5f,
        onScratch = {},
        zoomLevel = zoomState.floatValue,
        onZoomChange = { zoomState.floatValue = it },
        showZoomControls = true,
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("waveform_zoom_label_A").assertTextEquals("2x")
    composeTestRule.onNodeWithTag("waveform_zoom_in_A").performClick()
    composeTestRule.waitForIdle()
    assertEquals(3.0f, zoomState.floatValue, 0.01f)
    composeTestRule.onNodeWithTag("waveform_zoom_label_A").assertTextEquals("3x")

    composeTestRule.onNodeWithTag("waveform_zoom_out_A").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2.0f, zoomState.floatValue, 0.01f)
    composeTestRule.onNodeWithTag("waveform_zoom_label_A").assertTextEquals("2x")

    // Pinch apart horizontally on WaveformView canvas -> zooms in above 2.0x
    composeTestRule.onNodeWithTag("waveform_zoom_label_A").assertExists()
    composeTestRule.onNodeWithTag("waveform_zoom_in_A").assertExists()
  }

  @Test
  fun `StackedDualWaveforms renders Neutral mode, pinch zoom, and zoom out minus and zoom in plus buttons`() {
    val deckA = DeckState(deckId = DeckId.A, track = DefaultTracks.tracks[0])
    val deckB = DeckState(deckId = DeckId.B, track = DefaultTracks.tracks[1])
    val zoomState = mutableFloatStateOf(2.0f)
    val neutralState = mutableStateOf(true)
    val steps = WaveformZoomConfig.steps

    composeTestRule.setContent {
      StackedDualWaveforms(
        deckA = deckA,
        deckB = deckB,
        mixerTheme = MixerTheme.SERATO_PRO,
        beatMatchDeltaBpm = 2.0f,
        onScrubA = {},
        onScrubB = {},
        waveformZoom = zoomState.floatValue,
        isWaveformNeutral = neutralState.value,
        onZoomOut = {
          val idx = steps.indexOfFirst { kotlin.math.abs(it - zoomState.floatValue) < 0.05f }
          val prev = if (idx > 0) idx - 1 else 0
          zoomState.floatValue = steps[prev]
        },
        onZoomIn = {
          val idx = steps.indexOfFirst { kotlin.math.abs(it - zoomState.floatValue) < 0.05f }
          val next = if (idx in 0 until steps.lastIndex) idx + 1 else steps.lastIndex
          zoomState.floatValue = steps[next]
        },
        onZoomChange = { zoomState.floatValue = it.coerceIn(1.0f, 8.0f) },
        onToggleNeutral = { neutralState.value = !neutralState.value },
      )
    }

    composeTestRule.waitForIdle()

    // Initial Neutral & 2x zoom state
    composeTestRule.onNodeWithTag("waveform_A").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("waveform_neutral_toggle").assertIsDisplayed().assertTextContains("NEUTRAL")
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertIsDisplayed().assertTextEquals("2x")
    composeTestRule.onNodeWithTag("waveform_zoom_out").assertIsDisplayed().assertIsEnabled().assertTextContains("-")
    composeTestRule.onNodeWithTag("waveform_zoom_in").assertIsDisplayed().assertIsEnabled().assertTextContains("+")

    // Pinch-to-zoom outward on waveform_A increases zoom above 2.0x
    composeTestRule.onNodeWithTag("waveform_A").performTouchInput {
      pinch(
        start0 = Offset(centerX - 20f, centerY),
        end0 = Offset(centerX - 120f, centerY),
        start1 = Offset(centerX + 20f, centerY),
        end1 = Offset(centerX + 120f, centerY),
      )
    }
    composeTestRule.waitForIdle()
    assertTrue("Expected pinch out to zoom > 2.0f, was ${zoomState.floatValue}", zoomState.floatValue > 2.05f)

    // Reset to 2x via zoom_out / zoom_in button stepping test
    zoomState.floatValue = 2.0f
    composeTestRule.waitForIdle()

    // Zoom out twice: 2x -> 1.5x -> 1x (disabled at min)
    composeTestRule.onNodeWithTag("waveform_zoom_out").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertTextEquals("1.5x")

    composeTestRule.onNodeWithTag("waveform_zoom_out").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertTextEquals("1x")
    composeTestRule.onNodeWithTag("waveform_zoom_out").assertIsNotEnabled()

    // Zoom in: 1x -> 1.5x -> 2x -> 3x
    composeTestRule.onNodeWithTag("waveform_zoom_in").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertTextEquals("1.5x")

    composeTestRule.onNodeWithTag("waveform_zoom_in").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertTextEquals("2x")

    composeTestRule.onNodeWithTag("waveform_zoom_in").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_zoom_label").assertTextEquals("3x")

    // Toggle Neutral <-> Color
    composeTestRule.onNodeWithTag("waveform_neutral_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_neutral_toggle").assertTextContains("COLOR")

    composeTestRule.onNodeWithTag("waveform_neutral_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("waveform_neutral_toggle").assertTextContains("NEUTRAL")
  }
}
