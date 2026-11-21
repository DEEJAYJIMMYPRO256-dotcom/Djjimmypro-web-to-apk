package com.example.dj.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dj.model.MixerTheme
import com.example.dj.model.SamplePadBank
import com.example.dj.model.SamplePadCatalog
import com.example.dj.model.SamplePadEffect
import kotlin.math.roundToInt

@Composable
fun SamplePadsEffectsPanel(
  selectedBank: SamplePadBank,
  activePadIds: Set<String>,
  loopEnabled: Boolean,
  volume: Float,
  mixerTheme: MixerTheme,
  onSelectBank: (SamplePadBank) -> Unit,
  onTogglePad: (SamplePadEffect) -> Unit,
  onToggleLoop: () -> Unit,
  onStopAll: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val pads = SamplePadCatalog.padsForBank(selectedBank)
  val activeCount = activePadIds.size
  val padsScrollState = rememberScrollState()

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = mixerTheme.consoleSurface,
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF283449), RoundedCornerShape(12.dp))
        .testTag("sample_pads_effects_panel"),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top Header: Title + Active Status + Loop + Cut All + FX Gain
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Samples Pads Effects",
            tint = mixerTheme.deckAAccent,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "SAMPLES PADS EFFECTS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color =
              if (activeCount > 0) Color(0xFF00E676).copy(alpha = 0.22f) else Color(0xFF151C28),
            modifier =
              Modifier.semantics(mergeDescendants = true) {}
                .border(
                  1.dp,
                  if (activeCount > 0) Color(0xFF00E676) else Color(0xFF2D384C),
                  RoundedCornerShape(6.dp),
                )
                .testTag("sample_pads_active_badge"),
          ) {
            Text(
              text = if (activeCount > 0) "$activeCount ON" else "TAP ON/OFF",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (activeCount > 0) Color(0xFF00E676) else Color(0xFF9BA8BE),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color =
              if (loopEnabled) mixerTheme.deckAAccent.copy(alpha = 0.24f) else Color(0xFF151C28),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .border(
                  1.dp,
                  if (loopEnabled) mixerTheme.deckAAccent else Color(0xFF2D384C),
                  RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onToggleLoop)
                .testTag("sample_pads_loop_toggle"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Loop,
                contentDescription = "Loop samples",
                tint = if (loopEnabled) mixerTheme.deckAAccent else Color(0xFFB5C2D9),
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = if (loopEnabled) "LOOP ON" else "1-SHOT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (loopEnabled) mixerTheme.deckAAccent else Color(0xFFD5DFF0),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color =
              if (activeCount > 0) Color(0xFFFF3B5C).copy(alpha = 0.22f) else Color(0xFF151C28),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .border(
                  1.dp,
                  if (activeCount > 0) Color(0xFFFF3B5C) else Color(0xFF2D384C),
                  RoundedCornerShape(6.dp),
                )
                .clickable(enabled = activeCount > 0, onClick = onStopAll)
                .testTag("sample_pads_stop_all"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "All off",
                tint = if (activeCount > 0) Color(0xFFFF3B5C) else Color(0xFF6B778D),
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "ALL OFF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (activeCount > 0) Color(0xFFFF3B5C) else Color(0xFF6B778D),
              )
            }
          }
        }
      }

      // Bank Selector Tabs + Master FX Pad Gain Slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LazyRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(SamplePadBank.entries) { bank ->
            val selected = bank == selectedBank
            Surface(
              shape = RoundedCornerShape(6.dp),
              color =
                if (selected) mixerTheme.deckBAccent.copy(alpha = 0.24f) else Color(0xFF141B27),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(6.dp))
                  .border(
                    1.dp,
                    if (selected) mixerTheme.deckBAccent else Color(0xFF293448),
                    RoundedCornerShape(6.dp),
                  )
                  .clickable { onSelectBank(bank) }
                  .testTag("sample_bank_${bank.name}"),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = bank.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) mixerTheme.deckBAccent else Color(0xFFB5C2D9),
                )
              }
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.width(118.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = "Sample FX Volume",
            tint = Color(0xFF9BA8BE),
            modifier = Modifier.size(14.dp),
          )
          Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors =
              SliderDefaults.colors(
                thumbColor = mixerTheme.deckAAccent,
                activeTrackColor = mixerTheme.deckAAccent,
              ),
            modifier = Modifier.weight(1f).height(24.dp).testTag("sample_pads_volume_slider"),
          )
          Text(
            text = "${(volume * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = Color(0xFF9BA8BE),
          )
        }
      }

      // Responsive Color-Coded Pad Grid (Tap Once = ON, Tap Again = OFF)
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 620.dp) 4 else 2
        val rows = pads.chunked(columns)
        val gridContainerModifier =
          if (rows.size > 4) {
            Modifier.fillMaxWidth().heightIn(max = 264.dp).verticalScroll(padsScrollState)
          } else {
            Modifier.fillMaxWidth()
          }

        Column(
          modifier = gridContainerModifier,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          rows.forEach { rowPads ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              rowPads.forEach { pad ->
                val isOn = pad.id in activePadIds
                SamplePadTile(
                  pad = pad,
                  isOn = isOn,
                  onClick = { onTogglePad(pad) },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(columns - rowPads.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SamplePadTile(
  pad: SamplePadEffect,
  isOn: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val col = Color(pad.colorHex)
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (isOn) col.copy(alpha = 0.28f) else Color(0xFF0E131D),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(8.dp))
        .border(
          width = if (isOn) 1.75.dp else 1.dp,
          color = if (isOn) col else col.copy(alpha = 0.55f),
          shape = RoundedCornerShape(8.dp),
        )
        .clickable(onClick = onClick)
        .testTag("sample_pad_${pad.id}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier.size(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (isOn) col else col.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
      ) {
        if (isOn) {
          Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = "Pad active",
            tint = Color(0xFF06080D),
            modifier = Modifier.size(13.dp),
          )
        } else {
          Text(
            text = "${pad.slot}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF06080D),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = pad.title,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (isOn) col else Color(0xFF1A2232))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (isOn) "ON" else "OFF",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          color = if (isOn) Color(0xFF06080D) else Color(0xFF8C9AB3),
        )
      }
    }
  }
}
