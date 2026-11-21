package com.example.dj.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteOrder
import kotlin.math.abs

data class PcmTrackBuffer(
  val sampleRate: Int,
  val stereoSamples: ShortArray, // interleaved L, R
  val durationSeconds: Int,
  val detectedBpm: Float,
  val waveformPeaks: List<Float>,
) {
  val frameCount: Int
    get() = stereoSamples.size / 2
}

object AudioDecoder {
  private const val TARGET_SAMPLE_RATE = 22050
  private const val MAX_DECODE_SECONDS = 300 // Cap at 5 minutes (~26 MB) to stay memory safe

  fun decodeUriToStereoPcm(context: Context, uri: Uri): PcmTrackBuffer? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      if (uri.scheme == "asset") {
        val assetPath = uri.path?.trimStart('/').orEmpty()
        val opened =
          runCatching {
            context.assets.openFd(assetPath).use { afd ->
              extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
          }.isSuccess
        if (!opened) {
          val cached =
            com.example.dj.data.BundledAndLocalTrackScanner.copyAssetToCache(context, assetPath)
              ?: return null
          extractor.setDataSource(cached.absolutePath)
        }
      } else {
        extractor.setDataSource(context, uri, null)
      }
      var audioTrackIndex = -1
      var format: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          format = f
          break
        }
      }
      if (audioTrackIndex < 0 || format == null) return null

      extractor.selectTrack(audioTrackIndex)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
      var srcSampleRate =
        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
          format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
          44100
        }
      var srcChannels =
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
          format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
          2
        }

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val maxTargetFrames = TARGET_SAMPLE_RATE * MAX_DECODE_SECONDS
      val outStereo = ShortArray(maxTargetFrames * 2)
      var outFrameIndex = 0

      val info = MediaCodec.BufferInfo()
      var sawInputEos = false
      var sawOutputEos = false
      var fracPos = 0.0

      while (!sawOutputEos && outFrameIndex < maxTargetFrames) {
        if (!sawInputEos) {
          val inIdx = codec.dequeueInputBuffer(10_000L)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            if (inBuf != null) {
              val sampleSize = extractor.readSampleData(inBuf, 0)
              if (sampleSize < 0) {
                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                sawInputEos = true
              } else {
                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
              }
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
        if (outIdx >= 0) {
          val outBuf = codec.getOutputBuffer(outIdx)
          if (outBuf != null && info.size > 0) {
            outBuf.position(info.offset)
            outBuf.limit(info.offset + info.size)
            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val numShorts = shortBuf.remaining()
            val chunk = ShortArray(numShorts)
            shortBuf.get(chunk)

            val srcFrames = numShorts / srcChannels.coerceAtLeast(1)
            val step = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            while (fracPos < srcFrames && outFrameIndex < maxTargetFrames) {
              val idx = fracPos.toInt()
              val base = idx * srcChannels
              val l = chunk[base]
              val r = if (srcChannels >= 2 && base + 1 < chunk.size) chunk[base + 1] else l
              outStereo[outFrameIndex * 2] = l
              outStereo[outFrameIndex * 2 + 1] = r
              outFrameIndex++
              fracPos += step
            }
            fracPos -= srcFrames
            if (fracPos < 0.0) fracPos = 0.0
          }
          codec.releaseOutputBuffer(outIdx, false)
          if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            sawOutputEos = true
          }
        } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          val outFmt = codec.outputFormat
          if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          }
          if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          }
        }
      }

      if (outFrameIndex < 1024) return null
      val trimmed = outStereo.copyOf(outFrameIndex * 2)
      val durationSec = (outFrameIndex / TARGET_SAMPLE_RATE).coerceAtLeast(10)

      // Compute mono slice (first 30 seconds) for fast BPM detection
      val bpmFrames = minOf(outFrameIndex, TARGET_SAMPLE_RATE * 30)
      val monoForBpm = ShortArray(bpmFrames) { i ->
        ((trimmed[i * 2].toInt() + trimmed[i * 2 + 1].toInt()) / 2).toShort()
      }
      val detectedBpm = BpmSyncEngine.detectBpmFromPcm(monoForBpm, TARGET_SAMPLE_RATE)

      // Compute 72 normalized waveform peaks
      val peakCount = 72
      val framesPerBucket = (outFrameIndex / peakCount).coerceAtLeast(1)
      val peaks = List(peakCount) { b ->
        val start = b * framesPerBucket
        val end = minOf(outFrameIndex, start + framesPerBucket)
        var maxAmp = 0f
        var stepIdx = start
        val stride = (framesPerBucket / 64).coerceAtLeast(1)
        while (stepIdx < end) {
          val amp =
            maxOf(abs(trimmed[stepIdx * 2].toInt()), abs(trimmed[stepIdx * 2 + 1].toInt())) / 32768f
          if (amp > maxAmp) maxAmp = amp
          stepIdx += stride
        }
        maxAmp.coerceIn(0.15f, 1.0f)
      }

      PcmTrackBuffer(
        sampleRate = TARGET_SAMPLE_RATE,
        stereoSamples = trimmed,
        durationSeconds = durationSec,
        detectedBpm = detectedBpm,
        waveformPeaks = peaks,
      )
    } catch (t: Throwable) {
      Log.w("AudioDecoder", "PCM decode fallback: ${t.message}")
      null
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
    }
  }
}
