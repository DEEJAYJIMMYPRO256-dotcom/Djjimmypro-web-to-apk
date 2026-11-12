package com.example.dj.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dj.audio.AudioEngine
import com.example.dj.audio.BpmSyncEngine
import com.example.dj.audio.StemAudioEngine
import com.example.dj.data.TrackLibraryDatabase
import com.example.dj.data.TrackLibraryRepository
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.input.DjShortcutCommand
import com.example.dj.midi.MidiAction
import com.example.dj.midi.MidiControllerManager
import com.example.dj.model.DeckEffectsState
import com.example.dj.model.DeckId
import com.example.dj.model.DeckState
import com.example.dj.model.DjTrack
import com.example.dj.model.DjUiState
import com.example.dj.model.EqBand
import com.example.dj.model.EqPreset
import com.example.dj.model.HotCue
import com.example.dj.model.KeyboardShortcutFeedback
import com.example.dj.model.LoopState
import com.example.dj.model.StemType
import java.io.File
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
  val midiManager = MidiControllerManager(application)

  private val tapTrackerA = BpmSyncEngine.TapTempoTracker()
  private val tapTrackerB = BpmSyncEngine.TapTempoTracker()

  private val _uiState = MutableStateFlow(DjUiState())
  val uiState: StateFlow<DjUiState> = _uiState.asStateFlow()

  private var autoMixTargetCrossfader: Float? = null
  private var feedbackClearJob: Job? = null

  private val cueColors = listOf(0xFF00E5FF, 0xFFFF7A00, 0xFF00E676, 0xFFFF3DF0)
  private val loopBeatOptions = listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f)
  private val autoMixDurations = listOf(4, 8, 12, 16)

  init {
    viewModelScope.launch(Dispatchers.IO) {
      repository.seedDefaultsIfNeeded()
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

        var updatedA = advanceDeck(state.deckA, dtSeconds)
        var updatedB = advanceDeck(state.deckB, dtSeconds)
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

  private fun advanceDeck(deck: DeckState, dtSeconds: Float): DeckState {
    if (!deck.isPlaying) return deck
    val duration = deck.track.durationSeconds.coerceAtLeast(1).toFloat()
    val speed = (1f + deck.tempoOffset).coerceAtLeast(0.25f)
    var nextProgress = deck.playheadProgress + (dtSeconds * speed) / duration
    if (deck.loop.isActive && deck.loop.endProgress > deck.loop.startProgress) {
      if (nextProgress >= deck.loop.endProgress) {
        nextProgress = deck.loop.startProgress
        if (localAudioEngine.isLoaded(deck.deckId)) {
          localAudioEngine.seekTo(deck.deckId, nextProgress)
        }
      }
    } else if (nextProgress >= 1f) {
      nextProgress -= 1f
    }
    val nextRot = (deck.jogRotationDeg + dtSeconds * 198f * speed) % 360f
    return deck.copy(playheadProgress = nextProgress, jogRotationDeg = nextRot)
  }

  private fun syncAudioEngines() {
    val state = _uiState.value
    val hasLocalA = localAudioEngine.isLoaded(DeckId.A)
    val hasLocalB = localAudioEngine.isLoaded(DeckId.B)
    stemAudioEngine.updateMixState(
      deckA = state.deckA,
      deckB = state.deckB,
      crossfader = state.crossfader,
      curve = state.crossfaderCurve,
      deckAHasLocalAudio = hasLocalA,
      deckBHasLocalAudio = hasLocalB,
    )
    val (gainA, gainB) = state.crossfaderCurve.computeGains(state.crossfader)
    if (hasLocalA) {
      localAudioEngine.setVolume(
        DeckId.A,
        (gainA * state.deckA.trimGain * state.deckA.channelVolume).coerceIn(0f, 1f),
      )
    }
    if (hasLocalB) {
      localAudioEngine.setVolume(
        DeckId.B,
        (gainB * state.deckB.trimGain * state.deckB.channelVolume).coerceIn(0f, 1f),
      )
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
    if (localAudioEngine.isLoaded(deckId)) {
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
    updateDeck(deckId) { it.copy(isPlaying = false, playheadProgress = target) }
    if (localAudioEngine.isLoaded(deckId)) {
      localAudioEngine.pause(deckId)
      localAudioEngine.seekTo(deckId, target)
    }
  }

  fun syncDeck(deckId: DeckId) {
    val state = _uiState.value
    val source = if (deckId == DeckId.A) state.deckA else state.deckB
    val target = if (deckId == DeckId.A) state.deckB else state.deckA
    val offset =
      BpmSyncEngine.calculateSyncOffset(
        sourceBaseBpm = source.track.bpm,
        targetEffectiveBpm = target.effectiveBpm,
        maxRange = source.pitchRange.maxOffset,
      )
    updateDeck(deckId) {
      it.copy(tempoOffset = offset, playheadProgress = target.playheadProgress)
    }
    if (localAudioEngine.isLoaded(deckId)) {
      localAudioEngine.seekTo(deckId, target.playheadProgress)
    }
  }

  fun loadTrack(deckId: DeckId, track: DjTrack) {
    localAudioEngine.unloadDeck(deckId)
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
      )
    }
    if (!track.localUri.isNullOrBlank()) {
      runCatching {
        val uri = Uri.parse(track.localUri)
        localAudioEngine.loadFile(deckId, getApplication(), uri)
      }
    }
  }

  fun loadLocalAudioUri(deckId: DeckId, uri: Uri) {
    val context = getApplication<Application>()
    var title = "Local Track"
    var artist = "Local File"
    var durationSec = 180

    runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIdx >= 0) {
          title = cursor.getString(nameIdx).substringBeforeLast('.')
        }
      }
    }

    runCatching {
      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(context, uri)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
        title = it
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
        artist = it
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
        durationSec = (ms / 1000L).toInt().coerceAtLeast(10)
      }
      retriever.release()
    }

    val trackId = "local_${uri.toString().hashCode()}"
    val bpm = 124f
    val track =
      DjTrack(
        id = trackId,
        title = title,
        artist = artist,
        bpm = bpm,
        musicalKey = "Am",
        genre = "Local Audio",
        durationSeconds = durationSec,
        baseFreqHz = 220f,
        localUri = uri.toString(),
        waveformPeaks = DjTrack.generateWaveform(trackId, 64),
        beatGrid = DjTrack.computeBeatGrid(bpm, durationSec),
      )

    viewModelScope.launch(Dispatchers.IO) { repository.upsertTrack(track) }
    loadTrack(deckId, track)
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

  fun toggleStemMute(deckId: DeckId, stem: StemType) {
    updateDeck(deckId) { deck ->
      val current = deck.stems[stem] ?: return@updateDeck deck
      deck.copy(stems = deck.stems + (stem to current.copy(isMuted = !current.isMuted)))
    }
  }

  fun toggleStemSolo(deckId: DeckId, stem: StemType) {
    updateDeck(deckId) { deck ->
      val current = deck.stems[stem] ?: return@updateDeck deck
      deck.copy(stems = deck.stems + (stem to current.copy(isSolo = !current.isSolo)))
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

  fun setTrimGain(deckId: DeckId, trim: Float) {
    updateDeck(deckId) { it.copy(trimGain = trim.coerceIn(0.2f, 1.8f)) }
  }

  fun setChannelVolume(deckId: DeckId, volume: Float) {
    updateDeck(deckId) { it.copy(channelVolume = volume.coerceIn(0f, 1f)) }
  }

  fun setTempoOffset(deckId: DeckId, offset: Float) {
    updateDeck(deckId) { deck ->
      val max = deck.pitchRange.maxOffset
      deck.copy(tempoOffset = offset.coerceIn(-max, max))
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

  fun tapTempo(deckId: DeckId) {
    val tracker = if (deckId == DeckId.A) tapTrackerA else tapTrackerB
    val detected = tracker.recordTap() ?: return
    updateDeck(deckId) { deck ->
      val updatedTrack =
        deck.track.copy(
          bpm = detected,
          beatGrid = DjTrack.computeBeatGrid(detected, deck.track.durationSeconds),
        )
      deck.copy(track = updatedTrack, tempoOffset = 0f)
    }
  }

  fun scrubDeck(deckId: DeckId, deltaProgress: Float) {
    updateDeck(deckId) { deck ->
      val next = (deck.playheadProgress + deltaProgress).coerceIn(0f, 1f)
      if (localAudioEngine.isLoaded(deckId)) {
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
    if (!deck.isPlaying) {
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
        if (localAudioEngine.isLoaded(deckId)) {
          localAudioEngine.seekTo(deckId, existing.progress)
        }
        deck.copy(playheadProgress = existing.progress)
      } else {
        val snapped = deck.snapToBeatGrid(deck.playheadProgress)
        val cue = HotCue(slot = slot, progress = snapped, colorHex = cueColors[(slot - 1) % cueColors.size])
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
    stemAudioEngine.release()
    localAudioEngine.releaseAll()
    midiManager.release()
  }
}
