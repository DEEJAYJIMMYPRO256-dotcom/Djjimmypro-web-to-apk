package com.example.dj.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.KeyboardShortcutFeedback
import com.example.dj.model.MixerTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CrossfaderMixerCard(
  crossfader: Float,
  crossfaderCurve: CrossfaderCurve,
  peakLeft: Float,
  peakRight: Float,
  autoMixEnabled: Boolean,
  autoMixDurationSeconds: Int,
  isAutoMixing: Boolean,
  mixerTheme: MixerTheme,
  shortcutFeedback: KeyboardShortcutFeedback?,
  onCrossfaderChange: (Float) -> Unit,
  onCycleCurve: () -> Unit,
  onToggleAutoMix: () -> Unit,
  onCycleAutoMixDuration: () -> Unit,
  onOpenShortcuts: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  deckATrimGain: Float = 1.0f,
  deckBTrimGain: Float = 1.0f,
  deckAInputLevel: Float = 0f,
  deckBInputLevel: Float = 0f,
  onDeckATrimChange: (Float) -> Unit = {},
  onDeckBTrimChange: (Float) -> Unit = {},
) {
  val deckAColor = mixerTheme.deckAAccent
  val deckBColor = mixerTheme.deckBAccent
  val (gainA, gainB) = crossfaderCurve.computeGains(crossfader)
  val gainAPct = (gainA * 100f).roundToInt()
  val gainBPct = (gainB * 100f).roundToInt()

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = mixerTheme.consoleSurface,
    tonalElevation = 4.dp,
    modifier =
      modifier
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .testTag("horizontal_crossfader_card"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "CROSSFADER MIXER",
          style = MaterialTheme.typography.titleSmall,
          fontSize = if (compact) 12.sp else 14.sp,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onOpenShortcuts)
              .testTag("crossfader_shortcuts_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Keyboard,
              contentDescription = "Keyboard shortcuts",
              tint = deckAColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "KEYS ?",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // Live keyboard shortcut feedback badge
      AnimatedVisibility(
        visible = shortcutFeedback != null,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = deckAColor.copy(alpha = 0.16f),
          modifier = Modifier.border(1.dp, deckAColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        ) {
          Text(
            text = shortcutFeedback?.label.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = deckAColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Pre-Mixer Channel Input Gain Trim Stage (manages input levels before mixer EQ/stems/faders)
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF070B12))
            .border(1.dp, Color(0xFF1F2A3A), RoundedCornerShape(10.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 5.dp else 6.dp)
            .testTag("mixer_input_trim_section"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "INPUT GAIN TRIM",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "PRE-MIXER • 2x TAP 0dB",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RotaryTrimKnob(
            trimGain = deckATrimGain,
            accentColor = deckAColor,
            onTrimChange = onDeckATrimChange,
            label = "CH 1 TRIM",
            inputLevel = deckAInputLevel,
            showInputMeter = true,
            compact = compact,
            tagSuffix = "A",
            modifier = Modifier.weight(1f).testTag("mixer_trim_knob_A"),
          )
          Box(
            modifier =
              Modifier.width(1.dp)
                .height(if (compact) 48.dp else 56.dp)
                .background(Color(0xFF1F2A3A)),
          )
          RotaryTrimKnob(
            trimGain = deckBTrimGain,
            accentColor = deckBColor,
            onTrimChange = onDeckBTrimChange,
            label = "CH 2 TRIM",
            inputLevel = deckBInputLevel,
            showInputMeter = true,
            compact = compact,
            tagSuffix = "B",
            modifier = Modifier.weight(1f).testTag("mixer_trim_knob_B"),
          )
        }
      }

      // Dual-Channel L/R Peak Level Meter
      MasterPeakMeter(
        peakLeft = peakLeft,
        peakRight = peakRight,
        modifier = Modifier.fillMaxWidth().testTag("master_peak_meter"),
      )

      // Live Deck 1 vs Deck 2 Audio Balance Meter Strip
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070B12))
            .border(1.dp, Color(0xFF1F2A3A), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "D1 $gainAPct%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = deckAColor,
            modifier = Modifier.testTag("crossfader_balance_A"),
          )
          Text(
            text = "AUDIO BALANCE",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "D2 $gainBPct%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = deckBColor,
            modifier = Modifier.testTag("crossfader_balance_B"),
          )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).testTag("crossfader_balance_bar")) {
          val halfW = size.width / 2f
          val centerGap = 2.dp.toPx()
          val halfTrackW = (halfW - centerGap).coerceAtLeast(4f)
          // Background halves
          drawRoundRect(
            color = deckAColor.copy(alpha = 0.14f),
            topLeft = Offset(0f, 0f),
            size = Size(halfTrackW, size.height),
            cornerRadius = CornerRadius(3f, 3f),
          )
          drawRoundRect(
            color = deckBColor.copy(alpha = 0.14f),
            topLeft = Offset(halfW + centerGap, 0f),
            size = Size(halfTrackW, size.height),
            cornerRadius = CornerRadius(3f, 3f),
          )
          // Active Deck 1 balance (fills outward from center toward Deck 1)
          val fillA = halfTrackW * gainA.coerceIn(0f, 1f)
          if (fillA > 0.5f) {
            drawRoundRect(
              color = deckAColor,
              topLeft = Offset(halfTrackW - fillA, 0f),
              size = Size(fillA, size.height),
              cornerRadius = CornerRadius(3f, 3f),
            )
          }
          // Active Deck 2 balance (fills outward from center toward Deck 2)
          val fillB = halfTrackW * gainB.coerceIn(0f, 1f)
          if (fillB > 0.5f) {
            drawRoundRect(
              color = deckBColor,
              topLeft = Offset(halfW + centerGap, 0f),
              size = Size(fillB, size.height),
              cornerRadius = CornerRadius(3f, 3f),
            )
          }
        }
      }

      // Crossfader header & quick cut pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        KeycapBadge(
          keyLabel = "[",
          text = "CUT A",
          accent = deckAColor,
          compact = compact,
          onClick = { onCrossfaderChange(0f) },
          testTag = "crossfader_cut_a_button",
          modifier = Modifier.weight(1f),
        )
        KeycapBadge(
          keyLabel = "\\",
          text = "CTR ${(crossfader * 100).roundToInt()}%",
          accent = MaterialTheme.colorScheme.onSurface,
          compact = compact,
          onClick = { onCrossfaderChange(0.5f) },
          testTag = "crossfader_center_button",
          modifier = Modifier.weight(1.15f),
        )
        KeycapBadge(
          keyLabel = "]",
          text = "CUT B",
          accent = deckBColor,
          compact = compact,
          onClick = { onCrossfaderChange(1f) },
          testTag = "crossfader_cut_b_button",
          modifier = Modifier.weight(1f),
        )
      }

      // Dedicated DJ Hardware Horizontal Crossfader
      HorizontalCrossfader(
        value = crossfader,
        deckAColor = deckAColor,
        deckBColor = deckBColor,
        gainA = gainA,
        gainB = gainB,
        onValueChange = onCrossfaderChange,
        modifier = Modifier.fillMaxWidth().testTag("crossfader_slider"),
      )

      // Nudge shortcut hint strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "← / ,  Deck 1",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          color = deckAColor.copy(alpha = 0.85f),
        )
        Text(
          text = "2x Tap: Center",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Deck 2  → / .",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          color = deckBColor.copy(alpha = 0.85f),
        )
      }

      // Crossfader Curve & Auto-Mix Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = onCycleCurve)
              .testTag("crossfader_curve_button"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "CURVE [X]",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
            Text(
              text = crossfaderCurve.label,
              style = MaterialTheme.typography.labelMedium,
              fontSize = if (compact) 11.sp else 12.sp,
              color = deckAColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color =
            if (autoMixEnabled) deckBColor.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(10.dp))
              .border(
                1.dp,
                if (autoMixEnabled) deckBColor else Color.Transparent,
                RoundedCornerShape(10.dp),
              )
              .clickable(onClick = onToggleAutoMix)
              .testTag("automix_toggle_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = "Auto Mix",
              tint = if (autoMixEnabled) deckBColor else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text =
                when {
                  isAutoMixing -> "MIXING..."
                  autoMixEnabled -> "AUTO ON"
                  else -> "AUTO-MIX"
                },
              style = MaterialTheme.typography.labelSmall,
              color = if (autoMixEnabled) deckBColor else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = onCycleAutoMixDuration)
              .testTag("automix_duration_button"),
        ) {
          Text(
            text = "${autoMixDurationSeconds}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
          )
        }
      }
    }
  }
}

