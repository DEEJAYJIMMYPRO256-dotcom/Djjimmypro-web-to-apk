package com.example.dj.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.model.DeckId
import com.example.dj.ui.components.CrossfaderMixerCard
import com.example.dj.ui.components.DeckPanel
import com.example.dj.ui.components.KeyboardShortcutsDialog
import com.example.dj.ui.components.SessionHistorySheet
import com.example.dj.ui.components.TrackLibrarySheet
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjScreen(viewModel: DjViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showMidiSettings by remember { mutableStateOf(false) }
  var libraryTargetDeck by remember { mutableStateOf<DeckId?>(null) }
  var showHistorySheet by remember { mutableStateOf(false) }
  var filePickerTargetDeck by remember { mutableStateOf(DeckId.A) }

  val audioFilePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        viewModel.loadLocalAudioUri(filePickerTargetDeck, uri)
      }
    }

  if (showMidiSettings) {
    MidiSettingsScreen(
      midiManager = viewModel.midiManager,
      powerSavingMode = state.powerSavingMode,
      onTogglePowerSaving = { viewModel.togglePowerSavingMode() },
      onBack = { showMidiSettings = false },
    )
    return
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(libraryTargetDeck, showHistorySheet, state.showShortcutsDialog, showMidiSettings) {
    if (libraryTargetDeck == null && !showHistorySheet && !state.showShortcutsDialog) {
      runCatching { focusRequester.requestFocus() }
    }
  }

  val beatMatchDelta = abs(state.deckA.effectiveBpm - state.deckB.effectiveBpm)
  val isModalOpen = libraryTargetDeck != null || showHistorySheet

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = state.mixerTheme.background,
    modifier =
      Modifier.fillMaxSize()
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
          if (isModalOpen) return@onPreviewKeyEvent false
          val command = DjKeyboardShortcuts.mapKeyEvent(keyEvent)
          if (command != null) {
            viewModel.handleKeyboardShortcut(command)
            true
          } else {
            false
          }
        },
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = state.mixerTheme.consoleSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("STEMDECK DJ", style = MaterialTheme.typography.titleMedium)
            state.connectedMidiDevice?.let { midiName ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = state.mixerTheme.deckAAccent.copy(alpha = 0.16f),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    Icons.Filled.Usb,
                    contentDescription = "MIDI Connected",
                    tint = state.mixerTheme.deckAAccent,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = midiName,
                    style = MaterialTheme.typography.labelSmall,
                    color = state.mixerTheme.deckAAccent,
                  )
                }
              }
            }
          }
        },
        actions = {
          // Record Master Output WAV
          Surface(
            shape = RoundedCornerShape(8.dp),
            color =
              if (state.isRecording) Color(0xFFFF3B5C).copy(alpha = 0.24f)
              else MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.toggleRecording() }
                .testTag("rec_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                Icons.Filled.FiberManualRecord,
                contentDescription = "Record master output",
                tint = if (state.isRecording) Color(0xFFFF3B5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = if (state.isRecording) "REC" else "REC",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isRecording) Color(0xFFFF3B5C) else MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          Spacer(Modifier.width(6.dp))

          // Theme Skin Switcher
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.cycleMixerTheme() }
                .testTag("theme_switcher_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                Icons.Filled.Palette,
                contentDescription = "Switch mixer theme",
                tint = state.mixerTheme.deckAAccent,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = state.mixerTheme.label,
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          IconButton(
            onClick = { viewModel.toggleShortcutsDialog(true) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("shortcuts_help_button"),
          ) {
            Icon(
              Icons.Filled.Keyboard,
              contentDescription = "Keyboard shortcuts",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }

          IconButton(
            onClick = { showHistorySheet = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("history_button"),
          ) {
            Icon(
              Icons.Filled.History,
              contentDescription = "Session history",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }

          IconButton(
            onClick = { showMidiSettings = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("midi_settings_button"),
          ) {
            Icon(
              Icons.Filled.Settings,
              contentDescription = "MIDI and mixer settings",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
      )
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize()
          .background(state.mixerTheme.background)
          .padding(innerPadding)
          .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      val isWideLayout = maxWidth >= 720.dp

      if (isWideLayout) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Box(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            DeckPanel(
              deck = state.deckA,
              mixerTheme = state.mixerTheme,
              beatMatchDeltaBpm = beatMatchDelta,
              eqPresets = state.eqPresets,
              onTogglePlay = { viewModel.togglePlay(DeckId.A) },
              onCue = { viewModel.cueDeck(DeckId.A) },
              onSync = { viewModel.syncDeck(DeckId.A) },
              onTapTempo = { viewModel.tapTempo(DeckId.A) },
              onOpenLibrary = { libraryTargetDeck = DeckId.A },
              onPickLocalFile = {
                filePickerTargetDeck = DeckId.A
                audioFilePicker.launch(arrayOf("audio/*"))
              },
              onStemLevelChange = { stem, lvl -> viewModel.setStemLevel(DeckId.A, stem, lvl) },
              onToggleStemMute = { stem -> viewModel.toggleStemMute(DeckId.A, stem) },
              onToggleStemSolo = { stem -> viewModel.toggleStemSolo(DeckId.A, stem) },
              onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.A, band, v) },
              onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.A, preset) },
              onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.A, f, e, fl) },
              onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.A, trim) },
              onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.A, off) },
              onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.A) },
              onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.A, deg) },
              onScrubWaveform = { delta -> viewModel.scrubDeck(DeckId.A, delta) },
              onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.A, slot) },
              onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.A, slot) },
              onToggleQuantize = { viewModel.toggleQuantize(DeckId.A) },
              onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.A) },
              onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.A) },
              onToggleLoop = { viewModel.toggleLoop(DeckId.A) },
              onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.A) },
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Box(
            modifier = Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
          ) {
            CrossfaderMixerCard(
              crossfader = state.crossfader,
              crossfaderCurve = state.crossfaderCurve,
              peakLeft = state.masterPeakLeft,
              peakRight = state.masterPeakRight,
              autoMixEnabled = state.autoMixEnabled,
              autoMixDurationSeconds = state.autoMixDurationSeconds,
              isAutoMixing = state.isAutoMixing,
              mixerTheme = state.mixerTheme,
              shortcutFeedback = state.shortcutFeedback,
              onCrossfaderChange = { viewModel.setCrossfader(it) },
              onCycleCurve = { viewModel.cycleCrossfaderCurve() },
              onToggleAutoMix = { viewModel.toggleAutoMix() },
              onCycleAutoMixDuration = { viewModel.cycleAutoMixDuration() },
              onOpenShortcuts = { viewModel.toggleShortcutsDialog(true) },
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Box(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            DeckPanel(
              deck = state.deckB,
              mixerTheme = state.mixerTheme,
              beatMatchDeltaBpm = beatMatchDelta,
              eqPresets = state.eqPresets,
              onTogglePlay = { viewModel.togglePlay(DeckId.B) },
              onCue = { viewModel.cueDeck(DeckId.B) },
              onSync = { viewModel.syncDeck(DeckId.B) },
              onTapTempo = { viewModel.tapTempo(DeckId.B) },
              onOpenLibrary = { libraryTargetDeck = DeckId.B },
              onPickLocalFile = {
                filePickerTargetDeck = DeckId.B
                audioFilePicker.launch(arrayOf("audio/*"))
              },
              onStemLevelChange = { stem, lvl -> viewModel.setStemLevel(DeckId.B, stem, lvl) },
              onToggleStemMute = { stem -> viewModel.toggleStemMute(DeckId.B, stem) },
              onToggleStemSolo = { stem -> viewModel.toggleStemSolo(DeckId.B, stem) },
              onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.B, band, v) },
              onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.B, preset) },
              onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.B, f, e, fl) },
              onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.B, trim) },
              onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.B, off) },
              onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.B) },
              onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.B, deg) },
              onScrubWaveform = { delta -> viewModel.scrubDeck(DeckId.B, delta) },
              onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.B, slot) },
              onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.B, slot) },
              onToggleQuantize = { viewModel.toggleQuantize(DeckId.B) },
              onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.B) },
              onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.B) },
              onToggleLoop = { viewModel.toggleLoop(DeckId.B) },
              onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.B) },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DeckPanel(
            deck = state.deckA,
            mixerTheme = state.mixerTheme,
            beatMatchDeltaBpm = beatMatchDelta,
            eqPresets = state.eqPresets,
            onTogglePlay = { viewModel.togglePlay(DeckId.A) },
            onCue = { viewModel.cueDeck(DeckId.A) },
            onSync = { viewModel.syncDeck(DeckId.A) },
            onTapTempo = { viewModel.tapTempo(DeckId.A) },
            onOpenLibrary = { libraryTargetDeck = DeckId.A },
            onPickLocalFile = {
              filePickerTargetDeck = DeckId.A
              audioFilePicker.launch(arrayOf("audio/*"))
            },
            onStemLevelChange = { stem, lvl -> viewModel.setStemLevel(DeckId.A, stem, lvl) },
            onToggleStemMute = { stem -> viewModel.toggleStemMute(DeckId.A, stem) },
            onToggleStemSolo = { stem -> viewModel.toggleStemSolo(DeckId.A, stem) },
            onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.A, band, v) },
            onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.A, preset) },
            onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.A, f, e, fl) },
            onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.A, trim) },
            onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.A, off) },
            onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.A) },
            onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.A, deg) },
            onScrubWaveform = { delta -> viewModel.scrubDeck(DeckId.A, delta) },
            onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.A, slot) },
            onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.A, slot) },
            onToggleQuantize = { viewModel.toggleQuantize(DeckId.A) },
            onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.A) },
            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.A) },
            onToggleLoop = { viewModel.toggleLoop(DeckId.A) },
            onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.A) },
            modifier = Modifier.fillMaxWidth(),
          )

          CrossfaderMixerCard(
            crossfader = state.crossfader,
            crossfaderCurve = state.crossfaderCurve,
            peakLeft = state.masterPeakLeft,
            peakRight = state.masterPeakRight,
            autoMixEnabled = state.autoMixEnabled,
            autoMixDurationSeconds = state.autoMixDurationSeconds,
            isAutoMixing = state.isAutoMixing,
            mixerTheme = state.mixerTheme,
            shortcutFeedback = state.shortcutFeedback,
            onCrossfaderChange = { viewModel.setCrossfader(it) },
            onCycleCurve = { viewModel.cycleCrossfaderCurve() },
            onToggleAutoMix = { viewModel.toggleAutoMix() },
            onCycleAutoMixDuration = { viewModel.cycleAutoMixDuration() },
            onOpenShortcuts = { viewModel.toggleShortcutsDialog(true) },
            modifier = Modifier.fillMaxWidth(),
          )

          DeckPanel(
            deck = state.deckB,
            mixerTheme = state.mixerTheme,
            beatMatchDeltaBpm = beatMatchDelta,
            eqPresets = state.eqPresets,
            onTogglePlay = { viewModel.togglePlay(DeckId.B) },
            onCue = { viewModel.cueDeck(DeckId.B) },
            onSync = { viewModel.syncDeck(DeckId.B) },
            onTapTempo = { viewModel.tapTempo(DeckId.B) },
            onOpenLibrary = { libraryTargetDeck = DeckId.B },
            onPickLocalFile = {
              filePickerTargetDeck = DeckId.B
              audioFilePicker.launch(arrayOf("audio/*"))
            },
            onStemLevelChange = { stem, lvl -> viewModel.setStemLevel(DeckId.B, stem, lvl) },
            onToggleStemMute = { stem -> viewModel.toggleStemMute(DeckId.B, stem) },
            onToggleStemSolo = { stem -> viewModel.toggleStemSolo(DeckId.B, stem) },
            onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.B, band, v) },
            onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.B, preset) },
            onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.B, f, e, fl) },
            onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.B, trim) },
            onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.B, off) },
            onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.B) },
            onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.B, deg) },
            onScrubWaveform = { delta -> viewModel.scrubDeck(DeckId.B, delta) },
            onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.B, slot) },
            onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.B, slot) },
            onToggleQuantize = { viewModel.toggleQuantize(DeckId.B) },
            onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.B) },
            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.B) },
            onToggleLoop = { viewModel.toggleLoop(DeckId.B) },
            onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.B) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }

  libraryTargetDeck?.let { target ->
    val oppositeTrack = if (target == DeckId.A) state.deckB.track else state.deckA.track
    val accent = if (target == DeckId.A) state.mixerTheme.deckAAccent else state.mixerTheme.deckBAccent
    TrackLibrarySheet(
      targetDeckId = target,
      referenceTrack = oppositeTrack,
      tracks = state.libraryTracks,
      sessionHistory = state.sessionHistory,
      eqPresets = state.eqPresets,
      accentColor = accent,
      onSelectTrack = { track -> viewModel.loadTrack(target, track) },
      onUpdateMetadata = { id, title, artist, genre ->
        viewModel.updateTrackMetadata(id, title, artist, genre)
      },
      onDismiss = { libraryTargetDeck = null },
    )
  }

  if (showHistorySheet) {
    SessionHistorySheet(
      history = state.sessionHistory,
      onDismiss = { showHistorySheet = false },
    )
  }

  if (state.showShortcutsDialog) {
    KeyboardShortcutsDialog(onDismiss = { viewModel.toggleShortcutsDialog(false) })
  }
}
