package dev.trackdash.recording

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.*
import dev.trackdash.sources.discoverCxAdapters
import dev.trackdash.sources.discoverObdVehicle
import dev.trackdash.core.obd.*
import dev.trackdash.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun RecordingScreen(initialMode: String = OperatingMode.GPS_LAP_TIMER.name, onBack: () -> Unit, liveView: Boolean = false) {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val state by graph.recording.collectAsStateWithLifecycle()
    val controls by graph.simulationControls.collectAsStateWithLifecycle()
    val tracks by graph.database.dao().tracks().collectAsStateWithLifecycle(emptyList())
    val vehicles by graph.database.dao().vehicles().collectAsStateWithLifecycle(emptyList())
    var simulated by rememberSaveable { mutableStateOf(!liveView) }
    var modeName by rememberSaveable { mutableStateOf(initialMode) }
    val mode = if (liveView) OperatingMode.VEHICLE_DASHBOARD else OperatingMode.valueOf(modeName)
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
    var startRequested by rememberSaveable { mutableStateOf(false) }
    // Results belong to this exact setup, never to the address/profile that was previously selected.
    val discoveryKey = listOf(address, bleCx, vehicleId, simulated, modeName)
    var discovery by remember(discoveryKey) { mutableStateOf<ObdDiscoveryResult?>(null) }
    var discovering by remember(discoveryKey) { mutableStateOf(false) }
    var discoveryError by remember(discoveryKey) { mutableStateOf<String?>(null) }
    var discoveryJob by remember(discoveryKey) { mutableStateOf<Job?>(null) }
    var discoveryAtMillis by remember(discoveryKey) { mutableLongStateOf(0L) }
    var discoveryExpired by remember(discoveryKey) { mutableStateOf(false) }
    var compatibilityDialog by remember(discoveryKey) { mutableStateOf(false) }
    var reportVehicle by remember(discoveryKey) { mutableStateOf("") }
    var reportAdapter by remember(discoveryKey) { mutableStateOf("") }
    var pendingReport by rememberSaveable { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<String?>(null) }
    val exportCompatibility = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingReport
        pendingReport = null
        if (uri != null && payload != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot open the selected document" }
                        .use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
                reportMessage = "Compatibility report exported. Review it before sharing; this is a user report, not hardware certification."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { reportMessage = "Could not export report: ${failure.message}" }
        }
    }
    val selectedProfile = vehicles.firstOrNull { it.id == vehicleId }
    val cachedDiscovery = remember(selectedProfile?.capabilities) { ObdCapabilityCache.decode(selectedProfile?.capabilities.orEmpty()) }
    val identityMismatch = vehicleIdentityMismatch(selectedProfile?.vin, discovery?.vin)
    LaunchedEffect(discoveryAtMillis) {
        if (discoveryAtMillis > 0) { delay(120_000); discoveryExpired = true }
    }
    DisposableEffect(discoveryKey) { onDispose { discoveryJob?.cancel() } }
    LaunchedEffect(state.active) { if (state.active) discoveryJob?.cancel() }
    fun start() {
        startRequested = true
        runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)
            .putExtra(RecordingService.LIVE_VIEW_EXTRA, liveView)
            .putExtra("simulated", simulated).putExtra("mode", mode.name).putExtra("track", trackId)
            .putExtra("vehicle", vehicleId.ifBlank { null }).putExtra("address", address).putExtra("bleCx", bleCx)) }
            .onFailure { startRequested = false; error = it.message }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        error = if (it.any { entry -> !entry.value && entry.key != Manifest.permission.POST_NOTIFICATIONS }) "Required permission denied. Choose simulation or grant permission in Android settings." else null
    }
    val leave = {
        if (state.active && state.liveView || liveView && startRequested && !state.active)
            context.startService(Intent(context, RecordingService::class.java).setAction("STOP"))
        onBack()
    }
    BackHandler(enabled = liveView || state.active && state.liveView, onBack = leave)
    if (state.active) {
        RecordingDashboard(state, controls, { graph.simulationControls.value = it },
            { if (state.liveView) leave() else context.startService(Intent(context, RecordingService::class.java).setAction("STOP")) }, leave)
        return
    }
    val modePanel: @Composable () -> Unit = {
        TrackDashPanel {
            SectionTitle(if (liveView) "Live view" else "Session mode",
                if (liveView) "Performance gauges without recording or lap timing." else "Choose the data sources for this run.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(simulated, { simulated = it }, modifier = Modifier.semantics { contentDescription = "Simulation source" })
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Simulation", style = MaterialTheme.typography.titleMedium)
                    Text(if (simulated) "Uses safe synthetic data" else "Uses connected device sources",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (liveView) {
                Text("No telemetry, laps, or warning events are saved. Sharing is off even if enabled for recordings.")
                Text("Back or Stop live view ends monitoring. Rotation keeps it running.", style = MaterialTheme.typography.bodySmall)
            } else OperatingMode.entries.forEach { item ->
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
            SectionTitle("Sources", if (liveView) "Select your vehicle and adapter while parked."
                else if (simulated) "The built-in circuit and vehicle stream are ready." else "Select the hardware and track required for this mode.")
            if (!simulated && mode.usesGps) {
                Text("Track", style = MaterialTheme.typography.labelLarge)
                if (tracks.isEmpty()) Text("Create a track before recording GPS laps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                tracks.forEach { track -> FilterChip(trackId == track.id, { trackId = track.id }, { Text(track.name) }) }
            }
            if (mode.usesVehicle) {
                Text("Vehicle profile (optional)", style = MaterialTheme.typography.labelLarge)
                FilterChip(vehicleId.isEmpty(), { vehicleId = "" }, { Text("Default profile") })
                if (vehicles.isEmpty()) Text("No saved vehicles. Default dashboard settings will be used.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                vehicles.forEach { vehicle -> FilterChip(vehicleId == vehicle.id, { vehicleId = vehicle.id }, { Text(vehicle.name) }) }
            }
            if (!simulated && mode.usesVehicle) {
                OutlinedButton(enabled = !discovering && !scanning, onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    else runCatching { bleCx = false; address = ""; devices = context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map { (it.name ?: "Adapter") to it.address } ?: emptyList() }
                        .onFailure { error = it.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("Load paired Classic adapters") }
                Text("Pair a supported Classic Bluetooth adapter in Android settings, then select it here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !scanning && !discovering, onClick = {
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
                HorizontalDivider()
                Text("Check this vehicle", style = MaterialTheme.typography.titleMedium)
                Text("Keep the vehicle stationary with ignition on. Discovery uses read-only standard OBD-II queries; no manufacturer limits or enhanced sensors are assumed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (discovering) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Checking ECU support and reading advertised sensors… This can take up to two minutes.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { discoveryJob?.cancel() }) { Text("Cancel sensor check") }
                } else OutlinedButton(enabled = address.isNotBlank() && !scanning, modifier = Modifier.fillMaxWidth(), onClick = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                    else {
                        discovery = null; discoveryError = null; discovering = true; discoveryExpired = false
                        val scanVehicleId = vehicleId
                        discoveryJob = scope.launch {
                            try {
                                val result = discoverObdVehicle(context.applicationContext, address, bleCx)
                                ensureActive()
                                if (scanVehicleId.isNotBlank()) graph.database.dao().vehicle(scanVehicleId)?.let { row ->
                                    if (!liveView && !vehicleIdentityMismatch(row.vin, result.vin))
                                        graph.database.dao().saveVehicle(row.copy(capabilities = ObdCapabilityCache.encode(result), vin = result.vin ?: row.vin))
                                }
                                discovery = result
                                discoveryAtMillis = System.currentTimeMillis()
                                if (result.selectedEcu == null) discoveryError = "No usable sensor readings yet. Check ignition and adapter, then retry."
                            } catch (_: TimeoutCancellationException) { discoveryError = "Sensor check timed out. Check the adapter and retry." }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { discoveryError = "Sensor check failed: ${failure.message ?: "connection unavailable"}. Retry when ready." }
                            finally { discovering = false }
                        }
                    }
                }) { Text(if (discovery == null) "Discover available sensors" else "Check sensors again") }
                discoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (discovery == null && cachedDiscovery != null) {
                    Text("Saved check · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(cachedDiscovery.checkedAtMillis))}. Cached only — check this adapter and vehicle again before recording.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ObdDiscoverySummary(cachedDiscovery.discovery, cached = true)
                } else if (discovery == null && !selectedProfile?.capabilities.isNullOrBlank()) {
                    Text("This profile has an older capability list, not a verified sensor check. Discover again before recording.", style = MaterialTheme.typography.bodySmall)
                }
                discovery?.let { result ->
                    ObdDiscoverySummary(result, cached = false)
                    Text("Read during sensor check · ${java.text.DateFormat.getTimeInstance().format(java.util.Date(discoveryAtMillis))}. This is not a live feed.",
                        style = MaterialTheme.typography.bodySmall)
                    if (discoveryExpired) Text("This check expired. Check sensors again before recording.", color = MaterialTheme.colorScheme.error)
                    if (identityMismatch) Text("A different vehicle was detected. Choose or create the correct vehicle profile, or explicitly select Default profile and configure its limits. The saved vehicle identity has not been changed.", color = MaterialTheme.colorScheme.error)
                    if (result.vin == null) Text("Vehicle identity could not be verified. Confirm the selected profile and limits belong to this vehicle.", style = MaterialTheme.typography.bodySmall)
                    Text("Monitoring checks the connection again; these results are not a guarantee that readings will remain available.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { compatibilityDialog = true }) { Text("Export compatibility report") }
                }
                reportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (mode.usesVehicle) MonitoringReadinessPanel(preferences,
                discovery?.selected?.supportedSensors, if (discoveryExpired || identityMismatch) emptySet() else discovery?.selected?.availableSensors, simulated,
                discovery?.selected?.sensors?.filter { it.availability == ObdSensorAvailability.UNKNOWN }?.map { it.sensor }?.toSet().orEmpty())
            Button(enabled = simulated || (!mode.usesGps || trackId.isNotBlank()) && (!mode.usesVehicle || discovery?.selectedEcu != null && !discoveryExpired && !identityMismatch) && !discovering && !scanning, onClick = {
                val required = buildList {
                    if (!simulated && mode.usesGps) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    if (!simulated && mode.usesVehicle && Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                if (required.isNotEmpty()) permissions.launch(required.toTypedArray()) else start()
            }, modifier = Modifier.fillMaxWidth()) { Text(if (liveView) "Start live view" else "Start recording") }
            Text(if (liveView) "After granting permissions, press Start again. No session or telemetry history will be created."
                else "After granting permissions, press Start again. Simulation uses the built-in demo circuit.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.fillMaxSize()) {
      TopAppBar(
          title = { Text(if (liveView) "Live performance view" else "Session recorder") },
          navigationIcon = { IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          } },
      )
      BoxWithConstraints(Modifier.weight(1f)) {
        val wide = maxWidth > maxHeight
        TrackDashPage {
            PageHeader(if (liveView) "Prepare live view" else "Prepare to record",
                "Configure sources while the vehicle is stationary.", eyebrow = if (liveView) "Not recording" else "New session")
            if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Box(Modifier.weight(0.9f)) { modePanel() }
                Box(Modifier.weight(1.1f)) { sourcePanel() }
            } else {
                modePanel()
                sourcePanel()
            }
            Text(if (liveView) "View only · no telemetry saved · sharing off" else "${state.message} · ${state.samples} stored samples",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!liveView && state.sessionId != null) Text("Last session: ${state.laps} laps. Open Sessions to review saved data.")
        }
      }
    }
    if (compatibilityDialog && discovery != null) AlertDialog(
        onDismissRequest = { compatibilityDialog = false },
        title = { Text("Share compatibility evidence") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Exports sensor support and read results, standard ECU addresses, connection type and scan time. No VIN, Bluetooth address, location, notes or telemetry values are included. Nothing is uploaded automatically.")
            OutlinedTextField(reportVehicle, { reportVehicle = it.take(120) }, label = { Text("Vehicle model / year (optional)") }, singleLine = true)
            OutlinedTextField(reportAdapter, { reportAdapter = it.take(120) }, label = { Text("Adapter model (optional)") }, singleLine = true)
            Text("Do not enter VINs, registrations or personal information. A scan does not certify the hardware.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = {
            discovery?.let { result ->
                pendingReport = ObdCompatibilityReports.encode(result, discoveryAtMillis, bleCx, reportVehicle, reportAdapter)
                exportCompatibility.launch("trackdash-compatibility.json")
            }
            compatibilityDialog = false
        }) { Text("Choose save location") } },
        dismissButton = { TextButton(onClick = { compatibilityDialog = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ObdDiscoverySummary(result: ObdDiscoveryResult, cached: Boolean) {
    var expanded by remember(result, cached) { mutableStateOf(false) }
    val selected = result.selected
    Text(if (cached) "Previously selected ECU: ${result.selectedEcu ?: "none"}" else "Selected ECU: ${result.selectedEcu ?: "none usable"}",
        style = MaterialTheme.typography.titleSmall)
    Text("${selected?.availableSensors?.size ?: 0} sensors returned decoded readings · ${result.ecus.size} responding ECU(s)", style = MaterialTheme.typography.bodySmall)
    Text("Oil pressure is not monitored: no supported standard OBD-II pressure signal.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide sensor results" else "Show sensor results") }
    if (expanded) result.ecus.forEach { ecu ->
        Text("ECU ${ecu.ecu}${if (ecu.ecu == result.selectedEcu) " · selected" else ""}", style = MaterialTheme.typography.labelLarge)
        ecu.sensors.forEach { sensor ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sensor.sensor.name.lowercase().replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(when (sensor.availability) {
                    ObdSensorAvailability.AVAILABLE -> if (cached) "Previously read" else "Read successfully"
                    ObdSensorAvailability.UNAVAILABLE -> "Advertised · no data"
                    ObdSensorAvailability.UNSUPPORTED -> "Unsupported"
                    ObdSensorAvailability.UNKNOWN -> "Unknown · retry check"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun formatTime(nanos: Long): String = if (nanos <= 0) "—" else "%d:%06.3f".format(nanos / 60_000_000_000, nanos % 60_000_000_000 / 1e9)