/**
 * Custom hardware-styled horizontal DJ crossfader between Deck 1 (left, 0f) and Deck 2 (right, 1f).
 * Supports smooth horizontal dragging, single-tap positioning along the rail, double-tap snap to 50% center,
 * and Compose Slider/ProgressBar accessibility semantics.
 */
@Composable
fun HorizontalCrossfader(
  value: Float,
  deckAColor: Color,
  deckBColor: Color,
  gainA: Float,
  gainB: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val clamped = value.coerceIn(0f, 1f)
  val latestOnValueChange by rememberUpdatedState(onValueChange)
  val gainAPct = (gainA * 100f).roundToInt()
  val gainBPct = (gainB * 100f).roundToInt()

  BoxWithConstraints(
    modifier =
      modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF06090F))
        .border(1.dp, Color(0xFF232D3F), RoundedCornerShape(12.dp))
        .semantics(mergeDescendants = true) {
          contentDescription = "Deck 1 to Deck 2 horizontal crossfader"
          stateDescription = "Deck 1 $gainAPct percent, Deck 2 $gainBPct percent"
          progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
          setProgress { target ->
            latestOnValueChange(target.coerceIn(0f, 1f))
            true
          }
        }
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = { latestOnValueChange(0.5f) },
            onTap = { tapOffset ->
              val capHalfPx = 15.dp.toPx()
              val usableW = (size.width - capHalfPx * 2f).coerceAtLeast(1f)
              val next = ((tapOffset.x - capHalfPx) / usableW).coerceIn(0f, 1f)
              latestOnValueChange(next)
            },
          )
        }
        .pointerInput(Unit) {
          detectHorizontalDragGestures { change, _ ->
            change.consume()
            val capHalfPx = 15.dp.toPx()
            val usableW = (size.width - capHalfPx * 2f).coerceAtLeast(1f)
            val next = ((change.position.x - capHalfPx) / usableW).coerceIn(0f, 1f)
            latestOnValueChange(next)
          }
        }
        .padding(horizontal = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
      val capWidth = 28.dp.toPx()
      val capHeight = 34.dp.toPx()
      val capHalf = capWidth / 2f
      val trackLeft = capHalf
      val trackRight = (size.width - capHalf).coerceAtLeast(trackLeft + 1f)
      val trackWidth = trackRight - trackLeft
      val centerY = size.height / 2f
      val centerX = trackLeft + trackWidth * 0.5f
      val capCenterX = trackLeft + trackWidth * clamped

      // Calibrated DJ scale tick marks above and below the fader slot
      val tickFractions = listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
      tickFractions.forEach { frac ->
        val tx = trackLeft + trackWidth * frac
        val isCenter = abs(frac - 0.5f) < 0.01f
        val isQuarter = abs(frac - 0.25f) < 0.01f || abs(frac - 0.75f) < 0.01f || frac == 0f || frac == 1f
        val tickLen = if (isCenter) 7.dp.toPx() else if (isQuarter) 5.dp.toPx() else 3.dp.toPx()
        val tickColor =
          when {
            isCenter -> Color(0xFFE2E8F0)
            frac < 0.5f -> deckAColor.copy(alpha = 0.55f)
            else -> deckBColor.copy(alpha = 0.55f)
          }
        drawLine(
          color = tickColor,
          start = Offset(tx, centerY - 12.dp.toPx() - tickLen / 2f),
          end = Offset(tx, centerY - 12.dp.toPx() + tickLen / 2f),
          strokeWidth = if (isCenter) 2.dp.toPx() else 1.2.dp.toPx(),
        )
        drawLine(
          color = tickColor,
          start = Offset(tx, centerY + 12.dp.toPx() - tickLen / 2f),
          end = Offset(tx, centerY + 12.dp.toPx() + tickLen / 2f),
          strokeWidth = if (isCenter) 2.dp.toPx() else 1.2.dp.toPx(),
        )
      }

      // Recessed fader rail track
      val railHeight = 8.dp.toPx()
      drawRoundRect(
        color = Color(0xFF0E1520),
        topLeft = Offset(trackLeft, centerY - railHeight / 2f),
        size = Size(trackWidth, railHeight),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
      )

      // Left Deck 1 illuminated rail segment + Right Deck 2 illuminated rail segment
      if (capCenterX > trackLeft) {
        drawRoundRect(
          brush =
            Brush.horizontalGradient(
              colors = listOf(deckAColor.copy(alpha = 0.9f), deckAColor.copy(alpha = 0.35f)),
              startX = trackLeft,
              endX = capCenterX,
            ),
          topLeft = Offset(trackLeft, centerY - 2.5.dp.toPx()),
          size = Size((capCenterX - trackLeft).coerceAtLeast(0f), 5.dp.toPx()),
          cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
        )
      }
      if (trackRight > capCenterX) {
        drawRoundRect(
          brush =
            Brush.horizontalGradient(
              colors = listOf(deckBColor.copy(alpha = 0.35f), deckBColor.copy(alpha = 0.9f)),
              startX = capCenterX,
              endX = trackRight,
            ),
          topLeft = Offset(capCenterX, centerY - 2.5.dp.toPx()),
          size = Size((trackRight - capCenterX).coerceAtLeast(0f), 5.dp.toPx()),
          cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
        )
      }

      // Center 50% detent notch
      drawLine(
        color = Color.White.copy(alpha = 0.6f),
        start = Offset(centerX, centerY - 7.dp.toPx()),
        end = Offset(centerX, centerY + 7.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
      )

      // Milled hardware fader cap
      val capLeft = capCenterX - capHalf
      val capTop = centerY - capHeight / 2f
      val capGlowColor =
        when {
          clamped < 0.45f -> deckAColor
          clamped > 0.55f -> deckBColor
          else -> Color.White
        }

      // Subtle cap drop shadow
      drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(capLeft + 1.dp.toPx(), capTop + 2.dp.toPx()),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
      )

      // Cap body metallic gradient
      drawRoundRect(
        brush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF4B5565), Color(0xFF1F2633), Color(0xFF394252)),
            startY = capTop,
            endY = capTop + capHeight,
          ),
        topLeft = Offset(capLeft, capTop),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
      )

      // Cap glowing rim accent
      drawRoundRect(
        color = capGlowColor.copy(alpha = 0.85f),
        topLeft = Offset(capLeft, capTop),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx()),
      )

      // Center precision fader hairline
      drawLine(
        color = capGlowColor,
        start = Offset(capCenterX, capTop + 5.dp.toPx()),
        end = Offset(capCenterX, capTop + capHeight - 5.dp.toPx()),
        strokeWidth = 2.4.dp.toPx(),
      )
    }
  }
}

