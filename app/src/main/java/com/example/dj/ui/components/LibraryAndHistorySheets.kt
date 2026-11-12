package com.example.dj.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dj.input.DjKeyboardShortcuts
import com.example.dj.model.DeckId
import com.example.dj.model.DjTrack
import com.example.dj.model.EqPreset
import com.example.dj.model.SessionHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackLibrarySheet(
  targetDeckId: DeckId,
  referenceTrack: DjTrack,
  tracks: List<DjTrack>,
  sessionHistory: List<SessionHistoryItem>,
  eqPresets: List<EqPreset>,
  accentColor: Color,
  onSelectTrack: (DjTrack) -> Unit,
  onUpdateMetadata: (String, String, String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var smartPlaylistEnabled by remember { mutableStateOf(false) }
  var showCamelotWheel by remember { mutableStateOf(false) }
  var editingTrack by remember { mutableStateOf<DjTrack?>(null) }

  val exportDbLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri: Uri? ->
      if (uri != null) {
        val root = JSONObject()
        val tracksArr = JSONArray()
        tracks.forEach { t ->
          tracksArr.put(
            JSONObject().apply {
              put("id", t.id)
              put("title", t.title)
              put("artist", t.artist)
              put("bpm", t.bpm.toDouble())
              put("musicalKey", t.musicalKey)
              put("genre", t.genre)
              put("durationSeconds", t.durationSeconds)
            }
          )
        }
        val historyArr = JSONArray()
        sessionHistory.forEach { h ->
          historyArr.put(
            JSONObject().apply {
              put("id", h.id)
              put("trackTitle", h.trackTitle)
              put("artist", h.artist)
              put("deckName", h.deckName)
              put("playedAtMs", h.playedAtMs)
            }
          )
        }
        val presetsArr = JSONArray()
        eqPresets.forEach { p ->
          presetsArr.put(
            JSONObject().apply {
              put("name", p.name)
              put("low", p.low.toDouble())
              put("mid", p.mid.toDouble())
              put("high", p.high.toDouble())
            }
          )
        }
        root.put("tracks", tracksArr)
        root.put("sessionHistory", historyArr)
        root.put("eqPresets", presetsArr)
        runCatching {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray())
          }
        }
      }
    }

  val displayedTracks =
    remember(tracks, searchQuery, smartPlaylistEnabled, referenceTrack) {
      val filtered =
        if (searchQuery.isBlank()) tracks
        else {
          val q = searchQuery.trim().lowercase()
          tracks.filter {
            it.title.lowercase().contains(q) ||
              it.artist.lowercase().contains(q) ||
              it.genre.lowercase().contains(q)
          }
        }

      if (!smartPlaylistEnabled) {
        filtered
      } else {
        filtered
          .filter { it.id != referenceTrack.id }
          .sortedByDescending { candidate ->
            val keyScore =
              if (CamelotHelper.isCompatible(referenceTrack.musicalKey, candidate.musicalKey)) 50f
              else 0f
            val bpmScore = (20f - abs(referenceTrack.bpm - candidate.bpm)).coerceAtLeast(0f)
            keyScore + bpmScore
          }
      }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "LOAD TRACK TO ${targetDeckId.label}",
        style = MaterialTheme.typography.titleMedium,
        color = accentColor,
      )

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search title, artist, or genre...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("library_search_bar"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = smartPlaylistEnabled,
          onClick = { smartPlaylistEnabled = !smartPlaylistEnabled },
          label = { Text("Smart Playlist") },
          leadingIcon = {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("smart_playlist_chip"),
        )
        FilterChip(
          selected = showCamelotWheel,
          onClick = { showCamelotWheel = !showCamelotWheel },
          label = { Text("Camelot") },
          leadingIcon = {
            Icon(Icons.Filled.TrackChanges, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("camelot_wheel_chip"),
        )
        FilterChip(
          selected = false,
          onClick = { exportDbLauncher.launch("stemdeck_library_backup.json") },
          label = { Text("Export DB") },
          leadingIcon = {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("export_db_chip"),
        )
      }

      if (showCamelotWheel) {
        CamelotWheelOverlay(
          referenceKey = referenceTrack.musicalKey,
          accentColor = accentColor,
          onDismiss = { showCamelotWheel = false },
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(displayedTracks, key = { it.id }) { track ->
          val harmonic = CamelotHelper.isCompatible(referenceTrack.musicalKey, track.musicalKey)
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectTrack(track)
                  onDismiss()
                }
                .testTag("library_track_${track.id}"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = track.title,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${track.artist} • ${track.genre}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${String.format("%.1f", track.bpm)} BPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                  )
                  Text(
                    text = "${track.musicalKey} (${CamelotHelper.toCamelot(track.musicalKey)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (harmonic) {
                    Text(
                      text = "HARMONIC",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF00E676),
                    )
                  }
                }
              }

              IconButton(
                onClick = { editingTrack = track },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_track_${track.id}"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = "Edit track metadata",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }

  editingTrack?.let { track ->
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    AlertDialog(
      onDismissRequest = { editingTrack = null },
      title = { Text("Edit Track Metadata") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
          OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") })
          OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Album / Genre") })
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateMetadata(track.id, title, artist, genre)
            editingTrack = null
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingTrack = null }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistorySheet(
  history: List<SessionHistoryItem>,
  onDismiss: () -> Unit,
) {
  val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("SESSION PLAYBACK HISTORY", style = MaterialTheme.typography.titleMedium)
      }

      if (history.isEmpty()) {
        Text(
          text = "Press PLAY on either deck to begin logging tracks in this session.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 20.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(history, key = { it.id }) { item ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.trackTitle, style = MaterialTheme.typography.titleSmall)
                  Text(
                    "${item.artist} • ${item.deckName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = formatter.format(Date(item.playedAtMs)),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
fun KeyboardShortcutsDialog(
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium)
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(DjKeyboardShortcuts.catalog) { item ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    RoundedCornerShape(6.dp),
                  )
                  .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp),
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = item.keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("close_shortcuts_dialog")) {
        Text("Done")
      }
    },
  )
}
