package com.example.dj.data

import com.example.dj.model.DefaultTracks
import com.example.dj.model.DjTrack
import com.example.dj.model.EqPreset
import com.example.dj.model.SessionHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class TrackLibraryRepository(
  private val trackDao: TrackDao,
  private val historyDao: SessionHistoryDao,
  private val eqPresetDao: EqPresetDao,
) {
  val allTracks: Flow<List<DjTrack>> =
    trackDao.getAllTracks().map { entities -> entities.map { it.toDomain() } }

  val sessionHistory: Flow<List<SessionHistoryItem>> =
    historyDao.getAllHistory().map { list ->
      list.map {
        SessionHistoryItem(
          id = it.id,
          trackTitle = it.trackTitle,
          artist = it.artist,
          deckName = it.deckName,
          playedAtMs = it.playedAtMs,
        )
      }
    }

  val eqPresets: Flow<List<EqPreset>> =
    eqPresetDao.getAllPresets().map { list ->
      list.map { EqPreset(id = it.id, name = it.name, low = it.low, mid = it.mid, high = it.high) }
    }

  suspend fun seedDefaultsIfNeeded(bundledAssetTracks: List<DjTrack> = emptyList()) {
    val combined = DefaultTracks.tracks + bundledAssetTracks
    val now = System.currentTimeMillis()
    val defaultEntities =
      combined.mapIndexed { index, track ->
        track.toEntity(addedAtMs = now - (index * 1000L))
      }
    trackDao.insertAllIgnore(defaultEntities)

    if (eqPresetDao.countPresets() == 0) {
      eqPresetDao.insertAllIgnore(
        DefaultTracks.defaultEqPresets.map {
          EqPresetEntity(id = it.id, name = it.name, low = it.low, mid = it.mid, high = it.high)
        }
      )
    }
  }

  suspend fun upsertTracks(tracks: List<DjTrack>) {
    if (tracks.isEmpty()) return
    val now = System.currentTimeMillis()
    tracks.forEachIndexed { idx, track ->
      trackDao.insertTrack(track.toEntity(addedAtMs = now - idx))
    }
  }

  suspend fun upsertTrack(track: DjTrack) {
    trackDao.insertTrack(track.toEntity())
  }

  suspend fun deleteTrack(id: String) {
    trackDao.deleteTrackById(id)
  }

  suspend fun scanAndPopulateMissingBeatGrids() {
    val missing = trackDao.getTracksMissingBeatGrid()
    for (entity in missing) {
      val grid = DjTrack.computeBeatGrid(entity.bpm, entity.durationSeconds)
      trackDao.insertTrack(entity.copy(beatGridJson = serializeFloatList(grid)))
    }
  }

  suspend fun logPlayback(track: DjTrack, deckLabel: String) {
    historyDao.insertHistory(
      SessionHistoryEntity(
        trackTitle = track.title,
        artist = track.artist,
        deckName = deckLabel,
        playedAtMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun saveEqPreset(name: String, low: Float, mid: Float, high: Float) {
    eqPresetDao.insertPreset(EqPresetEntity(name = name.trim(), low = low, mid = mid, high = high))
  }

  private fun TrackEntity.toDomain(): DjTrack {
    val parsedGrid =
      parseFloatList(beatGridJson).ifEmpty { DjTrack.computeBeatGrid(bpm, durationSeconds) }
    val parsedPeaks =
      parseFloatList(waveformPeaksJson).ifEmpty { DjTrack.generateWaveform(id, 72) }
    return DjTrack(
      id = id,
      title = title,
      artist = artist,
      bpm = bpm,
      musicalKey = musicalKey,
      genre = genre,
      durationSeconds = durationSeconds,
      baseFreqHz = baseFreqHz,
      localUri = filePath,
      waveformPeaks = parsedPeaks,
      beatGrid = parsedGrid,
    )
  }

  private fun DjTrack.toEntity(addedAtMs: Long = System.currentTimeMillis()): TrackEntity {
    return TrackEntity(
      id = id,
      title = title,
      artist = artist,
      bpm = bpm,
      musicalKey = musicalKey,
      genre = genre,
      durationSeconds = durationSeconds,
      baseFreqHz = baseFreqHz,
      filePath = localUri,
      beatGridJson = serializeFloatList(beatGrid),
      waveformPeaksJson = serializeFloatList(waveformPeaks),
      addedAtMs = addedAtMs,
    )
  }

  private fun serializeFloatList(values: List<Float>): String {
    val array = JSONArray()
    values.forEach { array.put(it.toDouble()) }
    return array.toString()
  }

  private fun parseFloatList(json: String): List<Float> {
    if (json.isBlank() || json == "[]") return emptyList()
    return runCatching {
      val array = JSONArray(json)
      List(array.length()) { idx -> array.getDouble(idx).toFloat() }
    }.getOrDefault(emptyList())
  }
}
