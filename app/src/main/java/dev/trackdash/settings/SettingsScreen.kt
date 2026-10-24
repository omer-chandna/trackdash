package dev.trackdash.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.SensorId
import dev.trackdash.data.*
import kotlinx.coroutines.launch
import java.util.UUID
import dev.trackdash.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onConnectionStatus: () -> Unit, onOpenSource: () -> Unit) {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val dao = graph.database.dao()
    val vehicles by dao.vehicles().collectAsStateWithLifecycle(emptyList())
    var vehicleId by rememberSaveable { mutableStateOf("") }
    val preferences = remember { Preferences(context.applicationContext) }
    val persisted by remember(vehicleId) { preferences.dashboard(vehicleId) }.collectAsStateWithLifecycle(DashboardPreferences())
    var draft by remember { mutableStateOf(persisted) }
    LaunchedEffect(persisted) { draft = persisted }
    val style by preferences.mapStyle.collectAsStateWithLifecycle("")
    var mapUrl by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(style) { mapUrl = style }
    val endpoint by preferences.searchEndpoint.collectAsStateWithLifecycle("")
    var searchUrl by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(endpoint) { searchUrl = endpoint }
    var name by rememberSaveable { mutableStateOf("") }
    var maker by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dashboardPanel: @Composable () -> Unit = {
        TrackDashPanel {
            SectionTitle("Dashboard", "Display preferences for the selected vehicle profile.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vehicleId.isEmpty(), { vehicleId = "" }, { Text("Default profile") })
                vehicles.forEach { vehicle -> FilterChip(vehicleId == vehicle.id, { vehicleId = vehicle.id }, { Text("${vehicle.name} · ${vehicle.year}") }) }
            }
            Toggle("Fahrenheit", draft.fahrenheit) { draft = draft.copy(fahrenheit = it) }
            Toggle("Miles per hour", draft.mph) { draft = draft.copy(mph = it) }
            Toggle("Keep screen awake during recording", draft.keepAwake) { draft = draft.copy(keepAwake = it) }
            Toggle("Fullscreen recording", draft.fullscreen) { draft = draft.copy(fullscreen = it) }
            Toggle("Hide unsupported sensors", draft.hideUnsupported) { draft = draft.copy(hideUnsupported = it) }
            Toggle("Large gauges", draft.largeGauges) { draft = draft.copy(largeGauges = it) }
            Toggle("Gauge bars", draft.bars) { draft = draft.copy(bars = it) }
            Text("Visible gauges", style = MaterialTheme.typography.labelLarge)
            SensorId.entries.forEach { sensor -> Toggle(sensor.name.replace('_', ' '), sensor in draft.gauges) { enabled -> draft = draft.copy(gauges = if (enabled) draft.gauges + sensor else draft.gauges - sensor) } }
            Text("Gauge order", style = MaterialTheme.typography.labelLarge)
            draft.gauges.forEachIndexed { index, sensor -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(sensor.name.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (index > 0) TextButton(onClick = { draft = draft.copy(gauges = draft.gauges.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }) }) { Text("Move up") }
            } }
        }
    }
    val warningPanel: @Composable () -> Unit = {
        TrackDashPanel(emphasized = true) {
            SectionTitle("Warnings & shift light", "User-configured performance thresholds.")
            Toggle("Enable warnings", draft.alertsEnabled) { draft = draft.copy(alertsEnabled = it) }
            Toggle("Audible warnings", draft.audible) { draft = draft.copy(audible = it) }
            Text("Thresholds are user supplied in °C and RPM. Blank disables a threshold. No manufacturer limits are assumed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Limit("Coolant °C", draft.coolantLimit, -40.0..200.0) { draft = draft.copy(coolantLimit = it) }
            Limit("Oil °C", draft.oilLimit, -40.0..200.0) { draft = draft.copy(oilLimit = it) }
            Limit("Intake °C", draft.intakeLimit, -40.0..200.0) { draft = draft.copy(intakeLimit = it) }
            Limit("Shift RPM / redline", draft.rpmLimit, 1000.0..20_000.0) { draft = draft.copy(rpmLimit = it) }
            Button(onClick = { scope.launch { message = runCatching { preferences.saveDashboard(vehicleId, draft); "Profile settings saved. Recording uses settings from session start." }.getOrElse { "Save failed: ${it.message}" } } }, modifier = Modifier.fillMaxWidth()) { Text("Save dashboard & warnings") }
        }
    }
    val systemPanel: @Composable () -> Unit = {
        TrackDashPanel {
            SectionTitle("Maps & advanced", "Optional providers and live source health.")
            OutlinedButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth()) { Text("About & open-source licenses") }
            OutlinedButton(onClick = onConnectionStatus, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Info, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Connection status")
            }
            Text("Adapter, GPS, sensor availability, and recorder health details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            OutlinedTextField(mapUrl, { mapUrl = it }, label = { Text("HTTPS map style URL (optional)") }, modifier = Modifier.fillMaxWidth())
            Text("Empty uses the offline background with saved gates and traces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { scope.launch { message = runCatching { preferences.saveMapStyle(mapUrl.trim()); "Map provider saved" }.getOrElse { it.message } } }) { Text("Save map provider") }
            dev.trackdash.tracks.OfflineMaps()
            OutlinedTextField(searchUrl, { searchUrl = it }, label = { Text("Search endpoint (optional)") }, modifier = Modifier.fillMaxWidth())
            Text("Search sends only the typed query and requires network access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { scope.launch { message = runCatching { preferences.saveSearchEndpoint(searchUrl.trim()); "Search provider saved" }.getOrElse { it.message } } }) { Text("Save search provider") }
        }
    }
    val vehiclePanel: @Composable () -> Unit = {
        TrackDashPanel {
            SectionTitle("Add vehicle", "Save an identity for vehicle-specific dashboard settings.")
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maker, { maker = it.take(80) }, label = { Text("Manufacturer") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it.take(80) }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(year, { year = it }, label = { Text("Year") }, modifier = Modifier.weight(1f))
                OutlinedTextField(engine, { engine = it.take(80) }, label = { Text("Engine") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = { scope.launch { message = runCatching {
                require(name.isNotBlank() && year.toInt() in 1886..2100) { "Provide a name and valid year" }
                dao.saveVehicle(VehicleRow(UUID.randomUUID().toString(), name.trim(), maker.trim(), model.trim(), year.toInt(), engine.trim())); name = ""; "Vehicle saved"
            }.getOrElse { "Save failed: ${it.message}" } } }, modifier = Modifier.fillMaxWidth()) { Text("Add vehicle profile") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Vehicles & settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp
            TrackDashPage {
                PageHeader("Settings", "Tune the cockpit, warnings, maps, and vehicle profiles.", eyebrow = "TrackDash configuration")
                message?.let { StatusChip(it, if (it.startsWith("Save failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { dashboardPanel(); warningPanel() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { systemPanel(); vehiclePanel() }
                } else {
                    dashboardPanel(); warningPanel(); systemPanel(); vehiclePanel()
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f).padding(top = 12.dp)); Switch(value, change) } }
@Composable
private fun Limit(label: String, value: Double?, range: ClosedFloatingPointRange<Double>, change: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = value?.toString() ?: "" }
    val valid = text.isBlank() || text.toDoubleOrNull()?.let { it.isFinite() && it in range } == true
    OutlinedTextField(text, { text = it; if (it.isBlank()) change(null) else it.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n in range }?.let(change) }, label = { Text(label) }, isError = !valid)
}
