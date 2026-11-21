package com.example.dj.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.model.DeckId
import com.example.dj.model.ProModeTab
import com.example.dj.ui.components.CrossfaderMixerCard
import com.example.dj.ui.components.KeyboardShortcutsDialog
import com.example.dj.ui.components.SamplePadsEffectsPanel
import com.example.dj.ui.components.SeratoDeckCard
import com.example.dj.ui.components.SeratoLibraryDock
import com.example.dj.ui.components.SeratoTopProBar
import com.example.dj.ui.components.SessionHistorySheet
import com.example.dj.ui.components.StackedDualWaveforms
import com.example.dj.ui.components.TrackLibrarySheet
import kotlin.math.abs

@Composable
fun DjScreen(viewModel: DjViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showMidiSettings by rememberSaveable { mutableStateOf(false) }
  var libraryTargetDeck by rememberSaveable { mutableStateOf<DeckId?>(null) }
  var showHistorySheet by rememberSaveable { mutableStateOf(false) }
  var filePickerTargetDeck by rememberSaveable { mutableStateOf(DeckId.A) }
  var isDockSearchFocused by rememberSaveable { mutableStateOf(false) }

  val portraitScrollState = rememberScrollState()
  val landscapeRootScrollState = rememberScrollState()
  val landscapeDeckAScrollState = rememberScrollState()
  val landscapeCenterScrollState = rememberScrollState()
  val landscapeDeckBScrollState = rememberScrollState()

  val audioFilePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        viewModel.loadLocalAudioUri(filePickerTargetDeck, uri)
      }
    }

  val multiAudioImportPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.importLocalAudioUris(uris)
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
    if (libraryTargetDeck == null && !showHistorySheet && !state.showShortcutsDialog && !isDockSearchFocused) {
      runCatching { focusRequester.requestFocus() }
    }
  }

  val beatMatchDelta = abs(state.deckA.effectiveBpm - state.deckB.effectiveBpm)
  val isModalOpen = libraryTargetDeck != null || showHistorySheet || isDockSearchFocused
  val latestModalOpen by rememberUpdatedState(isModalOpen)
  val latestViewModel by rememberUpdatedState(viewModel)
  val hostView = LocalView.current

  // Global physical keyboard listener so deck & crossfader shortcuts work even if no Compose node holds focus
  DisposableEffect(hostView) {
    val listener =
      ViewCompat.OnUnhandledKeyEventListenerCompat { _, nativeEvent ->
        if (latestModalOpen) return@OnUnhandledKeyEventListenerCompat false
        val command = DjKeyboardShortcuts.mapNativeKeyEvent(nativeEvent)
        if (command != null) {
          latestViewModel.handleKeyboardShortcut(command)
          true
        } else {
          false
        }
      }
    ViewCompat.addOnUnhandledKeyEventListener(hostView, listener)
    onDispose { ViewCompat.removeOnUnhandledKeyEventListener(hostView, listener) }
  }

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
      SeratoTopProBar(
        activeTab = state.activeProTab,
        mixerTheme = state.mixerTheme,
        isRecording = state.isRecording,
        connectedMidiDevice = state.connectedMidiDevice,
        peakLeft = state.masterPeakLeft,
        peakRight = state.masterPeakRight,
        onSelectTab = { viewModel.setProModeTab(it) },
        onToggleRecording = { viewModel.toggleRecording() },
        onCycleTheme = { viewModel.cycleMixerTheme() },
        onOpenShortcuts = { viewModel.toggleShortcutsDialog(true) },
        onOpenSettings = { showMidiSettings = true },
        masterBpm = state.effectiveMasterBpm,
        isMasterTapActive = state.masterTappedBpm != null,
        masterTapSource = state.masterTapSource,
        lastMasterTapAtMs = state.lastMasterTapAtMs,
        isAnyDeckPlaying = state.deckA.isPlaying || state.deckB.isPlaying,
        onMasterTapTempo = { viewModel.tapMasterTempo() },
        onResetMasterTap = { viewModel.resetMasterTapTempo() },
      )
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxSize()
          .background(state.mixerTheme.background)
          .padding(innerPadding)
          .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
      val isLandscapeDualDeck = maxWidth >= 600.dp || (maxWidth > maxHeight && maxWidth >= 520.dp)
      val isCompactLandscapeHeight = maxHeight < 520.dp
      val centerPaneWidth = (maxWidth * 0.29f).coerceIn(242.dp, 312.dp)

      if (isLandscapeDualDeck) {
        val landscapeContainerModifier =
          if (isCompactLandscapeHeight) {
            Modifier.fillMaxSize().verticalScroll(landscapeRootScrollState).testTag("layout_landscape_dual_deck")
          } else {
            Modifier.fillMaxSize().testTag("layout_landscape_dual_deck")
          }

        Column(
          modifier = landscapeContainerModifier,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Upper Serato Pro Dual-Deck + Center Stacked Waveforms & Crossfader Stage
          Row(
            modifier =
              if (isCompactLandscapeHeight) {
                Modifier.fillMaxWidth().heightIn(min = 380.dp)
              } else {
                Modifier.weight(1f).fillMaxWidth()
              },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                if (isCompactLandscapeHeight) Modifier.weight(1f)
                else Modifier.weight(1f).fillMaxHeight().verticalScroll(landscapeDeckAScrollState)
            ) {
              SeratoDeckCard(
                deck = state.deckA,
                mixerTheme = state.mixerTheme,
                eqPresets = state.eqPresets,
                activeProTab = state.activeProTab,
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
                onApplyStemPreset = { preset -> viewModel.applyStemPreset(DeckId.A, preset) },
                onToggleQuickIsolate = { viewModel.toggleQuickIsolateMode(DeckId.A) },
                onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.A, band, v) },
                onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.A, preset) },
                onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.A, f, e, fl) },
                onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.A, trim) },
                onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.A, off) },
                onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.A) },
                onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.A, deg) },
                onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.A, slot) },
                onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.A, slot) },
                onToggleQuantize = { viewModel.toggleQuantize(DeckId.A) },
                onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.A) },
                onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.A) },
                onToggleLoop = { viewModel.toggleLoop(DeckId.A) },
                onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.A) },
                onTriggerAutoLoop = { beats -> viewModel.triggerAutoLoop(DeckId.A, beats) },
                onHalveLoop = { viewModel.halveLoop(DeckId.A) },
                onDoubleLoop = { viewModel.doubleLoop(DeckId.A) },
                otherDeckKey = state.deckB.effectiveMusicalKey,
                onKeySync = { viewModel.keySyncDeck(DeckId.A) },
                modifier = Modifier.fillMaxWidth(),
              )
            }

            Column(
              modifier =
                if (isCompactLandscapeHeight) Modifier.width(centerPaneWidth)
                else Modifier.width(centerPaneWidth).fillMaxHeight().verticalScroll(landscapeCenterScrollState),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StackedDualWaveforms(
                deckA = state.deckA,
                deckB = state.deckB,
                mixerTheme = state.mixerTheme,
                beatMatchDeltaBpm = beatMatchDelta,
                onScrubA = { viewModel.scrubDeck(DeckId.A, it) },
                onScrubB = { viewModel.scrubDeck(DeckId.B, it) },
                waveformZoom = state.waveformZoom,
                isWaveformNeutral = state.isWaveformNeutral,
                onZoomOut = { viewModel.zoomOutWaveform() },
                onZoomIn = { viewModel.zoomInWaveform() },
                onZoomChange = { viewModel.setWaveformZoom(it) },
                onToggleNeutral = { viewModel.toggleWaveformNeutral() },
                compact = isCompactLandscapeHeight,
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
                compact = isCompactLandscapeHeight,
                deckATrimGain = state.deckA.trimGain,
                deckBTrimGain = state.deckB.trimGain,
                deckAInputLevel = state.deckA.inputSignalLevel,
                deckBInputLevel = state.deckB.inputSignalLevel,
                onDeckATrimChange = { viewModel.setTrimGain(DeckId.A, it) },
                onDeckBTrimChange = { viewModel.setTrimGain(DeckId.B, it) },
                modifier = Modifier.fillMaxWidth(),
              )
            }

            Box(
              modifier =
                if (isCompactLandscapeHeight) Modifier.weight(1f)
                else Modifier.weight(1f).fillMaxHeight().verticalScroll(landscapeDeckBScrollState)
            ) {
              SeratoDeckCard(
                deck = state.deckB,
                mixerTheme = state.mixerTheme,
                eqPresets = state.eqPresets,
                activeProTab = state.activeProTab,
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
                onApplyStemPreset = { preset -> viewModel.applyStemPreset(DeckId.B, preset) },
                onToggleQuickIsolate = { viewModel.toggleQuickIsolateMode(DeckId.B) },
                onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.B, band, v) },
                onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.B, preset) },
                onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.B, f, e, fl) },
                onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.B, trim) },
                onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.B, off) },
                onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.B) },
                onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.B, deg) },
                onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.B, slot) },
                onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.B, slot) },
                onToggleQuantize = { viewModel.toggleQuantize(DeckId.B) },
                onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.B) },
                onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.B) },
                onToggleLoop = { viewModel.toggleLoop(DeckId.B) },
                onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.B) },
                onTriggerAutoLoop = { beats -> viewModel.triggerAutoLoop(DeckId.B, beats) },
                onHalveLoop = { viewModel.halveLoop(DeckId.B) },
                onDoubleLoop = { viewModel.doubleLoop(DeckId.B) },
                otherDeckKey = state.deckA.effectiveMusicalKey,
                onKeySync = { viewModel.keySyncDeck(DeckId.B) },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          if (state.activeProTab == ProModeTab.SAMPLER || state.activeProTab == ProModeTab.DJ || state.activeProTab == ProModeTab.FX) {
            SamplePadsEffectsPanel(
              selectedBank = state.selectedSampleBank,
              activePadIds = state.activeSamplePadIds,
              loopEnabled = state.samplePadLoopEnabled,
              volume = state.samplePadVolume,
              mixerTheme = state.mixerTheme,
              onSelectBank = { viewModel.selectSampleBank(it) },
              onTogglePad = { viewModel.toggleSamplePad(it) },
              onToggleLoop = { viewModel.toggleSamplePadLoop() },
              onStopAll = { viewModel.stopAllSamplePads() },
              onVolumeChange = { viewModel.setSamplePadVolume(it) },
            )
          }

          SeratoLibraryDock(
            tracks = state.libraryTracks,
            sessionHistory = state.sessionHistory,
            prepareTrackIds = state.prepareTrackIds,
            deckATrack = state.deckA.track,
            deckBTrack = state.deckB.track,
            selectedCrate = state.selectedCrate,
            bottomTab = state.bottomLibraryTab,
            searchQuery = state.librarySearchQuery,
            sortColumn = state.librarySortColumn,
            sortAscending = state.librarySortAscending,
            isRefreshing = state.isRefreshingLibrary,
            mixerTheme = state.mixerTheme,
            onSelectCrate = { viewModel.selectCrate(it) },
            onSelectBottomTab = { viewModel.setBottomLibraryTab(it) },
            onSearchQueryChange = { viewModel.setLibrarySearchQuery(it) },
            onSearchFocusChanged = { isDockSearchFocused = it },
            onSortColumnClick = { viewModel.setLibrarySortColumn(it) },
            onLoadToDeck = { deckId, track -> viewModel.loadTrack(deckId, track) },
            onTogglePrepare = { viewModel.togglePrepareTrack(it) },
            onImportFiles = { multiAudioImportPicker.launch(arrayOf("audio/*")) },
            onRefreshLibrary = { viewModel.refreshLibrary() },
          )
        }
      } else {
        // Portrait Auto-Rotated Console
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(portraitScrollState)
              .testTag("layout_portrait_dual_deck"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
          StackedDualWaveforms(
            deckA = state.deckA,
            deckB = state.deckB,
            mixerTheme = state.mixerTheme,
            beatMatchDeltaBpm = beatMatchDelta,
            onScrubA = { viewModel.scrubDeck(DeckId.A, it) },
            onScrubB = { viewModel.scrubDeck(DeckId.B, it) },
            waveformZoom = state.waveformZoom,
            isWaveformNeutral = state.isWaveformNeutral,
            onZoomOut = { viewModel.zoomOutWaveform() },
            onZoomIn = { viewModel.zoomInWaveform() },
            onZoomChange = { viewModel.setWaveformZoom(it) },
            onToggleNeutral = { viewModel.toggleWaveformNeutral() },
          )

          SeratoDeckCard(
            deck = state.deckA,
            mixerTheme = state.mixerTheme,
            eqPresets = state.eqPresets,
            activeProTab = state.activeProTab,
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
            onApplyStemPreset = { preset -> viewModel.applyStemPreset(DeckId.A, preset) },
            onToggleQuickIsolate = { viewModel.toggleQuickIsolateMode(DeckId.A) },
            onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.A, band, v) },
            onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.A, preset) },
            onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.A, f, e, fl) },
            onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.A, trim) },
            onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.A, off) },
            onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.A) },
            onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.A, deg) },
            onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.A, slot) },
            onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.A, slot) },
            onToggleQuantize = { viewModel.toggleQuantize(DeckId.A) },
            onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.A) },
            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.A) },
            onToggleLoop = { viewModel.toggleLoop(DeckId.A) },
            onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.A) },
            onTriggerAutoLoop = { beats -> viewModel.triggerAutoLoop(DeckId.A, beats) },
            onHalveLoop = { viewModel.halveLoop(DeckId.A) },
            onDoubleLoop = { viewModel.doubleLoop(DeckId.A) },
            otherDeckKey = state.deckB.effectiveMusicalKey,
            onKeySync = { viewModel.keySyncDeck(DeckId.A) },
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
            deckATrimGain = state.deckA.trimGain,
            deckBTrimGain = state.deckB.trimGain,
            deckAInputLevel = state.deckA.inputSignalLevel,
            deckBInputLevel = state.deckB.inputSignalLevel,
            onDeckATrimChange = { viewModel.setTrimGain(DeckId.A, it) },
            onDeckBTrimChange = { viewModel.setTrimGain(DeckId.B, it) },
            modifier = Modifier.fillMaxWidth(),
          )

          SeratoDeckCard(
            deck = state.deckB,
            mixerTheme = state.mixerTheme,
            eqPresets = state.eqPresets,
            activeProTab = state.activeProTab,
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
            onApplyStemPreset = { preset -> viewModel.applyStemPreset(DeckId.B, preset) },
            onToggleQuickIsolate = { viewModel.toggleQuickIsolateMode(DeckId.B) },
            onEqBandChange = { band, v -> viewModel.setEqBand(DeckId.B, band, v) },
            onApplyEqPreset = { preset -> viewModel.applyEqPreset(DeckId.B, preset) },
            onEffectsChange = { f, e, fl -> viewModel.setEffects(DeckId.B, f, e, fl) },
            onTrimGainChange = { trim -> viewModel.setTrimGain(DeckId.B, trim) },
            onTempoOffsetChange = { off -> viewModel.setTempoOffset(DeckId.B, off) },
            onCyclePitchRange = { viewModel.cyclePitchRange(DeckId.B) },
            onRotateJogWheel = { deg -> viewModel.rotateJogWheel(DeckId.B, deg) },
            onHotCueTap = { slot -> viewModel.triggerOrSetHotCue(DeckId.B, slot) },
            onHotCueLongPress = { slot -> viewModel.clearHotCue(DeckId.B, slot) },
            onToggleQuantize = { viewModel.toggleQuantize(DeckId.B) },
            onToggleScratchMode = { viewModel.toggleScratchMode(DeckId.B) },
            onToggleKeyLock = { viewModel.toggleKeyLock(DeckId.B) },
            onToggleLoop = { viewModel.toggleLoop(DeckId.B) },
            onCycleLoopBeats = { viewModel.cycleLoopBeats(DeckId.B) },
            onTriggerAutoLoop = { beats -> viewModel.triggerAutoLoop(DeckId.B, beats) },
            onHalveLoop = { viewModel.halveLoop(DeckId.B) },
            onDoubleLoop = { viewModel.doubleLoop(DeckId.B) },
            otherDeckKey = state.deckA.effectiveMusicalKey,
            onKeySync = { viewModel.keySyncDeck(DeckId.B) },
            modifier = Modifier.fillMaxWidth(),
          )

          SamplePadsEffectsPanel(
            selectedBank = state.selectedSampleBank,
            activePadIds = state.activeSamplePadIds,
            loopEnabled = state.samplePadLoopEnabled,
            volume = state.samplePadVolume,
            mixerTheme = state.mixerTheme,
            onSelectBank = { viewModel.selectSampleBank(it) },
            onTogglePad = { viewModel.toggleSamplePad(it) },
            onToggleLoop = { viewModel.toggleSamplePadLoop() },
            onStopAll = { viewModel.stopAllSamplePads() },
            onVolumeChange = { viewModel.setSamplePadVolume(it) },
          )

          SeratoLibraryDock(
            tracks = state.libraryTracks,
            sessionHistory = state.sessionHistory,
            prepareTrackIds = state.prepareTrackIds,
            deckATrack = state.deckA.track,
            deckBTrack = state.deckB.track,
            selectedCrate = state.selectedCrate,
            bottomTab = state.bottomLibraryTab,
            searchQuery = state.librarySearchQuery,
            sortColumn = state.librarySortColumn,
            sortAscending = state.librarySortAscending,
            isRefreshing = state.isRefreshingLibrary,
            mixerTheme = state.mixerTheme,
            onSelectCrate = { viewModel.selectCrate(it) },
            onSelectBottomTab = { viewModel.setBottomLibraryTab(it) },
            onSearchQueryChange = { viewModel.setLibrarySearchQuery(it) },
            onSearchFocusChanged = { isDockSearchFocused = it },
            onSortColumnClick = { viewModel.setLibrarySortColumn(it) },
            onLoadToDeck = { deckId, track -> viewModel.loadTrack(deckId, track) },
            onTogglePrepare = { viewModel.togglePrepareTrack(it) },
            onImportFiles = { multiAudioImportPicker.launch(arrayOf("audio/*")) },
            onRefreshLibrary = { viewModel.refreshLibrary() },
          )
          }
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
