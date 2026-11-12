package com.example.dj.audio

import kotlin.math.abs

object BpmSyncEngine {

  /**
   * Estimates BPM from mono 16-bit PCM audio samples using 10ms energy windows and onset detection.
   * Normalized into the standard 60..180 BPM range.
   */
  fun detectBpmFromPcm(samples: ShortArray, sampleRate: Int): Float {
    if (samples.isEmpty() || sampleRate <= 0) return 124f
    val windowSize = (sampleRate / 100).coerceAtLeast(64) // 10ms
    val numWindows = samples.size / windowSize
    if (numWindows < 10) return 124f

    val energies = FloatArray(numWindows)
    var avgEnergy = 0f
    for (w in 0 until numWindows) {
      var sum = 0f
      val start = w * windowSize
      for (i in 0 until windowSize) {
        val v = samples[start + i] / 32768f
        sum += v * v
      }
      val e = sum / windowSize
      energies[w] = e
      avgEnergy += e
    }
    avgEnergy /= numWindows

    val threshold = avgEnergy * 1.45f
    val onsetTimesSec = mutableListOf<Float>()
    var lastOnsetWindow = -15 // 150ms refractory
    for (w in 1 until numWindows - 1) {
      if (energies[w] > threshold &&
        energies[w] > energies[w - 1] &&
        energies[w] >= energies[w + 1] &&
        w - lastOnsetWindow >= 15
      ) {
        onsetTimesSec.add(w * 0.01f)
        lastOnsetWindow = w
      }
    }

    if (onsetTimesSec.size < 2) return 124f
    val intervals = mutableListOf<Float>()
    for (i in 1 until onsetTimesSec.size) {
      val diff = onsetTimesSec[i] - onsetTimesSec[i - 1]
      if (diff in 0.25f..1.5f) {
        intervals.add(diff)
      }
    }
    if (intervals.isEmpty()) return 124f
    intervals.sort()
    val medianInterval = intervals[intervals.size / 2]
    var bpm = 60f / medianInterval
    while (bpm < 60f) bpm *= 2f
    while (bpm > 180f) bpm /= 2f
    return bpm
  }

  /**
   * Calculates pitch/tempo offset required for sourceBaseBpm to match targetEffectiveBpm
   * (including half-time / double-time octave matching), clamped to maxRange.
   */
  fun calculateSyncOffset(
    sourceBaseBpm: Float,
    targetEffectiveBpm: Float,
    maxRange: Float = 0.08f,
  ): Float {
    if (sourceBaseBpm <= 0f || targetEffectiveBpm <= 0f) return 0f
    val candidates = listOf(targetEffectiveBpm, targetEffectiveBpm * 2f, targetEffectiveBpm * 0.5f)
    val bestTarget = candidates.minByOrNull { abs(it - sourceBaseBpm) } ?: targetEffectiveBpm
    val rawOffset = (bestTarget / sourceBaseBpm) - 1f
    return rawOffset.coerceIn(-maxRange, maxRange)
  }

  class TapTempoTracker {
    private val timestamps = mutableListOf<Long>()

    fun recordTap(nowMs: Long = System.currentTimeMillis()): Float? {
      if (timestamps.isNotEmpty() && nowMs - timestamps.last() > 2000L) {
        timestamps.clear()
      }
      timestamps.add(nowMs)
      if (timestamps.size < 2) return null
      if (timestamps.size > 8) {
        timestamps.removeAt(0)
      }
      val intervals = mutableListOf<Long>()
      for (i in 1 until timestamps.size) {
        intervals.add(timestamps[i] - timestamps[i - 1])
      }
      val avgIntervalMs = intervals.average().toFloat().coerceAtLeast(250f)
      return (60_000f / avgIntervalMs).coerceIn(60f, 190f)
    }

    fun reset() {
      timestamps.clear()
    }
  }
}
