package com.example.dj.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.dj.model.DjTrack
import com.example.dj.model.SamplePadCatalog
import java.io.File
import kotlin.math.abs

/**
 * Discovers all bundled song assets (.mp3 / .m4a / .wav / .aac) inside assets/samples/
 * and extracts rich ID3/file metadata for Storage Access Framework (SAF) single files
 * and entire folder trees (`OpenDocumentTree`) without broad storage permissions.
 */
object BundledAndLocalTrackScanner {
  private val songExtensions = setOf("mp3", "m4a", "wav", "aac", "flac", "opus")
  private val camelotKeysPool = listOf("Am", "Dm", "Gm", "Cm", "Fm", "Em", "Bm", "C", "G", "D", "F")
  private val baseFreqPool =
    mapOf(
      "Am" to 220.0f,
      "Dm" to 293.66f,
      "Gm" to 196.0f,
      "Cm" to 261.63f,
      "Fm" to 174.61f,
      "Em" to 164.81f,
      "Bm" to 246.94f,
      "C" to 261.63f,
      "G" to 196.0f,
      "D" to 293.66f,
      "F" to 174.61f,
    )

  private data class CuratedInfo(
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Float,
    val key: String,
  )

  private val curatedOverrides: Map<String, CuratedInfo> =
    mapOf(
      "Tea_-_Rema__Redrum_Remix_Edit_OpdBeats___HD__128k__702d4e46.mp3" to
        CuratedInfo("Tea (Redrum Remix)", "Rema • OpdBeats", "Afrobeats", 118f, "Am"),
      "Chet_Back_By_dj_Jimmy_pro_256_Effects_fa9dab8f.mp3" to
        CuratedInfo("Chet Back (Effects Mix)", "DJ Jimmy Pro 256", "DJ Mix", 126f, "Dm"),
      "DEEJAY-JIMMY-PRO-2026-11-16-16-07-DJ-JIMMY_56800d27.mp3" to
        CuratedInfo("DJ Jimmy Club Intro", "DJ Jimmy Pro 256", "DJ Tag", 128f, "Am"),
      "DEEJAY-JIMMY-PRO-2026-11-16-16-06-JIMMY-PRO.-UG_708fe1c1.mp3" to
        CuratedInfo("Jimmy Pro UG Anthem", "DJ Jimmy Pro UG", "Afro Club", 124f, "Gm"),
      "DEEJAY-JIMMY-PRO-2026-11-16-16-06-JIMMY-PRO_00521137.mp3" to
        CuratedInfo("Jimmy Pro Signature", "DJ Jimmy Pro", "DJ Mix", 126f, "Cm"),
      "Wigman-10-2026-11-16-15-55-You_re-listening-to-the-Old-School-Show_-droppin_aa72306c.mp3" to
        CuratedInfo("Old School Show Mix", "Wigman 10", "Old School", 104f, "Fm"),
      "Wigman-10-2026-11-16-15-58-agwater-booster-DJ_s__058ebb06.mp3" to
        CuratedInfo("Agwater Booster DJs", "Wigman 10", "Afro Mix", 122f, "Dm"),
      "Wigman-10-2026-11-16-16-02-agwater-booster-DJ_s_-entertainment-it_s-dj-Jimm_768c24ae.mp3" to
        CuratedInfo("Booster Entertainment", "Wigman 10 • DJ Jimmy", "Afro Mix", 125f, "Am"),
      "Wigman-10-2026-11-16-16-04-booster_s-deejays-entertainment-it_s-dj-Jimmy-pr_a7bf0482.mp3" to
        CuratedInfo("Boosters 256 Club", "Wigman 10 • DJ Jimmy", "Club Mix", 126f, "Gm"),
      "Ian_pro-20260109174934480Z_a0e62c0a.mp3" to
        CuratedInfo("DJ Alvin Pro Set", "DJ Alvin • Ian Pro", "Bongo / Afro", 124f, "Dm"),
      "Ian_pro-20260109174730832Z_5704e8fd.mp3" to
        CuratedInfo("Scratch General Drop", "Ian Pro", "Scratch", 120f, "Am"),
    )

