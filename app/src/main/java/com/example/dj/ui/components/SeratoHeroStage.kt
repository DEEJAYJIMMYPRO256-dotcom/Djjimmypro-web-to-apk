package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.MixerTheme
import com.example.dj.model.SamplePadBank
import com.example.dj.model.SamplePadCatalog
import com.example.dj.model.SamplePadEffect
import com.example.dj.model.SeratoHotCueDefaults
import com.example.dj.model.StemFxType
import com.example.dj.model.StemType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SeratoElectricBlue = Color(0xFF0D6EFD)
private val SeratoDeepRoyal = Color(0xFF1342A8)
private val SeratoDeckSurface = Color(0xFF1C1F26)
private val SeratoMatteWell = Color(0xFF0B0D10)
private val SeratoPadBorder = Color(0xFF282D37)

@Composable
fun SeratoHeroStage(
  deckA: DeckState,
  deckB: DeckState,
  mixerTheme: MixerTheme,
  waveformZoom: Float,
  isWaveformNeutral: Boolean,
  activeSamplePadIds: Set<String>,
  isDrawerOpen: Boolean,
  isLandscape: Boolean,
  onTogglePlay: (DeckId) -> Unit,
  onCue: (DeckId) -> Unit,
  onSync: (DeckId) -> Unit,
  onTapTempo: (DeckId) -> Unit,
  onToggleQuickLoop: (DeckId) -> Unit,
  onRotateJogWheel: (DeckId, Float) -> Unit,
  onScrubDeck: (DeckId, Float) -> Unit,
  onOpenLibrary: (DeckId) -> Unit,
  onZoomOutWaveform: () -> Unit,
  onZoomInWaveform: () -> Unit,
  onSetWaveformZoom: (Float) -> Unit,
  onToggleWaveformNeutral: () -> Unit,
  onHotCueTap: (DeckId, Int) -> Unit,
  onHotCueLongPress: (DeckId, Int) -> Unit,
  onToggleStemMute: (DeckId, StemType) -> Unit,
  onToggleStemFx: (DeckId, StemFxType) -> Unit,
  onToggleSamplePad: (SamplePadEffect) -> Unit,
  onToggleMixerDrawer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var leftSamplerMode by rememberSaveable { mutableStateOf(false) }
  var rightSamplerMode by rememberSaveable { mutableStateOf(false) }
  val beatMatchDelta = abs(deckA.effectiveBpm - deckB.effectiveBpm)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF050608))
        .border(1.dp, Color(0xFF1B1F28), RoundedCornerShape(10.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .testTag("serato_hero_stage"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // 1. Track Info Headers + Miniature Overview Waveforms
    SeratoTrackHeaderStrip(
      deckA = deckA,
      deckB = deckB,
      isLandscape = isLandscape,
      onScrubA = { onScrubDeck(DeckId.A, it) },
      onScrubB = { onScrubDeck(DeckId.B, it) },
    )

    // 2. Dual Platters + Center Stacked Waveforms & Transient Strip + Side VU Meters / Library
    if (isLandscape) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        VerticalChannelMeterWithLibraryBtn(
          deck = deckA,
          onOpenLibrary = { onOpenLibrary(DeckId.A) },
        )

        SeratoPlatter(
          deck = deckA,
          accentColor = mixerTheme.deckAAccent,
          onTap = { onTogglePlay(DeckId.A) },
          onRotate = { onRotateJogWheel(DeckId.A, it) },
          platterSize = 114.dp,
          modifier = Modifier.testTag("jog_wheel_A"),
        )

        StackedDualWaveforms(
          deckA = deckA,
          deckB = deckB,
          mixerTheme = mixerTheme,
          beatMatchDeltaBpm = beatMatchDelta,
          onScrubA = { onScrubDeck(DeckId.A, it) },
          onScrubB = { onScrubDeck(DeckId.B, it) },
          waveformZoom = waveformZoom,
          isWaveformNeutral = isWaveformNeutral,
          onZoomOut = onZoomOutWaveform,
          onZoomIn = onZoomInWaveform,
          onZoomChange = onSetWaveformZoom,
          onToggleNeutral = onToggleWaveformNeutral,
          compact = true,
          showCenterTransientStrip = true,
          modifier = Modifier.weight(1f),
        )

        SeratoPlatter(
          deck = deckB,
          accentColor = mixerTheme.deckBAccent,
          onTap = { onTogglePlay(DeckId.B) },
          onRotate = { onRotateJogWheel(DeckId.B, it) },
          platterSize = 114.dp,
          modifier = Modifier.testTag("jog_wheel_B"),
        )

        VerticalChannelMeterWithLibraryBtn(
          deck = deckB,
          onOpenLibrary = { onOpenLibrary(DeckId.B) },
        )
      }
    } else {
      // Portrait Hero Stack: Center Stacked Waveforms immediately below headers (ensuring telemetry is above fold)
      StackedDualWaveforms(
        deckA = deckA,
        deckB = deckB,
        mixerTheme = mixerTheme,
        beatMatchDeltaBpm = beatMatchDelta,
        onScrubA = { onScrubDeck(DeckId.A, it) },
        onScrubB = { onScrubDeck(DeckId.B, it) },
        waveformZoom = waveformZoom,
        isWaveformNeutral = isWaveformNeutral,
        onZoomOut = onZoomOutWaveform,
        onZoomIn = onZoomInWaveform,
        onZoomChange = onSetWaveformZoom,
        onToggleNeutral = onToggleWaveformNeutral,
        compact = true,
        showCenterTransientStrip = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        VerticalChannelMeterWithLibraryBtn(
          deck = deckA,
          onOpenLibrary = { onOpenLibrary(DeckId.A) },
        )
        SeratoPlatter(
          deck = deckA,
          accentColor = mixerTheme.deckAAccent,
          onTap = { onTogglePlay(DeckId.A) },
          onRotate = { onRotateJogWheel(DeckId.A, it) },
          platterSize = 116.dp,
          modifier = Modifier.testTag("jog_wheel_A"),
        )
        SeratoPlatter(
          deck = deckB,
          accentColor = mixerTheme.deckBAccent,
          onTap = { onTogglePlay(DeckId.B) },
          onRotate = { onRotateJogWheel(DeckId.B, it) },
          platterSize = 116.dp,
          modifier = Modifier.testTag("jog_wheel_B"),
        )
        VerticalChannelMeterWithLibraryBtn(
          deck = deckB,
          onOpenLibrary = { onOpenLibrary(DeckId.B) },
        )
      }
    }

    // 3. Lower Deck Slate Console: Transport Pills + 4x2 Hot Cues & Stems/FX Pads + Center Drawer Chevron
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SeratoDeckSurface)
          .padding(horizontal = 6.dp, vertical = 5.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (isLandscape) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SeratoTransportRow(
            deck = deckA,
            mirrorOrder = false,
            onTogglePlay = { onTogglePlay(DeckId.A) },
            onCue = { onCue(DeckId.A) },
            onSync = { onSync(DeckId.A) },
            onToggleQuickLoop = { onToggleQuickLoop(DeckId.A) },
            onTapTempo = { onTapTempo(DeckId.A) },
            onOpenMixerDrawer = onToggleMixerDrawer,
            modifier = Modifier.weight(1f),
          )
          SeratoTransportRow(
            deck = deckB,
            mirrorOrder = true,
            onTogglePlay = { onTogglePlay(DeckId.B) },
            onCue = { onCue(DeckId.B) },
            onSync = { onSync(DeckId.B) },
            onToggleQuickLoop = { onToggleQuickLoop(DeckId.B) },
            onTapTempo = { onTapTempo(DeckId.B) },
            onOpenMixerDrawer = onToggleMixerDrawer,
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SeratoPadMatrixLeft(
            deck = deckA,
            useSamplerMode = leftSamplerMode,
            activeSamplePadIds = activeSamplePadIds,
            onTogglePadMode = { leftSamplerMode = !leftSamplerMode },
            onHotCueTap = { slot -> onHotCueTap(DeckId.A, slot) },
            onHotCueLongPress = { slot -> onHotCueLongPress(DeckId.A, slot) },
            onToggleSamplePad = onToggleSamplePad,
            modifier = Modifier.weight(1f),
          )

          SeratoMixerDrawerHandle(
            isDrawerOpen = isDrawerOpen,
            onClick = onToggleMixerDrawer,
          )

          SeratoPadMatrixRight(
            deck = deckB,
            useSamplerMode = rightSamplerMode,
            activeSamplePadIds = activeSamplePadIds,
            onTogglePadMode = { rightSamplerMode = !rightSamplerMode },
            onToggleStemMute = { stem -> onToggleStemMute(DeckId.B, stem) },
            onToggleStemFx = { fx -> onToggleStemFx(DeckId.B, fx) },
            onToggleSamplePad = onToggleSamplePad,
            modifier = Modifier.weight(1f),
          )
        }
      } else {
        // Portrait Transport + Dual Pad Matrices + Drawer Handle
        SeratoTransportRow(
          deck = deckA,
          mirrorOrder = false,
          onTogglePlay = { onTogglePlay(DeckId.A) },
          onCue = { onCue(DeckId.A) },
          onSync = { onSync(DeckId.A) },
          onToggleQuickLoop = { onToggleQuickLoop(DeckId.A) },
          onTapTempo = { onTapTempo(DeckId.A) },
          onOpenMixerDrawer = onToggleMixerDrawer,
          modifier = Modifier.fillMaxWidth(),
        )
        SeratoPadMatrixLeft(
          deck = deckA,
          useSamplerMode = leftSamplerMode,
          activeSamplePadIds = activeSamplePadIds,
          onTogglePadMode = { leftSamplerMode = !leftSamplerMode },
          onHotCueTap = { slot -> onHotCueTap(DeckId.A, slot) },
          onHotCueLongPress = { slot -> onHotCueLongPress(DeckId.A, slot) },
          onToggleSamplePad = onToggleSamplePad,
          modifier = Modifier.fillMaxWidth(),
        )

        SeratoTransportRow(
          deck = deckB,
          mirrorOrder = true,
          onTogglePlay = { onTogglePlay(DeckId.B) },
          onCue = { onCue(DeckId.B) },
          onSync = { onSync(DeckId.B) },
          onToggleQuickLoop = { onToggleQuickLoop(DeckId.B) },
          onTapTempo = { onTapTempo(DeckId.B) },
          onOpenMixerDrawer = onToggleMixerDrawer,
          modifier = Modifier.fillMaxWidth(),
        )
        SeratoPadMatrixRight(
          deck = deckB,
          useSamplerMode = rightSamplerMode,
          activeSamplePadIds = activeSamplePadIds,
          onTogglePadMode = { rightSamplerMode = !rightSamplerMode },
          onToggleStemMute = { stem -> onToggleStemMute(DeckId.B, stem) },
          onToggleStemFx = { fx -> onToggleStemFx(DeckId.B, fx) },
          onToggleSamplePad = onToggleSamplePad,
          modifier = Modifier.fillMaxWidth(),
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          SeratoMixerDrawerHandle(
            isDrawerOpen = isDrawerOpen,
            onClick = onToggleMixerDrawer,
          )
        }
      }
    }
  }
}

