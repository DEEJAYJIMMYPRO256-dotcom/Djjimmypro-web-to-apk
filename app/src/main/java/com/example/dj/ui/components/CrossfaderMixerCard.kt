package com.example.dj.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dj.model.CrossfaderCurve
import com.example.dj.model.KeyboardShortcutFeedback
import com.example.dj.model.MixerTheme
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
) {
  val deckAColor = mixerTheme.deckAAccent
  val deckBColor = mixerTheme.deckBAccent

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = mixerTheme.consoleSurface,
    tonalElevation = 4.dp,
    modifier =
      modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "MASTER MIXER",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
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

      // Dual-Channel L/R Peak Level Meter
      MasterPeakMeter(
        peakLeft = peakLeft,
        peakRight = peakRight,
        modifier = Modifier.fillMaxWidth().testTag("master_peak_meter"),
      )

      // Crossfader header & quick cut pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        KeycapBadge(
          keyLabel = "[",
          text = "CUT A",
          accent = deckAColor,
          onClick = { onCrossfaderChange(0f) },
          testTag = "crossfader_cut_a_button",
        )
        KeycapBadge(
          keyLabel = "C",
          text = "CENTER ${(crossfader * 100).roundToInt()}%",
          accent = MaterialTheme.colorScheme.onSurface,
          onClick = { onCrossfaderChange(0.5f) },
          testTag = "crossfader_center_button",
        )
        KeycapBadge(
          keyLabel = "]",
          text = "CUT B",
          accent = deckBColor,
          onClick = { onCrossfaderChange(1f) },
          testTag = "crossfader_cut_b_button",
        )
      }

      // Main Crossfader Slider
      Slider(
        value = crossfader,
        onValueChange = onCrossfaderChange,
        valueRange = 0f..1f,
        colors =
          SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = deckAColor,
            inactiveTrackColor = deckBColor,
          ),
        modifier = Modifier.fillMaxWidth().testTag("crossfader_slider"),
      )

      // Nudge shortcut hint strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "← / ,  Nudge A",
          style = MaterialTheme.typography.labelSmall,
          color = deckAColor.copy(alpha = 0.85f),
        )
        Text(
          text = "Shift: Fast",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Nudge B  → / .",
          style = MaterialTheme.typography.labelSmall,
          color = deckBColor.copy(alpha = 0.85f),
        )
      }

      // Crossfader Curve & Auto-Mix Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "CURVE [X]",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = crossfaderCurve.label,
              style = MaterialTheme.typography.labelMedium,
              color = deckAColor,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                  else -> "AUTO-MIX [M]"
                },
              style = MaterialTheme.typography.labelSmall,
              color = if (autoMixEnabled) deckBColor else MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun KeycapBadge(
  keyLabel: String,
  text: String,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = accent.copy(alpha = 0.12f),
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
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
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
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
