package com.example.dj.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.DeckId
import com.example.dj.model.DjTrack
import com.example.dj.model.LibraryBottomTab
import com.example.dj.model.LibraryCrate
import com.example.dj.model.LibrarySortColumn
import com.example.dj.model.MixerTheme
import com.example.dj.model.SessionHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SeratoLibraryDock(
  tracks: List<DjTrack>,
  sessionHistory: List<SessionHistoryItem>,
  prepareTrackIds: Set<String>,
  deckATrack: DjTrack,
  deckBTrack: DjTrack,
  selectedCrate: LibraryCrate,
  bottomTab: LibraryBottomTab,
  searchQuery: String,
  sortColumn: LibrarySortColumn,
  sortAscending: Boolean,
  isRefreshing: Boolean,
  mixerTheme: MixerTheme,
  onSelectCrate: (LibraryCrate) -> Unit,
  onSelectBottomTab: (LibraryBottomTab) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSearchFocusChanged: (Boolean) -> Unit,
  onSortColumnClick: (LibrarySortColumn) -> Unit,
  onLoadToDeck: (DeckId, DjTrack) -> Unit,
  onTogglePrepare: (String) -> Unit,
  onImportFiles: () -> Unit,
  onImportFolder: () -> Unit = {},
  onRefreshLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val displayedTracks =
    remember(
      tracks,
      prepareTrackIds,
      selectedCrate,
      bottomTab,
      searchQuery,
      sortColumn,
      sortAscending,
      deckATrack.musicalKey,
    ) {
      var list = tracks
      if (bottomTab == LibraryBottomTab.PREPARE) {
        list = list.filter { it.id in prepareTrackIds }
      } else if (bottomTab == LibraryBottomTab.FILES || selectedCrate == LibraryCrate.LOCAL) {
        val local = list.filter { !it.localUri.isNullOrBlank() }
        if (selectedCrate == LibraryCrate.LOCAL || bottomTab == LibraryBottomTab.FILES) {
          list = local
        }
      }

      if (selectedCrate == LibraryCrate.HARMONIC) {
        list = list.filter { CamelotHelper.isCompatible(deckATrack.musicalKey, it.musicalKey) }
      }

      if (searchQuery.isNotBlank()) {
        val q = searchQuery.trim().lowercase()
        list =
          list.filter {
            it.title.lowercase().contains(q) ||
              it.artist.lowercase().contains(q) ||
              it.genre.lowercase().contains(q) ||
              it.musicalKey.lowercase().contains(q)
          }
      }

      val sorted =
        when (sortColumn) {
          LibrarySortColumn.INDEX -> list
          LibrarySortColumn.TITLE -> list.sortedBy { it.title.lowercase() }
          LibrarySortColumn.ARTIST -> list.sortedBy { it.artist.lowercase() }
          LibrarySortColumn.BPM -> list.sortedBy { it.bpm }
          LibrarySortColumn.KEY -> list.sortedBy { CamelotHelper.toCamelot(it.musicalKey) }
          LibrarySortColumn.LENGTH -> list.sortedBy { it.durationSeconds }
        }
      if (sortAscending) sorted else sorted.reversed()
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xFF090D14),
    modifier = modifier.fillMaxWidth().border(1.dp, Color(0xFF232E42), RoundedCornerShape(10.dp)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 175.dp, max = 235.dp),
      ) {
        // Left Crate Sidebar
        Column(
          modifier =
            Modifier.width(102.dp)
              .fillMaxHeight()
              .background(Color(0xFF0C111A))
              .border(1.dp, Color(0xFF1A2232))
              .padding(vertical = 4.dp),
        ) {
          LibraryCrate.entries.forEach { crate ->
            val selected = crate == selectedCrate
            val icon =
              when (crate) {
                LibraryCrate.ALL -> Icons.Filled.Star
                LibraryCrate.TITLE -> Icons.Filled.LibraryMusic
                LibraryCrate.ARTISTS -> Icons.Filled.Person
                LibraryCrate.GENRES -> Icons.Filled.QueueMusic
                LibraryCrate.HARMONIC -> Icons.Filled.TrackChanges
                LibraryCrate.LOCAL -> Icons.Filled.Folder
              }
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(if (selected) Color(0xFF1A6DFF) else Color.Transparent)
                  .clickable { onSelectCrate(crate) }
                  .padding(horizontal = 8.dp, vertical = 6.dp)
                  .testTag("crate_${crate.name}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF8C9BB5),
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = crate.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else Color(0xFFD0D9E8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Right Sortable Track Table or History View
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          if (bottomTab == LibraryBottomTab.HISTORY) {
            HistoryTableView(history = sessionHistory, accentColor = mixerTheme.deckAAccent)
          } else {
            // Table Header
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(Color(0xFF121824))
                  .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TableHeaderCell("#", Modifier.width(26.dp)) { onSortColumnClick(LibrarySortColumn.INDEX) }
              TableHeaderCell("Title", Modifier.weight(1.4f)) { onSortColumnClick(LibrarySortColumn.TITLE) }
              TableHeaderCell("Artist", Modifier.weight(1.1f)) { onSortColumnClick(LibrarySortColumn.ARTIST) }
              TableHeaderCell("BPM", Modifier.width(50.dp)) { onSortColumnClick(LibrarySortColumn.BPM) }
              TableHeaderCell("Key", Modifier.width(38.dp)) { onSortColumnClick(LibrarySortColumn.KEY) }
              TableHeaderCell("Length", Modifier.width(46.dp)) { onSortColumnClick(LibrarySortColumn.LENGTH) }
              Text(
                text = "Load",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9BA8BE),
                modifier = Modifier.width(74.dp),
              )
            }

            if (displayedTracks.isEmpty()) {
              Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text =
                    if (bottomTab == LibraryBottomTab.FILES || selectedCrate == LibraryCrate.LOCAL)
                      "No local audio files imported yet. Tap + Import to add songs from your device."
                    else "No matching tracks. Clear search or tap + Import.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFF8C9BB5),
                )
              }
            } else {
              LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(displayedTracks, key = { _, t -> t.id }) { idx, track ->
                  val loadedA = track.id == deckATrack.id
                  val loadedB = track.id == deckBTrack.id
                  val rowBg =
                    when {
                      loadedA -> mixerTheme.deckAAccent.copy(alpha = 0.16f)
                      loadedB -> mixerTheme.deckBAccent.copy(alpha = 0.16f)
                      idx % 2 == 0 -> Color(0xFF0A0E16)
                      else -> Color(0xFF0E141F)
                    }
                  val camelot = CamelotHelper.toCamelot(track.musicalKey)
                  val harmonic = CamelotHelper.isCompatible(deckATrack.musicalKey, track.musicalKey)
                  val inPrepare = track.id in prepareTrackIds

                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .background(rowBg)
                        .clickable { onLoadToDeck(DeckId.A, track) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("dock_track_${track.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "${idx + 1}",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF8C9BB5),
                      modifier = Modifier.width(26.dp),
                    )
                    Text(
                      text = track.title,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (loadedA || loadedB) FontWeight.Bold else FontWeight.Normal,
                      color = Color.White,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1.4f).padding(end = 4.dp),
                    )
                    Text(
                      text = track.artist,
                      style = MaterialTheme.typography.bodyMedium,
                      color = Color(0xFFB5C2D8),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1.1f).padding(end = 4.dp),
                    )
                    Text(
                      text = String.format("%.2f", track.bpm),
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                      modifier = Modifier.width(50.dp),
                    )
                    Text(
                      text = camelot,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = if (harmonic) Color(0xFF00E676) else Color(0xFFFFCA28),
                      modifier = Modifier.width(38.dp),
                    )
                    Text(
                      text = track.formattedDuration(),
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFFB5C2D8),
                      modifier = Modifier.width(46.dp),
                    )

                    Row(
                      modifier = Modifier.width(74.dp),
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      MiniDeckLoadButton("1", mixerTheme.deckAAccent) {
                        onLoadToDeck(DeckId.A, track)
                      }
                      MiniDeckLoadButton("2", mixerTheme.deckBAccent) {
                        onLoadToDeck(DeckId.B, track)
                      }
                      Icon(
                        imageVector = if (inPrepare) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Prepare",
                        tint = if (inPrepare) Color(0xFFFFCA28) else Color(0xFF6B7A94),
                        modifier =
                          Modifier.size(16.dp).clickable { onTogglePrepare(track.id) },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Bottom Serato Bar: Files | Browse | Prepare | History + Import + Refresh + Search
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFF0E131D))
            .border(1.dp, Color(0xFF1F2736))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        LazyRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          items(LibraryBottomTab.entries) { tab ->
            val selected = tab == bottomTab
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = if (selected) Color(0xFF1A6DFF) else Color(0xFF182030),
              modifier =
                Modifier.clip(RoundedCornerShape(4.dp))
                  .clickable { onSelectBottomTab(tab) }
                  .testTag("bottom_tab_${tab.name}"),
            ) {
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
              )
            }
          }

          item {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color(0xFF182030),
              modifier =
                Modifier.clip(RoundedCornerShape(4.dp))
                  .clickable(onClick = onImportFiles)
                  .testTag("dock_import_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Import tracks", tint = mixerTheme.deckAAccent, modifier = Modifier.size(13.dp))
                Text("Import", style = MaterialTheme.typography.labelSmall, color = Color.White)
              }
            }
          }

          item {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color(0xFF182030),
              modifier =
                Modifier.clip(RoundedCornerShape(4.dp))
                  .clickable(onClick = onImportFolder)
                  .testTag("dock_import_folder_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(Icons.Filled.Folder, contentDescription = "Import music folder", tint = mixerTheme.deckBAccent, modifier = Modifier.size(13.dp))
                Text("Folder", style = MaterialTheme.typography.labelSmall, color = Color.White)
              }
            }
          }

          item {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color(0xFF182030),
              modifier =
                Modifier.clip(RoundedCornerShape(4.dp))
                  .clickable(onClick = onRefreshLibrary)
                  .testTag("dock_refresh_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                if (isRefreshing) {
                  CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = mixerTheme.deckAAccent)
                } else {
                  Icon(Icons.Filled.Refresh, contentDescription = "Refresh library", tint = Color(0xFF00E676), modifier = Modifier.size(13.dp))
                }
                Text("Sync (${displayedTracks.size})", style = MaterialTheme.typography.labelSmall, color = Color.White)
              }
            }
          }
        }

        // Compact Search Input
        Row(
          modifier =
            Modifier.width(150.dp)
              .height(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF06080D))
              .border(1.dp, Color(0xFF2B3548), RoundedCornerShape(6.dp))
              .padding(horizontal = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF7D8AA3), modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Box(modifier = Modifier.weight(1f)) {
            if (searchQuery.isEmpty()) {
              Text("Search...", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7A94))
            }
            BasicTextField(
              value = searchQuery,
              onValueChange = onSearchQueryChange,
              singleLine = true,
              textStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 11.sp),
              cursorBrush = SolidColor(mixerTheme.deckAAccent),
              modifier =
                Modifier.fillMaxWidth()
                  .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                  .testTag("dock_search_input"),
            )
          }
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(18.dp)) {
              Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF9BA8BE), modifier = Modifier.size(12.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TableHeaderCell(label: String, modifier: Modifier, onClick: () -> Unit) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF9BA8BE),
    modifier = modifier.clickable(onClick = onClick),
  )
}

@Composable
private fun MiniDeckLoadButton(label: String, accent: Color, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.size(22.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(accent.copy(alpha = 0.22f))
        .border(1.dp, accent, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = accent,
    )
  }
}

@Composable
private fun HistoryTableView(history: List<SessionHistoryItem>, accentColor: Color) {
  val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
  if (history.isEmpty()) {
    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
      Text(
        text = "No playback history yet. Start playing a track on Deck 1 or Deck 2.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF8C9BB5),
      )
    }
  } else {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
      items(history, key = { it.id }) { item ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "${item.trackTitle} — ${item.artist} (${item.deckName})",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = fmt.format(Date(item.playedAtMs)),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
          )
        }
      }
    }
  }
}
