package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.MixerTheme
import com.example.dj.model.ProModeTab
import com.example.dj.model.SamplerSound
import com.example.dj.model.StemIsolationPreset
import com.example.dj.model.StemType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SeratoTopProBar(
  activeTab: ProModeTab,
  mixerTheme: MixerTheme,
  isRecording: Boolean,
  connectedMidiDevice: String?,
  peakLeft: Float,
  peakRight: Float,
  onSelectTab: (ProModeTab) -> Unit,
  onToggleRecording: () -> Unit,
  onCycleTheme: () -> Unit,
  onOpenShortcuts: () -> Unit,
  onOpenSettings: () -> Unit,
  masterBpm: Float = 128f,
  isMasterTapActive: Boolean = false,
  masterTapSource: String? = null,
  lastMasterTapAtMs: Long = 0L,
  isAnyDeckPlaying: Boolean = false,
  onMasterTapTempo: () -> Unit = {},
  onResetMasterTap: () -> Unit = {},
) {
  var clockText by remember { mutableStateOf("") }
  LaunchedEffect(Unit) {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    while (true) {
      clockText = fmt.format(Date())
      delay(15_000L)
    }
  }

  Surface(
    color = Color(0xFF040507),
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0xFF181C24)),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val showInlineHud = maxWidth >= 720.dp

      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          // Mode tabs (DJ, FX, Stems, Sampler, Master)
          LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            items(ProModeTab.entries) { tab ->
              val selected = tab == activeTab
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) Color(0xFF0D6EFD) else Color(0xFF13161E),
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectTab(tab) }
                    .testTag("pro_tab_${tab.name}"),
              ) {
                Text(
                  text = tab.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) Color.White else Color(0xFFB5C0D0),
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
              }
            }
          }

          if (showInlineHud) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(horizontal = 4.dp),
            ) {
              Text(
                text = "serato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                color = Color.White,
              )
              MasterTapTempoHud(
                masterBpm = masterBpm,
                isMasterTapActive = isMasterTapActive,
                masterTapSource = masterTapSource,
                lastMasterTapAtMs = lastMasterTapAtMs,
                isAnyDeckPlaying = isAnyDeckPlaying,
                accentColor = Color(0xFF0D6EFD),
                secondaryAccent = mixerTheme.deckBAccent,
                onTap = onMasterTapTempo,
                onReset = onResetMasterTap,
                compact = true,
              )
            }
          } else {
            // Compact Serato lowercase branding on portrait widths
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(horizontal = 6.dp),
            ) {
              Text(
                text = "serato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                color = Color.White,
              )
              if (connectedMidiDevice != null) {
                Text(
                  text = "MIDI",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF00E676),
                )
              }
            }
          }

          // Right toolbar: REC, Headphones, Theme, Shortcuts, Settings, Clock
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (isRecording) Color(0xFFFF3B5C).copy(alpha = 0.25f) else Color(0xFF13161E),
              modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onToggleRecording).testTag("rec_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(
                  Icons.Filled.FiberManualRecord,
                  contentDescription = "Record",
                  tint = if (isRecording) Color(0xFFFF3B5C) else Color(0xFF9BA8BE),
                  modifier = Modifier.size(10.dp),
                )
                Text(
                  text = "REC",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color = if (isRecording) Color(0xFFFF3B5C) else Color.White,
                )
              }
            }

            Icon(
              Icons.Filled.Headphones,
              contentDescription = "Cue Monitor",
              tint = if ((peakLeft + peakRight) > 0.05f) Color(0xFF00E676) else Color(0xFF6B7280),
              modifier = Modifier.size(15.dp),
            )

            IconButton(onClick = onCycleTheme, modifier = Modifier.size(32.dp).testTag("theme_switcher_button")) {
              Icon(Icons.Filled.Palette, contentDescription = "Switch Theme", tint = mixerTheme.deckAAccent, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onOpenShortcuts, modifier = Modifier.size(32.dp).testTag("shortcuts_help_button")) {
              Icon(Icons.Filled.Keyboard, contentDescription = "Keyboard Shortcuts", tint = Color(0xFFB5C0D0), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp).testTag("midi_settings_button")) {
              Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
            }
            if (clockText.isNotEmpty()) {
              Text(
                text = clockText,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(end = 2.dp),
              )
            }
          }
        }

        if (!showInlineHud) {
          MasterTapTempoHud(
            masterBpm = masterBpm,
            isMasterTapActive = isMasterTapActive,
            masterTapSource = masterTapSource,
            lastMasterTapAtMs = lastMasterTapAtMs,
            isAnyDeckPlaying = isAnyDeckPlaying,
            accentColor = Color(0xFF0D6EFD),
            secondaryAccent = mixerTheme.deckBAccent,
            onTap = onMasterTapTempo,
            onReset = onResetMasterTap,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
fun MasterTapTempoHud(
  masterBpm: Float,
  isMasterTapActive: Boolean,
  masterTapSource: String?,
  lastMasterTapAtMs: Long,
  isAnyDeckPlaying: Boolean,
  accentColor: Color,
  secondaryAccent: Color,
  onTap: () -> Unit,
  onReset: () -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val hudColor = if (isMasterTapActive) Color(0xFF00E676) else accentColor
  val sourceBadge =
    when {
      isMasterTapActive && masterTapSource != null -> masterTapSource
      masterTapSource != null -> "ARMED • $masterTapSource"
      else -> "AUTO"
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color.Transparent,
    tonalElevation = 6.dp,
    modifier =
      modifier
        .clip(RoundedCornerShape(10.dp))
        .background(
          Brush.horizontalGradient(
            colors =
              listOf(
                Color(0xFF0D1422),
                if (isMasterTapActive) Color(0xFF0E231D) else Color(0xFF111A2B),
                Color(0xFF1A1224),
              )
          )
        )
        .border(
          width = if (isMasterTapActive) 1.5.dp else 1.dp,
          brush =
            Brush.horizontalGradient(
              colors = listOf(hudColor.copy(alpha = 0.8f), secondaryAccent.copy(alpha = 0.6f))
            ),
          shape = RoundedCornerShape(10.dp),
        )
        .testTag("master_tap_tempo_hud"),
  ) {
    Row(
      modifier =
        Modifier.padding(
          horizontal = if (compact) 8.dp else 10.dp,
          vertical = if (compact) 3.dp else 4.dp,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        BpmBeatIndicator(
          bpm = masterBpm,
          isPlaying = isAnyDeckPlaying || isMasterTapActive || lastMasterTapAtMs > 0L,
          accentColor = hudColor,
        )
        Column {
          Text(
            text = "MASTER TAP TEMPO",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9BA8BE),
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Text(
              text = String.format(Locale.US, "%.2f BPM", masterBpm),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = if (isMasterTapActive) Color(0xFF00E676) else Color.White,
              modifier = Modifier.testTag("master_bpm_readout"),
            )
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = hudColor.copy(alpha = 0.18f),
              modifier = Modifier.border(1.dp, hudColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
            ) {
              Text(
                text = sourceBadge,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = hudColor,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).testTag("master_tap_source_badge"),
              )
            }
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (isMasterTapActive) {
          IconButton(
            onClick = onReset,
            modifier = Modifier.minimumInteractiveComponentSize().size(36.dp).testTag("master_tap_tempo_reset"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Reset Master Tap Tempo",
              tint = Color(0xFF9BA8BE),
              modifier = Modifier.size(16.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = hudColor.copy(alpha = 0.22f),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(8.dp))
              .border(1.dp, hudColor, RoundedCornerShape(8.dp))
              .clickable(onClick = onTap)
              .testTag("master_tap_tempo_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.TouchApp,
              contentDescription = "Tap Master Tempo",
              tint = hudColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "TAP",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = hudColor,
            )
          }
        }
      }
    }
  }
}

@Composable
fun StackedDualWaveforms(
  deckA: DeckState,
  deckB: DeckState,
  mixerTheme: MixerTheme,
  beatMatchDeltaBpm: Float,
  onScrubA: (Float) -> Unit,
  onScrubB: (Float) -> Unit,
  modifier: Modifier = Modifier,
  waveformZoom: Float = 2.0f,
  isWaveformNeutral: Boolean = true,
  onZoomOut: () -> Unit = {},
  onZoomIn: () -> Unit = {},
  onZoomChange: (Float) -> Unit = {},
  onToggleNeutral: () -> Unit = {},
  compact: Boolean = false,
  showCenterTransientStrip: Boolean = true,
) {
  val isClose = beatMatchDeltaBpm <= 3.0f
  val canZoomOut = waveformZoom > 1.05f
  val canZoomIn = waveformZoom < 7.95f
  val zoomFormatted =
    if (kotlin.math.abs(waveformZoom - waveformZoom.roundToInt()) < 0.05f) {
      "${waveformZoom.roundToInt()}x"
    } else {
      String.format(Locale.US, "%.1fx", waveformZoom)
    }

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF030406))
        .border(
          width = if (isClose) 1.dp else 0.75.dp,
          color =
            when {
              isWaveformNeutral && isClose -> Color(0xFF64748B)
              isWaveformNeutral -> Color(0xFF1E242E)
              isClose -> Color(0xFF00E676)
              else -> Color(0xFF1F2838)
            },
          shape = RoundedCornerShape(8.dp),
        )
        .padding(horizontal = 4.dp, vertical = if (compact) 3.dp else 5.dp),
    verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
  ) {
    if (!compact) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "1: ${deckA.track.title}",
          style = MaterialTheme.typography.labelSmall,
          color = if (isWaveformNeutral) Color(0xFFE2E8F0) else mixerTheme.deckAAccent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (isClose) {
          Text(
            text = "BEAT SYNC",
            style = MaterialTheme.typography.labelSmall,
            color = if (isWaveformNeutral) Color(0xFFE2E8F0) else Color(0xFF00E676),
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
        Text(
          text = "2: ${deckB.track.title}",
          style = MaterialTheme.typography.labelSmall,
          color = if (isWaveformNeutral) Color(0xFFCBD5E1) else mixerTheme.deckBAccent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Neutral Style + Zoom Out (-) / Zoom In (+) Control Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (isWaveformNeutral) Color(0xFF1B2029) else Color(0xFF0D6EFD).copy(alpha = 0.25f),
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(5.dp))
            .border(
              1.dp,
              if (isWaveformNeutral) Color(0xFF6B7280) else Color(0xFF0D6EFD),
              RoundedCornerShape(5.dp),
            )
            .clickable(onClick = onToggleNeutral)
            .testTag("waveform_neutral_toggle"),
      ) {
        Text(
          text = if (isWaveformNeutral) "NEUTRAL" else "COLOR",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (isWaveformNeutral) Color(0xFFF1F5F9) else Color(0xFF60A5FA),
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(5.dp),
          color = if (canZoomOut) Color(0xFF151921) else Color(0xFF0B0D12),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(5.dp))
              .border(
                1.dp,
                if (canZoomOut) Color(0xFF3F4856) else Color(0xFF1F242E),
                RoundedCornerShape(5.dp),
              )
              .clickable(enabled = canZoomOut, onClick = onZoomOut)
              .testTag("waveform_zoom_out"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ZoomOut,
              contentDescription = "Zoom out",
              tint = if (canZoomOut) Color(0xFFE2E8F0) else Color(0xFF525E70),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "-",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (canZoomOut) Color(0xFFE2E8F0) else Color(0xFF525E70),
            )
          }
        }

        Text(
          text = zoomFormatted,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFFE2E8F0),
          modifier = Modifier.padding(horizontal = 3.dp).testTag("waveform_zoom_label"),
        )

        Surface(
          shape = RoundedCornerShape(5.dp),
          color = if (canZoomIn) Color(0xFF151921) else Color(0xFF0B0D12),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(5.dp))
              .border(
                1.dp,
                if (canZoomIn) Color(0xFF3F4856) else Color(0xFF1F242E),
                RoundedCornerShape(5.dp),
              )
              .clickable(enabled = canZoomIn, onClick = onZoomIn)
              .testTag("waveform_zoom_in"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ZoomIn,
              contentDescription = "Zoom in",
              tint = if (canZoomIn) Color(0xFFE2E8F0) else Color(0xFF525E70),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "+",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (canZoomIn) Color(0xFFE2E8F0) else Color(0xFF525E70),
            )
          }
        }
      }
    }

    WaveformView(
      deck = deckA,
      accentColor = mixerTheme.deckAAccent,
      beatMatchDeltaBpm = beatMatchDeltaBpm,
      onScratch = onScrubA,
      zoomLevel = waveformZoom,
      isNeutral = isWaveformNeutral,
      onZoomChange = onZoomChange,
      compact = compact,
      modifier = Modifier.fillMaxWidth().testTag("waveform_A"),
    )
    if (showCenterTransientStrip) {
      SeratoCenterTransientStrip(
        deckA = deckA,
        deckB = deckB,
        zoomLevel = waveformZoom,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    WaveformView(
      deck = deckB,
      accentColor = mixerTheme.deckBAccent,
      beatMatchDeltaBpm = beatMatchDeltaBpm,
      onScratch = onScrubB,
      zoomLevel = waveformZoom,
      isNeutral = isWaveformNeutral,
      onZoomChange = onZoomChange,
      compact = compact,
      modifier = Modifier.fillMaxWidth().testTag("waveform_B"),
    )
  }
}

