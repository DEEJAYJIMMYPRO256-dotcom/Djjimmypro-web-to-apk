package com.example.dj.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.DeckState
import com.example.dj.model.StemType

enum class StemToggleActionMode(val label: String) {
  MUTE("MUTE"),
  ISOLATE("ISOLATE"),
}

/**
 * Dedicated 4-button stem deconstruction toggle bar for each deck allowing real-time
 * isolation (solo) or muting of Vocals, Drums, Bass, and Melody stems.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StemDeconstructionBar(
  deck: DeckState,
  accentColor: Color,
  onToggleMute: (StemType) -> Unit,
  onToggleSolo: (StemType) -> Unit,
  onResetAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tagSuffix = deck.deckId.name
  var actionMode by rememberSaveable(deck.deckId.name) { mutableStateOf(StemToggleActionMode.MUTE) }

  val anySolo = deck.stems.values.any { it.isSolo }
  val anyMuted = deck.stems.values.any { it.isMuted }
  val isDeconstructed = anySolo || anyMuted

  val statusSummary =
    when {
      anySolo -> {
        val isolatedNames =
          StemType.entries.filter { deck.stems[it]?.isSolo == true }.joinToString("+") { it.shortLabel }
        "ISO: $isolatedNames"
      }
      anyMuted -> {
        val mutedNames =
          StemType.entries.filter { deck.stems[it]?.isMuted == true }.joinToString(",") { it.shortLabel }
        "MUTED: $mutedNames"
      }
      else -> "4/4 STEMS ON"
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF070B12))
        .border(
          1.dp,
          if (isDeconstructed) accentColor.copy(alpha = 0.55f) else Color(0xFF202A3B),
          RoundedCornerShape(10.dp),
        )
        .padding(8.dp)
        .testTag("stem_deconstruct_bar_$tagSuffix"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Header: Title + Active Deconstruction Status + MUTE / ISOLATE mode switch + ALL ON reset
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "STEM TOGGLES",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = accentColor,
        )
        Text(
          text = statusSummary,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          color = if (isDeconstructed) Color(0xFF00E676) else Color(0xFF9BA8BE),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("stem_deconstruct_status_$tagSuffix"),
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        ModeSwitchPill(
          label = "MUTE",
          selected = actionMode == StemToggleActionMode.MUTE,
          activeColor = Color(0xFFFF3B5C),
          onClick = { actionMode = StemToggleActionMode.MUTE },
          modifier = Modifier.testTag("stem_mode_mute_$tagSuffix"),
        )
        ModeSwitchPill(
          label = "ISOLATE",
          selected = actionMode == StemToggleActionMode.ISOLATE,
          activeColor = Color(0xFF00E676),
          onClick = { actionMode = StemToggleActionMode.ISOLATE },
          modifier = Modifier.testTag("stem_mode_isolate_$tagSuffix"),
        )
        if (isDeconstructed) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = 0.18f),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .clickable(onClick = onResetAll)
                .testTag("stem_deconstruct_reset_$tagSuffix"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Restore all 4 stems",
                tint = accentColor,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "ALL",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
              )
            }
          }
        }
      }
    }

    // 4-Button Toggle Row: Vocals, Drums, Bass, Melody
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StemType.entries.forEach { stem ->
        val stemState = deck.stems[stem]
        val isMuted = stemState?.isMuted == true
        val isSolo = stemState?.isSolo == true
        val isAudible = deck.isStemAudible(stem)
        val stemColor = stemThemeColor(stem)
        val stemIcon = stemIcon(stem, isMuted = isMuted || !isAudible)

        val statusLabel =
          when {
            isSolo -> "ISO"
            isMuted -> "MUTED"
            !isAudible -> "SILENT"
            else -> "ON"
          }

        val bgColor =
          when {
            isSolo -> Color(0xFF00E676).copy(alpha = 0.24f)
            isMuted || !isAudible -> Color(0xFF141820)
            else -> stemColor.copy(alpha = 0.22f)
          }

        val borderColor =
          when {
            isSolo -> Color(0xFF00E676)
            isMuted -> Color(0xFFFF3B5C)
            !isAudible -> Color(0xFF283142)
            else -> stemColor
          }

        val textColor =
          when {
            isSolo -> Color(0xFF00E676)
            isMuted -> Color(0xFFFF3B5C)
            !isAudible -> Color(0xFF6B778D)
            else -> Color.White
          }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = bgColor,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(8.dp))
              .border(1.2.dp, borderColor, RoundedCornerShape(8.dp))
              .semantics(mergeDescendants = true) {
                stateDescription = "${stem.fullName} $statusLabel"
              }
              .combinedClickable(
                onClick = {
                  if (actionMode == StemToggleActionMode.ISOLATE) {
                    onToggleSolo(stem)
                  } else {
                    onToggleMute(stem)
                  }
                },
                onLongClick = {
                  if (actionMode == StemToggleActionMode.ISOLATE) {
                    onToggleMute(stem)
                  } else {
                    onToggleSolo(stem)
                  }
                },
              )
              .testTag("stem_toggle_${stem.name}_$tagSuffix"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Icon(
              imageVector = stemIcon,
              contentDescription = "${stem.fullName} stem $statusLabel",
              tint =
                when {
                  isSolo -> Color(0xFF00E676)
                  isMuted -> Color(0xFFFF3B5C)
                  !isAudible -> Color(0xFF6B778D)
                  else -> stemColor
                },
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stem.fullName,
              style = MaterialTheme.typography.labelSmall,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = textColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(4.dp))
                  .background(
                    when {
                      isSolo -> Color(0xFF00E676).copy(alpha = 0.2f)
                      isMuted -> Color(0xFFFF3B5C).copy(alpha = 0.2f)
                      !isAudible -> Color(0xFF1E2533)
                      else -> stemColor.copy(alpha = 0.24f)
                    }
                  )
                  .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
              )
            }
          }
        }
      }
    }

    Text(
      text =
        if (actionMode == StemToggleActionMode.ISOLATE) {
          "Tap button to isolate stem • Long-press to mute"
        } else {
          "Tap button to mute stem • Long-press to isolate"
        },
      style = MaterialTheme.typography.labelSmall,
      fontSize = 9.sp,
      color = Color(0xFF7D8AA1),
    )
  }
}

@Composable
private fun ModeSwitchPill(
  label: String,
  selected: Boolean,
  activeColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (selected) activeColor.copy(alpha = 0.22f) else Color(0xFF141B27),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(6.dp))
        .border(
          1.dp,
          if (selected) activeColor else Color(0xFF2B3548),
          RoundedCornerShape(6.dp),
        )
        .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      if (label == "ISOLATE") {
        Icon(
          imageVector = Icons.Filled.Headphones,
          contentDescription = null,
          tint = if (selected) activeColor else Color(0xFF9BA8BE),
          modifier = Modifier.size(11.dp),
        )
      } else {
        Icon(
          imageVector = Icons.Filled.VolumeOff,
          contentDescription = null,
          tint = if (selected) activeColor else Color(0xFF9BA8BE),
          modifier = Modifier.size(11.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) activeColor else Color(0xFF9BA8BE),
      )
    }
  }
}

private fun stemThemeColor(stem: StemType): Color =
  when (stem) {
    StemType.VOCALS -> Color(0xFFAA00FF)
    StemType.DRUMS -> Color(0xFFFF6D00)
    StemType.BASS -> Color(0xFF00B8D4)
    StemType.MELODY -> Color(0xFF2979FF)
  }

private fun stemIcon(stem: StemType, isMuted: Boolean): ImageVector =
  when (stem) {
    StemType.VOCALS -> if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic
    StemType.DRUMS -> Icons.Filled.GraphicEq
    StemType.BASS -> if (isMuted) Icons.Filled.MusicOff else Icons.Filled.MusicNote
    StemType.MELODY -> Icons.Filled.Piano
  }
