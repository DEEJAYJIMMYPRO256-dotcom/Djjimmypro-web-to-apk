package com.example

import android.app.Application
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.dj.model.DeckId
import com.example.dj.model.MixerTheme
import com.example.dj.ui.DjViewModel
import com.example.dj.ui.components.JogWheelPhysics
import com.example.dj.ui.components.JogWheelPhysicsConfig
import com.example.dj.ui.components.VinylJogWheel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
class VinylJogWheelFrictionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `JogWheelPhysics decelerates monotonically under viscous and coulomb friction to complete rest`() {
    val config = JogWheelPhysicsConfig()
    var vel = -900f // Rapid counter-clockwise back-spin
    var prevAbsDelta = Float.MAX_VALUE
    var totalBackspinAngle = 0f
    var steps = 0

    while (abs(vel) >= config.stopVelocityDegPerSec && steps < 300) {
      val (deltaAngle, nextVel) = JogWheelPhysics.stepInertia(vel, 0.016f, config)
      assertTrue("Expected negative delta during back-spin, got $deltaAngle", deltaAngle < 0f)
      assertTrue(
        "Expected decreasing speed: prev=$vel, next=$nextVel",
        abs(nextVel) < abs(vel),
      )
      assertTrue(
        "Expected decreasing frame delta magnitude: prev=$prevAbsDelta, cur=${abs(deltaAngle)}",
        abs(deltaAngle) < prevAbsDelta,
      )
      prevAbsDelta = abs(deltaAngle)
      totalBackspinAngle += deltaAngle
      vel = nextVel
      steps++
    }

    assertEquals("Velocity should settle cleanly to 0f at rest", 0f, vel, 0.001f)
    assertTrue("Expected multi-frame coasting after rapid back-spin", steps > 10)
    assertTrue("Expected significant inertial back-spin travel", totalBackspinAngle < -180f)

