package dev.trackdash.tracks

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
import dev.trackdash.core.gps.*
import dev.trackdash.data.*
import kotlinx.coroutines.*
import java.util.UUID

@Composable
fun TracksScreen() {
    val context = LocalContext.current
    val dao = (context.applicationContext as TrackDashApplication).database.dao()
    val tracks by dao.tracks().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var sharing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching {
            val track = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).reader().use {
                    val text = it.readTextBounded(128_000); TrackJson.decode(text)
                }
            }
            // Import as a copy; a shared id must never overwrite a local track silently.
            val copy = track.copy(id = UUID.randomUUID().toString())
            dao.saveTrack(TrackRow(copy.id, copy.name, TrackJson.encode(copy)))
            error = "Track imported"
        }.onFailure { error = "Import failed: ${it.message}" } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching {
            val json = requireNotNull(sharing)
            withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).writer().use { it.write(json) } }
            error = "Track exported"
        }.onFailure { error = "Export failed: ${it.message}" } }
    }
    val edit = editing
    if (edit != null) {
        TrackEditor(edit, onDone = { editing = null })
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Track library", style = MaterialTheme.typography.headlineLarge)
        Row { Button(onClick = { editing = "new" }) { Text("New track") }; TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Import JSON") } }
        error?.let { Text(it) }
        tracks.forEach { row ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleLarge)
                Row { TextButton(onClick = { editing = row.id }) { Text("Edit") }
                    TextButton(onClick = { sharing = row.json; export.launch("trackdash-track.json") }) { Text("Export") } }
            } }
        }
    }
}

private fun java.io.Reader.readTextBounded(maximum: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(4096)
    while (true) { val count = read(buffer); if (count < 0) break; require(result.length + count <= maximum) { "File too large" }; result.append(buffer, 0, count) }
    return result.toString()
}

@Composable
private fun TrackEditor(id: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as TrackDashApplication).database.dao()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(id) { mutableStateOf("New circuit") }
    val style by remember { Preferences(context.applicationContext).mapStyle }.collectAsStateWithLifecycle("")
    var gatesJson by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var gates by remember { mutableStateOf(emptyList<TimingGate>()) }
    var center by remember { mutableStateOf(GeoPoint(43.0, -79.0)) }
    var first by remember { mutableStateOf<GeoPoint?>(null) }
    var latitude by rememberSaveable { mutableStateOf("43.0") }
    var longitude by rememberSaveable { mutableStateOf("-79.0") }
    var direction by remember { mutableStateOf(CrossingDirection.EITHER) }
    var message by remember { mutableStateOf<String?>(null) }
    var drawing by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) {
        val track = gatesJson?.let { TrackJson.decode(it) } ?: dao.track(id)?.let { TrackJson.decode(it.json) }
        track?.let { name = it.name; gates = listOf(it.startFinish) + it.sectors; center = it.startFinish.start; latitude = center.latitude.toString(); longitude = center.longitude.toString() }
    }
    LaunchedEffect(gates, name) { if (gates.isNotEmpty() && name.isNotBlank()) gatesJson = TrackJson.encode(Track(id, name, gates.first(), gates.drop(1))) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Timing gate editor", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Track name") })
        CircuitSearch { point -> center = point; latitude = point.latitude.toString(); longitude = point.longitude.toString() }
        Row { OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f)); OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f)) }
        TextButton(onClick = { runCatching { center = GeoPoint(latitude.toDouble(), longitude.toDouble()) }.onFailure { message = "Enter valid coordinates" } }) { Text("Go to coordinates") }
        CircuitMap(gates, center, Modifier.fillMaxWidth().height(300.dp), styleUrl = style) { point ->
            if (drawing) {
                val start = first
                if (start == null) { first = point; message = "Tap the second endpoint" }
                else runCatching {
                    val gate = TimingGate(UUID.randomUUID().toString(), if (gates.isEmpty()) "Start / Finish" else "Sector ${gates.size}", start, point, direction)
                    val replacement = replacing
                    gates = if (replacement == null) gates + gate else gates.toMutableList().also { it[replacement] = gate.copy(id = it[replacement].id, name = it[replacement].name) }
                    first = null; replacing = null; drawing = false; message = "Gate saved"
                }.onFailure { message = "Gate must be 1–500 m long" }
            }
        }
        Text("Map background is offline. Gates work without map tiles. Endpoint order defines the crossing direction.")
        CrossingDirection.entries.forEach { value -> FilterChip(direction == value, { direction = value }, { Text(value.name.replace('_', ' ')) }) }
        Button(enabled = gates.size < 13 || drawing, onClick = { drawing = !drawing; first = null; replacing = null; message = if (drawing) "Tap two endpoints across the circuit" else "Drawing cancelled" }) { Text(if (drawing) "Cancel drawing" else if (gates.isEmpty()) "Draw start / finish" else "Draw sector gate") }
        first?.let { Text("First endpoint: %.6f, %.6f".format(it.latitude, it.longitude)) }
        message?.let { Text(it) }
        gates.forEachIndexed { index, gate ->
            Text("${index + 1}. ${gate.name} · ${gate.direction}")
            Row {
                TextButton(onClick = { replacing = index; first = null; drawing = true; direction = gate.direction; message = "Tap two replacement endpoints" }) { Text("Redraw") }
                TextButton(onClick = { gates = gates.toMutableList().also { it[index] = gate.copy(direction = CrossingDirection.entries[(gate.direction.ordinal + 1) % 3]) } }) { Text("Direction") }
                if (index > 1) TextButton(onClick = { gates = gates.toMutableList().also { java.util.Collections.swap(it, index, index - 1) } }) { Text("Move up") }
                TextButton(onClick = { gates = if (index == 0) emptyList() else gates.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
            }
        }
        Button(enabled = gates.isNotEmpty() && name.isNotBlank(), onClick = { scope.launch { runCatching {
            val track = Track(if (id == "new") UUID.randomUUID().toString() else id, name.trim(), gates.first(), gates.drop(1))
            dao.saveTrack(TrackRow(track.id, track.name, TrackJson.encode(track))); onDone()
        }.onFailure { message = "Save failed: ${it.message}" } } }) { Text("Save track") }
        TextButton(onClick = onDone) { Text("Cancel") }
    }
}
