package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.dj.model.DeckState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.StemType
import com.example.dj.model.TrimGainMath
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
  label: String = "GAIN TRIM",
  inputLevel: Float = 0f,
  showInputMeter: Boolean = true,
  compact: Boolean = false,
  tagSuffix: String? = null,
) {
  val clamped = TrimGainMath.clamp(trimGain)
  val dbText = TrimGainMath.formatDb(clamped)
  val isUnity = abs(clamped - TrimGainMath.UNITY_TRIM) < 0.03f
  val isClipping = inputLevel >= 0.85f || clamped >= 1.55f
  val isHot = clamped > 1.25f || inputLevel >= 0.65f
  val readoutColor =
    when {
      isClipping -> Color(0xFFFF3B5C)
      isHot -> Color(0xFFFFB300)
      isUnity -> Color(0xFFE2E8F0)
      else -> accentColor
    }
  val latestTrim by rememberUpdatedState(clamped)
  val latestOnTrimChange by rememberUpdatedState(onTrimChange)

  Column(
    modifier =
      modifier
        .semantics {
          contentDescription = "$label rotary gain trim"
          stateDescription = "$dbText, input level ${(inputLevel.coerceIn(0f, 1f) * 100f).roundToInt()} percent"
          progressBarRangeInfo =
            ProgressBarRangeInfo(clamped, TrimGainMath.MIN_TRIM..TrimGainMath.MAX_TRIM)
          setProgress { target ->
            latestOnTrimChange(TrimGainMath.clamp(target))
            true
          }
        }
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            // Dragging up or right increases gain; dragging down or left attenuates gain
            val delta = (-dragAmount.y + dragAmount.x * 0.65f) * 0.0055f
            latestOnTrimChange(TrimGainMath.clamp(latestTrim + delta))
          }
        },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontSize = if (compact) 9.sp else 10.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
      val touchSize = if (compact) 48.dp else 52.dp
      val dialSize = if (compact) 40.dp else 44.dp
      Box(
        modifier =
          Modifier.size(touchSize)
            .clip(CircleShape)
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = { latestOnTrimChange(TrimGainMath.UNITY_TRIM) },
              )
            },
        contentAlignment = Alignment.Center,
      ) {
        Canvas(modifier = Modifier.size(dialSize)) {
          val cx = size.width / 2f
          val cy = size.height / 2f
          val outerR = size.minDimension / 2f
          val knobR = outerR * 0.78f
          val norm =
            ((clamped - TrimGainMath.MIN_TRIM) /
              (TrimGainMath.MAX_TRIM - TrimGainMath.MIN_TRIM)).coerceIn(0f, 1f)
          val unityAngleDeg = -90f // 12 o'clock = 1.0x (0.0 dB)
          val sweepStart = -225f
          val sweepTotal = 270f
          val currentAngleDeg = sweepStart + norm * sweepTotal

          // 11 calibrated dial ticks around the 270-degree arc
          for (tick in 0..10) {
            val tickFrac = tick / 10f
            val deg = sweepStart + tickFrac * sweepTotal
            val rad = deg * (PI / 180.0)
            val isCenterTick = tick == 5
            val innerTickR = outerR * (if (isCenterTick) 0.80f else 0.85f)
            val tickColor =
              when {
                isCenterTick -> Color(0xFFE2E8F0)
                tick >= 8 -> Color(0xFFFFB300).copy(alpha = 0.75f)
                else -> Color(0xFF475569)
              }
            drawLine(
              color = tickColor,
              start =
                Offset(
                  cx + innerTickR * cos(rad).toFloat(),
                  cy + innerTickR * sin(rad).toFloat(),
                ),
              end =
                Offset(
                  cx + outerR * cos(rad).toFloat(),
                  cy + outerR * sin(rad).toFloat(),
                ),
              strokeWidth = if (isCenterTick) 2.dp.toPx() else 1.2.dp.toPx(),
              cap = StrokeCap.Round,
            )
          }

          // Background track arc + bipolar boost/cut arc from 0 dB center
          val arcInset = outerR * 0.12f
          val arcSize = Size(size.width - arcInset * 2f, size.height - arcInset * 2f)
          drawArc(
            color = Color(0xFF1F2937),
            startAngle = sweepStart,
            sweepAngle = sweepTotal,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
          )
          val arcSweep = currentAngleDeg - unityAngleDeg
          if (abs(arcSweep) > 1.5f) {
            drawArc(
              color = if (isClipping) Color(0xFFFF3B5C) else accentColor,
              startAngle = unityAngleDeg,
              sweepAngle = arcSweep,
              useCenter = false,
              topLeft = Offset(arcInset, arcInset),
              size = arcSize,
              style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
            )
          }

          // Hardware rotary cap body
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(Color(0xFF333D4E), Color(0xFF131923)),
                center = Offset(cx - knobR * 0.25f, cy - knobR * 0.25f),
                radius = knobR * 1.4f,
              ),
            radius = knobR,
            center = Offset(cx, cy),
          )
          drawCircle(
            color = readoutColor.copy(alpha = 0.75f),
            radius = knobR - 1.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.4.dp.toPx()),
          )

          // Pointer needle
          val rad = currentAngleDeg * (PI / 180.0)
          val start =
            Offset(
              cx + (knobR * 0.22f) * cos(rad).toFloat(),
              cy + (knobR * 0.22f) * sin(rad).toFloat(),
            )
          val end =
            Offset(
              cx + (knobR * 0.84f) * cos(rad).toFloat(),
              cy + (knobR * 0.84f) * sin(rad).toFloat(),
            )
          drawLine(
            color = Color.White,
            start = start,
            end = end,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }
      }

      if (showInputMeter) {
        val meterModifier =
          if (tagSuffix != null) {
            Modifier.width(6.dp).height(if (compact) 34.dp else 40.dp).testTag("input_level_meter_$tagSuffix")
          } else {
            Modifier.width(6.dp).height(if (compact) 34.dp else 40.dp)
          }
        Canvas(modifier = meterModifier) {
          val segments = 8
          val gap = 1.5.dp.toPx()
          val segH = (size.height - gap * (segments - 1)) / segments
          val lit = (inputLevel.coerceIn(0f, 1f) * segments).roundToInt()
          for (i in 0 until segments) {
            val fromBottom = segments - 1 - i
            val frac = (fromBottom + 1).toFloat() / segments
            val baseCol =
              when {
                frac >= 0.85f -> Color(0xFFFF3B5C)
                frac >= 0.62f -> Color(0xFFFFB300)
                else -> Color(0xFF00E676)
              }
            val color = if (fromBottom < lit) baseCol else baseCol.copy(alpha = 0.18f)
            drawRoundRect(
              color = color,
              topLeft = Offset(0f, i * (segH + gap)),
              size = Size(size.width, segH),
              cornerRadius = CornerRadius(2f, 2f),
            )
          }
        }
      }
    }

    val badgeModifier =
      Modifier.clip(RoundedCornerShape(4.dp))
        .background(Color(0xFF0E141E))
        .border(1.dp, readoutColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
        .clickable { onTrimChange(TrimGainMath.UNITY_TRIM) }
        .padding(horizontal = 5.dp, vertical = 1.dp)
        .let { if (tagSuffix != null) it.testTag("mixer_trim_db_$tagSuffix") else it }

    Text(
      text = dbText,
      style = MaterialTheme.typography.labelSmall,
      fontSize = if (compact) 9.sp else 10.sp,
      fontWeight = FontWeight.Bold,
      color = readoutColor,
      maxLines = 1,
      modifier = badgeModifier,
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
  onTriggerAutoLoop: (Float) -> Unit = {},
  onHalveLoop: () -> Unit = {},
  onDoubleLoop: () -> Unit = {},
) {
  val tagSuffix = deck.deckId.name
  val beatLabel =
    when (deck.loop.beats) {
      0.25f -> "1/4B"
      0.5f -> "1/2B"
      else -> "${deck.loop.beats.toInt()}B"
    }
  val is4BeatActive = deck.loop.isActive && kotlin.math.abs(deck.loop.beats - 4f) < 0.05f
  val is8BeatActive = deck.loop.isActive && kotlin.math.abs(deck.loop.beats - 8f) < 0.05f

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(7.dp),
        color =
          if (deck.loop.isActive) accentColor.copy(alpha = 0.24f)
          else MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(1.15f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(
              1.dp,
              if (deck.loop.isActive) accentColor else Color(0xFF2D384C),
              RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onToggleLoop)
            .testTag("loop_button_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = if (deck.loop.isActive) "LOOP ON" else "LOOP",
            style = MaterialTheme.typography.labelSmall,
            color = if (deck.loop.isActive) accentColor else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(7.dp),
        color =
          if (is4BeatActive) accentColor.copy(alpha = 0.26f)
          else MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(0.9f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(
              1.dp,
              if (is4BeatActive) accentColor else Color(0xFF2D384C),
              RoundedCornerShape(7.dp),
            )
            .clickable { onTriggerAutoLoop(4f) }
            .testTag("loop_4_button_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = "4 BEAT",
            style = MaterialTheme.typography.labelSmall,
            color = if (is4BeatActive) accentColor else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(7.dp),
        color =
          if (is8BeatActive) accentColor.copy(alpha = 0.26f)
          else MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(0.9f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(
              1.dp,
              if (is8BeatActive) accentColor else Color(0xFF2D384C),
              RoundedCornerShape(7.dp),
            )
            .clickable { onTriggerAutoLoop(8f) }
            .testTag("loop_8_button_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = "8 BEAT",
            style = MaterialTheme.typography.labelSmall,
            color = if (is8BeatActive) accentColor else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(0.65f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFF2D384C), RoundedCornerShape(7.dp))
            .clickable(onClick = onHalveLoop)
            .testTag("loop_half_button_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = "1/2x",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD5DFF0),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.weight(0.65f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFF2D384C), RoundedCornerShape(7.dp))
            .clickable(onClick = onDoubleLoop)
            .testTag("loop_double_button_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = "2x",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD5DFF0),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF151C28),
        modifier =
          Modifier.weight(0.75f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .clickable(onClick = onCycleLoopBeats)
            .testTag("loop_beats_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
          Text(
            text = beatLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
          )
        }
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
  onToggleQuickIsolate: () -> Unit = {},
) {
  val quickIsolateOn = deck.quickIsolateEnabled
  val karaokeActive = deck.isQuickIsolateActive

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "4-STEM ISOLATION",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (quickIsolateOn) {
          Text(
            text =
              if (karaokeActive) {
                "KARAOKE ACTIVE • VOX 0% + HPF ${(deck.effects.filter * 100).roundToInt()}%"
              } else {
                "Tap VOX Mute (M) for instant karaoke HPF"
              },
            style = MaterialTheme.typography.labelSmall,
            color = if (karaokeActive) Color(0xFF00E676) else accentColor,
            modifier = Modifier.testTag("stem_quick_isolate_status_${deck.deckId.name}"),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color =
          when {
            karaokeActive -> Color(0xFF00E676).copy(alpha = 0.24f)
            quickIsolateOn -> accentColor.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant
          },
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(
              width = 1.dp,
              color =
                when {
                  karaokeActive -> Color(0xFF00E676)
                  quickIsolateOn -> accentColor
                  else -> Color(0xFF2D384C)
                },
              shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onToggleQuickIsolate)
            .testTag("stem_quick_isolate_${deck.deckId.name}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Quick Isolate Karaoke Mode",
            tint =
              when {
                karaokeActive -> Color(0xFF00E676)
                quickIsolateOn -> accentColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
            modifier = Modifier.size(13.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = if (karaokeActive) "QUICK ISOLATE • ON" else "QUICK ISOLATE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
              when {
                karaokeActive -> Color(0xFF00E676)
                quickIsolateOn -> accentColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StemType.entries.forEach { stem ->
        val state = deck.stems[stem]
        val level = state?.level ?: 1f
        val isMuted = state?.isMuted ?: false
        val isSolo = state?.isSolo ?: false
        val isVocalsQuickArmed = stem == StemType.VOCALS && quickIsolateOn

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.weight(1f)
              .border(
                1.dp,
                when {
                  isVocalsQuickArmed && isMuted -> Color(0xFF00E676)
                  isSolo || isVocalsQuickArmed -> accentColor
                  else -> Color(0xFF2B3445)
                },
                RoundedCornerShape(10.dp),
              ),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = if (isVocalsQuickArmed) "${stem.shortLabel}•HPF" else stem.shortLabel,
              style = MaterialTheme.typography.labelMedium,
              color =
                when {
                  isVocalsQuickArmed && isMuted -> Color(0xFF00E676)
                  isMuted -> Color(0xFFFF3B5C)
                  else -> accentColor
                },
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
              deck.effects.filter < -0.05f -> "LPF ${(-deck.effects.filter * 100).roundToInt()}%"
              deck.effects.filter > 0.05f -> "HPF ${(deck.effects.filter * 100).roundToInt()}%"
              else -> "FILTER"
            },
          style = MaterialTheme.typography.labelSmall,
          color =
            if (deck.effects.filter > 0.05f || deck.effects.filter < -0.05f) accentColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("fx_filter_label_${deck.deckId.name}"),
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