@Composable
fun SeratoTrackHeaderStrip(
  deckA: DeckState,
  deckB: DeckState,
  isLandscape: Boolean,
  onScrubA: (Float) -> Unit,
  onScrubB: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (isLandscape) {
    Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DeckHeaderCard(
        deck = deckA,
        camelotColor = Color(0xFF7E22CE),
        alignEnd = false,
        onScrub = onScrubA,
        modifier = Modifier.weight(1f),
      )
      DeckHeaderCard(
        deck = deckB,
        camelotColor = Color(0xFFEA580C),
        alignEnd = true,
        onScrub = onScrubB,
        modifier = Modifier.weight(1f),
      )
    }
  } else {
    Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DeckHeaderCard(
        deck = deckA,
        camelotColor = Color(0xFF7E22CE),
        alignEnd = false,
        onScrub = onScrubA,
        modifier = Modifier.weight(1f),
      )
      DeckHeaderCard(
        deck = deckB,
        camelotColor = Color(0xFFEA580C),
        alignEnd = true,
        onScrub = onScrubB,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun DeckHeaderCard(
  deck: DeckState,
  camelotColor: Color,
  alignEnd: Boolean,
  onScrub: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val camelot = CamelotHelper.toCamelot(deck.effectiveMusicalKey)
  val latestPlayhead by rememberUpdatedState(deck.playheadProgress)
  val latestOnScrub by rememberUpdatedState(onScrub)

  Column(
    modifier = modifier.padding(horizontal = 2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (!alignEnd) {
        TrackArtThumb(deck.deckId, camelotColor)
      }

      if (!alignEnd) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = deck.track.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = deck.track.artist,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Duration + Camelot Pill + BPM
      Column(horizontalAlignment = if (alignEnd) Alignment.Start else Alignment.End) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (alignEnd) {
            Surface(
              shape = RoundedCornerShape(3.dp),
              color = camelotColor,
            ) {
              Text(
                text = camelot,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              )
            }
          }
          Text(
            text = deck.track.formattedDuration(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color(0xFFD1D5DB),
          )
          if (!alignEnd) {
            Surface(
              shape = RoundedCornerShape(3.dp),
              color = camelotColor,
            ) {
              Text(
                text = camelot,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              )
            }
          }
        }
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = "${deck.track.bpm.roundToInt()}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Text(
            text = "BPM",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = Color(0xFF9CA3AF),
          )
        }
      }

      if (alignEnd) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
          Text(
            text = deck.track.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = deck.track.artist,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        TrackArtThumb(deck.deckId, camelotColor)
      }
    }

    // Serato Mini Overview Waveform with colored Hot Cue triangles & playhead line
    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(14.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0xFF0A0C10))
          .pointerInput(deck.deckId) {
            detectTapGestures { offset ->
              val target = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
              latestOnScrub(target - latestPlayhead)
            }
          }
          .testTag("hero_overview_waveform_${deck.deckId.name}")
    ) {
      val w = size.width
      val h = size.height
      val peaks = deck.track.waveformPeaks
      if (peaks.isNotEmpty()) {
        val barW = (w / peaks.size).coerceAtLeast(1.5f)
        peaks.forEachIndexed { idx, amp ->
          val frac = idx.toFloat() / peaks.size
          val bh = (amp * h * 0.9f).coerceIn(2f, h)
          val baseColor =
            when {
              (idx % 5) == 0 -> Color(0xFFEF4444)
              (idx % 3) == 0 -> Color(0xFF22D3EE)
              (idx % 2) == 0 -> Color(0xFF22C55E)
              else -> Color(0xFFEAB308)
            }
          drawRect(
            color = if (frac <= deck.playheadProgress) baseColor else baseColor.copy(alpha = 0.45f),
            topLeft = Offset(idx * barW, h - bh),
            size = Size((barW * 0.8f).coerceAtLeast(1f), bh),
          )
        }
      }

      // Hot Cue markers along overview bar
      deck.hotCues.values.forEach { cue ->
        val cx = (cue.progress.coerceIn(0f, 1f) * w)
        val path =
          Path().apply {
            moveTo(cx - 4f, h)
            lineTo(cx + 4f, h)
            lineTo(cx, h - 6f)
            close()
          }
        drawPath(path = path, color = Color(cue.colorHex))
      }

      // White playhead needle
      val px = (deck.playheadProgress.coerceIn(0f, 1f) * w).coerceIn(1f, w - 1f)
      drawLine(
        color = Color.White,
        start = Offset(px, 0f),
        end = Offset(px, h),
        strokeWidth = 2.dp.toPx(),
      )
    }
  }
}

