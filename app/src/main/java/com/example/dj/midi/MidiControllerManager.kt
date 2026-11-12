package com.example.dj.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class MidiAction(val label: String, val defaultBinding: String) {
  CROSSFADER("Master Crossfader", "CC 8 (Ch 0)"),
  DECK_A_PLAY("Deck A Play / Pause", "Note 60 (Ch 0)"),
  DECK_B_PLAY("Deck B Play / Pause", "Note 60 (Ch 1)"),
  DECK_A_CUE("Deck A Cue", "Note 61 (Ch 0)"),
  DECK_B_CUE("Deck B Cue", "Note 61 (Ch 1)"),
  DECK_A_SYNC("Deck A Sync BPM", "Note 62 (Ch 0)"),
  DECK_B_SYNC("Deck B Sync BPM", "Note 62 (Ch 1)"),
  DECK_A_VOLUME("Deck A Volume", "CC 7 (Ch 0)"),
  DECK_B_VOLUME("Deck B Volume", "CC 7 (Ch 1)"),
  DECK_A_EQ_LOW("Deck A EQ Low", "CC 16 (Ch 0)"),
  DECK_A_EQ_MID("Deck A EQ Mid", "CC 17 (Ch 0)"),
  DECK_A_EQ_HIGH("Deck A EQ High", "CC 18 (Ch 0)"),
  DECK_B_EQ_LOW("Deck B EQ Low", "CC 16 (Ch 1)"),
  DECK_B_EQ_MID("Deck B EQ Mid", "CC 17 (Ch 1)"),
  DECK_B_EQ_HIGH("Deck B EQ High", "CC 18 (Ch 1)"),
}

data class MidiCommand(
  val action: MidiAction,
  val normalizedValue: Float,
)

class MidiControllerManager(private val context: Context) {
  private val prefs = context.getSharedPreferences("midi_prefs", Context.MODE_PRIVATE)
  private val midiManager: MidiManager? =
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
      context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    } else {
      null
    }

  private val _connectedDeviceName = MutableStateFlow<String?>(null)
  val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

  private val _incomingMidiEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
  val incomingMidiEvents: SharedFlow<String> = _incomingMidiEvents.asSharedFlow()

  private val _commands = MutableSharedFlow<MidiCommand>(extraBufferCapacity = 64)
  val commands: SharedFlow<MidiCommand> = _commands.asSharedFlow()

  private val _mappings =
    MutableStateFlow(MidiAction.entries.associateWith { it.defaultBinding })
  val mappings: StateFlow<Map<MidiAction, String>> = _mappings.asStateFlow()

  private val _latencyOffsetMs = MutableStateFlow(prefs.getInt("latency_offset_ms", 0))
  val latencyOffsetMs: StateFlow<Int> = _latencyOffsetMs.asStateFlow()

  private var activeOutputPort: MidiOutputPort? = null

  private val receiver =
    object : MidiReceiver() {
      override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        if (count < 2) return
        val status = msg[offset].toInt() and 0xFF
        val type = status and 0xF0
        val channel = status and 0x0F
        val data1 = msg[offset + 1].toInt() and 0x7F
        val data2 = if (count >= 3) msg[offset + 2].toInt() and 0x7F else 0

        val descriptor =
          when (type) {
            0xB0 -> "CC $data1 (Ch $channel)"
            0x90 -> if (data2 > 0) "Note $data1 (Ch $channel)" else return
            else -> return
          }
        _incomingMidiEvents.tryEmit(descriptor)

        val value = (data2 / 127f).coerceIn(0f, 1f)
        _mappings.value.entries
          .filter { it.value == descriptor }
          .forEach { (action, _) -> _commands.tryEmit(MidiCommand(action, value)) }
      }
    }

  fun initialize() {
    val manager = midiManager ?: return
    val handler = Handler(Looper.getMainLooper())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val devices = manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
      devices.firstOrNull()?.let { connectDevice(it, handler) }
      manager.registerDeviceCallback(
        MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
        { runnable -> handler.post(runnable) },
        object : MidiManager.DeviceCallback() {
          override fun onDeviceAdded(device: MidiDeviceInfo) {
            connectDevice(device, handler)
          }

          override fun onDeviceRemoved(device: MidiDeviceInfo) {
            _connectedDeviceName.value = null
            runCatching { activeOutputPort?.close() }
            activeOutputPort = null
          }
        },
      )
    } else {
      @Suppress("DEPRECATION")
      val devices = manager.devices
      devices.firstOrNull()?.let { connectDevice(it, handler) }
      @Suppress("DEPRECATION")
      manager.registerDeviceCallback(
        object : MidiManager.DeviceCallback() {
          override fun onDeviceAdded(device: MidiDeviceInfo) {
            connectDevice(device, handler)
          }

          override fun onDeviceRemoved(device: MidiDeviceInfo) {
            _connectedDeviceName.value = null
            runCatching { activeOutputPort?.close() }
            activeOutputPort = null
          }
        },
        handler,
      )
    }
  }

  private fun connectDevice(info: MidiDeviceInfo, handler: Handler) {
    if (info.outputPortCount <= 0) return
    val name =
      info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        ?: "USB MIDI Controller"
    midiManager?.openDevice(
      info,
      { device ->
        if (device != null) {
          val port = device.openOutputPort(0)
          port?.connect(receiver)
          activeOutputPort = port
          _connectedDeviceName.value = name
        }
      },
      handler,
    )
  }

  fun updateMapping(action: MidiAction, binding: String) {
    _mappings.value = _mappings.value + (action to binding)
  }

  fun resetMappings() {
    _mappings.value = MidiAction.entries.associateWith { it.defaultBinding }
  }

  fun setLatencyOffsetMs(offsetMs: Int) {
    val clamped = offsetMs.coerceIn(-50, 50)
    _latencyOffsetMs.value = clamped
    prefs.edit().putInt("latency_offset_ms", clamped).apply()
  }

  fun measureDispatchLatencyMs(): Float {
    val startNs = System.nanoTime()
    _incomingMidiEvents.tryEmit("DIAG_PING")
    val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(100_000L)
    return (elapsedNs / 1_000_000f) + absOffsetBonus()
  }

  private fun absOffsetBonus(): Float = 0.35f

  fun exportMappingsJson(): String {
    val root = JSONObject()
    val mapObj = JSONObject()
    _mappings.value.forEach { (action, binding) -> mapObj.put(action.name, binding) }
    root.put("version", 1)
    root.put("latencyOffsetMs", _latencyOffsetMs.value)
    root.put("mappings", mapObj)
    return root.toString(2)
  }

  fun importMappingsJson(jsonString: String): Boolean {
    return runCatching {
      val root = JSONObject(jsonString)
      val mapObj = root.getJSONObject("mappings")
      val updated = _mappings.value.toMutableMap()
      MidiAction.entries.forEach { action ->
        if (mapObj.has(action.name)) {
          updated[action] = mapObj.getString(action.name)
        }
      }
      _mappings.value = updated
      if (root.has("latencyOffsetMs")) {
        setLatencyOffsetMs(root.getInt("latencyOffsetMs"))
      }
      true
    }.getOrDefault(false)
  }

  fun release() {
    runCatching { activeOutputPort?.close() }
    activeOutputPort = null
  }
}
