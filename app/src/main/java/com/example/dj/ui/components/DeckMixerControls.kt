package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dj.model.DeckState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.StemType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DeckTogglePill(
  label: String,
  active: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (active) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(8.dp))
        .border(
          1.dp,
          if (active) accentColor else Color.Transparent,
          RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun RotaryTrimKnob(
  trimGain: Float,
  accentColor: Color,
  onTrimChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = "TRIM",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
      modifier =
        Modifier.size(48.dp)
          .clip(CircleShape)
          .pointerInput(trimGain) {
            detectVerticalDragGestures { change, dragAmount ->
              change.consume()
              onTrimChange((trimGain - dragAmount * 0.006f).coerceIn(0.2f, 1.8f))
            }
          },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFF1A2230), radius = r)
        drawCircle(
          color = accentColor.copy(alpha = 0.7f),
          radius = r - 2.dp.toPx(),
          style = Stroke(width = 2.dp.toPx()),
        )
        val norm = ((trimGain - 0.2f) / 1.6f).coerceIn(0f, 1f)
        val angleDeg = -225f + norm * 270f
        val rad = angleDeg * (PI / 180.0)
        val end = Offset(cx + (r * 0.75f) * cos(rad).toFloat(), cy + (r * 0.75f) * sin(rad).toFloat())
        drawLine(color = Color.White, start = Offset(cx, cy), end = end, strokeWidth = 2.5.dp.toPx())
      }
    }
    Text(
      text = "${String.format("%.2f", trimGain)}x",
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotCueRow(
  deck: DeckState,
  onHotCueTap: (Int) -> Unit,
  onHotCueLongPress: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (slot in 1..4) {
      val cue = deck.hotCues[slot]
      val cueColor = if (cue != null) Color(cue.colorHex) else MaterialTheme.colorScheme.onSurfaceVariant
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (cue != null) cueColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(1f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(
              1.dp,
              if (cue != null) cueColor else Color(0xFF2D384C),
              RoundedCornerShape(8.dp),
            )
            .combinedClickable(
              onClick = { onHotCueTap(slot) },
              onLongClick = { onHotCueLongPress(slot) },
            )
            .testTag("hot_cue_${slot}_${deck.deckId.name}"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
          Text(
            text = "CUE $slot",
            style = MaterialTheme.typography.labelSmall,
            color = cueColor,
          )
        }
      }
    }
  }
}

@Composable
fun LoopControlsRow(
  deck: DeckState,
  accentColor: Color,
  onToggleLoop: () -> Unit,
  onCycleLoopBeats: () -> Unit,
) {
  val beatLabel =
    when (deck.loop.beats) {
      0.25f -> "1/4 BT"
      0.5f -> "1/2 BT"
      else -> "${deck.loop.beats.toInt()} BT"
    }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color =
        if (deck.loop.isActive) accentColor.copy(alpha = 0.24f)
        else MaterialTheme.colorScheme.surfaceVariant,
      modifier =
        Modifier.weight(1.3f)
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(8.dp))
          .border(
            1.dp,
            if (deck.loop.isActive) accentColor else Color(0xFF2D384C),
            RoundedCornerShape(8.dp),
          )
          .clickable(onClick = onToggleLoop)
          .testTag("loop_button_${deck.deckId.name}"),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
          text = if (deck.loop.isActive) "LOOP ON" else "AUTO LOOP",
          style = MaterialTheme.typography.labelMedium,
          color = if (deck.loop.isActive) accentColor else MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier =
        Modifier.weight(1f)
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(8.dp))
          .clickable(onClick = onCycleLoopBeats)
          .testTag("loop_beats_${deck.deckId.name}"),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
          text = beatLabel,
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
        )
      }
    }
  }
}