@Composable
private fun TrackArtThumb(deckId: DeckId, accent: Color) {
  Box(
    modifier =
      Modifier.size(30.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(
          Brush.linearGradient(
            listOf(Color(0xFF1F2937), accent.copy(alpha = 0.45f), Color(0xFF0B0F17))
          )
        )
        .border(1.dp, Color(0xFF374151), RoundedCornerShape(4.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = deckId.numberLabel,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
  }
}

@Composable
fun VerticalChannelMeterWithLibraryBtn(
  deck: DeckState,
  onOpenLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val level =
    when {
      deck.livePeakIntensity > 0.01f -> deck.livePeakIntensity
      deck.isPlaying -> (0.45f + deck.inputSignalLevel * 0.45f).coerceIn(0f, 1f)
      else -> (deck.inputSignalLevel * 0.25f).coerceIn(0f, 0.35f)
    }

  Column(
    modifier = modifier.width(48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    // Dual segmented L/R VU meter
    Canvas(
      modifier =
        Modifier.width(22.dp)
          .height(74.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0xFF0A0C10))
          .border(0.75.dp, Color(0xFF262B35), RoundedCornerShape(3.dp))
          .padding(3.dp)
          .testTag("hero_vu_meter_${deck.deckId.name}")
    ) {
      val segments = 14
      val gap = 2.dp.toPx()
      val segH = ((size.height - gap * (segments - 1)) / segments).coerceAtLeast(1.5f)
      val colW = (size.width - 2.dp.toPx()) / 2f
      val litCount = (level * segments).roundToInt()

      for (s in 0 until segments) {
        val y = size.height - ((s + 1) * segH + s * gap)
        val isLit = s < litCount
        val baseCol =
          when {
            s >= segments - 2 -> Color(0xFFEF4444)
            s >= segments - 5 -> Color(0xFFEAB308)
            else -> Color(0xFF22C55E)
          }
        val segColor = if (isLit) baseCol else Color(0xFF1F242D)
        drawRoundRect(
          color = segColor,
          topLeft = Offset(0f, y),
          size = Size(colW, segH),
          cornerRadius = CornerRadius(1.5f, 1.5f),
        )
        drawRoundRect(
          color = segColor,
          topLeft = Offset(colW + 2.dp.toPx(), y),
          size = Size(colW, segH),
          cornerRadius = CornerRadius(1.5f, 1.5f),
        )
      }
    }

    // Electric Blue Library Music Button (>=48.dp accessible target)
    Box(
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .size(36.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(SeratoElectricBlue)
          .clickable(onClick = onOpenLibrary)
          .testTag("open_library_${deck.deckId.name}"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = "Open Library for ${deck.deckId.label}",
        tint = Color.White,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun SeratoTransportRow(
  deck: DeckState,
  mirrorOrder: Boolean,
  onTogglePlay: () -> Unit,
  onCue: () -> Unit,
  onSync: () -> Unit,
  onToggleQuickLoop: () -> Unit,
  onTapTempo: () -> Unit,
  onOpenMixerDrawer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val suffix = deck.deckId.name

  @Composable
  fun SlidersBtn(mod: Modifier) {
    TransportPill(
      active = false,
      activeColor = SeratoElectricBlue,
      onClick = onOpenMixerDrawer,
      modifier = mod.testTag("hero_mixer_btn_$suffix"),
    ) {
      Icon(
        imageVector = Icons.Filled.Tune,
        contentDescription = "Open Mixer Console",
        tint = Color(0xFFD1D5DB),
        modifier = Modifier.size(15.dp),
      )
    }
  }

  @Composable
  fun PlayBtn(mod: Modifier) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = if (deck.isPlaying) Color(0xFF2563EB) else SeratoElectricBlue,
      modifier =
        mod.minimumInteractiveComponentSize()
          .height(34.dp)
          .clip(RoundedCornerShape(18.dp))
          .border(
            1.dp,
            if (deck.isPlaying) Color(0xFF93C5FD) else SeratoElectricBlue,
            RoundedCornerShape(18.dp),
          )
          .clickable(onClick = onTogglePlay)
          .testTag("play_button_$suffix"),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (deck.isPlaying) "Pause ${deck.deckId.label}" else "Play ${deck.deckId.label}",
          tint = Color.White,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }

  @Composable
  fun CueBtn(mod: Modifier) {
    TransportPill(
      active = false,
      activeColor = Color(0xFFFF7A00),
      onClick = onCue,
      modifier = mod.testTag("cue_button_$suffix"),
    ) {
      Text(
        text = "CUE",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFE5E7EB),
      )
    }
  }

  @Composable
  fun SyncBtn(mod: Modifier) {
    TransportPill(
      active = deck.isAutoSynced,
      activeColor = Color(0xFF00E676),
      onClick = onSync,
      modifier = mod.testTag("sync_button_$suffix"),
    ) {
      Text(
        text = "SYNC",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (deck.isAutoSynced) Color(0xFF00E676) else Color(0xFFE5E7EB),
      )
    }
  }

  @Composable
  fun LoopBtn(mod: Modifier) {
    TransportPill(
      active = deck.loop.isActive,
      activeColor = SeratoElectricBlue,
      onClick = onToggleQuickLoop,
      modifier = mod.testTag("hero_loop_button_$suffix"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Loop,
          contentDescription = "Auto Loop",
          tint = if (deck.loop.isActive) Color(0xFF60A5FA) else Color(0xFFD1D5DB),
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = if (deck.loop.beats >= 1f) "${deck.loop.beats.roundToInt()}" else "${deck.loop.beats}",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (deck.loop.isActive) Color(0xFF60A5FA) else Color(0xFFE5E7EB),
        )
      }
    }
  }

  @Composable
  fun TapBtn(mod: Modifier) {
    TransportPill(
      active = false,
      activeColor = SeratoElectricBlue,
      onClick = onTapTempo,
      modifier = mod.testTag("tap_tempo_$suffix"),
    ) {
      Text(
        text = "TAP",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF9CA3AF),
      )
    }
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!mirrorOrder) {
      SlidersBtn(Modifier.weight(0.95f))
      PlayBtn(Modifier.weight(1.1f))
      CueBtn(Modifier.weight(0.95f))
      SyncBtn(Modifier.weight(0.95f))
      LoopBtn(Modifier.weight(0.95f))
      TapBtn(Modifier.weight(0.75f))
    } else {
      TapBtn(Modifier.weight(0.75f))
      LoopBtn(Modifier.weight(0.95f))
      SyncBtn(Modifier.weight(0.95f))
      CueBtn(Modifier.weight(0.95f))
      PlayBtn(Modifier.weight(1.1f))
      SlidersBtn(Modifier.weight(0.95f))
    }
  }
}

@Composable
private fun TransportPill(
  active: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (active) activeColor.copy(alpha = 0.22f) else Color(0xFF13151B),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .height(34.dp)
        .clip(RoundedCornerShape(18.dp))
        .border(
          1.dp,
          if (active) activeColor else Color(0xFF2D313C),
          RoundedCornerShape(18.dp),
        )
        .clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
      content()
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeratoPadMatrixLeft(
  deck: DeckState,
  useSamplerMode: Boolean,
  activeSamplePadIds: Set<String>,
  onTogglePadMode: () -> Unit,
  onHotCueTap: (Int) -> Unit,
  onHotCueLongPress: (Int) -> Unit,
  onToggleSamplePad: (SamplePadEffect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val samplerPads = SamplePadCatalog.allPads.take(8)
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PadBankSideToggle(
      topLabel = "PADS",
      bottomLabel = if (useSamplerMode) "FX\nPADS" else "HOT\nCUES",
      active = useSamplerMode,
      onClick = onTogglePadMode,
      modifier = Modifier.testTag("left_pad_mode_toggle"),
    )

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (!useSamplerMode) {
        val slots = SeratoHotCueDefaults.slots
        listOf(slots.subList(0, 4), slots.subList(4, 8)).forEach { rowSlots ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            rowSlots.forEach { slotMeta ->
              val cue = deck.hotCues[slotMeta.slot]
              val isSet = cue != null
              val showColored = isSet || slotMeta.defaultArmedColor
              val padColor = Color(cue?.colorHex ?: slotMeta.colorHex)
              val label = cue?.label ?: if (slotMeta.defaultArmedColor) slotMeta.label else ""
              Box(
                modifier =
                  Modifier.weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showColored) padColor else SeratoMatteWell)
                    .border(
                      width = if (isSet) 1.5.dp else 1.dp,
                      color = if (isSet) Color.White else if (showColored) padColor.copy(alpha = 0.85f) else SeratoPadBorder,
                      shape = RoundedCornerShape(6.dp),
                    )
                    .combinedClickable(
                      onClick = { onHotCueTap(slotMeta.slot) },
                      onLongClick = { onHotCueLongPress(slotMeta.slot) },
                    )
                    .testTag("hero_hot_cue_${slotMeta.slot}_${deck.deckId.name}"),
                contentAlignment = Alignment.Center,
              ) {
                if (label.isNotEmpty()) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      } else {
        listOf(samplerPads.subList(0, 4), samplerPads.subList(4, 8)).forEach { rowPads ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            rowPads.forEach { pad ->
              val isOn = pad.id in activeSamplePadIds
              val col = Color(pad.colorHex)
              Box(
                modifier =
                  Modifier.weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isOn) col else SeratoMatteWell)
                    .border(1.dp, if (isOn) Color.White else col.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .clickable { onToggleSamplePad(pad) }
                    .testTag("hero_sample_pad_left_${pad.id}"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = if (isOn) "${pad.title} ON" else pad.title,
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isOn) Color(0xFF050608) else Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 3.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SeratoPadMatrixRight(
  deck: DeckState,
  useSamplerMode: Boolean,
  activeSamplePadIds: Set<String>,
  onTogglePadMode: () -> Unit,
  onToggleStemMute: (StemType) -> Unit,
  onToggleStemFx: (StemFxType) -> Unit,
  onToggleSamplePad: (SamplePadEffect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val stemOrder = listOf(StemType.VOCALS, StemType.MELODY, StemType.BASS, StemType.DRUMS)
  val stemFxOrder = StemFxType.entries
  val samplerPads =
    rememberPadBankSamples(
      SamplePadCatalog.padsForBank(SamplePadBank.DRUMS_909).take(4) +
        SamplePadCatalog.padsForBank(SamplePadBank.FX_SIRENS).take(4)
    )

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (!useSamplerMode) {
        // Row 1: VOCAL, MELODY, BASS, DRUMS
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          stemOrder.forEach { stem ->
            val audible = deck.isStemAudible(stem)
            val label =
              when (stem) {
                StemType.VOCALS -> "VOCAL"
                StemType.MELODY -> "MELODY"
                StemType.BASS -> "BASS"
                StemType.DRUMS -> "DRUMS"
              }
            Box(
              modifier =
                Modifier.weight(1f)
                  .minimumInteractiveComponentSize()
                  .height(34.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (audible) SeratoDeepRoyal else SeratoMatteWell)
                  .border(
                    1.dp,
                    if (audible) Color(0xFF3B82F6) else SeratoPadBorder,
                    RoundedCornerShape(6.dp),
                  )
                  .clickable { onToggleStemMute(stem) }
                  .testTag("hero_stem_pad_${stem.name}"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (audible) Color.White else Color(0xFF9CA3AF),
                maxLines = 1,
              )
            }
          }
        }

        // Row 2: VOCAL ECHO, INST ECHO, INST BRAKER, DRUMS ECHO
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          stemFxOrder.forEach { fxType ->
            val active = deck.effects.isStemFxActive(fxType)
            Box(
              modifier =
                Modifier.weight(1f)
                  .minimumInteractiveComponentSize()
                  .height(34.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (active) SeratoDeepRoyal else SeratoMatteWell)
                  .border(
                    1.dp,
                    if (active) Color(0xFF22D3EE) else SeratoPadBorder,
                    RoundedCornerShape(6.dp),
                  )
                  .clickable { onToggleStemFx(fxType) }
                  .testTag("hero_stem_fx_${fxType.name}"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = fxType.multilineLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.5.sp,
                lineHeight = 8.5.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else Color(0xFFE5E7EB),
              )
            }
          }
        }
      } else {
        listOf(samplerPads.subList(0, 4), samplerPads.subList(4, 8)).forEach { rowPads ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            rowPads.forEach { pad ->
              val isOn = pad.id in activeSamplePadIds
              val col = Color(pad.colorHex)
              Box(
                modifier =
                  Modifier.weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isOn) col else SeratoMatteWell)
                    .border(1.dp, if (isOn) Color.White else col.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .clickable { onToggleSamplePad(pad) }
                    .testTag("hero_sample_pad_right_${pad.id}"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = if (isOn) "${pad.title} ON" else pad.title,
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isOn) Color(0xFF050608) else Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 3.dp),
                )
              }
            }
          }
        }
      }
    }

    PadBankSideToggle(
      topLabel = "PADS",
      bottomLabel = if (useSamplerMode) "FX\nPADS" else "STEMS",
      active = useSamplerMode,
      onClick = onTogglePadMode,
      modifier = Modifier.testTag("right_pad_mode_toggle"),
    )
  }
}

@Composable
private fun rememberPadBankSamples(list: List<SamplePadEffect>): List<SamplePadEffect> = list

@Composable
private fun PadBankSideToggle(
  topLabel: String,
  bottomLabel: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (active) SeratoDeepRoyal else Color(0xFF282D37),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .width(46.dp)
        .height(72.dp)
        .clip(RoundedCornerShape(8.dp))
        .border(1.dp, if (active) SeratoElectricBlue else Color(0xFF3A414F), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = topLabel,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 7.5.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF9CA3AF),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = bottomLabel,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = Color.White,
      )
    }
  }
}

@Composable
fun SeratoMixerDrawerHandle(
  isDrawerOpen: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = Color(0xFF151820),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .size(width = 48.dp, height = 34.dp)
        .clip(RoundedCornerShape(18.dp))
        .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("mixer_drawer_toggle"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = if (isDrawerOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
        contentDescription = if (isDrawerOpen) "Return to DJ Stage" else "Open Mixer Drawer",
        tint = Color(0xFFE5E7EB),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
