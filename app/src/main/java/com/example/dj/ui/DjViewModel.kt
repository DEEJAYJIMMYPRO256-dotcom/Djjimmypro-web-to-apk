package com.example.dj.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dj.audio.AudioDecoder
import com.example.dj.audio.AudioEngine
import com.example.dj.audio.BpmSyncEngine
import com.example.dj.audio.SamplePadAudioEngine
import com.example.dj.audio.StemAudioEngine
import com.example.dj.data.BundledAndLocalTrackScanner
import com.example.dj.data.TrackLibraryDatabase
import com.example.dj.data.TrackLibraryRepository
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.input.DjShortcutCommand
import com.example.dj.midi.MidiAction
import com.example.dj.midi.MidiControllerManager
import com.example.dj.model.BeatSyncedWaveformMath
import com.example.dj.model.CamelotKeyMath
import com.example.dj.model.DeckEffectsState
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DjTrack
import com.example.dj.model.DjUiState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.HotCue
import com.example.dj.model.KeyboardShortcutFeedback
import com.example.dj.model.LibraryBottomTab
import com.example.dj.model.LibraryCrate
import com.example.dj.model.LibrarySortColumn
import com.example.dj.model.LoopState
import com.example.dj.model.ProModeTab
import com.example.dj.model.SamplePadBank
import com.example.dj.model.SamplePadEffect
import com.example.dj.model.SamplerSound
import com.example.dj.model.SeratoHotCueDefaults
import com.example.dj.model.StemChannelState
import com.example.dj.model.StemFxType
import com.example.dj.model.StemIsolationPreset
import com.example.dj.model.StemType
import com.example.dj.model.TrimGainMath
import com.example.dj.model.WaveformZoomConfig
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DjViewModel(application: Application) : AndroidViewModel(application) {
  private val db = TrackLibraryDatabase.getInstance(application)
  private val repository =
    TrackLibraryRepository(db.trackDao(), db.sessionHistoryDao(), db.eqPresetDao())

  val stemAudioEngine = StemAudioEngine()
  val localAudioEngine = AudioEngine()
  val samplePadAudioEngine = SamplePadAudioEngine(application)
  val midiManager = MidiControllerManager(application)

  private val tapTrackerA = BpmSyncEngine.TapTempoTracker()
  private val tapTrackerB = BpmSyncEngine.TapTempoTracker()
  private val masterTapAggregator = BpmSyncEngine.MasterTapTempoAggregator()

  private val _uiState = MutableStateFlow(DjUiState())
  val uiState: StateFlow<DjUiState> = _uiState.asStateFlow()

  private var autoMixTargetCrossfader: Float? = null
  private var feedbackClearJob: Job? = null

  private val cueColors = listOf(0xFF00E5FF, 0xFFFF7A00, 0xFF00E676, 0xFFFF3DF0)
  private val loopBeatOptions = listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f)
  private val autoMixDurations = listOf(4, 8, 12, 16)

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val bundled = BundledAndLocalTrackScanner.discoverBundledSongTracks(getApplication())
      repository.seedDefaultsIfNeeded(bundled)
      repository.scanAndPopulateMissingBeatGrids()
    }

    viewModelScope.launch {
      repository.allTracks.collect { tracks ->
        if (tracks.isNotEmpty()) {
          _uiState.update { it.copy(libraryTracks = tracks) }
        }
      }
    }

    viewModelScope.launch {
      repository.sessionHistory.collect { history ->
        _uiState.update { it.copy(sessionHistory = history) }
      }
    }

    viewModelScope.launch {
      repository.eqPresets.collect { presets ->
        if (presets.isNotEmpty()) {
          _uiState.update { it.copy(eqPresets = presets) }
        }
      }
    }

    viewModelScope.launch {
      stemAudioEngine.masterPeaks.collect { (left, right) ->
        _uiState.update { it.copy(masterPeakLeft = left, masterPeakRight = right) }
      }
    }

    midiManager.initialize()
    viewModelScope.launch {
      midiManager.connectedDeviceName.collect { name ->
        _uiState.update { it.copy(connectedMidiDevice = name) }
      }
    }
    viewModelScope.launch {
      midiManager.commands.collect { cmd -> handleMidiCommand(cmd.action, cmd.normalizedValue) }
    }

    startPlaybackLoop()
  }

  private fun startPlaybackLoop() {
    viewModelScope.launch {
      while (isActive) {
        val state = _uiState.value
        val intervalMs = if (state.powerSavingMode) 100L else 33L
        val dtSeconds = intervalMs / 1000f

        val (enginePeakA, enginePeakB) = stemAudioEngine.deckPeaks.value
        val (engineInputA, engineInputB) = stemAudioEngine.deckInputLevels.value
        var updatedA = advanceDeck(state.deckA, dtSeconds, enginePeakA, engineInputA)
        var updatedB = advanceDeck(state.deckB, dtSeconds, enginePeakB, engineInputB)
        var newCrossfader = state.crossfader
        var isAutoMixing = state.isAutoMixing

        if (state.autoMixEnabled) {
          val target = autoMixTargetCrossfader
          if (target != null) {
            val step = dtSeconds / state.autoMixDurationSeconds.coerceAtLeast(2)
            newCrossfader =
              if (target > newCrossfader) {
                (newCrossfader + step).coerceAtMost(target)
              } else {
                (newCrossfader - step).coerceAtLeast(target)
              }
            if (kotlin.math.abs(newCrossfader - target) < 0.005f) {
              newCrossfader = target
              autoMixTargetCrossfader = null
              isAutoMixing = false
            }
          } else {
            val dominantDeck = if (newCrossfader <= 0.5f) updatedA else updatedB
            val otherDeck = if (newCrossfader <= 0.5f) updatedB else updatedA
            val remainingSeconds =
              (1f - dominantDeck.playheadProgress) *
                dominantDeck.track.durationSeconds /
                (1f + dominantDeck.tempoOffset).coerceAtLeast(0.5f)
            if (
              dominantDeck.isPlaying &&
                !dominantDeck.loop.isActive &&
                remainingSeconds <= state.autoMixDurationSeconds.toFloat()
            ) {
              isAutoMixing = true
              autoMixTargetCrossfader = if (dominantDeck.deckId == DeckId.A) 1f else 0f
              if (!otherDeck.isPlaying) {
                if (otherDeck.deckId == DeckId.A) {
                  updatedA = updatedA.copy(isPlaying = true)
                  if (localAudioEngine.isLoaded(DeckId.A)) localAudioEngine.play(DeckId.A)
                } else {
                  updatedB = updatedB.copy(isPlaying = true)
                  if (localAudioEngine.isLoaded(DeckId.B)) localAudioEngine.play(DeckId.B)
                }
              }
            }
          }
        } else {
          autoMixTargetCrossfader = null
          isAutoMixing = false
        }

        if (updatedA != state.deckA || updatedB != state.deckB || newCrossfader != state.crossfader || isAutoMixing != state.isAutoMixing) {
          _uiState.update {
            it.copy(
              deckA = updatedA,
              deckB = updatedB,
              crossfader = newCrossfader,
              isAutoMixing = isAutoMixing,
            )
          }
          syncAudioEngines()
        }

        delay(intervalMs)
      }
    }
  }

  private fun advanceDeck(
    deck: DeckState,
    dtSeconds: Float,
    enginePeak: Float = 0f,
    engineInputLevel: Float = 0f,
  ): DeckState {
    if (!deck.isPlaying) {
      if (deck.livePeakIntensity <= 0.01f && deck.inputSignalLevel <= 0.01f) return deck
      val decayedPeak = (deck.livePeakIntensity * 0.55f).let { if (it < 0.015f) 0f else it }
      val decayedInput = (deck.inputSignalLevel * 0.55f).let { if (it < 0.015f) 0f else it }
      return deck.copy(livePeakIntensity = decayedPeak, inputSignalLevel = decayedInput)
    }
    val duration = deck.track.durationSeconds.coerceAtLeast(1).toFloat()
    val speed = (1f + deck.tempoOffset).coerceAtLeast(0.25f)
    var nextProgress = deck.playheadProgress + (dtSeconds * speed) / duration
    if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
      if (nextProgress >= deck.loop.endProgress) {
        nextProgress = deck.loop.startProgress
        if (stemAudioEngine.hasDecodedBuffer(deck.deckId)) {
          stemAudioEngine.seekDeck(deck.deckId, nextProgress)
        } else if (localAudioEngine.isLoaded(deck.deckId)) {
          localAudioEngine.seekTo(deck.deckId, nextProgress)
        }
      }
    } else if (nextProgress >= 1f) {
      nextProgress -= 1f
      if (stemAudioEngine.hasDecodedBuffer(deck.deckId)) {
        stemAudioEngine.seekDeck(deck.deckId, nextProgress)
      }
    }
    val nextRot = (deck.jogRotationDeg + dtSeconds * 198f * speed) % 360f
    val advancedDeck = deck.copy(playheadProgress = nextProgress)
    val targetPeak =
      if (enginePeak > 0.01f) enginePeak.coerceIn(0f, 1f)
      else BeatSyncedWaveformMath.computeFallbackPeakIntensity(advancedDeck)
    val peakSmoothing = if (targetPeak > deck.livePeakIntensity) 0.70f else 0.35f
    val smoothedPeak =
      (deck.livePeakIntensity * (1f - peakSmoothing) + targetPeak * peakSmoothing).coerceIn(0f, 1f)

    val targetInput =
      if (engineInputLevel > 0.01f) engineInputLevel.coerceIn(0f, 1f)
      else TrimGainMath.computeFallbackInputLevel(advancedDeck)
    val inputSmoothing = if (targetInput > deck.inputSignalLevel) 0.72f else 0.38f
    val smoothedInput =
      (deck.inputSignalLevel * (1f - inputSmoothing) + targetInput * inputSmoothing).coerceIn(0f, 1f)

    return deck.copy(
      playheadProgress = nextProgress,
      jogRotationDeg = nextRot,
      livePeakIntensity = smoothedPeak,
      inputSignalLevel = smoothedInput,
    )
  }

  private fun syncAudioEngines() {
    val state = _uiState.value
    val hasLocalA = localAudioEngine.isLoaded(DeckId.A) && !stemAudioEngine.hasDecodedBuffer(DeckId.A)
    val hasLocalB = localAudioEngine.isLoaded(DeckId.B) && !stemAudioEngine.hasDecodedBuffer(DeckId.B)
    stemAudioEngine.updateMixState(
      deckA = state.deckA,
      deckB = state.deckB,
      crossfader = state.crossfader,
      curve = state.crossfaderCurve,
      deckAHasLocalFallbackOnly = hasLocalA,
      deckBHasLocalFallbackOnly = hasLocalB,
    )
    val (gainA, gainB) = state.crossfaderCurve.computeGains(state.crossfader)
    if (hasLocalA) {
      localAudioEngine.setVolume(
        DeckId.A,
        (gainA * state.deckA.trimGain * state.deckA.channelVolume).coerceIn(0f, 1f),
      )
      localAudioEngine.setPitch(DeckId.A, state.deckA.keyPitchMultiplier)
    }
    if (hasLocalB) {
      localAudioEngine.setVolume(
        DeckId.B,
        (gainB * state.deckB.trimGain * state.deckB.channelVolume).coerceIn(0f, 1f),
      )
      localAudioEngine.setPitch(DeckId.B, state.deckB.keyPitchMultiplier)
    }
  }

  private fun updateDeck(deckId: DeckId, transform: (DeckState) -> DeckState) {
    _uiState.update { state ->
      when (deckId) {
        DeckId.A -> state.copy(deckA = transform(state.deckA))
        DeckId.B -> state.copy(deckB = transform(state.deckB))
      }
    }
    syncAudioEngines()
  }

  fun togglePlay(deckId: DeckId) {
    val current = if (deckId == DeckId.A) _uiState.value.deckA else _uiState.value.deckB
    val nextPlaying = !current.isPlaying
    updateDeck(deckId) { it.copy(isPlaying = nextPlaying) }
    if (localAudioEngine.isLoaded(deckId) && !stemAudioEngine.hasDecodedBuffer(deckId)) {
      if (nextPlaying) localAudioEngine.play(deckId) else localAudioEngine.pause(deckId)
    }
    if (nextPlaying) {
      viewModelScope.launch(Dispatchers.IO) {
        repository.logPlayback(current.track, deckId.label)
      }
    }
  }

  fun cueDeck(deckId: DeckId) {
    val current = if (deckId == DeckId.A) _uiState.value.deckA else _uiState.value.deckB
    val target = current.hotCues[1]?.progress ?: 0f
    updateDeck(deckId) {
      it.copy(
        isPlaying = false,
        playheadProgress = target,
        livePeakIntensity = 0f,
        inputSignalLevel = 0f,
      )
    }
    stemAudioEngine.seekDeck(deckId, target)
    if (localAudioEngine.isLoaded(deckId) && !stemAudioEngine.hasDecodedBuffer(deckId)) {
      localAudioEngine.pause(deckId)
      localAudioEngine.seekTo(deckId, target)
    }
  }

  fun syncDeck(deckId: DeckId) {
    val state = _uiState.value
    val source = if (deckId == DeckId.A) state.deckA else state.deckB
    val target = if (deckId == DeckId.A) state.deckB else state.deckA
    if (source.isAutoSynced && abs(source.effectiveBpm - target.effectiveBpm) < 0.08f) {
      updateDeck(deckId) { it.copy(isAutoSynced = false) }
      showShortcutFeedback("${deckId.label} AUTO-SYNC OFF")
      return
    }
    val offset =
      BpmSyncEngine.calculateSyncOffset(
        sourceBaseBpm = source.track.bpm,
        targetEffectiveBpm = target.effectiveBpm,
        maxRange = source.pitchRange.maxOffset,
      )
    val syncedBpm = source.track.bpm * (1f + offset)
    val alignedProgress =
      BpmSyncEngine.calculatePhaseAlignedProgress(
        sourcePlayheadProgress = source.playheadProgress,
        sourceSyncedBpm = syncedBpm,
        sourceDurationSeconds = source.track.durationSeconds,
        targetPlayheadProgress = target.playheadProgress,
        targetEffectiveBpm = target.effectiveBpm,
        targetDurationSeconds = target.track.durationSeconds,
      )
    updateDeck(deckId) {
      it.copy(
        tempoOffset = offset,
        playheadProgress = alignedProgress,
        isAutoSynced = true,
      )
    }
    stemAudioEngine.seekDeck(deckId, alignedProgress)
    if (localAudioEngine.isLoaded(deckId) && !stemAudioEngine.hasDecodedBuffer(deckId)) {
      localAudioEngine.seekTo(deckId, alignedProgress)
    }
    showShortcutFeedback("${deckId.label} AUTO-SYNCED ${String.format("%.1f", target.effectiveBpm)} BPM")
  }

  fun keySyncDeck(deckId: DeckId) {
    val state = _uiState.value
    val source = if (deckId == DeckId.A) state.deckA else state.deckB
    val target = if (deckId == DeckId.A) state.deckB else state.deckA
    val targetEffectiveKey = target.effectiveMusicalKey
    updateDeck(deckId) { deck ->
      if (deck.isKeySynced) {
        deck.copy(isKeySynced = false, keyShiftSemitones = 0)
      } else {
        val semitones =
          CamelotKeyMath.calculateKeySyncSemitones(
            sourceMusicalKey = deck.track.musicalKey,
            targetMusicalKey = targetEffectiveKey,
          )
        deck.copy(isKeySynced = true, keyShiftSemitones = semitones)
      }
    }
  }

  fun loadTrack(deckId: DeckId, track: DjTrack) {
    localAudioEngine.unloadDeck(deckId)
    stemAudioEngine.setDecodedBuffer(deckId, null)
    val autoTrim = track.computeAutoGainTrim()
    updateDeck(deckId) {
      it.copy(
        track = track,
        isPlaying = false,
        playheadProgress = 0f,
        tempoOffset = 0f,
        trimGain = autoTrim,
        hotCues = emptyMap(),
        loop = LoopState(),
        preQuickIsolateVocalLevel = null,
        preQuickIsolateFilter = null,
        keyShiftSemitones = 0,
        isKeySynced = false,
        isAutoSynced = false,
        livePeakIntensity = 0f,
      )
    }
    if (!track.localUri.isNullOrBlank()) {
      val uri = Uri.parse(track.localUri)
      runCatching { localAudioEngine.loadFile(deckId, getApplication(), uri) }
      decodeLocalTrackForStems(deckId, uri, track)
    }
  }

  private fun decodeLocalTrackForStems(deckId: DeckId, uri: Uri, existingTrack: DjTrack) {
    viewModelScope.launch(Dispatchers.IO) {
      val pcm = AudioDecoder.decodeUriToStereoPcm(getApplication(), uri) ?: return@launch
      val updatedTrack =
        existingTrack.copy(
          bpm = pcm.detectedBpm,
          durationSeconds = pcm.durationSeconds,
          waveformPeaks = pcm.waveformPeaks,
          beatGrid = DjTrack.computeBeatGrid(pcm.detectedBpm, pcm.durationSeconds),
        )
      repository.upsertTrack(updatedTrack)

      val currentDeckTrackId =
        if (deckId == DeckId.A) _uiState.value.deckA.track.id else _uiState.value.deckB.track.id
      if (currentDeckTrackId == existingTrack.id) {
        val currentProgress =
          if (deckId == DeckId.A) _uiState.value.deckA.playheadProgress
          else _uiState.value.deckB.playheadProgress
        stemAudioEngine.setDecodedBuffer(deckId, pcm)
        stemAudioEngine.seekDeck(deckId, currentProgress)
        localAudioEngine.unloadDeck(deckId)
        updateDeck(deckId) {
          it.copy(track = updatedTrack, trimGain = updatedTrack.computeAutoGainTrim())
        }
      }
    }
  }

  fun loadLocalAudioUri(deckId: DeckId, uri: Uri) {
    val track = BundledAndLocalTrackScanner.extractTrackFromUri(getApplication(), uri)
    viewModelScope.launch(Dispatchers.IO) { repository.upsertTrack(track) }
    loadTrack(deckId, track)
  }

  fun importLocalAudioUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isRefreshingLibrary = true) }
      val imported = uris.map { BundledAndLocalTrackScanner.extractTrackFromUri(getApplication(), it) }
      repository.upsertTracks(imported)
      _uiState.update { it.copy(isRefreshingLibrary = false) }
      showShortcutFeedback("IMPORTED ${imported.size} SONGS")
    }
  }

  fun importAudioFolderTree(treeUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isRefreshingLibrary = true) }
      val uris = BundledAndLocalTrackScanner.scanDocumentTreeAudioUris(getApplication(), treeUri)
      if (uris.isNotEmpty()) {
        val imported = uris.map { BundledAndLocalTrackScanner.extractTrackFromUri(getApplication(), it) }
        repository.upsertTracks(imported)
        showShortcutFeedback("LOADED ${imported.size} FOLDER SONGS")
      } else {
        showShortcutFeedback("NO AUDIO FILES IN FOLDER")
      }
      _uiState.update { it.copy(isRefreshingLibrary = false) }
    }
  }

  fun updateTrackMetadata(trackId: String, title: String, artist: String, genre: String) {
    val existing = _uiState.value.libraryTracks.firstOrNull { it.id == trackId } ?: return
    val updated = existing.copy(title = title.trim(), artist = artist.trim(), genre = genre.trim())
    viewModelScope.launch(Dispatchers.IO) { repository.upsertTrack(updated) }
    _uiState.update { state ->
      state.copy(
        libraryTracks = state.libraryTracks.map { if (it.id == trackId) updated else it },
        deckA = if (state.deckA.track.id == trackId) state.deckA.copy(track = updated) else state.deckA,
        deckB = if (state.deckB.track.id == trackId) state.deckB.copy(track = updated) else state.deckB,
      )
    }
  }

  fun setStemLevel(deckId: DeckId, stem: StemType, level: Float) {
    updateDeck(deckId) { deck ->
      val current = deck.stems[stem] ?: return@updateDeck deck
      deck.copy(stems = deck.stems + (stem to current.copy(level = level.coerceIn(0f, 1f))))
    }
  }

  fun toggleQuickIsolateMode(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      val nextEnabled = !deck.quickIsolateEnabled
      if (!nextEnabled && deck.isQuickIsolateActive) {
        val vox = deck.stems[StemType.VOCALS]
        val restoredLevel = deck.preQuickIsolateVocalLevel ?: 1f
        val restoredFilter =
          if (abs(deck.effects.filter - DeckState.QUICK_ISOLATE_HPF) < 0.03f) {
            deck.preQuickIsolateFilter ?: 0f
          } else {
            deck.effects.filter
          }
        deck.copy(
          quickIsolateEnabled = false,
          stems =
            if (vox != null) {
              deck.stems + (StemType.VOCALS to vox.copy(level = restoredLevel, isMuted = false, isSolo = false))
            } else {
              deck.stems
            },
          effects = deck.effects.copy(filter = restoredFilter),
          preQuickIsolateVocalLevel = null,
          preQuickIsolateFilter = null,
        )
      } else {
        deck.copy(quickIsolateEnabled = nextEnabled)
      }
    }
  }

  fun toggleStemMute(deckId: DeckId, stem: StemType) {
    updateDeck(deckId) { deck ->
      val current = deck.stems[stem] ?: return@updateDeck deck
      if (stem == StemType.VOCALS && deck.quickIsolateEnabled) {
        val nextMuted = !current.isMuted
        if (nextMuted) {
          val savedVocalLevel =
            if (current.level > 0.01f) current.level else (deck.preQuickIsolateVocalLevel ?: 1f)
          val savedFilter = deck.preQuickIsolateFilter ?: deck.effects.filter
          val nextFilter =
            if (deck.effects.filter > DeckState.QUICK_ISOLATE_HPF) {
              deck.effects.filter
            } else {
              DeckState.QUICK_ISOLATE_HPF
            }
          deck.copy(
            stems =
              deck.stems +
                (StemType.VOCALS to current.copy(level = 0f, isMuted = true, isSolo = false)),
            effects = deck.effects.copy(filter = nextFilter),
            preQuickIsolateVocalLevel = savedVocalLevel,
            preQuickIsolateFilter = savedFilter,
          )
        } else {
          val restoredLevel =
            deck.preQuickIsolateVocalLevel ?: if (current.level <= 0.01f) 1f else current.level
          val restoredFilter =
            if (abs(deck.effects.filter - DeckState.QUICK_ISOLATE_HPF) < 0.03f) {
              deck.preQuickIsolateFilter ?: 0f
            } else {
              deck.effects.filter
            }
          deck.copy(
            stems =
              deck.stems +
                (StemType.VOCALS to
                  current.copy(level = restoredLevel, isMuted = false, isSolo = false)),
            effects = deck.effects.copy(filter = restoredFilter),
            preQuickIsolateVocalLevel = null,
            preQuickIsolateFilter = null,
          )
        }
      } else {
        val nextMuted = !current.isMuted
        if (stem == StemType.VOCALS && !nextMuted && (current.level <= 0.01f || deck.preQuickIsolateFilter != null)) {
          val restoredLevel =
            if (current.level <= 0.01f) (deck.preQuickIsolateVocalLevel ?: 1f) else current.level
          val restoredFilter =
            if (abs(deck.effects.filter - DeckState.QUICK_ISOLATE_HPF) < 0.03f) {
              deck.preQuickIsolateFilter ?: deck.effects.filter
            } else {
              deck.effects.filter
            }
          deck.copy(
            stems =
              deck.stems +
                (stem to current.copy(level = restoredLevel, isMuted = false, isSolo = false)),
            effects = deck.effects.copy(filter = restoredFilter),
            preQuickIsolateVocalLevel = null,
            preQuickIsolateFilter = null,
          )
        } else {
          deck.copy(
            stems = deck.stems + (stem to current.copy(isMuted = nextMuted, isSolo = false))
          )
        }
      }
    }
  }

  fun toggleStemSolo(deckId: DeckId, stem: StemType) {
    updateDeck(deckId) { deck ->
      val current = deck.stems[stem] ?: return@updateDeck deck
      val restoredLevel =
        if (stem == StemType.VOCALS && current.level <= 0.01f) {
          deck.preQuickIsolateVocalLevel ?: 1f
        } else {
          current.level
        }
      val restoredFilter =
        if (stem == StemType.VOCALS && deck.preQuickIsolateFilter != null && abs(deck.effects.filter - DeckState.QUICK_ISOLATE_HPF) < 0.03f) {
          deck.preQuickIsolateFilter
        } else {
          deck.effects.filter
        }
      deck.copy(
        stems =
          deck.stems +
            (stem to current.copy(level = restoredLevel, isSolo = !current.isSolo, isMuted = false)),
        effects = deck.effects.copy(filter = restoredFilter),
        preQuickIsolateVocalLevel = if (stem == StemType.VOCALS) null else deck.preQuickIsolateVocalLevel,
        preQuickIsolateFilter = if (stem == StemType.VOCALS) null else deck.preQuickIsolateFilter,
      )
    }
  }

  fun applyStemPreset(deckId: DeckId, preset: StemIsolationPreset) {
    updateDeck(deckId) { deck ->
      val effectivePreset =
        when {
          preset == StemIsolationPreset.INSTRUMENTAL && deck.isVoiceRemoved ->
            StemIsolationPreset.FULL
          preset == StemIsolationPreset.ACAPELLA && deck.isInstrumentalRemoved ->
            StemIsolationPreset.FULL
          else -> preset
        }
      val nextStems =
        when (effectivePreset) {
          StemIsolationPreset.FULL ->
            StemType.entries.associateWith { StemChannelState(level = 1f, isMuted = false, isSolo = false) }
          StemIsolationPreset.INSTRUMENTAL ->
            mapOf(
              StemType.VOCALS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.DRUMS to StemChannelState(level = 1f, isMuted = false, isSolo = false),
              StemType.BASS to StemChannelState(level = 1f, isMuted = false, isSolo = false),
              StemType.MELODY to StemChannelState(level = 1f, isMuted = false, isSolo = false),
            )
          StemIsolationPreset.ACAPELLA ->
            mapOf(
              StemType.VOCALS to StemChannelState(level = 1f, isMuted = false, isSolo = true),
              StemType.DRUMS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.BASS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.MELODY to StemChannelState(level = 1f, isMuted = true, isSolo = false),
            )
          StemIsolationPreset.DRUMS_ONLY ->
            mapOf(
              StemType.VOCALS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.DRUMS to StemChannelState(level = 1f, isMuted = false, isSolo = true),
              StemType.BASS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.MELODY to StemChannelState(level = 1f, isMuted = true, isSolo = false),
            )
          StemIsolationPreset.BASS_ONLY ->
            mapOf(
              StemType.VOCALS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.DRUMS to StemChannelState(level = 1f, isMuted = true, isSolo = false),
              StemType.BASS to StemChannelState(level = 1f, isMuted = false, isSolo = true),
              StemType.MELODY to StemChannelState(level = 1f, isMuted = true, isSolo = false),
            )
        }
      val restoredFilter =
        if (effectivePreset == StemIsolationPreset.FULL &&
          deck.preQuickIsolateFilter != null &&
          abs(deck.effects.filter - DeckState.QUICK_ISOLATE_HPF) < 0.03f
        ) {
          deck.preQuickIsolateFilter
        } else {
          deck.effects.filter
        }
      deck.copy(
        stems = nextStems,
        effects = deck.effects.copy(filter = restoredFilter),
        preQuickIsolateVocalLevel = if (effectivePreset == StemIsolationPreset.FULL) null else deck.preQuickIsolateVocalLevel,
        preQuickIsolateFilter = if (effectivePreset == StemIsolationPreset.FULL) null else deck.preQuickIsolateFilter,
      )
    }
  }

  fun setEqBand(deckId: DeckId, band: EqBand, value: Float) {
    val clamped = value.coerceIn(0f, 1.5f)
    updateDeck(deckId) { deck ->
      val nextEq =
        when (band) {
          EqBand.LOW -> deck.eq.copy(low = clamped)
          EqBand.MID -> deck.eq.copy(mid = clamped)
          EqBand.HIGH -> deck.eq.copy(high = clamped)
        }
      deck.copy(eq = nextEq)
    }
  }

  fun applyEqPreset(deckId: DeckId, preset: EqPreset) {
    updateDeck(deckId) { deck ->
      deck.copy(eq = deck.eq.copy(low = preset.low, mid = preset.mid, high = preset.high))
    }
  }

  fun saveEqPreset(deckId: DeckId, name: String) {
    if (name.isBlank()) return
    val eq = if (deckId == DeckId.A) _uiState.value.deckA.eq else _uiState.value.deckB.eq
    viewModelScope.launch(Dispatchers.IO) {
      repository.saveEqPreset(name, eq.low, eq.mid, eq.high)
    }
  }

  fun setEffects(deckId: DeckId, filter: Float? = null, echo: Float? = null, flanger: Float? = null) {
    updateDeck(deckId) { deck ->
      val fx: DeckEffectsState = deck.effects
      deck.copy(
        effects =
          fx.copy(
            filter = (filter ?: fx.filter).coerceIn(-1f, 1f),
            echo = (echo ?: fx.echo).coerceIn(0f, 1f),
            flanger = (flanger ?: fx.flanger).coerceIn(0f, 1f),
          )
      )
    }
  }

  fun toggleStemFx(deckId: DeckId, fxType: StemFxType) {
    updateDeck(deckId) { deck ->
      val fx = deck.effects
      val nextFx =
        when (fxType) {
          StemFxType.VOCAL_ECHO -> fx.copy(vocalEcho = !fx.vocalEcho)
          StemFxType.INST_ECHO -> fx.copy(instEcho = !fx.instEcho)
          StemFxType.INST_BRAKER -> fx.copy(instBrake = !fx.instBrake)
          StemFxType.DRUMS_ECHO -> fx.copy(drumsEcho = !fx.drumsEcho)
        }
      deck.copy(effects = nextFx)
    }
  }

  fun setTrimGain(deckId: DeckId, trim: Float) {
    val clamped = TrimGainMath.clamp(trim)
    updateDeck(deckId) { deck ->
      val next = deck.copy(trimGain = clamped)
      if (next.isPlaying) {
        next.copy(inputSignalLevel = TrimGainMath.computeFallbackInputLevel(next))
      } else {
        next
      }
    }
  }

  fun resetTrimGain(deckId: DeckId) {
    setTrimGain(deckId, TrimGainMath.UNITY_TRIM)
  }

  fun setChannelVolume(deckId: DeckId, volume: Float) {
    updateDeck(deckId) { it.copy(channelVolume = volume.coerceIn(0f, 1f)) }
  }

  fun setTempoOffset(deckId: DeckId, offset: Float) {
    updateDeck(deckId) { deck ->
      val max = deck.pitchRange.maxOffset
      deck.copy(tempoOffset = offset.coerceIn(-max, max), isAutoSynced = false)
    }
  }

  fun cyclePitchRange(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      val next = deck.pitchRange.next()
      deck.copy(
        pitchRange = next,
        tempoOffset = deck.tempoOffset.coerceIn(-next.maxOffset, next.maxOffset),
      )
    }
  }

  fun tapTempo(deckId: DeckId, nowMs: Long = System.currentTimeMillis()) {
    val tracker = if (deckId == DeckId.A) tapTrackerA else tapTrackerB
    val detected = tracker.recordTap(nowMs)
    val tapSource =
      if (deckId == DeckId.A) BpmSyncEngine.TapSource.DECK_A
      else BpmSyncEngine.TapSource.DECK_B
    val masterDetected = masterTapAggregator.recordTap(tapSource, nowMs)

    _uiState.update { state ->
      val nextState =
        if (detected != null) {
          val updateTarget: (DeckState) -> DeckState = { deck ->
            val updatedTrack =
              deck.track.copy(
                bpm = detected,
                beatGrid = DjTrack.computeBeatGrid(detected, deck.track.durationSeconds),
              )
            deck.copy(track = updatedTrack, tempoOffset = 0f)
          }
          if (deckId == DeckId.A) state.copy(deckA = updateTarget(state.deckA))
          else state.copy(deckB = updateTarget(state.deckB))
        } else {
          state
        }
      nextState.copy(
        masterTappedBpm = masterDetected ?: nextState.masterTappedBpm,
        masterTapSource = masterTapAggregator.sourceSummary(),
        masterTapCount = masterTapAggregator.tapCount(),
        lastMasterTapAtMs = nowMs,
      )
    }
    if (detected != null) {
      syncAudioEngines()
    }
  }

  fun tapMasterTempo(nowMs: Long = System.currentTimeMillis()) {
    val masterDetected = masterTapAggregator.recordTap(BpmSyncEngine.TapSource.MASTER, nowMs)
    _uiState.update { state ->
      state.copy(
        masterTappedBpm = masterDetected ?: state.masterTappedBpm,
        masterTapSource = masterTapAggregator.sourceSummary(),
        masterTapCount = masterTapAggregator.tapCount(),
        lastMasterTapAtMs = nowMs,
      )
    }
  }

  fun resetMasterTapTempo() {
    masterTapAggregator.reset()
    _uiState.update { state ->
      state.copy(
        masterTappedBpm = null,
        masterTapSource = null,
        masterTapCount = 0,
        lastMasterTapAtMs = 0L,
      )
    }
  }

  fun scrubDeck(deckId: DeckId, deltaProgress: Float) {
    updateDeck(deckId) { deck ->
      val next = (deck.playheadProgress + deltaProgress).coerceIn(0f, 1f)
      stemAudioEngine.seekDeck(deckId, next)
      if (localAudioEngine.isLoaded(deckId) && !stemAudioEngine.hasDecodedBuffer(deckId)) {
        localAudioEngine.seekTo(deckId, next)
      }
      deck.copy(
        playheadProgress = next,
        jogRotationDeg = (deck.jogRotationDeg + deltaProgress * 1800f) % 360f,
      )
    }
  }

  fun rotateJogWheel(deckId: DeckId, deltaAngleDeg: Float) {
    val deck = if (deckId == DeckId.A) _uiState.value.deckA else _uiState.value.deckB
    if (!deck.isPlaying || deck.scratchMode) {
      scrubDeck(deckId, deltaAngleDeg / 720f)
    } else {
      val nudge = (deltaAngleDeg / 1800f).coerceIn(-0.04f, 0.04f)
      setTempoOffset(deckId, deck.tempoOffset + nudge)
      updateDeck(deckId) { it.copy(jogRotationDeg = (it.jogRotationDeg + deltaAngleDeg) % 360f) }
    }
  }

  fun triggerOrSetHotCue(deckId: DeckId, slot: Int) {
    updateDeck(deckId) { deck ->
      val existing = deck.hotCues[slot]
      if (existing != null) {
        stemAudioEngine.seekDeck(deckId, existing.progress)
        if (localAudioEngine.isLoaded(deckId) && !stemAudioEngine.hasDecodedBuffer(deckId)) {
          localAudioEngine.seekTo(deckId, existing.progress)
        }
        deck.copy(playheadProgress = existing.progress)
      } else {
        val snapped = deck.snapToBeatGrid(deck.playheadProgress)
        val meta = SeratoHotCueDefaults.metaForSlot(slot)
        val cue =
          HotCue(
            slot = slot,
            progress = snapped,
            colorHex = meta.colorHex,
            label = meta.label,
          )
        deck.copy(hotCues = deck.hotCues + (slot to cue))
      }
    }
  }

  fun clearHotCue(deckId: DeckId, slot: Int) {
    updateDeck(deckId) { it.copy(hotCues = it.hotCues - slot) }
  }

  fun toggleQuantize(deckId: DeckId) {
    updateDeck(deckId) { it.copy(quantizeEnabled = !it.quantizeEnabled) }
  }

  fun toggleScratchMode(deckId: DeckId) {
    updateDeck(deckId) { it.copy(scratchMode = !it.scratchMode) }
  }

  fun toggleKeyLock(deckId: DeckId) {
    updateDeck(deckId) { it.copy(keyLockEnabled = !it.keyLockEnabled) }
  }

  fun toggleLoop(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      if (deck.loop.isActive) {
        deck.copy(loop = deck.loop.copy(isActive = false))
      } else {
        val start = deck.snapToBeatGrid(deck.playheadProgress)
        val beatDurationSec = 60f / deck.effectiveBpm
        val loopSpanProgress =
          (deck.loop.beats * beatDurationSec) / deck.track.durationSeconds.coerceAtLeast(1)
        val end = (start + loopSpanProgress).coerceAtMost(1f)
        deck.copy(
          loop = deck.loop.copy(isActive = true, startProgress = start, endProgress = end)
        )
      }
    }
  }

  fun cycleLoopBeats(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      val idx = loopBeatOptions.indexOf(deck.loop.beats)
      val nextBeats = loopBeatOptions[(idx + 1) % loopBeatOptions.size]
      val beatDurationSec = 60f / deck.effectiveBpm
      val loopSpanProgress =
        (nextBeats * beatDurationSec) / deck.track.durationSeconds.coerceAtLeast(1)
      val end = (deck.loop.startProgress + loopSpanProgress).coerceAtMost(1f)
      deck.copy(loop = deck.loop.copy(beats = nextBeats, endProgress = end))
    }
  }

  fun triggerAutoLoop(deckId: DeckId, beats: Float) {
    val normalizedBeats = beats.coerceIn(0.25f, 16f)
    updateDeck(deckId) { deck ->
      if (deck.loop.isActive && abs(deck.loop.beats - normalizedBeats) < 0.01f) {
        deck.copy(loop = deck.loop.copy(isActive = false))
      } else {
        val start = deck.snapToBeatGrid(deck.playheadProgress)
        val beatDurationSec = 60f / deck.effectiveBpm.coerceAtLeast(1f)
        val loopSpanProgress =
          (normalizedBeats * beatDurationSec) / deck.track.durationSeconds.coerceAtLeast(1)
        val end = (start + loopSpanProgress).coerceAtMost(1f)
        deck.copy(
          loop =
            deck.loop.copy(
              isActive = true,
              beats = normalizedBeats,
              startProgress = start,
              endProgress = end,
            )
        )
      }
    }
  }

  fun halveLoop(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      val nextBeats = (deck.loop.beats * 0.5f).coerceAtLeast(loopBeatOptions.first())
      val start =
        if (deck.loop.isActive) deck.loop.startProgress
        else deck.snapToBeatGrid(deck.playheadProgress)
      val beatDurationSec = 60f / deck.effectiveBpm.coerceAtLeast(1f)
      val loopSpanProgress =
        (nextBeats * beatDurationSec) / deck.track.durationSeconds.coerceAtLeast(1)
      val end = (start + loopSpanProgress).coerceAtMost(1f)
      deck.copy(
        loop = deck.loop.copy(beats = nextBeats, startProgress = start, endProgress = end)
      )
    }
  }

  fun doubleLoop(deckId: DeckId) {
    updateDeck(deckId) { deck ->
      val nextBeats = (deck.loop.beats * 2f).coerceAtMost(loopBeatOptions.last())
      val start =
        if (deck.loop.isActive) deck.loop.startProgress
        else deck.snapToBeatGrid(deck.playheadProgress)
      val beatDurationSec = 60f / deck.effectiveBpm.coerceAtLeast(1f)
      val loopSpanProgress =
        (nextBeats * beatDurationSec) / deck.track.durationSeconds.coerceAtLeast(1)
      val end = (start + loopSpanProgress).coerceAtMost(1f)
      deck.copy(
        loop = deck.loop.copy(beats = nextBeats, startProgress = start, endProgress = end)
      )
    }
  }

  fun setCrossfader(value: Float) {
    autoMixTargetCrossfader = null
    _uiState.update { it.copy(crossfader = value.coerceIn(0f, 1f), isAutoMixing = false) }
    syncAudioEngines()
  }

  fun nudgeCrossfader(delta: Float) {
    val next = (_uiState.value.crossfader + delta).coerceIn(0f, 1f)
    setCrossfader(next)
    showShortcutFeedback("XFADE ${(next * 100).roundToInt()}%")
  }

  fun cycleCrossfaderCurve() {
    _uiState.update { it.copy(crossfaderCurve = it.crossfaderCurve.next()) }
    syncAudioEngines()
  }

  fun toggleAutoMix() {
    _uiState.update { it.copy(autoMixEnabled = !it.autoMixEnabled, isAutoMixing = false) }
    autoMixTargetCrossfader = null
  }

  fun cycleAutoMixDuration() {
    _uiState.update { state ->
      val idx = autoMixDurations.indexOf(state.autoMixDurationSeconds)
      state.copy(autoMixDurationSeconds = autoMixDurations[(idx + 1) % autoMixDurations.size])
    }
  }

  fun cycleMixerTheme() {
    _uiState.update { it.copy(mixerTheme = it.mixerTheme.next()) }
  }

  fun togglePowerSavingMode() {
    _uiState.update { it.copy(powerSavingMode = !it.powerSavingMode) }
  }

  fun toggleRecording() {
    val state = _uiState.value
    if (state.isRecording) {
      stemAudioEngine.stopRecording()
      _uiState.update { it.copy(isRecording = false) }
    } else {
      val dir = File(getApplication<Application>().filesDir, "recordings")
      val outFile = File(dir, "stemdeck_mix_${System.currentTimeMillis()}.wav")
      if (stemAudioEngine.startRecording(outFile)) {
        _uiState.update { it.copy(isRecording = true, lastRecordingPath = outFile.name) }
      }
    }
  }

  fun toggleShortcutsDialog(show: Boolean? = null) {
    _uiState.update { it.copy(showShortcutsDialog = show ?: !it.showShortcutsDialog) }
  }

  fun setWaveformZoom(zoom: Float) {
    _uiState.update { it.copy(waveformZoom = zoom.coerceIn(1.0f, 8.0f)) }
  }

  fun zoomInWaveform() {
    _uiState.update { state ->
      val next = WaveformZoomConfig.steps.firstOrNull { it > state.waveformZoom + 0.05f }
        ?: WaveformZoomConfig.steps.last()
      state.copy(waveformZoom = next)
    }
  }

  fun zoomOutWaveform() {
    _uiState.update { state ->
      val prev = WaveformZoomConfig.steps.lastOrNull { it < state.waveformZoom - 0.05f }
        ?: WaveformZoomConfig.steps.first()
      state.copy(waveformZoom = prev)
    }
  }

  fun toggleWaveformNeutral() {
    _uiState.update { it.copy(isWaveformNeutral = !it.isWaveformNeutral) }
  }

  fun setProModeTab(tab: ProModeTab) {
    _uiState.update { it.copy(activeProTab = tab) }
  }

  fun selectCrate(crate: LibraryCrate) {
    _uiState.update { it.copy(selectedCrate = crate) }
  }

  fun setBottomLibraryTab(tab: LibraryBottomTab) {
    _uiState.update { it.copy(bottomLibraryTab = tab) }
  }

  fun setLibrarySearchQuery(query: String) {
    _uiState.update { it.copy(librarySearchQuery = query) }
  }

  fun setLibrarySortColumn(column: LibrarySortColumn) {
    _uiState.update { state ->
      val asc = if (state.librarySortColumn == column) !state.librarySortAscending else true
      state.copy(librarySortColumn = column, librarySortAscending = asc)
    }
  }

  fun togglePrepareTrack(trackId: String) {
    _uiState.update { state ->
      val next =
        if (trackId in state.prepareTrackIds) state.prepareTrackIds - trackId
        else state.prepareTrackIds + trackId
      state.copy(prepareTrackIds = next)
    }
  }

  fun refreshLibrary() {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isRefreshingLibrary = true) }
      val bundled = BundledAndLocalTrackScanner.discoverBundledSongTracks(getApplication())
      repository.seedDefaultsIfNeeded(bundled)
      repository.scanAndPopulateMissingBeatGrids()
      delay(300L)
      _uiState.update { it.copy(isRefreshingLibrary = false) }
    }
  }

  fun triggerSampler(sound: SamplerSound) {
    stemAudioEngine.triggerSampler(sound)
    _uiState.update { it.copy(lastSamplerTriggered = sound) }
    showShortcutFeedback("SAMPLER: ${sound.title.uppercase()}")
  }

  fun toggleSamplePad(pad: SamplePadEffect) {
    val state = _uiState.value
    if (pad.id in state.activeSamplePadIds) {
      samplePadAudioEngine.stopPad(pad.id)
      _uiState.update { it.copy(activeSamplePadIds = it.activeSamplePadIds - pad.id) }
      showShortcutFeedback("PAD OFF: ${pad.title.uppercase()}")
    } else {
      _uiState.update {
        it.copy(
          activeSamplePadIds = it.activeSamplePadIds + pad.id,
          lastSamplePadTriggeredId = pad.id,
        )
      }
      val startedReal =
        samplePadAudioEngine.playPad(
          pad = pad,
          volume = state.samplePadVolume,
          loop = state.samplePadLoopEnabled,
          onCompleted = { finishedId -> onSamplePadCompleted(finishedId) },
        )
      if (!startedReal) {
        stemAudioEngine.triggerSampler(pad.fallbackSynth)
      }
      showShortcutFeedback("PAD ON: ${pad.title.uppercase()}")
    }
  }

  fun onSamplePadCompleted(padId: String) {
    _uiState.update { it.copy(activeSamplePadIds = it.activeSamplePadIds - padId) }
  }

  fun stopAllSamplePads() {
    samplePadAudioEngine.stopAll()
    _uiState.update { it.copy(activeSamplePadIds = emptySet()) }
    showShortcutFeedback("ALL SAMPLE PADS OFF")
  }

  fun selectSampleBank(bank: SamplePadBank) {
    _uiState.update { it.copy(selectedSampleBank = bank) }
  }

  fun toggleSamplePadLoop() {
    val next = !_uiState.value.samplePadLoopEnabled
    _uiState.update { it.copy(samplePadLoopEnabled = next) }
    samplePadAudioEngine.setLoopAll(next)
    showShortcutFeedback(if (next) "SAMPLE LOOP ON" else "SAMPLE 1-SHOT MODE")
  }

  fun setSamplePadVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _uiState.update { it.copy(samplePadVolume = clamped) }
    samplePadAudioEngine.setVolume(clamped)
  }

  fun handleKeyboardShortcut(command: DjShortcutCommand) {
    when (command) {
      is DjShortcutCommand.NudgeCrossfader -> nudgeCrossfader(command.delta)
      is DjShortcutCommand.SetCrossfader -> {
        setCrossfader(command.value)
        showShortcutFeedback(command.label)
      }
      DjShortcutCommand.CycleCrossfaderCurve -> {
        cycleCrossfaderCurve()
        showShortcutFeedback("CURVE: ${_uiState.value.crossfaderCurve.label.uppercase()}")
      }
      is DjShortcutCommand.TogglePlay -> {
        togglePlay(command.deckId)
        val playing =
          if (command.deckId == DeckId.A) _uiState.value.deckA.isPlaying
          else _uiState.value.deckB.isPlaying
        showShortcutFeedback("${command.deckId.label}: ${if (playing) "PLAY" else "PAUSE"}")
      }
      is DjShortcutCommand.CueDeck -> {
        cueDeck(command.deckId)
        showShortcutFeedback("${command.deckId.label} CUE")
      }
      is DjShortcutCommand.SyncDeck -> {
        syncDeck(command.deckId)
        showShortcutFeedback("${command.deckId.label} SYNCED")
      }
      is DjShortcutCommand.TriggerHotCue -> {
        triggerOrSetHotCue(command.deckId, command.slot)
        showShortcutFeedback("${command.deckId.label} CUE ${command.slot}")
      }
      is DjShortcutCommand.ToggleStemMute -> {
        toggleStemMute(command.deckId, command.stem)
        showShortcutFeedback("${command.deckId.label} ${command.stem.shortLabel} MUTE")
      }
      DjShortcutCommand.ToggleAutoMix -> {
        toggleAutoMix()
        showShortcutFeedback(
          if (_uiState.value.autoMixEnabled) "AUTO-MIX ON" else "AUTO-MIX OFF"
        )
      }
      DjShortcutCommand.ToggleShortcutsHelp -> toggleShortcutsDialog()
    }
  }

  private fun showShortcutFeedback(label: String) {
    _uiState.update {
      it.copy(shortcutFeedback = KeyboardShortcutFeedback(label, System.currentTimeMillis()))
    }
    feedbackClearJob?.cancel()
    feedbackClearJob =
      viewModelScope.launch {
        delay(1400L)
        _uiState.update { it.copy(shortcutFeedback = null) }
      }
  }

  private fun handleMidiCommand(action: MidiAction, value: Float) {
    when (action) {
      MidiAction.CROSSFADER -> setCrossfader(value)
      MidiAction.DECK_A_PLAY -> if (value > 0f) togglePlay(DeckId.A)
      MidiAction.DECK_B_PLAY -> if (value > 0f) togglePlay(DeckId.B)
      MidiAction.DECK_A_CUE -> if (value > 0f) cueDeck(DeckId.A)
      MidiAction.DECK_B_CUE -> if (value > 0f) cueDeck(DeckId.B)
      MidiAction.DECK_A_SYNC -> if (value > 0f) syncDeck(DeckId.A)
      MidiAction.DECK_B_SYNC -> if (value > 0f) syncDeck(DeckId.B)
      MidiAction.DECK_A_VOLUME -> setChannelVolume(DeckId.A, value)
      MidiAction.DECK_B_VOLUME -> setChannelVolume(DeckId.B, value)
      MidiAction.DECK_A_EQ_LOW -> setEqBand(DeckId.A, EqBand.LOW, value * 1.5f)
      MidiAction.DECK_A_EQ_MID -> setEqBand(DeckId.A, EqBand.MID, value * 1.5f)
      MidiAction.DECK_A_EQ_HIGH -> setEqBand(DeckId.A, EqBand.HIGH, value * 1.5f)
      MidiAction.DECK_B_EQ_LOW -> setEqBand(DeckId.B, EqBand.LOW, value * 1.5f)
      MidiAction.DECK_B_EQ_MID -> setEqBand(DeckId.B, EqBand.MID, value * 1.5f)
      MidiAction.DECK_B_EQ_HIGH -> setEqBand(DeckId.B, EqBand.HIGH, value * 1.5f)
    }
  }

  override fun onCleared() {
    super.onCleared()
    samplePadAudioEngine.release()
    stemAudioEngine.release()
    localAudioEngine.releaseAll()
    midiManager.release()
  }
}
