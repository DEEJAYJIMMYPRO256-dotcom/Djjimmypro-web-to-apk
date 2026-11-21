package com.example.dj.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.dj.model.DeckId
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles concurrent local audio file loading, playback, seeking, and gain mixing for Deck A and Deck B.
 */
class AudioEngine {
  private val players = ConcurrentHashMap<DeckId, MediaPlayer>()
  private val preparedDecks = ConcurrentHashMap<DeckId, Boolean>()
  private val deckPitches = ConcurrentHashMap<DeckId, Float>()
  private val deckVolumes = ConcurrentHashMap<DeckId, Float>()

  fun loadFile(deckId: DeckId, context: Context, uri: Uri, onPrepared: ((Int) -> Unit)? = null) {
    releaseDeck(deckId)
    runCatching {
      val player =
        MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          if (uri.scheme == "asset") {
            val assetPath = uri.path?.trimStart('/').orEmpty()
            val setViaAfd =
              runCatching {
                context.assets.openFd(assetPath).use { afd ->
                  setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
              }.isSuccess
            if (!setViaAfd) {
              val cached =
                com.example.dj.data.BundledAndLocalTrackScanner.copyAssetToCache(context, assetPath)
                  ?: error("Missing bundled audio asset: $assetPath")
              setDataSource(cached.absolutePath)
            }
          } else {
            setDataSource(context, uri)
          }
          isLooping = true
          setOnPreparedListener { mp ->
            preparedDecks[deckId] = true
            val cachedVol = (deckVolumes[deckId] ?: 1f).coerceIn(0f, 1f)
            runCatching { mp.setVolume(cachedVol, cachedVol) }
            onPrepared?.invoke((mp.duration / 1000).coerceAtLeast(10))
          }
          setOnErrorListener { _, what, extra ->
            Log.w("AudioEngine", "MediaPlayer error deck=$deckId what=$what extra=$extra")
            preparedDecks[deckId] = false
            true
          }
          prepareAsync()
        }
      players[deckId] = player
    }.onFailure { e ->
      Log.e("AudioEngine", "Failed to load audio URI for $deckId", e)
    }
  }

  fun unloadDeck(deckId: DeckId) {
    releaseDeck(deckId)
  }

  fun play(deckId: DeckId) {
    val player = players[deckId] ?: return
    if (preparedDecks[deckId] == true && !player.isPlaying) {
      runCatching {
        val cachedVol = (deckVolumes[deckId] ?: 1f).coerceIn(0f, 1f)
        player.setVolume(cachedVol, cachedVol)
        player.start()
        val pitch = deckPitches[deckId] ?: 1f
        if (kotlin.math.abs(pitch - 1f) > 0.005f) {
          player.playbackParams = player.playbackParams.setPitch(pitch.coerceIn(0.5f, 2.0f))
        }
      }
    }
  }

  fun pause(deckId: DeckId) {
    val player = players[deckId] ?: return
    if (preparedDecks[deckId] == true && player.isPlaying) {
      runCatching { player.pause() }
    }
  }

  fun isLoaded(deckId: DeckId): Boolean = players.containsKey(deckId) && preparedDecks[deckId] == true

  fun seekTo(deckId: DeckId, progress: Float) {
    val player = players[deckId] ?: return
    if (preparedDecks[deckId] == true) {
      val duration = player.duration.coerceAtLeast(1)
      val targetMs = (progress.coerceIn(0f, 1f) * duration).toInt()
      runCatching { player.seekTo(targetMs) }
    }
  }

  fun setVolume(deckId: DeckId, gain: Float) {
    val clamped = gain.coerceIn(0f, 1f)
    deckVolumes[deckId] = clamped
    val player = players[deckId] ?: return
    if (preparedDecks[deckId] == true) {
      runCatching { player.setVolume(clamped, clamped) }
    }
  }

  fun setPitch(deckId: DeckId, pitch: Float) {
    val clamped = pitch.coerceIn(0.5f, 2.0f)
    deckPitches[deckId] = clamped
    val player = players[deckId] ?: return
    if (preparedDecks[deckId] == true && player.isPlaying) {
      runCatching {
        player.playbackParams = player.playbackParams.setPitch(clamped)
      }
    }
  }

  private fun releaseDeck(deckId: DeckId) {
    preparedDecks[deckId] = false
    deckVolumes.remove(deckId)
    players.remove(deckId)?.let { mp ->
      runCatching {
        if (mp.isPlaying) mp.stop()
        mp.reset()
        mp.release()
      }
    }
  }

  fun releaseAll() {
    DeckId.entries.forEach { releaseDeck(it) }
  }
}
