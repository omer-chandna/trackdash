package dev.trackdash.settings

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
import dev.trackdash.core.SensorId
import dev.trackdash.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SettingsScreen() {
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vehicles & settings", style = MaterialTheme.typography.headlineLarge)
        FilterChip(vehicleId.isEmpty(), { vehicleId = "" }, { Text("Default profile") })
        vehicles.forEach { vehicle -> FilterChip(vehicleId == vehicle.id, { vehicleId = vehicle.id }, { Text("${vehicle.name} · ${vehicle.year}") }) }
        Text("Dashboard for selected profile", style = MaterialTheme.typography.titleLarge)
        Toggle("Fahrenheit", draft.fahrenheit) { draft = draft.copy(fahrenheit = it) }
        Toggle("Miles per hour", draft.mph) { draft = draft.copy(mph = it) }
        Toggle("Keep screen awake during recording", draft.keepAwake) { draft = draft.copy(keepAwake = it) }
        Toggle("Fullscreen recording", draft.fullscreen) { draft = draft.copy(fullscreen = it) }
        Toggle("Hide unsupported sensors", draft.hideUnsupported) { draft = draft.copy(hideUnsupported = it) }
        Toggle("Large gauges", draft.largeGauges) { draft = draft.copy(largeGauges = it) }
        Toggle("Gauge bars", draft.bars) { draft = draft.copy(bars = it) }
        SensorId.entries.forEach { sensor -> Toggle(sensor.name.replace('_', ' '), sensor in draft.gauges) { enabled -> draft = draft.copy(gauges = if (enabled) draft.gauges + sensor else draft.gauges - sensor) } }
        Text("Selected gauge order")
        draft.gauges.forEachIndexed { index, sensor -> Row {
            Text(sensor.name, Modifier.weight(1f))
            if (index > 0) TextButton(onClick = { draft = draft.copy(gauges = draft.gauges.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }) }) { Text("Up") }
        } }
        Toggle("Enable warnings", draft.alertsEnabled) { draft = draft.copy(alertsEnabled = it) }
        Toggle("Audible warnings", draft.audible) { draft = draft.copy(audible = it) }
        Text("Thresholds are user supplied, in °C and RPM. Blank disables that threshold. No BMW limits are assumed.")
        Limit("Coolant °C", draft.coolantLimit, -40.0..200.0) { draft = draft.copy(coolantLimit = it) }
        Limit("Oil °C", draft.oilLimit, -40.0..200.0) { draft = draft.copy(oilLimit = it) }
        Limit("Intake °C", draft.intakeLimit, -40.0..200.0) { draft = draft.copy(intakeLimit = it) }
        Limit("RPM warning / shift light", draft.rpmLimit, 1000.0..20_000.0) { draft = draft.copy(rpmLimit = it) }
        Button(onClick = { scope.launch { message = runCatching { preferences.saveDashboard(vehicleId, draft); "Profile settings saved. Recording uses settings from session start." }.getOrElse { "Save failed: ${it.message}" } } }) { Text("Save dashboard & warnings") }
        HorizontalDivider()
        Text("Map provider", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(mapUrl, { mapUrl = it }, label = { Text("HTTPS style URL (optional)") })
        Text("Use a provider you are authorized to access. Attribution remains visible. Empty uses an offline background with saved gates and traces.")
        Button(onClick = { scope.launch { message = runCatching { preferences.saveMapStyle(mapUrl.trim()); "Map provider saved" }.getOrElse { it.message } } }) { Text("Save map provider") }
        dev.trackdash.tracks.OfflineMaps()
        OutlinedTextField(searchUrl, { searchUrl = it }, label = { Text("Nominatim search endpoint (optional)") })
        Text("Configure your provider's HTTPS /search endpoint. Search sends only your typed query and requires network access.")
        Button(onClick = { scope.launch { message = runCatching { preferences.saveSearchEndpoint(searchUrl.trim()); "Search provider saved" }.getOrElse { it.message } } }) { Text("Save search provider") }
        HorizontalDivider()
        Text("Add vehicle", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Display name") })
        OutlinedTextField(maker, { maker = it.take(80) }, label = { Text("Manufacturer") })
        OutlinedTextField(model, { model = it.take(80) }, label = { Text("Model") })
        OutlinedTextField(year, { year = it }, label = { Text("Year") })
        OutlinedTextField(engine, { engine = it.take(80) }, label = { Text("Engine") })
        Button(onClick = { scope.launch { message = runCatching {
            require(name.isNotBlank() && year.toInt() in 1886..2100) { "Provide a name and valid year" }
            dao.saveVehicle(VehicleRow(UUID.randomUUID().toString(), name.trim(), maker.trim(), model.trim(), year.toInt(), engine.trim())); name = ""; "Vehicle saved"
        }.getOrElse { "Save failed: ${it.message}" } } }) { Text("Add vehicle profile") }
        message?.let { Text(it) }
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