@Composable
fun StemsControlPanel(
  deck: DeckState,
  accentColor: Color,
  onStemLevelChange: (StemType, Float) -> Unit,
  onToggleMute: (StemType) -> Unit,
  onToggleSolo: (StemType) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "4-STEM ISOLATION",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StemType.entries.forEach { stem ->
        val state = deck.stems[stem]
        val level = state?.level ?: 1f
        val isMuted = state?.isMuted ?: false
        val isSolo = state?.isSolo ?: false

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.weight(1f)
              .border(
                1.dp,
                if (isSolo) accentColor else Color(0xFF2B3445),
                RoundedCornerShape(10.dp),
              ),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stem.shortLabel,
              style = MaterialTheme.typography.labelMedium,
              color = if (isMuted) Color(0xFFFF3B5C) else accentColor,
            )
            Slider(
              value = level,
              onValueChange = { onStemLevelChange(stem, it) },
              valueRange = 0f..1f,
              colors =
                SliderDefaults.colors(
                  thumbColor = if (isMuted) Color.Gray else accentColor,
                  activeTrackColor = if (isMuted) Color.Gray else accentColor,
                ),
              modifier = Modifier.height(28.dp).testTag("stem_slider_${stem.shortLabel}_${deck.deckId.name}"),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              MiniToggleBadge(
                label = "M",
                active = isMuted,
                activeColor = Color(0xFFFF3B5C),
                onClick = { onToggleMute(stem) },
                modifier = Modifier.weight(1f).testTag("stem_mute_${stem.shortLabel}_${deck.deckId.name}"),
              )
              MiniToggleBadge(
                label = "S",
                active = isSolo,
                activeColor = accentColor,
                onClick = { onToggleSolo(stem) },
                modifier = Modifier.weight(1f).testTag("stem_solo_${stem.shortLabel}_${deck.deckId.name}"),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MiniToggleBadge(
  label: String,
  active: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(6.dp))
        .background(if (active) activeColor.copy(alpha = 0.28f) else Color(0xFF10151E))
        .border(
          1.dp,
          if (active) activeColor else Color(0xFF2D384C),
          RoundedCornerShape(6.dp),
        )
        .clickable(onClick = onClick)
        .padding(vertical = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun EqControlRow(
  deck: DeckState,
  accentColor: Color,
  presets: List<EqPreset>,
  onEqChange: (EqBand, Float) -> Unit,
  onApplyPreset: (EqPreset) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "3-BAND EQUALIZER",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EqBandSlider(
        label = "LOW",
        value = deck.eq.low,
        accentColor = accentColor,
        onValueChange = { onEqChange(EqBand.LOW, it) },
        modifier = Modifier.weight(1f).testTag("eq_low_${deck.deckId.name}"),
      )
      EqBandSlider(
        label = "MID",
        value = deck.eq.mid,
        accentColor = accentColor,
        onValueChange = { onEqChange(EqBand.MID, it) },
        modifier = Modifier.weight(1f).testTag("eq_mid_${deck.deckId.name}"),
      )
      EqBandSlider(
        label = "HIGH",
        value = deck.eq.high,
        accentColor = accentColor,
        onValueChange = { onEqChange(EqBand.HIGH, it) },
        modifier = Modifier.weight(1f).testTag("eq_high_${deck.deckId.name}"),
      )
    }

    if (presets.isNotEmpty()) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(presets) { preset ->
          val isActive =
            abs(deck.eq.low - preset.low) < 0.04f &&
              abs(deck.eq.mid - preset.mid) < 0.04f &&
              abs(deck.eq.high - preset.high) < 0.04f
          Surface(
            shape = RoundedCornerShape(8.dp),
            color =
              if (isActive) accentColor.copy(alpha = 0.22f)
              else MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .border(
                  1.dp,
                  if (isActive) accentColor else Color(0xFF2D384C),
                  RoundedCornerShape(8.dp),
                )
                .clickable { onApplyPreset(preset) }
                .testTag("eq_preset_${preset.name}_${deck.deckId.name}"),
          ) {
            Text(
              text = preset.name,
              style = MaterialTheme.typography.labelSmall,
              color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EqBandSlider(
  label: String,
  value: Float,
  accentColor: Color,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = "$label ${(value * 100).roundToInt()}%",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = 0f..1.5f,
      colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
      modifier = Modifier.height(28.dp),
    )
  }
}

@Composable
fun EffectsControlRow(
  deck: DeckState,
  accentColor: Color,
  onEffectsChange: (Float?, Float?, Float?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = "DSP EFFECTS (LPF/HPF • ECHO • FLANGER)",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text =
            when {
              deck.effects.filter < -0.05f -> "LPF"
              deck.effects.filter > 0.05f -> "HPF"
              else -> "FILTER"
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = deck.effects.filter,
          onValueChange = { onEffectsChange(it, null, null) },
          valueRange = -1f..1f,
          colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
          modifier = Modifier.height(28.dp).testTag("fx_filter_${deck.deckId.name}"),
        )
      }
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "ECHO ${(deck.effects.echo * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = deck.effects.echo,
          onValueChange = { onEffectsChange(null, it, null) },
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
          modifier = Modifier.height(28.dp).testTag("fx_echo_${deck.deckId.name}"),
        )
      }
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "FLANGE ${(deck.effects.flanger * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = deck.effects.flanger,
          onValueChange = { onEffectsChange(null, null, it) },
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
          modifier = Modifier.height(28.dp).testTag("fx_flanger_${deck.deckId.name}"),
        )
      }
    }
  }
}
