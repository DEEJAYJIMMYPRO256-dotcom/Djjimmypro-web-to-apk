package com.example.dj.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.MixerTheme
import com.example.dj.model.StemType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun DeckPanel(
  deck: DeckState,
  mixerTheme: MixerTheme,
  beatMatchDeltaBpm: Float,
  eqPresets: List<EqPreset>,
  onTogglePlay: () -> Unit,
  onCue: () -> Unit,
  onSync: () -> Unit,
  onTapTempo: () -> Unit,
  onOpenLibrary: () -> Unit,
  onPickLocalFile: () -> Unit,
  onStemLevelChange: (StemType, Float) -> Unit,
  onToggleStemMute: (StemType) -> Unit,
  onToggleStemSolo: (StemType) -> Unit,
  onEqBandChange: (EqBand, Float) -> Unit,
  onApplyEqPreset: (EqPreset) -> Unit,
  onEffectsChange: (Float?, Float?, Float?) -> Unit,
  onTrimGainChange: (Float) -> Unit,
  onTempoOffsetChange: (Float) -> Unit,
  onCyclePitchRange: () -> Unit,
  onRotateJogWheel: (Float) -> Unit,
  onScrubWaveform: (Float) -> Unit,
  onHotCueTap: (Int) -> Unit,
  onHotCueLongPress: (Int) -> Unit,
  onToggleQuantize: () -> Unit,
  onToggleScratchMode: () -> Unit,
  onToggleKeyLock: () -> Unit,
  onToggleLoop: () -> Unit,
  onCycleLoopBeats: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accentColor =
    if (deck.deckId == DeckId.A) mixerTheme.deckAAccent else mixerTheme.deckBAccent
  val tagSuffix = deck.deckId.name

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = mixerTheme.consoleSurface,
    tonalElevation = 2.dp,
    modifier =
      modifier.border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Header: Deck label, BPM + beat pulse, Library & File Picker
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = 0.18f),
          ) {
            Text(
              text = deck.deckId.label,
              style = MaterialTheme.typography.labelMedium,
              color = accentColor,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          Text(
            text = "${String.format("%.1f", deck.effectiveBpm)} BPM",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          BpmBeatIndicator(
            bpm = deck.effectiveBpm,
            isPlaying = deck.isPlaying,
            accentColor = accentColor,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onPickLocalFile,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("pick_file_$tagSuffix"),
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = "Pick local audio file for ${deck.deckId.label}",
              tint = accentColor,
            )
          }
          IconButton(
            onClick = onOpenLibrary,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_library_$tagSuffix"),
          ) {
            Icon(
              imageVector = Icons.Filled.LibraryMusic,
              contentDescription = "Open track library for ${deck.deckId.label}",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // Track Info & Artwork Badge
      TrackInfoDisplay(deck = deck, accentColor = accentColor)

      // Waveform with Beat Grid, Hot Cues, Loop, Beat-Match Assistant & Scrubbing
      WaveformView(
        deck = deck,
        accentColor = accentColor,
        beatMatchDeltaBpm = beatMatchDeltaBpm,
        onScratch = onScrubWaveform,
        modifier = Modifier.fillMaxWidth().testTag("waveform_$tagSuffix"),
      )

      // Real-Time 16-Band Frequency Spectral Analyzer
      FrequencyBandVisualizer(
        deck = deck,
        accentColor = accentColor,
        modifier = Modifier.fillMaxWidth().testTag("spectrum_$tagSuffix"),
      )

      // Jog Wheel + Trim Knob + Pitch Slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        VinylJogWheel(
          rotationDeg = deck.jogRotationDeg,
          isPlaying = deck.isPlaying,
          accentColor = accentColor,
          onTap = onTogglePlay,
          onRotate = onRotateJogWheel,
          modifier = Modifier.testTag("jog_wheel_$tagSuffix"),
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            RotaryTrimKnob(
              trimGain = deck.trimGain,
              accentColor = accentColor,
              onTrimChange = onTrimGainChange,
              modifier = Modifier.testTag("trim_knob_$tagSuffix"),
            )

            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "PITCH ${String.format("%+.1f%%", deck.tempoOffset * 100f)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                      .clickable(onClick = onCyclePitchRange)
                      .testTag("pitch_range_$tagSuffix"),
                ) {
                  Text(
                    text = deck.pitchRange.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                  )
                }
              }
              Slider(
                value = deck.tempoOffset,
                onValueChange = onTempoOffsetChange,
                valueRange = -deck.pitchRange.maxOffset..deck.pitchRange.maxOffset,
                colors =
                  SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                  ),
                modifier = Modifier.fillMaxWidth().testTag("pitch_slider_$tagSuffix"),
              )
            }
          }

          // Performance Toggles: Quantize, Scratch, Key Lock
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            DeckTogglePill(
              label = "Q",
              active = deck.quantizeEnabled,
              accentColor = accentColor,
              onClick = onToggleQuantize,
              testTag = "quantize_button_$tagSuffix",
              modifier = Modifier.weight(1f),
            )
            DeckTogglePill(
              label = "SCR",
              active = deck.scratchMode,
              accentColor = accentColor,
              onClick = onToggleScratchMode,
              testTag = "scratch_button_$tagSuffix",
              modifier = Modifier.weight(1f),
            )
            DeckTogglePill(
              label = "KEY",
              active = deck.keyLockEnabled,
              accentColor = accentColor,
              onClick = onToggleKeyLock,
              testTag = "key_lock_button_$tagSuffix",
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Transport Controls: Play/Pause, Cue, Sync, Tap Tempo
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onTogglePlay,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (deck.isPlaying) accentColor else MaterialTheme.colorScheme.surfaceVariant,
              contentColor = if (deck.isPlaying) Color(0xFF0A0D12) else MaterialTheme.colorScheme.onSurface,
            ),
          modifier = Modifier.weight(1.3f).minimumInteractiveComponentSize().testTag("play_button_$tagSuffix"),
        ) {
          Icon(
            imageVector = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (deck.isPlaying) "Pause" else "Play",
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = if (deck.isPlaying) "PAUSE" else "PLAY",
            style = MaterialTheme.typography.labelMedium,
          )
        }

        OutlinedButton(
          onClick = onCue,
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cue_button_$tagSuffix"),
        ) {
          Text("CUE", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
          onClick = onSync,
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("sync_button_$tagSuffix"),
        ) {
          Icon(Icons.Filled.Sync, contentDescription = "Sync BPM", modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("SYNC", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
          onClick = onTapTempo,
          modifier = Modifier.weight(0.9f).minimumInteractiveComponentSize().testTag("tap_tempo_$tagSuffix"),
        ) {
          Text("TAP", style = MaterialTheme.typography.labelMedium)
        }
      }

      // Visual Countdown Timer to Next Cue + Hot Cues 1-4 + Auto-Loop
      deck.nextCueCountdown()?.let { countdown ->
        Text(
          text = countdown,
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
          modifier = Modifier.testTag("cue_countdown_$tagSuffix"),
        )
      }

      HotCueRow(
        deck = deck,
        onHotCueTap = onHotCueTap,
        onHotCueLongPress = onHotCueLongPress,
      )

      LoopControlsRow(
        deck = deck,
        accentColor = accentColor,
        onToggleLoop = onToggleLoop,
        onCycleLoopBeats = onCycleLoopBeats,
      )

      // 4-Stem Isolation Sliders + Mute/Solo
      StemsControlPanel(
        deck = deck,
        accentColor = accentColor,
        onStemLevelChange = onStemLevelChange,
        onToggleMute = onToggleStemMute,
        onToggleSolo = onToggleStemSolo,
      )

      // 3-Band EQ + Presets
      EqControlRow(
        deck = deck,
        accentColor = accentColor,
        presets = eqPresets,
        onEqChange = onEqBandChange,
        onApplyPreset = onApplyEqPreset,
      )

      // Real-Time Audio Effects (Filter, Echo, Flanger)
      EffectsControlRow(
        deck = deck,
        accentColor = accentColor,
        onEffectsChange = onEffectsChange,
      )
    }
  }
}

