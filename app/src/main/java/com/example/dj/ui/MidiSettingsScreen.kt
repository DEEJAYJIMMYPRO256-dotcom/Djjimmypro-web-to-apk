package com.example.dj.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dj.midi.MidiAction
import com.example.dj.midi.MidiControllerManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiSettingsScreen(
  midiManager: MidiControllerManager,
  powerSavingMode: Boolean,
  onTogglePowerSaving: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val connectedDevice by midiManager.connectedDeviceName.collectAsStateWithLifecycle()
  val mappings by midiManager.mappings.collectAsStateWithLifecycle()
  val latencyOffsetMs by midiManager.latencyOffsetMs.collectAsStateWithLifecycle()

  var learningAction by remember { mutableStateOf<MidiAction?>(null) }
  var presetName by remember { mutableStateOf("") }
  var measuredLatencyMs by remember { mutableStateOf<Float?>(null) }
  var statusBanner by remember { mutableStateOf<String?>(null) }

  val presetsDir = remember { File(context.filesDir, "midi_presets").apply { mkdirs() } }
  var savedPresets by remember {
    mutableStateOf(presetsDir.listFiles()?.map { it.nameWithoutExtension }?.sorted().orEmpty())
  }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri: Uri? ->
      if (uri != null) {
        runCatching {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(midiManager.exportMappingsJson().toByteArray())
          }
          statusBanner = "Exported MIDI mapping JSON"
        }
      }
    }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        runCatching {
          val json =
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
          if (json != null && midiManager.importMappingsJson(json)) {
            statusBanner = "Imported MIDI mapping JSON"
          } else {
            statusBanner = "Invalid MIDI JSON profile"
          }
        }
      }
    }

  LaunchedEffect(learningAction) {
    val target = learningAction ?: return@LaunchedEffect
    midiManager.incomingMidiEvents.collect { descriptor ->
      if (descriptor != "DIAG_PING") {
        midiManager.updateMapping(target, descriptor)
        statusBanner = "Mapped ${target.label} → $descriptor"
        learningAction = null
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("MIDI & Mixer Settings") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("midi_settings_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Mixer")
          }
        },
        actions = {
          IconButton(
            onClick = {
              midiManager.resetMappings()
              statusBanner = "Restored default MIDI mappings"
            },
            modifier = Modifier.testTag("midi_reset_button"),
          ) {
            Icon(Icons.Filled.Restore, contentDescription = "Reset MIDI mappings")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      statusBanner?.let { msg ->
        item {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }

      // Connected USB MIDI Controller Card
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              Icons.Filled.Usb,
              contentDescription = "USB MIDI status",
              tint =
                if (connectedDevice != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
              Text(
                text = connectedDevice ?: "No USB MIDI Controller Detected",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  if (connectedDevice != null) "Connected and listening for CC / Note messages"
                  else "Connect a USB-OTG class-compliant MIDI controller to enable MIDI Learn",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Power Saving Mode Card
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Filled.BatterySaver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
              )
              Column {
                Text("Power Saving Mode", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Reduces waveform & beat grid refresh rate (30fps → 10fps) during long sets",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = powerSavingMode,
              onCheckedChange = { onTogglePowerSaving() },
              modifier = Modifier.testTag("power_saving_switch"),
            )
          }
        }
      }

      // MIDI Latency Diagnostic & Calibration Offset
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("MIDI Latency Diagnostic", style = MaterialTheme.typography.titleSmall)
              }
              Button(
                onClick = { measuredLatencyMs = midiManager.measureDispatchLatencyMs() },
                modifier = Modifier.testTag("midi_latency_test_button"),
              ) {
                Text("Test")
              }
            }

            measuredLatencyMs?.let { ms ->
              Text(
                text = "Measured Dispatch Latency: ${String.format("%.2f", ms)} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Text(
              text = "Calibration Offset: ${if (latencyOffsetMs >= 0) "+$latencyOffsetMs" else latencyOffsetMs} ms",
              style = MaterialTheme.typography.labelMedium,
            )
            Slider(
              value = latencyOffsetMs.toFloat(),
              onValueChange = { midiManager.setLatencyOffsetMs(it.toInt()) },
              valueRange = -50f..50f,
              modifier = Modifier.testTag("midi_latency_slider"),
            )
          }
        }
      }

      // Save / Load / Export / Import JSON Profiles
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("MIDI Mapping Profiles (JSON)", style = MaterialTheme.typography.titleSmall)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                placeholder = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("midi_preset_name_input"),
              )
              Button(
                onClick = {
                  val clean = presetName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
                  if (clean.isNotEmpty()) {
                    File(presetsDir, "$clean.json").writeText(midiManager.exportMappingsJson())
                    savedPresets =
                      presetsDir.listFiles()?.map { it.nameWithoutExtension }?.sorted().orEmpty()
                    statusBanner = "Saved profile '$clean'"
                    presetName = ""
                  }
                },
                modifier = Modifier.testTag("save_midi_preset_button"),
              ) {
                Text("Save")
              }
            }

            if (savedPresets.isNotEmpty()) {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedPresets) { name ->
                  AssistChip(
                    onClick = {
                      val file = File(presetsDir, "$name.json")
                      if (file.exists() && midiManager.importMappingsJson(file.readText())) {
                        statusBanner = "Loaded profile '$name'"
                      }
                    },
                    label = { Text(name) },
                    modifier = Modifier.testTag("midi_preset_$name"),
                  )
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { exportLauncher.launch("stemdeck_midi_profile.json") },
                modifier = Modifier.weight(1f).testTag("export_midi_json_button"),
              ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Text(" Export JSON")
              }
              OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f).testTag("import_midi_json_button"),
              ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text(" Import JSON")
              }
            }
          }
        }
      }

      // MIDI Learn Control Mappings
      item {
        Text(
          text = "CONTROL MAPPINGS (TAP LEARN, THEN MOVE HARDWARE CONTROL)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      items(MidiAction.entries) { action ->
        val isLearning = learningAction == action
        val binding = mappings[action] ?: action.defaultBinding
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.fillMaxWidth()
              .then(
                if (isLearning) {
                  Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp),
                  )
                } else {
                  Modifier
                }
              ),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(action.label, style = MaterialTheme.typography.titleSmall)
              Text(
                text = if (isLearning) "Listening for hardware MIDI..." else binding,
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (isLearning) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { learningAction = if (isLearning) null else action },
              modifier = Modifier.testTag("midi_learn_${action.name}"),
            ) {
              Text(if (isLearning) "Cancel" else "Learn")
            }
          }
        }
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}