  fun isSongAssetFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in songExtensions
  }

  fun discoverBundledSongTracks(context: Context): List<DjTrack> {
    val assetFiles =
      runCatching { context.assets.list("samples")?.toList().orEmpty() }
        .getOrDefault(emptyList())
        .filter { isSongAssetFile(it) }
        .sorted()

    val catalogByFile = SamplePadCatalog.allPads.associateBy { it.assetFileName }

    return assetFiles.mapIndexed { idx, fileName ->
      val override = curatedOverrides[fileName]
      val padMeta = catalogByFile[fileName]

      var title = override?.title ?: padMeta?.title ?: cleanDisplayName(fileName)
      var artist =
        override?.artist
          ?: when {
            fileName.startsWith("AUD-", ignoreCase = true) -> "DJ Jimmy Crate"
            else -> "Serato Crate"
          }
      var genre = override?.genre ?: padMeta?.bank?.label ?: "Club Audio"
      var durationSec = 45

      runCatching {
        context.assets.openFd("samples/$fileName").use { afd ->
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
              ?.takeIf { it.isNotBlank() }
              ?.let { title = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
              ?.takeIf { it.isNotBlank() }
              ?.let { artist = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
              ?.takeIf { it.isNotBlank() }
              ?.let { genre = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
              ?.toLongOrNull()
              ?.let { ms -> durationSec = (ms / 1000L).toInt().coerceAtLeast(10) }
          } finally {
            runCatching { retriever.release() }
          }
        }
      }

      val hash = abs(fileName.hashCode())
      val key = override?.key ?: camelotKeysPool[hash % camelotKeysPool.size]
      val bpm = override?.bpm ?: (116f + (hash % 15))
      val baseFreq = baseFreqPool[key] ?: 220f
      val trackId = "asset_${fileName.substringBeforeLast('.')}"

      DjTrack(
        id = trackId,
        title = title,
        artist = artist,
        bpm = bpm,
        musicalKey = key,
        genre = genre,
        durationSeconds = durationSec,
        baseFreqHz = baseFreq,
        localUri = "asset:///samples/$fileName",
        waveformPeaks = DjTrack.generateWaveform(trackId, 72),
        beatGrid = DjTrack.computeBeatGrid(bpm, durationSec),
      )
    }
  }

  fun extractTrackFromUri(context: Context, uri: Uri): DjTrack {
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }

    var fileNameTitle = "Local Track"
    runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIdx >= 0) {
          val rawName = cursor.getString(nameIdx) ?: "Local Track"
          fileNameTitle = cleanDisplayName(rawName)
        }
      }
    }

    // Parse "Artist - Title" from filename if available
    var title = fileNameTitle
    var artist = "Local Library"
    if (fileNameTitle.contains(" - ")) {
      val parts = fileNameTitle.split(" - ", limit = 2)
      if (parts[0].isNotBlank() && parts[1].isNotBlank()) {
        artist = parts[0].trim()
        title = parts[1].trim()
      }
    }

    var genre = "Local Audio"
    var durationSec = 180
    var bpm = 124f

    runCatching {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
          ?.takeIf { it.isNotBlank() }
          ?.let { title = it.trim() }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
          ?.takeIf { it.isNotBlank() }
          ?.let { artist = it.trim() }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
          ?.takeIf { it.isNotBlank() }
          ?.let { genre = it.trim() }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull()
          ?.let { ms -> durationSec = (ms / 1000L).toInt().coerceAtLeast(10) }
      } finally {
        runCatching { retriever.release() }
      }
    }

    val hash = abs(uri.toString().hashCode())
    val key = camelotKeysPool[hash % camelotKeysPool.size]
    val trackId = "local_${uri.toString().hashCode()}"

    return DjTrack(
      id = trackId,
      title = title,
      artist = artist,
      bpm = bpm,
      musicalKey = key,
      genre = genre,
      durationSeconds = durationSec,
      baseFreqHz = baseFreqPool[key] ?: 220f,
      localUri = uri.toString(),
      waveformPeaks = DjTrack.generateWaveform(trackId, 72),
      beatGrid = DjTrack.computeBeatGrid(bpm, durationSec),
    )
  }

  /**
   * Zero-permission recursive SAF folder traversal (`OpenDocumentTree`) that collects all
   * playable audio URIs inside the user-selected folder and its subfolders.
   */
  fun scanDocumentTreeAudioUris(context: Context, treeUri: Uri, maxFiles: Int = 250): List<Uri> {
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }

    val results = mutableListOf<Uri>()
    val queue = ArrayDeque<String>()
    val rootDocId =
      runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
    queue.addLast(rootDocId)

    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      )

    while (queue.isNotEmpty() && results.size < maxFiles) {
      val parentId = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
      runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
          val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          while (cursor.moveToNext() && results.size < maxFiles) {
            if (idCol < 0) continue
            val docId = cursor.getString(idCol) ?: continue
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
            when {
              mime == DocumentsContract.Document.MIME_TYPE_DIR -> queue.addLast(docId)
              mime.startsWith("audio/") || isSongAssetFile(name) -> {
                results.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
              }
            }
          }
        }
      }
    }
    return results
  }

  fun copyAssetToCache(context: Context, assetRelativePath: String): File? {
    val clean = assetRelativePath.trimStart('/')
    val outFile = File(context.cacheDir, "asset_audio_${clean.replace('/', '_')}")
    if (outFile.exists() && outFile.length() > 0L) return outFile
    return runCatching {
      context.assets.open(clean).use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
      }
      outFile
    }.getOrNull()
  }

  private fun cleanDisplayName(rawFileName: String): String {
    val withoutExt = rawFileName.substringBeforeLast('.')
    return withoutExt
      .replace(Regex("_[0-9a-f]{6,10}$", RegexOption.IGNORE_CASE), "")
      .replace('_', ' ')
      .replace(Regex("\\s+"), " ")
      .trim()
      .ifBlank { "Track" }
  }
}
