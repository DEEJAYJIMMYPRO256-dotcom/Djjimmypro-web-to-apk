package com.example.dj.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.dj.model.SamplePadEffect
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays bundled sample-pad FX assets from `assets/samples/` with per-pad toggle ON/OFF control,
 * natural-completion auto-reset, looping, and live master sampler volume.
 */
class SamplePadAudioEngine(private val context: Context) {
  private val activePlayers = ConcurrentHashMap<String, MediaPlayer>()

  fun isPlaying(padId: String): Boolean = activePlayers.containsKey(padId)

  fun activeCount(): Int = activePlayers.size

  fun playPad(
    pad: SamplePadEffect,
    volume: Float,
    loop: Boolean,
    onCompleted: (String) -> Unit,
  ): Boolean {
    stopPad(pad.id)
    val clampedVol = volume.coerceIn(0f, 1f)
    return runCatching {
      val player =
        MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          try {
            context.assets.openFd("samples/${pad.assetFileName}").use { afd ->
              setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
          } catch (_: Exception) {
            val cached = ensureCachedSampleFile(pad.assetFileName)
            setDataSource(cached.absolutePath)
          }
          isLooping = loop
          setVolume(clampedVol, clampedVol)
          setOnCompletionListener { mp ->
            if (!loop) {
              activePlayers.remove(pad.id, mp)
              runCatching {
                mp.reset()
                mp.release()
              }
              onCompleted(pad.id)
            }
          }
          setOnErrorListener { mp, _, _ ->
            activePlayers.remove(pad.id, mp)
            runCatching {
              mp.reset()
              mp.release()
            }
            onCompleted(pad.id)
            true
          }
          prepare()
          start()
        }
      activePlayers[pad.id] = player
      true
    }.onFailure { err ->
      Log.w("SamplePadAudioEngine", "Failed to start pad ${pad.assetFileName}: ${err.message}")
    }.getOrDefault(false)
  }

  fun stopPad(padId: String) {
    activePlayers.remove(padId)?.let { mp ->
      runCatching {
        if (mp.isPlaying) mp.stop()
        mp.reset()
        mp.release()
      }
    }
  }

  fun stopAll() {
    val keys = activePlayers.keys.toList()
    keys.forEach { stopPad(it) }
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    activePlayers.values.forEach { mp ->
      runCatching { mp.setVolume(clamped, clamped) }
    }
  }

  fun setLoopAll(loop: Boolean) {
    activePlayers.values.forEach { mp ->
      runCatching { mp.isLooping = loop }
    }
  }

  fun release() {
    stopAll()
  }

  private fun ensureCachedSampleFile(fileName: String): File {
    val dir = File(context.cacheDir, "sample_pads").apply { mkdirs() }
    val outFile = File(dir, fileName)
    if (!outFile.exists() || outFile.length() == 0L) {
      context.assets.open("samples/$fileName").use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
      }
    }
    return outFile
  }
}