    // Positive clockwise spin should mirror symmetrically
    val (posDelta, posNextVel) = JogWheelPhysics.stepInertia(900f, 0.016f, config)
    assertTrue(posDelta > 0f)
    assertTrue(posNextVel in 0f..900f)
  }

  @Test
  fun `higher rotationalInertia produces longer coasting distance and slower deceleration`() {
    fun simulateTotalAngle(inertia: Float): Pair<Float, Int> {
      val cfg = JogWheelPhysicsConfig(rotationalInertia = inertia)
      var vel = -1080f
      var angle = 0f
      var steps = 0
      while (abs(vel) >= cfg.stopVelocityDegPerSec && steps < 600) {
        val (d, nextV) = JogWheelPhysics.stepInertia(vel, 0.016f, cfg)
        angle += d
        vel = nextV
        steps++
      }
      return abs(angle) to steps
    }

    val (lightAngle, lightSteps) = simulateTotalAngle(inertia = 0.5f)
    val (heavyAngle, heavySteps) = simulateTotalAngle(inertia = 2.0f)

    assertTrue(
      "Heavy platter ($heavyAngle deg) should coast further than light platter ($lightAngle deg)",
      heavyAngle > lightAngle * 2f,
    )
    assertTrue(
      "Heavy platter ($heavySteps steps) should take more frames to stop than light ($lightSteps)",
      heavySteps > lightSteps,
    )
  }

  @Test
  fun `updateAngularVelocity normalizes 180-degree wrap-around during back-spin`() {
    // Crossing from -170 deg to +170 deg counter-clockwise is a -20 deg step, not +340 deg
    val delta = JogWheelPhysics.normalizeAngleDelta(170f - (-170f))
    assertEquals(-20f, delta, 0.001f)

    val vel = JogWheelPhysics.updateAngularVelocity(0f, delta, dtSeconds = 0.016f)
    assertTrue("Expected negative angular velocity for CCW crossing, got $vel", vel < -200f)
  }

  @Test
  fun `DjViewModel rotateJogWheel rewinds playing track during scratch-mode back-spin`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DjViewModel(app)

    vm.scrubDeck(DeckId.A, 0.5f)
    vm.togglePlay(DeckId.A)
    vm.toggleScratchMode(DeckId.A)
    val initialProgress = vm.uiState.value.deckA.playheadProgress
    assertTrue(vm.uiState.value.deckA.isPlaying)
    assertTrue(vm.uiState.value.deckA.scratchMode)

    // Simulate back-spin negative angles while playing in scratch mode
    vm.rotateJogWheel(DeckId.A, -180f)
    val afterBackspin = vm.uiState.value.deckA.playheadProgress
    assertTrue(
      "Back-spin while playing in scratchMode should rewind playhead ($afterBackspin < $initialProgress)",
      afterBackspin < initialProgress,
    )

    // Disable scratch mode (pitch-bend nudge mode): rotation nudges tempo instead of rewinding
    vm.toggleScratchMode(DeckId.A)
    assertFalse(vm.uiState.value.deckA.scratchMode)
    val beforeNudgeProgress = vm.uiState.value.deckA.playheadProgress
    val beforeTempo = vm.uiState.value.deckA.tempoOffset
    vm.rotateJogWheel(DeckId.A, -90f)
    assertTrue(vm.uiState.value.deckA.tempoOffset < beforeTempo)
    assertEquals(beforeNudgeProgress, vm.uiState.value.deckA.playheadProgress, 0.0001f)
  }

  @Test
  fun `VinylJogWheel continues decelerating back-spin after finger release and stops on grab`() {
    composeTestRule.mainClock.autoAdvance = false

    val rotationDeg = mutableFloatStateOf(0f)
    val postReleaseDeltas = mutableListOf<Float>()
    var released = false

    composeTestRule.setContent {
      VinylJogWheel(
        rotationDeg = rotationDeg.floatValue,
        isPlaying = false,
        accentColor = MixerTheme.SERATO_PRO.deckAAccent,
        onTap = {},
        onRotate = { delta ->
          rotationDeg.floatValue += delta
          if (released) {
            postReleaseDeltas.add(delta)
          }
        },
        modifier = Modifier.testTag("vinyl_jog_wheel_test"),
      )
    }

    composeTestRule.mainClock.advanceTimeByFrame()

    // Perform a rapid counter-clockwise circular swipe around the platter perimeter
    composeTestRule.onNodeWithTag("vinyl_jog_wheel_test").performTouchInput {
      val radius = width * 0.36f
      val startAngleDeg = 0f
      fun pointAt(deg: Float): Offset {
        val rad = deg * (PI.toFloat() / 180f)
        return Offset(center.x + radius * cos(rad), center.y + radius * sin(rad))
      }

      down(pointAt(startAngleDeg))
      // Move counter-clockwise (-18 deg per 16ms frame => ~ -1125 deg/sec)
      for (step in 1..10) {
        advanceEventTime(16L)
        moveTo(pointAt(startAngleDeg - step * 18f))
      }
      advanceEventTime(16L)
      up()
    }

    released = true

    // Advance frames so inertial coasting runs step-by-step
    repeat(60) {
      composeTestRule.mainClock.advanceTimeByFrame()
    }

    assertTrue(
      "Expected multiple inertial coasting frames after finger lift, got ${postReleaseDeltas.size}",
      postReleaseDeltas.size >= 5,
    )
    assertTrue(
      "All post-release back-spin deltas should be negative, got $postReleaseDeltas",
      postReleaseDeltas.all { it < 0f },
    )
    assertTrue(
      "Friction should decelerate frame deltas over time (first=${postReleaseDeltas.first()}, last=${postReleaseDeltas.last()})",
      abs(postReleaseDeltas.last()) < abs(postReleaseDeltas.first()),
    )

    // Verify coasting eventually settles cleanly to rest (no further deltas emitted)
    val settledCount = postReleaseDeltas.size
    repeat(20) {
      composeTestRule.mainClock.advanceTimeByFrame()
    }
    assertEquals(
      "Jog wheel should reach complete rest after friction decay",
      settledCount,
      postReleaseDeltas.size,
    )

    // Now test grabbing the platter mid-coast cancels inertia immediately
    postReleaseDeltas.clear()
    released = false

    composeTestRule.onNodeWithTag("vinyl_jog_wheel_test").performTouchInput {
      val radius = width * 0.36f
      fun pointAt(deg: Float): Offset {
        val rad = deg * (PI.toFloat() / 180f)
        return Offset(center.x + radius * cos(rad), center.y + radius * sin(rad))
      }
      down(pointAt(0f))
      for (step in 1..8) {
        advanceEventTime(16L)
        moveTo(pointAt(-step * 18f))
      }
      advanceEventTime(16L)
      up()
    }

    released = true
    repeat(3) {
      composeTestRule.mainClock.advanceTimeByFrame()
    }
    val deltasDuringShortCoast = postReleaseDeltas.size
    assertTrue("Expected active coasting right after release", deltasDuringShortCoast >= 1)

    // Grab the vinyl without dragging (hold down and cancel/up slowly)
    composeTestRule.onNodeWithTag("vinyl_jog_wheel_test").performTouchInput {
      down(center + Offset(width * 0.35f, 0f))
    }
    val countAfterGrab = postReleaseDeltas.size
    repeat(15) {
      composeTestRule.mainClock.advanceTimeByFrame()
    }
    assertEquals(
      "Grabbing the platter should immediately cancel inertial coasting",
      countAfterGrab,
      postReleaseDeltas.size,
    )
  }
}
