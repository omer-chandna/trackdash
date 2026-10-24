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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.*
import dev.trackdash.data.TrackDashApplication
import dev.trackdash.data.Preferences
import dev.trackdash.data.DashboardPreferences
import dev.trackdash.core.simulation.SimulationScenario
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.*
import dev.trackdash.sources.discoverCxAdapters
import dev.trackdash.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun RecordingScreen(initialMode: String = OperatingMode.GPS_LAP_TIMER.name, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val state by graph.recording.collectAsStateWithLifecycle()
    val controls by graph.simulationControls.collectAsStateWithLifecycle()
    val tracks by graph.database.dao().tracks().collectAsStateWithLifecycle(emptyList())
    val vehicles by graph.database.dao().vehicles().collectAsStateWithLifecycle(emptyList())
    var simulated by rememberSaveable { mutableStateOf(true) }
    var modeName by rememberSaveable { mutableStateOf(initialMode) }
    val mode = OperatingMode.valueOf(modeName)
    var trackId by rememberSaveable { mutableStateOf("") }
    var vehicleId by rememberSaveable { mutableStateOf("") }
    val preferences by remember(vehicleId) { Preferences(context.applicationContext).dashboard(vehicleId) }.collectAsStateWithLifecycle(DashboardPreferences())
    RecordingDisplayPolicy(state.dashboard, state.active)
    var address by rememberSaveable { mutableStateOf("") }
    var bleCx by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun start() {
        runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)
            .putExtra("simulated", simulated).putExtra("mode", mode.name).putExtra("track", trackId)
            .putExtra("vehicle", vehicleId.ifBlank { null }).putExtra("address", address).putExtra("bleCx", bleCx)) }.onFailure { error = it.message }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        error = if (it.any { entry -> !entry.value && entry.key != Manifest.permission.POST_NOTIFICATIONS }) "Required permission denied. Choose simulation or grant permission in Android settings." else null
    }
    if (state.active) {
        RecordingDashboard(state, controls, { graph.simulationControls.value = it },
            { context.startService(Intent(context, RecordingService::class.java).setAction("STOP")) }, onBack)
        return
    }
    val modePanel: @Composable () -> Unit = {
        TrackDashPanel {
            SectionTitle("Session mode", "Choose the data sources for this run.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(simulated, { simulated = it })
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Simulation", style = MaterialTheme.typography.titleMedium)
                    Text(if (simulated) "Uses safe synthetic data" else "Uses connected device sources",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OperatingMode.entries.forEach { item ->
                Surface(
                    color = if (mode == item) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().selectable(mode == item, role = Role.RadioButton, onClick = { modeName = item.name }),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(mode == item, null)
                        Text(item.name.replace('_', ' '), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    val sourcePanel: @Composable () -> Unit = {
        TrackDashPanel(emphasized = true) {
            SectionTitle("Sources", if (simulated) "The built-in circuit and vehicle stream are ready." else "Select the hardware and track required for this mode.")
            if (!simulated && mode.usesGps) {
                Text("Track", style = MaterialTheme.typography.labelLarge)
                if (tracks.isEmpty()) Text("Create a track before recording GPS laps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                tracks.forEach { track -> FilterChip(trackId == track.id, { trackId = track.id }, { Text(track.name) }) }
            }
            if (mode.usesVehicle) {
                Text("Vehicle profile (optional)", style = MaterialTheme.typography.labelLarge)
                if (vehicles.isEmpty()) Text("No saved vehicles. Default dashboard settings will be used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                vehicles.forEach { vehicle -> FilterChip(vehicleId == vehicle.id, { vehicleId = vehicle.id }, { Text(vehicle.name) }) }
            }
            if (!simulated && mode.usesVehicle) {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    else runCatching { bleCx = false; address = ""; devices = context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map { (it.name ?: "Adapter") to it.address } ?: emptyList() }
                        .onFailure { error = it.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("Load paired Classic adapters") }
                Text("Pair a supported Classic Bluetooth adapter in Android settings, then select it here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !scanning, onClick = {
                    val needed = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (needed.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) permissions.launch(needed)
                    else scope.launch {
                        scanning = true; devices = emptyList(); address = ""; bleCx = true
                        try { withTimeoutOrNull(10_000) { discoverCxAdapters(context).collect { found ->
                            if (devices.none { it.second == found.address }) devices = (devices + (found.name to found.address)).take(30)
                        } } } catch (errorValue: Exception) { if (errorValue is CancellationException) throw errorValue; error = errorValue.message }
                        finally { scanning = false }
                    }
                }) { Text(if (scanning) "Scanning BLE adapters…" else "Scan OBDLink CX (experimental BLE)") }
                devices.forEach { (name, value) -> FilterChip(address == value, { address = value }, { Text("$name · $value") }) }
            }
            Button(enabled = simulated || (!mode.usesGps || trackId.isNotBlank()) && (!mode.usesVehicle || address.isNotBlank()), onClick = {
                val required = buildList {
                    if (!simulated && mode.usesGps) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    if (!simulated && mode.usesVehicle && Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                if (required.isNotEmpty()) permissions.launch(required.toTypedArray()) else start()
            }, modifier = Modifier.fillMaxWidth()) { Text("Start recording") }
            Text("After granting permissions, press Start again. Simulation uses the built-in demo circuit.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.fillMaxSize()) {
      TopAppBar(
          title = { Text("Session recorder") },
          navigationIcon = { IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          } },
      )
      BoxWithConstraints(Modifier.weight(1f)) {
        val wide = maxWidth > maxHeight
        TrackDashPage {
            PageHeader("Prepare to record", "Configure sources while the vehicle is stationary.", eyebrow = "New session")
            if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Box(Modifier.weight(0.9f)) { modePanel() }
                Box(Modifier.weight(1.1f)) { sourcePanel() }
            } else {
                modePanel()
                sourcePanel()
            }
            Text("${state.message} · ${state.samples} stored samples", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.sessionId != null) Text("Last session: ${state.laps} laps. Open Sessions to review saved data.")
        }
      }
    }
}

fun formatTime(nanos: Long): String = if (nanos <= 0) "—" else "%d:%06.3f".format(nanos / 60_000_000_000, nanos % 60_000_000_000 / 1e9)
