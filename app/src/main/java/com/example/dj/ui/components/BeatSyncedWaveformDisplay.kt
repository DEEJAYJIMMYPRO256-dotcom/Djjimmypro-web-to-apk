package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.BeatSyncedWaveformMath
import com.example.dj.model.DeckState
import com.example.dj.model.WaveformZoomConfig
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Beat-synced waveform display component that tracks real-time audio progress,
 * beat-grid phase, and live peak intensity for an individual DJ deck.
 */
@Composable
fun BeatSyncedWaveformDisplay(
  deck: DeckState,
  accentColor: Color,
  beatMatchDeltaBpm: Float,
  onScratch: (Float) -> Unit,
  modifier: Modifier = Modifier,
  zoomLevel: Float = 1.0f,
  isNeutral: Boolean = true,
  onZoomChange: ((Float) -> Unit)? = null,
  showZoomControls: Boolean = false,
  compact: Boolean = false,
) {
  val tagSuffix = deck.deckId.name
  val peaks = deck.track.waveformPeaks
  val safeZoom = zoomLevel.coerceIn(1.0f, 8.0f)
  val latestZoom by rememberUpdatedState(safeZoom)
  val latestOnZoomChange by rememberUpdatedState(onZoomChange)
  val latestPlayhead by rememberUpdatedState(deck.playheadProgress)
  val latestOnScratch by rememberUpdatedState(onScratch)

  val beatPos =
    BeatSyncedWaveformMath.beatPosition(
      playheadProgress = deck.playheadProgress,
      effectiveBpm = deck.effectiveBpm,
      durationSeconds = deck.track.durationSeconds,
    )
  val beatTransient =
    if (deck.isPlaying) exp((-beatPos.phase * 4.5f).toDouble()).toFloat() else 0f

  val effectivePeak =
    when {
      deck.livePeakIntensity > 0.01f -> deck.livePeakIntensity.coerceIn(0f, 1f)
      deck.isPlaying -> BeatSyncedWaveformMath.computeFallbackPeakIntensity(deck)
      else -> 0f
    }

  var peakHold by remember(deck.deckId) { mutableFloatStateOf(0f) }
  LaunchedEffect(effectivePeak, deck.isPlaying) {
    peakHold =
      if (!deck.isPlaying && effectivePeak <= 0.01f) {
        0f
      } else if (effectivePeak >= peakHold) {
        effectivePeak
      } else {
        (peakHold * 0.92f).coerceAtLeast(effectivePeak)
      }
  }

  val isBeatMatchClose = beatMatchDeltaBpm <= 3.0f
  val matchGlowAlpha =
    if (isBeatMatchClose) ((3.0f - beatMatchDeltaBpm) / 3.0f).coerceIn(0.25f, 1f) else 0f

  val bgColor = if (isNeutral) Color(0xFF06080B) else Color(0xFF040507)
  val playedBarColor = if (isNeutral) Color(0xFFE5EAF2) else accentColor
  val unplayedBarColor = if (isNeutral) Color(0xFF6B7482) else accentColor.copy(alpha = 0.28f)
  val loopHighlightColor =
    if (isNeutral) Color.White.copy(alpha = 0.14f) else accentColor.copy(alpha = 0.16f)

  val pinchZoomModifier =
    if (onZoomChange != null) {
      Modifier.pointerInput(deck.deckId) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
              val zoomFactor = event.calculateZoom()
              if (abs(zoomFactor - 1f) > 0.005f) {
                val nextZoom = (latestZoom * zoomFactor).coerceIn(1.0f, 8.0f)
                latestOnZoomChange?.invoke(nextZoom)
                event.changes.forEach { change ->
                  if (change.positionChanged()) change.consume()
                }
              }
            }
          } while (event.changes.any { it.pressed })
        }
      }
    } else {
      Modifier
    }

  val gestureModifier =
    if (deck.scratchMode) {
      Modifier.pointerInput(deck.deckId, deck.scratchMode, safeZoom) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          val width = size.width.coerceAtLeast(1)
          latestOnScratch((dragAmount.x / width) / safeZoom)
        }
      }
    } else {
      Modifier.pointerInput(deck.deckId, deck.scratchMode, safeZoom) {
        detectDragGesturesAfterLongPress { change, dragAmount ->
          change.consume()
          val width = size.width.coerceAtLeast(1)
          latestOnScratch(((dragAmount.x / width) * 0.25f) / safeZoom)
        }
      }
    }

  val canZoomOut = safeZoom > 1.05f
  val canZoomIn = safeZoom < 7.95f
  val zoomReadout =
    if (abs(safeZoom - safeZoom.roundToInt()) < 0.05f) {
      "${safeZoom.roundToInt()}x"
    } else {
      String.format(Locale.US, "%.1fx", safeZoom)
    }

  val progressPct = (deck.playheadProgress.coerceIn(0f, 1f) * 100f).roundToInt()
  val peakPct = (effectivePeak * 100f).roundToInt()
  val peakColor =
    when {
      effectivePeak >= 0.85f -> Color(0xFFFF3B5C)
      effectivePeak >= 0.62f -> Color(0xFFFFB300)
      else -> Color(0xFF00E676)
    }

  Column(
    modifier = modifier.then(pinchZoomModifier),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    if (showZoomControls && onZoomChange != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "WAVEFORM • ${deck.track.formattedDuration()}",
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF94A3B8),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (canZoomOut) Color(0xFF151B24) else Color(0xFF0D1016),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .border(
                  1.dp,
                  if (canZoomOut) Color(0xFF475569) else Color(0xFF222833),
                  RoundedCornerShape(6.dp),
                )
                .clickable(enabled = canZoomOut) {
                  val prev =
                    WaveformZoomConfig.steps.lastOrNull { it < safeZoom - 0.05f }
                      ?: WaveformZoomConfig.steps.first()
                  onZoomChange(prev)
                }
                .testTag("waveform_zoom_out_$tagSuffix"),
          ) {
            Text(
              text = "-",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (canZoomOut) Color(0xFFE2E8F0) else Color(0xFF525E70),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }

          Text(
            text = zoomReadout,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E8F0),
            modifier = Modifier.padding(horizontal = 2.dp).testTag("waveform_zoom_label_$tagSuffix"),
          )

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (canZoomIn) Color(0xFF151B24) else Color(0xFF0D1016),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .border(
                  1.dp,
                  if (canZoomIn) Color(0xFF475569) else Color(0xFF222833),
                  RoundedCornerShape(6.dp),
                )
                .clickable(enabled = canZoomIn) {
                  val next =
                    WaveformZoomConfig.steps.firstOrNull { it > safeZoom + 0.05f }
                      ?: WaveformZoomConfig.steps.last()
                  onZoomChange(next)
                }
                .testTag("waveform_zoom_in_$tagSuffix"),
          ) {
            Text(
              text = "+",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (canZoomIn) Color(0xFFE2E8F0) else Color(0xFF525E70),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    // Beat-Synced Audio Progress & Live Peak Intensity Telemetry Strip
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 2.dp)
          .testTag("waveform_telemetry_$tagSuffix"),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        // Beat Pulse LED synced to effectiveBpm & phase
        val ledColor =
          when {
            !deck.isPlaying -> Color(0xFF3A4454)
            beatPos.beatInBar == 1 && beatPos.phase < 0.30f -> Color(0xFF00E676)
            beatPos.phase < 0.28f -> accentColor
            else -> accentColor.copy(alpha = 0.25f)
          }
        Box(
          modifier =
            Modifier.size(7.dp)
              .clip(CircleShape)
              .background(ledColor)
              .testTag("waveform_beat_led_$tagSuffix")
        )
        Text(
          text = beatPos.formattedBarBeat,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (deck.isPlaying && beatPos.phase < 0.25f) accentColor else Color(0xFFCBD5E1),
          modifier = Modifier.testTag("waveform_beat_pos_$tagSuffix"),
        )
        Text(
          text = "$progressPct% • ${deck.formattedElapsed()}",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          color = Color(0xFF94A3B8),
          modifier = Modifier.testTag("waveform_progress_label_$tagSuffix"),
        )
      }

      // Live Peak Intensity Bar + Readout
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Canvas(
          modifier =
            Modifier.width(46.dp)
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .testTag("waveform_peak_meter_$tagSuffix")
        ) {
          drawRoundRect(
            color = Color(0xFF171E2B),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(3f, 3f),
          )
          val fillW = size.width * effectivePeak.coerceIn(0f, 1f)
          if (fillW > 0.5f) {
            drawRoundRect(
              brush =
                Brush.horizontalGradient(
                  colors = listOf(Color(0xFF00E676), Color(0xFFFFB300), Color(0xFFFF3B5C)),
                  startX = 0f,
                  endX = size.width,
                ),
              topLeft = Offset.Zero,
              size = Size(fillW, size.height),
              cornerRadius = CornerRadius(3f, 3f),
            )
          }
          if (peakHold > 0.03f) {
            val holdX = (size.width * peakHold.coerceIn(0f, 1f)).coerceIn(1f, size.width - 1f)
            drawLine(
              color = Color.White,
              start = Offset(holdX, 0f),
              end = Offset(holdX, size.height),
              strokeWidth = 1.5.dp.toPx(),
            )
          }
        }
        Text(
          text = "PK $peakPct%",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (effectivePeak > 0.02f) peakColor else Color(0xFF64748B),
          modifier = Modifier.testTag("waveform_peak_label_$tagSuffix"),
        )
      }
    }

    // Zoomable Beat-Synced Waveform Canvas
    val canvasHeight = if (compact) 44.dp else 60.dp
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(canvasHeight)
          .clip(RoundedCornerShape(10.dp))
          .background(bgColor)
          .then(
            if (isBeatMatchClose && !isNeutral) {
              Modifier.border(
                width = (1.5f + matchGlowAlpha * 1.5f).dp,
                color = Color(0xFF00E676).copy(alpha = 0.45f + matchGlowAlpha * 0.55f),
                shape = RoundedCornerShape(10.dp),
              )
            } else {
              Modifier.border(
                width = if (isBeatMatchClose) 1.5.dp else 1.dp,
                color =
                  if (isNeutral) {
                    if (isBeatMatchClose) Color(0xFF94A3B8) else Color(0xFF323946)
                  } else {
                    Color(0xFF242E40)
                  },
                shape = RoundedCornerShape(10.dp),
              )
            }
          )
          .then(gestureModifier)
          .testTag("waveform_canvas_$tagSuffix")
    ) {
      Canvas(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val viewport = WaveformViewportMath.computeViewport(deck.playheadProgress, safeZoom)
        val windowSpan = viewport.windowSpan
        val startProgress = viewport.startProgress
        val endProgress = viewport.endProgress

        fun toViewX(progress: Float): Float = viewport.toViewX(progress, w)

        // Subtle beat-synced background energy wash driven by live peak intensity
        if (effectivePeak > 0.05f) {
          val pulseBoost = (0.04f + 0.10f * effectivePeak + 0.08f * beatTransient).coerceAtMost(0.22f)
          drawRect(
            color = (if (isNeutral) Color.White else accentColor).copy(alpha = pulseBoost * 0.45f),
            topLeft = Offset.Zero,
            size = Size(w, h),
          )
        }

        // Active loop highlight + IN/OUT beat-grid brackets within visible zoom window
        val loopBracketColor = if (isNeutral) Color(0xFF00E676) else accentColor
        if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
          val startX = toViewX(deck.loop.startProgress).coerceIn(0f, w)
          val endX = toViewX(deck.loop.endProgress).coerceIn(0f, w)
          if (endX > startX) {
            drawRect(
              color = loopHighlightColor,
              topLeft = Offset(startX, 0f),
              size = Size((endX - startX).coerceAtLeast(2f), h),
            )
            drawLine(
              color = loopBracketColor,
              start = Offset(startX, 0f),
              end = Offset(startX, h),
              strokeWidth = 2.dp.toPx(),
            )
            drawLine(
              color = loopBracketColor,
              start = Offset(endX, 0f),
              end = Offset(endX, h),
              strokeWidth = 2.dp.toPx(),
            )
          }
        }

        // Neutral center baseline
        drawLine(
          color = Color.White.copy(alpha = 0.10f),
          start = Offset(0f, centerY),
          end = Offset(w, centerY),
          strokeWidth = 1.dp.toPx(),
        )

        // Dynamic beat grid overlay synchronized exactly to track duration & effectiveBpm across zoom window
        val exactTotalBeats =
          WaveformViewportMath.exactTotalBeats(deck.effectiveBpm, deck.track.durationSeconds)
        val visibleBeats = (exactTotalBeats * windowSpan).roundToInt().coerceAtLeast(1)
        val drawStep =
          when {
            visibleBeats > 140 -> 4
            visibleBeats > 70 -> 2
            else -> 1
          }
        val maxBeatIdx = exactTotalBeats.toInt()
        val firstBeat = (startProgress * exactTotalBeats).toInt().coerceAtLeast(0)
        val lastBeat = (endProgress * exactTotalBeats).toInt().coerceAtMost(maxBeatIdx)
        val currentBeatIndex = (deck.playheadProgress * exactTotalBeats).toInt()
        var beatIdx = (firstBeat / drawStep) * drawStep
        while (beatIdx <= lastBeat) {
          val beatProg =
            WaveformViewportMath.beatProgress(beatIdx, deck.effectiveBpm, deck.track.durationSeconds)
          val x = toViewX(beatProg)
          if (x in 0f..w) {
            val isDownbeat = beatIdx % 4 == 0
            val isCurrentBeat = beatIdx == currentBeatIndex
            drawLine(
              color =
                when {
                  isCurrentBeat -> accentColor.copy(alpha = 0.65f + 0.30f * beatTransient)
                  isDownbeat -> Color.White.copy(alpha = 0.30f)
                  else -> Color.White.copy(alpha = 0.11f)
                },
              start = Offset(x, 0f),
              end = Offset(x, h),
              strokeWidth = if (isDownbeat || isCurrentBeat) 1.5.dp.toPx() else 1.dp.toPx(),
            )
            if (isDownbeat) {
              drawRect(
                color = Color.White.copy(alpha = 0.42f),
                topLeft = Offset((x - 1.5.dp.toPx()).coerceAtLeast(0f), h - 4.dp.toPx()),
                size = Size(3.dp.toPx(), 4.dp.toPx()),
              )
            }
          }
          beatIdx += drawStep
        }

        // Interpolated symmetric waveform bars modulated near playhead by live peak intensity
        if (peaks.isNotEmpty()) {
          val barCount = (72 * safeZoom.coerceAtMost(2.5f)).roundToInt().coerceIn(64, 144)
          val barSlot = w / barCount
          val barW = (barSlot * 0.68f).coerceAtLeast(2f)
          for (i in 0 until barCount) {
            val frac = startProgress + (i.toFloat() / barCount) * windowSpan
            val exactIdx = (frac * (peaks.size - 1)).coerceIn(0f, (peaks.size - 1).toFloat())
            val idx0 = exactIdx.toInt()
            val idx1 = (idx0 + 1).coerceAtMost(peaks.size - 1)
            val t = exactIdx - idx0
            val basePeak = peaks[idx0] * (1f - t) + peaks[idx1] * t
            val distFromPlayhead = abs(frac - deck.playheadProgress) / windowSpan.coerceAtLeast(0.05f)
            val nearPlayheadBoost =
              if (deck.isPlaying && distFromPlayhead < 0.08f) {
                (1f + effectivePeak * 0.24f * (1f - distFromPlayhead / 0.08f))
              } else {
                1f
              }
            val barH = (basePeak * nearPlayheadBoost * h * 0.80f).coerceIn(4f, h * 0.94f)
            val color =
              if (isNeutral) {
                if (frac <= deck.playheadProgress) playedBarColor else unplayedBarColor
              } else {
                // Authentic Serato RGB multiband waveform palette (Blue low, Cyan/Violet mid, Pink/Magenta high)
                val bandColor =
                  when {
                    basePeak >= 0.78f -> Color(0xFFEC4899)
                    (i % 3) == 0 -> Color(0xFF2563EB)
                    (i % 3) == 1 -> Color(0xFF22D3EE)
                    else -> Color(0xFFA855F7)
                  }
                val isPlayedOrActive = deck.isPlaying || frac <= deck.playheadProgress
                if (isPlayedOrActive) bandColor else bandColor.copy(alpha = 0.42f)
              }
            drawRoundRect(
              color = color,
              topLeft = Offset(i * barSlot, centerY - barH / 2f),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(2f, 2f),
            )
          }
        }

        // Hot cue visual indicators within visible zoom window
        deck.hotCues.values.forEach { cue ->
          if (cue.progress in startProgress..endProgress) {
            val cx = toViewX(cue.progress)
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
        }

        // Beat-synced playhead aura + crisp needle inside zoomed viewport
        val playheadX = toViewX(deck.playheadProgress).coerceIn(0f, w)
        if (deck.isPlaying) {
          val glowWidth = (6.dp.toPx() + 10.dp.toPx() * effectivePeak)
          drawRect(
            color = accentColor.copy(alpha = (0.14f + 0.26f * beatTransient).coerceAtMost(0.45f)),
            topLeft = Offset((playheadX - glowWidth / 2f).coerceAtLeast(0f), 0f),
            size = Size(glowWidth, h),
          )
        }
        drawLine(
          color = Color.White,
          start = Offset(playheadX, 0f),
          end = Offset(playheadX, h),
          strokeWidth = 2.dp.toPx(),
        )
      }
    }

    // Full-Track Audio Progress Overview Track (0%..100%)
    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0xFF101520))
          .semantics {
            stateDescription = "$progressPct percent audio progress"
            progressBarRangeInfo = ProgressBarRangeInfo(deck.playheadProgress.coerceIn(0f, 1f), 0f..1f)
          }
          .pointerInput(deck.deckId) {
            detectTapGestures { offset ->
              val target = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
              latestOnScratch(target - latestPlayhead)
            }
          }
          .testTag("waveform_progress_bar_$tagSuffix")
    ) {
      val progressW = size.width * deck.playheadProgress.coerceIn(0f, 1f)
      if (progressW > 0.5f) {
        drawRoundRect(
          color = if (isNeutral) Color(0xFFCBD5E1) else accentColor,
          topLeft = Offset.Zero,
          size = Size(progressW, size.height),
          cornerRadius = CornerRadius(3f, 3f),
        )
      }
      if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
        val lx = size.width * deck.loop.startProgress
        val lw = (size.width * (deck.loop.endProgress - deck.loop.startProgress)).coerceAtLeast(2f)
        drawRect(
          color = Color(0xFF00E676).copy(alpha = 0.65f),
          topLeft = Offset(lx, 0f),
          size = Size(lw, size.height),
        )
      }
      deck.hotCues.values.forEach { cue ->
        val cx = size.width * cue.progress.coerceIn(0f, 1f)
        drawRect(
          color = Color(cue.colorHex),
          topLeft = Offset((cx - 1.dp.toPx()).coerceAtLeast(0f), 0f),
          size = Size(2.dp.toPx(), size.height),
        )
      }
    }
  }
}