@Composable
fun SeratoPlatter(
  deck: DeckState,
  accentColor: Color,
  onTap: () -> Unit,
  onRotate: (Float) -> Unit,
  modifier: Modifier = Modifier,
  physicsConfig: JogWheelPhysicsConfig = JogWheelPhysicsConfig(),
  platterSize: Dp = 116.dp,
) {
  val scope = rememberCoroutineScope()
  val latestOnRotate by rememberUpdatedState(onRotate)
  val latestConfig by rememberUpdatedState(physicsConfig)
  var lastAngle by remember { mutableStateOf<Float?>(null) }
  var lastTouchTimeMs by remember { mutableStateOf(0L) }
  var angularVelocity by remember { mutableFloatStateOf(0f) }
  var coastJob by remember { mutableStateOf<Job?>(null) }
  var coastSpeedVisual by remember { mutableFloatStateOf(0f) }

  val scaleFactor = (platterSize / 116.dp).coerceIn(0.85f, 1.65f)

  Box(
    modifier =
      modifier
        .size(platterSize)
        .clip(CircleShape)
        .clickable(onClick = onTap)
        .pointerInput(Unit) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            coastJob?.cancel()
            coastJob = null
            coastSpeedVisual = 0f
          }
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { offset ->
              coastJob?.cancel()
              coastJob = null
              coastSpeedVisual = 0f
              angularVelocity = 0f
              lastTouchTimeMs = 0L
              val cx = size.width / 2f
              val cy = size.height / 2f
              lastAngle = atan2(offset.y - cy, offset.x - cx) * (180f / PI.toFloat())
            },
            onDragEnd = {
              lastAngle = null
              val launchVel = angularVelocity
              if (abs(launchVel) >= latestConfig.minCoastVelocityDegPerSec) {
                coastJob =
                  scope.launch {
                    var vel = launchVel
                    var prevNanos = 0L
                    while (abs(vel) >= latestConfig.stopVelocityDegPerSec) {
                      val dtSec =
                        withFrameNanos { frameNanos ->
                          val dt =
                            if (prevNanos == 0L) 0.016f
                            else ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                          prevNanos = frameNanos
                          dt
                        }
                      val (deltaStep, nextVel) =
                        JogWheelPhysics.stepInertia(vel, dtSec, latestConfig)
                      vel = nextVel
                      coastSpeedVisual = abs(vel)
                      if (deltaStep != 0f) {
                        latestOnRotate(deltaStep)
                      }
                    }
                    coastSpeedVisual = 0f
                    angularVelocity = 0f
                  }
              }
              angularVelocity = 0f
            },
            onDragCancel = {
              lastAngle = null
              angularVelocity = 0f
              coastSpeedVisual = 0f
            },
          ) { change, _ ->
            change.consume()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val currentAngle =
              atan2(change.position.y - cy, change.position.x - cx) * (180f / PI.toFloat())
            val prev = lastAngle
            val nowMs = change.uptimeMillis
            if (prev != null) {
              val delta = JogWheelPhysics.normalizeAngleDelta(currentAngle - prev)
              val dtSec =
                if (lastTouchTimeMs > 0L && nowMs > lastTouchTimeMs) {
                  (nowMs - lastTouchTimeMs) / 1000f
                } else {
                  0.016f
                }
              angularVelocity =
                JogWheelPhysics.updateAngularVelocity(
                  previousVelocityDegPerSec = angularVelocity,
                  deltaDeg = delta,
                  dtSeconds = dtSec,
                  config = latestConfig,
                )
              latestOnRotate(delta)
            }
            lastAngle = currentAngle
            lastTouchTimeMs = nowMs
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    val inertiaGlow = (coastSpeedVisual / 720f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.size(platterSize)) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val outerR = size.minDimension / 2f
      val ringStroke = (5.5f * scaleFactor + inertiaGlow * 1.5f).dp.toPx()

      // Dark charcoal outer bezel + subtle progress arc
      drawCircle(color = Color(0xFF1F232B), radius = outerR)
      drawCircle(
        color = Color(0xFF2B303A),
        radius = outerR - ringStroke * 0.5f,
        style = Stroke(width = ringStroke),
      )
      drawArc(
        color =
          if (deck.isPlaying || inertiaGlow > 0.05f) Color(0xFF6B7280)
          else Color(0xFF4B5563),
        startAngle = -90f,
        sweepAngle = (deck.playheadProgress.coerceIn(0f, 1f) * 360f).coerceAtLeast(18f),
        useCenter = false,
        topLeft = Offset(ringStroke * 0.5f, ringStroke * 0.5f),
        size = Size(size.width - ringStroke, size.height - ringStroke),
        style = Stroke(width = ringStroke),
      )

      // Pure crisp Serato white inner platter disc
      val innerR = outerR * 0.84f
      drawCircle(color = Color(0xFFF5F7FA), radius = innerR)

      // Subtle dark border separating white disc & outer bezel
      drawCircle(
        color = Color(0xFF0A0D12),
        radius = innerR,
        style = Stroke(width = 1.5.dp.toPx()),
      )

      // Rotating crisp black pointer needle from center grey spindle to rim (matching Serato DJ Mobile)
      val rad = (deck.jogRotationDeg - 165f) * (PI / 180.0)
      val spindleRadius = (5.5f * scaleFactor).dp.toPx()
      val needleStart =
        Offset(
          cx + spindleRadius * 0.5f * cos(rad).toFloat(),
          cy + spindleRadius * 0.5f * sin(rad).toFloat(),
        )
      val needleEnd =
        Offset(
          cx + (innerR * 0.99f) * cos(rad).toFloat(),
          cy + (innerR * 0.99f) * sin(rad).toFloat(),
        )
      drawLine(
        color = Color(0xFF050608),
        start = needleStart,
        end = needleEnd,
        strokeWidth = (3.5f * scaleFactor).dp.toPx(),
      )

      // Center grey spindle dot
      drawCircle(color = Color(0xFF9CA3AF), radius = spindleRadius)
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = "BPM",
        style = MaterialTheme.typography.labelSmall,
        fontSize = (7.5f * scaleFactor).sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF6B7280),
      )
      Text(
        text = String.format(Locale.US, "%.1f", deck.effectiveBpm),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        fontSize = (20f * scaleFactor).sp,
        color = Color(0xFF07090D),
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy((12f * scaleFactor).dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = String.format(Locale.US, "%+.1f%%", deck.tempoOffset * 100f),
          style = MaterialTheme.typography.labelSmall,
          fontSize = (8.5f * scaleFactor).sp,
          color = Color(0xFF374151),
        )
        Text(
          text = deck.pitchRange.label,
          style = MaterialTheme.typography.labelSmall,
          fontSize = (8.5f * scaleFactor).sp,
          color = Color(0xFF374151),
        )
      }
      Spacer(modifier = Modifier.height((3f * scaleFactor).dp))
      Text(
        text = deck.formattedElapsed(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        fontSize = (10.5f * scaleFactor).sp,
        color = Color(0xFF6B7280),
      )
      Text(
        text = deck.formattedRemaining().removePrefix("-"),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = (10.5f * scaleFactor).sp,
        color = Color(0xFF07090D),
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeratoDeckCard(
  deck: DeckState,
  mixerTheme: MixerTheme,
  eqPresets: List<EqPreset>,
  activeProTab: ProModeTab,
  onTogglePlay: () -> Unit,
  onCue: () -> Unit,
  onSync: () -> Unit,
  onTapTempo: () -> Unit,
  onOpenLibrary: () -> Unit,
  onPickLocalFile: () -> Unit,
  onStemLevelChange: (StemType, Float) -> Unit,
  onToggleStemMute: (StemType) -> Unit,
  onToggleStemSolo: (StemType) -> Unit,
  onApplyStemPreset: (StemIsolationPreset) -> Unit,
  onToggleQuickIsolate: () -> Unit = {},
  onEqBandChange: (EqBand, Float) -> Unit,
  onApplyEqPreset: (EqPreset) -> Unit,
  onEffectsChange: (Float?, Float?, Float?) -> Unit,
  onTrimGainChange: (Float) -> Unit,
  onTempoOffsetChange: (Float) -> Unit,
  onCyclePitchRange: () -> Unit,
  onRotateJogWheel: (Float) -> Unit,
  onHotCueTap: (Int) -> Unit,
  onHotCueLongPress: (Int) -> Unit,
  onToggleQuantize: () -> Unit,
  onToggleScratchMode: () -> Unit,
  onToggleKeyLock: () -> Unit,
  onToggleLoop: () -> Unit,
  onCycleLoopBeats: () -> Unit,
  onTriggerAutoLoop: (Float) -> Unit = {},
  onHalveLoop: () -> Unit = {},
  onDoubleLoop: () -> Unit = {},
  otherDeckKey: String = deck.track.musicalKey,
  onKeySync: () -> Unit = {},
  includePrimaryDeckTags: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val accentColor =
    if (deck.deckId == DeckId.A) mixerTheme.deckAAccent else mixerTheme.deckBAccent
  val tagSuffix = deck.deckId.name
  val primaryTagSuffix = if (includePrimaryDeckTags) tagSuffix else "${tagSuffix}_drawer"
  val effectiveKey = deck.effectiveMusicalKey
  val effectiveCamelot = CamelotHelper.toCamelot(effectiveKey)
  val isHarmonicWithOther = CamelotHelper.isCompatible(effectiveKey, otherDeckKey)
  val shiftLabel =
    when {
      deck.keyShiftSemitones > 0 -> "+${deck.keyShiftSemitones}st"
      deck.keyShiftSemitones < 0 -> "${deck.keyShiftSemitones}st"
      else -> null
    }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = mixerTheme.consoleSurface,
    modifier = modifier.border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Deck Top Strip: [1/2] Track Title & Artist | BPM & Remaining Time
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier =
            Modifier.size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(accentColor),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = deck.deckId.numberLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF06080D),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${deck.track.artist} - ${deck.track.title}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (shiftLabel != null) {
                "$effectiveKey ($effectiveCamelot $shiftLabel) • ${deck.track.genre}"
              } else {
                "$effectiveKey ($effectiveCamelot) • ${deck.track.genre}"
              },
            style = MaterialTheme.typography.labelSmall,
            color = if (deck.isKeySynced || isHarmonicWithOther) Color(0xFF00E676) else Color(0xFF9BA8BE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            BpmBeatIndicator(bpm = deck.effectiveBpm, isPlaying = deck.isPlaying, accentColor = accentColor)
            Text(
              text = String.format("%.2f", deck.effectiveBpm),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = accentColor,
            )
          }
          Text(
            text = deck.formattedRemaining(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD5DFF0),
          )
        }

        IconButton(
          onClick = onPickLocalFile,
          modifier = Modifier.size(34.dp).testTag("pick_file_$tagSuffix"),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = "Pick Audio", tint = accentColor, modifier = Modifier.size(18.dp))
        }
        IconButton(
          onClick = onOpenLibrary,
          modifier = Modifier.size(34.dp).testTag("open_library_$primaryTagSuffix"),
        ) {
          Icon(Icons.Filled.LibraryMusic, contentDescription = "Library", tint = Color.White, modifier = Modifier.size(18.dp))
        }
      }

      // Sync / Transport Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DeckActionChip(
          label = if (deck.isPlaying) "PAUSE" else "PLAY",
          active = deck.isPlaying,
          accent = accentColor,
          onClick = onTogglePlay,
          icon = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          modifier = Modifier.weight(1.25f).testTag("play_button_$primaryTagSuffix"),
        )
        DeckActionChip(
          label = "CUE",
          active = false,
          accent = accentColor,
          onClick = onCue,
          modifier = Modifier.weight(0.95f).testTag("cue_button_$primaryTagSuffix"),
        )
        DeckActionChip(
          label = if (deck.isAutoSynced) "SYNCED" else "AUTO-SYNC",
          active = deck.isAutoSynced,
          accent = if (deck.isAutoSynced) Color(0xFF00E676) else accentColor,
          onClick = onSync,
          icon = Icons.Filled.Sync,
          modifier = Modifier.weight(1.1f).testTag("sync_button_$primaryTagSuffix"),
        )
        DeckActionChip(
          label = "TAP",
          active = false,
          accent = accentColor,
          onClick = onTapTempo,
          modifier = Modifier.weight(0.8f).testTag("tap_tempo_$primaryTagSuffix"),
        )
      }

      // Harmonic Mixing Row: Key Lock + Key Sync + Live Effective Key Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DeckActionChip(
          label = "KEY LOCK",
          active = deck.keyLockEnabled,
          accent = accentColor,
          onClick = onToggleKeyLock,
          modifier = Modifier.weight(0.95f).testTag("key_lock_button_$tagSuffix"),
        )
        DeckActionChip(
          label =
            when {
              deck.isKeySynced && shiftLabel != null -> "KEY SYNC $shiftLabel"
              deck.isKeySynced -> "KEY SYNCED"
              else -> "KEY SYNC"
            },
          active = deck.isKeySynced,
          accent = if (deck.isKeySynced) Color(0xFF00E676) else accentColor,
          onClick = onKeySync,
          icon = Icons.Filled.MusicNote,
          modifier = Modifier.weight(1.25f).testTag("key_sync_button_$tagSuffix"),
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color =
            if (isHarmonicWithOther || deck.isKeySynced) Color(0xFF00E676).copy(alpha = 0.16f)
            else Color(0xFF151C28),
          modifier =
            Modifier.weight(0.85f)
              .semantics(mergeDescendants = true) {}
              .border(
                1.dp,
                if (isHarmonicWithOther || deck.isKeySynced) Color(0xFF00E676) else Color(0xFF2D384C),
                RoundedCornerShape(8.dp),
              )
              .testTag("effective_key_badge_$tagSuffix"),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
          ) {
            Text(
              text = "$effectiveKey • $effectiveCamelot",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isHarmonicWithOther || deck.isKeySynced) Color(0xFF00E676) else Color(0xFFD5DFF0),
              maxLines = 1,
            )
          }
        }
      }

      // Serato Platter + 8 Color-Coded Performance Pads + Loop Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SeratoPlatter(
            deck = deck,
            accentColor = accentColor,
            onTap = onTogglePlay,
            onRotate = onRotateJogWheel,
            modifier = Modifier.testTag("jog_wheel_$primaryTagSuffix"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DeckTogglePill(
              label = "Q",
              active = deck.quantizeEnabled,
              accentColor = accentColor,
              onClick = onToggleQuantize,
              testTag = "quantize_button_$tagSuffix",
              modifier = Modifier.width(42.dp),
            )
            DeckTogglePill(
              label = "SCR",
              active = deck.scratchMode,
              accentColor = accentColor,
              onClick = onToggleScratchMode,
              testTag = "scratch_button_$tagSuffix",
              modifier = Modifier.width(48.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // 8 Color-Coded Pads: Row 1 (Hot Cues 1-4), Row 2 (Quick Stem Pads 5-8: VOX / DRM / BAS / MEL)
          val padColors = listOf(0xFFFF3B30, 0xFFFFCC00, 0xFF00E676, 0xFF00B0FF)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            for (slot in 1..4) {
              val hasCue = deck.hotCues.containsKey(slot)
              val col = Color(padColors[slot - 1])
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasCue) col else col.copy(alpha = 0.25f))
                    .border(1.dp, col, RoundedCornerShape(6.dp))
                    .combinedClickable(
                      onClick = { onHotCueTap(slot) },
                      onLongClick = { onHotCueLongPress(slot) },
                    )
                    .testTag("hot_cue_${slot}_$tagSuffix"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "$slot",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (hasCue) Color(0xFF06080D) else Color.White,
                )
              }
            }
          }

          // Pads 5-8: Serato Stems Instant Pads (5:VOX, 6:DRM, 7:BAS, 8:MEL)
          val stemPads =
            listOf(
              StemType.VOCALS to Color(0xFFAA00FF),
              StemType.DRUMS to Color(0xFFFF6D00),
              StemType.BASS to Color(0xFF00B8D4),
              StemType.MELODY to Color(0xFF2979FF),
            )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            stemPads.forEachIndexed { idx, (stem, col) ->
              val stemState = deck.stems[stem]
              val muted = stemState?.isMuted == true
              val solo = stemState?.isSolo == true
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (muted) Color(0xFF1A1F2B) else col.copy(alpha = if (solo) 1f else 0.78f))
                    .border(1.dp, if (muted) Color(0xFFFF3B5C) else col, RoundedCornerShape(6.dp))
                    .combinedClickable(
                      onClick = { onToggleStemMute(stem) },
                      onLongClick = { onToggleStemSolo(stem) },
                    )
                    .testTag("stem_pad_${stem.shortLabel}_$tagSuffix"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = if (muted) "✕${stem.shortLabel}" else "${idx + 5} ${stem.shortLabel}",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (muted) Color(0xFFFF3B5C) else Color.White,
                )
              }
            }
          }

          // Auto-Loop Row + Pitch Range
          LoopControlsRow(
            deck = deck,
            accentColor = accentColor,
            onToggleLoop = onToggleLoop,
            onCycleLoopBeats = onCycleLoopBeats,
            onTriggerAutoLoop = onTriggerAutoLoop,
            onHalveLoop = onHalveLoop,
            onDoubleLoop = onDoubleLoop,
          )

          // Pitch slider & Trim
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            RotaryTrimKnob(
              trimGain = deck.trimGain,
              accentColor = accentColor,
              onTrimChange = onTrimGainChange,
              label = "GAIN",
              inputLevel = deck.inputSignalLevel,
              compact = true,
              modifier = Modifier.testTag("trim_knob_$tagSuffix"),
            )
            Column(modifier = Modifier.weight(1f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "PITCH ${String.format("%+.1f%%", deck.tempoOffset * 100f)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF9BA8BE),
                )
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = Color(0xFF182030),
                  modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                      .clickable(onClick = onCyclePitchRange)
                      .testTag("pitch_range_$tagSuffix"),
                ) {
                  Text(
                    text = deck.pitchRange.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
              Slider(
                value = deck.tempoOffset,
                onValueChange = onTempoOffsetChange,
                valueRange = -deck.pitchRange.maxOffset..deck.pitchRange.maxOffset,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                modifier = Modifier.height(24.dp).testTag("pitch_slider_$tagSuffix"),
              )
            }
          }
        }
      }

      // Serato Stems One-Tap Voice & Instrumental Isolation Bar (ALWAYS visible!)
      SeratoStemsQuickBar(
        deck = deck,
        accentColor = accentColor,
        onApplyPreset = onApplyStemPreset,
        onStemLevelChange = onStemLevelChange,
        onToggleMute = onToggleStemMute,
        onToggleSolo = onToggleStemSolo,
        onToggleQuickIsolate = onToggleQuickIsolate,
        showSliders = activeProTab == ProModeTab.STEMS || activeProTab == ProModeTab.DJ,
      )

      // Show 3-Band EQ & FX Rack when on DJ, FX, or Master tabs
      if (activeProTab == ProModeTab.DJ || activeProTab == ProModeTab.FX || activeProTab == ProModeTab.MASTER) {
        EqControlRow(
          deck = deck,
          accentColor = accentColor,
          presets = eqPresets,
          onEqChange = onEqBandChange,
          onApplyPreset = onApplyEqPreset,
        )
        EffectsControlRow(
          deck = deck,
          accentColor = accentColor,
          onEffectsChange = onEffectsChange,
        )
      }
    }
  }
}