@Composable
private fun KeycapBadge(
  keyLabel: String,
  text: String,
  accent: Color,
  compact: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = accent.copy(alpha = 0.12f),
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier =
        Modifier.padding(
          horizontal = if (compact) 5.dp else 7.dp,
          vertical = if (compact) 4.dp else 5.dp,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
      Box(
        modifier =
          Modifier.background(accent.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
      ) {
        Text(
          text = keyLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = accent,
          maxLines = 1,
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = if (compact) 10.sp else 11.sp,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun MasterPeakMeter(
  peakLeft: Float,
  peakRight: Float,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    PeakRow(channel = "L", level = peakLeft)
    PeakRow(channel = "R", level = peakRight)
  }
}

@Composable
private fun PeakRow(channel: String, level: Float) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = channel,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(12.dp),
    )
    Canvas(modifier = Modifier.weight(1f).height(8.dp)) {
      val segments = 20
      val gap = 2.dp.toPx()
      val segWidth = (size.width - gap * (segments - 1)) / segments
      val litCount = (level.coerceIn(0f, 1f) * segments).roundToInt()

      for (i in 0 until segments) {
        val frac = (i + 1).toFloat() / segments
        val baseColor =
          when {
            frac >= 0.85f -> Color(0xFFFF3B5C)
            frac >= 0.65f -> Color(0xFFFFB300)
            else -> Color(0xFF00E676)
          }
        val color = if (i < litCount) baseColor else baseColor.copy(alpha = 0.16f)
        drawRoundRect(
          color = color,
          topLeft = Offset(i * (segWidth + gap), 0f),
          size = Size(segWidth, size.height),
          cornerRadius = CornerRadius(2f, 2f),
        )
      }
    }
  }
}