/**
 * Serato-style central beat-transient phase comparison strip rendered between Deck 1 & Deck 2 waveforms.
 */
@Composable
fun SeratoCenterTransientStrip(
  deckA: DeckState,
  deckB: DeckState,
  zoomLevel: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(14.dp)
        .background(Color(0xFF030406))
        .testTag("serato_center_transient_strip")
  ) {
    val w = size.width
    val h = size.height
    val midY = h / 2f

    val vpA = WaveformViewportMath.computeViewport(deckA.playheadProgress, zoomLevel)
    val vpB = WaveformViewportMath.computeViewport(deckB.playheadProgress, zoomLevel)

    // Upper Deck 1 transient ticks (Cyan / Pink)
    val beatsA = WaveformViewportMath.exactTotalBeats(deckA.effectiveBpm, deckA.track.durationSeconds)
    val firstA = (vpA.startProgress * beatsA).toInt().coerceAtLeast(0)
    val lastA = (vpA.endProgress * beatsA).toInt().coerceAtLeast(firstA)
    val stepA = ((lastA - firstA) / 28).coerceAtLeast(1)
    var iA = firstA
    while (iA <= lastA) {
      val prog = WaveformViewportMath.beatProgress(iA, deckA.effectiveBpm, deckA.track.durationSeconds)
      val x = vpA.toViewX(prog, w)
      if (x in 0f..w) {
        val isBar = iA % 4 == 0
        drawLine(
          color = if (isBar) Color(0xFFEC4899) else Color(0xFF22D3EE),
          start = Offset(x, 1f),
          end = Offset(x, midY - 1f),
          strokeWidth = if (isBar) 2.2.dp.toPx() else 1.4.dp.toPx(),
        )
      }
      iA += stepA
    }

    // Lower Deck 2 transient ticks (Orange / Blue)
    val beatsB = WaveformViewportMath.exactTotalBeats(deckB.effectiveBpm, deckB.track.durationSeconds)
    val firstB = (vpB.startProgress * beatsB).toInt().coerceAtLeast(0)
    val lastB = (vpB.endProgress * beatsB).toInt().coerceAtLeast(firstB)
    val stepB = ((lastB - firstB) / 28).coerceAtLeast(1)
    var iB = firstB
    while (iB <= lastB) {
      val prog = WaveformViewportMath.beatProgress(iB, deckB.effectiveBpm, deckB.track.durationSeconds)
      val x = vpB.toViewX(prog, w)
      if (x in 0f..w) {
        val isBar = iB % 4 == 0
        drawLine(
          color = if (isBar) Color(0xFFF97316) else Color(0xFF3B82F6),
          start = Offset(x, midY + 1f),
          end = Offset(x, h - 1f),
          strokeWidth = if (isBar) 2.2.dp.toPx() else 1.4.dp.toPx(),
        )
      }
      iB += stepB
    }

    // Center white playhead divider
    drawLine(
      color = Color.White,
      start = Offset(w * 0.5f, 0f),
      end = Offset(w * 0.5f, h),
      strokeWidth = 1.5.dp.toPx(),
    )
  }
}