@Composable
fun SeratoStemsQuickBar(
  deck: DeckState,
  accentColor: Color,
  onApplyPreset: (StemIsolationPreset) -> Unit,
  onStemLevelChange: (StemType, Float) -> Unit,
  onToggleMute: (StemType) -> Unit,
  onToggleSolo: (StemType) -> Unit,
  showSliders: Boolean,
  onToggleQuickIsolate: () -> Unit = {},
) {
  val tagSuffix = deck.deckId.name
  val voiceRemoved = deck.isVoiceRemoved
  val instRemoved = deck.isInstrumentalRemoved

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF0A0E16))
        .border(1.dp, Color(0xFF232D3F), RoundedCornerShape(10.dp))
        .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "SERATO STEMS",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
      Text(
        text =
          when {
            voiceRemoved -> "INSTRUMENTAL (VOICE REMOVED)"
            instRemoved -> "ACAPELLA (INSTRUMENTAL REMOVED)"
            else -> "TAP TO ISOLATE / REMOVE"
          },
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        color = if (voiceRemoved || instRemoved) Color(0xFF00E676) else Color(0xFF9BA8BE),
      )
    }

    // 4-Button Stem Deconstruction Toggle Component (Vocals, Drums, Bass, Melody)
    StemDeconstructionBar(
      deck = deck,
      accentColor = accentColor,
      onToggleMute = onToggleMute,
      onToggleSolo = onToggleSolo,
      onResetAll = { onApplyPreset(StemIsolationPreset.FULL) },
    )

    // One-Tap Remove Voice (Instrumental) & Remove Instrumental (Acapella) + Reset
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (voiceRemoved) Color(0xFF00E676).copy(alpha = 0.24f) else Color(0xFF151C28),
        modifier =
          Modifier.weight(1.2f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(
              1.dp,
              if (voiceRemoved) Color(0xFF00E676) else Color(0xFF2D384C),
              RoundedCornerShape(8.dp),
            )
            .clickable { onApplyPreset(StemIsolationPreset.INSTRUMENTAL) }
            .testTag("stem_remove_voice_$tagSuffix"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            Icons.Filled.MicOff,
            contentDescription = "Remove voice",
            tint = if (voiceRemoved) Color(0xFF00E676) else Color.White,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "NO VOICE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (voiceRemoved) Color(0xFF00E676) else Color.White,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (instRemoved) Color(0xFFAA00FF).copy(alpha = 0.28f) else Color(0xFF151C28),
        modifier =
          Modifier.weight(1.2f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(
              1.dp,
              if (instRemoved) Color(0xFFCE6DFF) else Color(0xFF2D384C),
              RoundedCornerShape(8.dp),
            )
            .clickable { onApplyPreset(StemIsolationPreset.ACAPELLA) }
            .testTag("stem_remove_inst_$tagSuffix"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            Icons.Filled.MusicOff,
            contentDescription = "Remove instrumental",
            tint = if (instRemoved) Color(0xFFCE6DFF) else Color.White,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "VOX ONLY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (instRemoved) Color(0xFFCE6DFF) else Color.White,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151C28),
        modifier =
          Modifier.weight(0.75f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2D384C), RoundedCornerShape(8.dp))
            .clickable { onApplyPreset(StemIsolationPreset.FULL) }
            .testTag("stem_reset_$tagSuffix"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
          Text(
            text = "ALL",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
          )
        }
      }
    }

    if (showSliders) {
      StemsControlPanel(
        deck = deck,
        accentColor = accentColor,
        onStemLevelChange = onStemLevelChange,
        onToggleMute = onToggleMute,
        onToggleSolo = onToggleSolo,
        onToggleQuickIsolate = onToggleQuickIsolate,
      )
    }
  }
}

@Composable
fun SeratoSamplerStrip(
  lastTriggered: SamplerSound?,
  onTriggerSampler: (SamplerSound) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SamplerSound.entries.forEach { sound ->
      val col = Color(sound.colorHex)
      val active = lastTriggered == sound
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) col.copy(alpha = 0.28f) else Color(0xFF0E131D),
        modifier =
          Modifier.weight(1f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable { onTriggerSampler(sound) }
            .testTag("sampler_pad_${sound.name}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier.size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(col),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${sound.slot}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
          }
          Icon(
            Icons.Filled.VolumeUp,
            contentDescription = null,
            tint = col,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = sound.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun DeckActionChip(
  label: String,
  active: Boolean,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (active) accent else Color(0xFF151C28),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(6.dp))
        .border(1.dp, if (active) accent else Color(0xFF2D384C), RoundedCornerShape(6.dp))
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (active) Color(0xFF06080D) else Color.White,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (active) Color(0xFF06080D) else Color.White,
        maxLines = 1,
      )
    }
  }
}