@Composable
fun TrackInfoDisplay(deck: DeckState, accentColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
          .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.Album,
        contentDescription = "Track artwork",
        tint = accentColor,
        modifier = Modifier.size(22.dp),
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = deck.track.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${deck.track.artist} • Key ${deck.track.musicalKey} (${CamelotHelper.toCamelot(deck.track.musicalKey)}) • ${deck.track.genre}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun BpmBeatIndicator(bpm: Float, isPlaying: Boolean, accentColor: Color) {
  var beatOn by remember { mutableStateOf(false) }
  LaunchedEffect(bpm, isPlaying) {
    if (!isPlaying) {
      beatOn = false
      return@LaunchedEffect
    }
    val halfBeatMs = ((60_000f / bpm.coerceIn(40f, 240f)) / 2f).toLong().coerceAtLeast(80L)
    while (true) {
      beatOn = !beatOn
      delay(halfBeatMs)
    }
  }
  val alpha by animateFloatAsState(
    targetValue = if (isPlaying && beatOn) 1f else 0.2f,
    animationSpec = tween(durationMillis = 60),
    label = "bpm_pulse",
  )
  Box(
    modifier =
      Modifier.size(10.dp)
        .clip(CircleShape)
        .background(accentColor.copy(alpha = alpha))
  )
}

@Composable
fun WaveformView(
  deck: DeckState,
  accentColor: Color,
  beatMatchDeltaBpm: Float,
  onScratch: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val peaks = deck.track.waveformPeaks
  val isBeatMatchClose = beatMatchDeltaBpm <= 3.0f
  val matchGlowAlpha =
    if (isBeatMatchClose) ((3.0f - beatMatchDeltaBpm) / 3.0f).coerceIn(0.25f, 1f) else 0f

  val gestureModifier =
    if (deck.scratchMode) {
      Modifier.pointerInput(deck.deckId, deck.scratchMode) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          val width = size.width.coerceAtLeast(1)
          onScratch(dragAmount.x / width)
        }
      }
    } else {
      Modifier.pointerInput(deck.deckId, deck.scratchMode) {
        detectDragGesturesAfterLongPress { change, dragAmount ->
          change.consume()
          val width = size.width.coerceAtLeast(1)
          onScratch((dragAmount.x / width) * 0.25f)
        }
      }
    }

  Box(
    modifier =
      modifier
        .height(68.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF0A0E14))
        .then(
          if (isBeatMatchClose) {
            Modifier.border(
              width = (1.5f + matchGlowAlpha * 1.5f).dp,
              color = Color(0xFF00E676).copy(alpha = 0.45f + matchGlowAlpha * 0.55f),
              shape = RoundedCornerShape(10.dp),
            )
          } else {
            Modifier.border(1.dp, Color(0xFF242E40), RoundedCornerShape(10.dp))
          }
        )
        .then(gestureModifier)
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(68.dp)) {
      val w = size.width
      val h = size.height
      val centerY = h / 2f

      // Active loop highlight
      if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
        val startX = deck.loop.startProgress * w
        val endX = deck.loop.endProgress * w
        drawRect(
          color = accentColor.copy(alpha = 0.16f),
          topLeft = Offset(startX, 0f),
          size = Size((endX - startX).coerceAtLeast(2f), h),
        )
      }

      // Dynamic beat grid overlay synced to effectiveBpm
      val totalBeats =
        ((deck.effectiveBpm / 60f) * deck.track.durationSeconds).roundToInt().coerceIn(8, 500)
      val beatStepPx = w / totalBeats.toFloat()
      val drawStep = if (totalBeats > 120) 4 else 1
      var beatIdx = 0
      while (beatIdx < totalBeats) {
        val x = beatIdx * beatStepPx
        val isDownbeat = beatIdx % 4 == 0
        drawLine(
          color = Color.White.copy(alpha = if (isDownbeat) 0.22f else 0.08f),
          start = Offset(x, 0f),
          end = Offset(x, h),
          strokeWidth = if (isDownbeat) 1.5.dp.toPx() else 1.dp.toPx(),
        )
        beatIdx += drawStep
      }

      // Symmetric waveform bars
      if (peaks.isNotEmpty()) {
        val barSlot = w / peaks.size
        val barW = (barSlot * 0.65f).coerceAtLeast(2f)
        peaks.forEachIndexed { idx, peak ->
          val frac = idx.toFloat() / peaks.size
          val barH = (peak * h * 0.78f).coerceAtLeast(4f)
          val color = if (frac <= deck.playheadProgress) accentColor else accentColor.copy(alpha = 0.32f)
          drawRoundRect(
            color = color,
            topLeft = Offset(idx * barSlot, centerY - barH / 2f),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(2f, 2f),
          )
        }
      }

      // Hot cue visual indicators
      deck.hotCues.values.forEach { cue ->
        val cx = cue.progress.coerceIn(0f, 1f) * w
        val cueColor = Color(cue.colorHex)
        drawLine(
          color = cueColor,
          start = Offset(cx, 0f),
          end = Offset(cx, h),
          strokeWidth = 2.dp.toPx(),
        )
        drawRect(
          color = cueColor,
          topLeft = Offset((cx - 4.dp.toPx()).coerceAtLeast(0f), 0f),
          size = Size(8.dp.toPx(), 6.dp.toPx()),
        )
      }

      // Crisp playhead
      val playheadX = deck.playheadProgress.coerceIn(0f, 1f) * w
      drawLine(
        color = Color.White,
        start = Offset(playheadX, 0f),
        end = Offset(playheadX, h),
        strokeWidth = 2.dp.toPx(),
      )
    }
  }
}

