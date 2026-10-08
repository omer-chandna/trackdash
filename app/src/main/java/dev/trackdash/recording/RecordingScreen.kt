package dev.trackdash.recording

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.*
import dev.trackdash.data.TrackDashApplication

@SuppressLint("MissingPermission")
@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val state by graph.recording.collectAsStateWithLifecycle()
    val tracks by graph.database.dao().tracks().collectAsStateWithLifecycle(emptyList())
    val vehicles by graph.database.dao().vehicles().collectAsStateWithLifecycle(emptyList())
    var simulated by rememberSaveable { mutableStateOf(true) }
    var modeName by rememberSaveable { mutableStateOf(OperatingMode.GPS_LAP_TIMER.name) }
    val mode = OperatingMode.valueOf(modeName)
    var trackId by rememberSaveable { mutableStateOf("") }
    var vehicleId by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var devices by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun start() {
        runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)
            .putExtra("simulated", simulated).putExtra("mode", mode.name).putExtra("track", trackId)
            .putExtra("vehicle", vehicleId.ifBlank { null }).putExtra("address", address)) }.onFailure { error = it.message }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        error = if (it.any { entry -> !entry.value && entry.key != Manifest.permission.POST_NOTIFICATIONS }) "Required permission denied. Choose simulation or grant permission in Android settings." else null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session recorder", style = MaterialTheme.typography.headlineLarge)
        Text(if (state.active) if (state.simulated) "SIMULATED DATA · RECORDING" else "DEVICE SOURCES · RECORDING" else "Prepare while stationary", color = MaterialTheme.colorScheme.primary)
        if (!state.active) {
            Row { Switch(simulated, { simulated = it }); Text("Simulation", Modifier.padding(12.dp)) }
            OperatingMode.entries.forEach { item ->
                Row { RadioButton(mode == item, { modeName = item.name }); Text(item.name.replace('_', ' '), Modifier.padding(12.dp)) }
            }
            if (!simulated && mode.usesGps) {
                Text("Track")
                tracks.forEach { track -> FilterChip(trackId == track.id, { trackId = track.id }, { Text(track.name) }) }
            }
            if (mode.usesVehicle) {
                Text("Vehicle profile (optional)")
                vehicles.forEach { vehicle -> FilterChip(vehicleId == vehicle.id, { vehicleId = vehicle.id }, { Text(vehicle.name) }) }
            }
            if (!simulated && mode.usesVehicle) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    else runCatching { devices = context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map { (it.name ?: "Adapter") to it.address } ?: emptyList() }
                        .onFailure { error = it.message }
                }) { Text("Load paired Classic adapters") }
                Text("Pair an ELM-compatible Classic adapter in Android Bluetooth settings first. BLE requires an adapter-specific driver.")
                devices.forEach { (name, value) -> FilterChip(address == value, { address = value }, { Text("$name · $value") }) }
            }
            Button(enabled = simulated || (!mode.usesGps || trackId.isNotBlank()) && (!mode.usesVehicle || address.isNotBlank()), onClick = {
                val required = buildList {
                    if (!simulated && mode.usesGps) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    if (!simulated && mode.usesVehicle && Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                if (required.isNotEmpty()) permissions.launch(required.toTypedArray()) else start()
            }, modifier = Modifier.fillMaxWidth()) { Text("Start recording") }
            Text("After granting permissions, press Start again. Simulation uses the built-in demo circuit.")
        } else Button(onClick = { context.startService(Intent(context, RecordingService::class.java).setAction("STOP")) }, modifier = Modifier.fillMaxWidth()) { Text("Stop and save") }
        Text("${state.message} · ${state.samples} stored samples")
        (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.sessionId != null) {
            Text("CURRENT LAP  ${formatTime(state.currentLapNanos)}", style = MaterialTheme.typography.headlineMedium)
            Text("Last ${state.lastLap?.let { formatTime(it.durationNanos) } ?: "—"} · Best ${state.bestLap?.let { formatTime(it.durationNanos) } ?: "—"}")
            Text("${state.laps} laps · GPS accuracy ${state.gps?.horizontalAccuracyMeters?.let { "%.1f m".format(it) } ?: "—"} · Age ${state.gpsAgeSeconds?.let { "%.1f s".format(it) } ?: "—"}")
            state.lastLap?.takeUnless { it.valid }?.let { Text("Invalid lap: ${it.invalidReasons.joinToString()}", color = MaterialTheme.colorScheme.error) }
            state.vehicle?.let { sample ->
                Text("${sample.source} · ${if (sample.connected) "Connected" else "Disconnected"}")
                sample.readings.forEach { reading -> Text("${reading.sensorId.name.replace('_', ' ')}: ${if (reading.status == SensorStatus.AVAILABLE) "%.1f".format(reading.value) else reading.status.name} ${reading.sensorId.unit}") }
                Text("Oil-pressure switch: ${sample.oilPressure}")
            }
        }
    }
}

fun formatTime(nanos: Long): String = if (nanos <= 0) "—" else "%d:%06.3f".format(nanos / 60_000_000_000, nanos % 60_000_000_000 / 1e9)