@Composable
fun FrequencyBandVisualizer(
  deck: DeckState,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(
    modifier =
      modifier
        .height(26.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF0B1018))
        .padding(horizontal = 4.dp, vertical = 3.dp)
  ) {
    val bands = 16
    val gap = 3.dp.toPx()
    val barW = (size.width - gap * (bands - 1)) / bands
    val activeFactor = if (deck.isPlaying) 1f else 0.22f
    val phase = deck.playheadProgress * 90f

    for (i in 0 until bands) {
      val eqGain =
        when {
          i <= 4 -> deck.eq.low
          i <= 10 -> deck.eq.mid
          else -> deck.eq.high
        }
      val wave = abs(sin((phase + i * 0.75f).toDouble())).toFloat()
      val mag = (wave * eqGain * activeFactor).coerceIn(0.08f, 1f)
      val barH = size.height * mag
      val color = if (mag > 0.85f) Color(0xFFFF3B5C) else accentColor.copy(alpha = 0.55f + 0.45f * mag)
      drawRoundRect(
        color = color,
        topLeft = Offset(i * (barW + gap), size.height - barH),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(2f, 2f),
      )
    }
  }
}

@Composable
fun VinylJogWheel(
  rotationDeg: Float,
  isPlaying: Boolean,
  accentColor: Color,
  onTap: () -> Unit,
  onRotate: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var lastAngle by remember { mutableStateOf<Float?>(null) }

  Box(
    modifier =
      modifier
        .size(96.dp)
        .clip(CircleShape)
        .clickable(onClick = onTap)
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { offset ->
              val cx = size.width / 2f
              val cy = size.height / 2f
              lastAngle = atan2(offset.y - cy, offset.x - cx) * (180f / PI.toFloat())
            },
            onDragEnd = { lastAngle = null },
            onDragCancel = { lastAngle = null },
          ) { change, _ ->
            change.consume()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val currentAngle =
              atan2(change.position.y - cy, change.position.x - cx) * (180f / PI.toFloat())
            val prev = lastAngle
            if (prev != null) {
              var delta = currentAngle - prev
              if (delta > 180f) delta -= 360f
              if (delta < -180f) delta += 360f
              onRotate(delta)
            }
            lastAngle = currentAngle
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(94.dp)) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val radius = size.minDimension / 2f

      drawCircle(color = Color(0xFF10151E), radius = radius)
      drawCircle(
        color = if (isPlaying) accentColor else Color(0xFF39465D),
        radius = radius - 3.dp.toPx(),
        style = Stroke(width = 3.dp.toPx()),
      )
      drawCircle(
        color = Color(0xFF1D2534),
        radius = radius * 0.72f,
        style = Stroke(width = 1.5.dp.toPx()),
      )
      drawCircle(
        color = accentColor.copy(alpha = 0.25f),
        radius = radius * 0.34f,
      )

      val rad = rotationDeg * (PI / 180.0)
      val markerEnd =
        Offset(
          x = cx + (radius * 0.85f) * cos(rad).toFloat(),
          y = cy + (radius * 0.85f) * sin(rad).toFloat(),
        )
      drawLine(
        color = accentColor,
        start = Offset(cx, cy),
        end = markerEnd,
        strokeWidth = 2.5.dp.toPx(),
      )
      drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx, cy))
    }
  }
}
