package dev.trackdash.tracks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import dev.trackdash.ui.*

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
    BackHandler(enabled = edit != null) { editing = null }
    if (edit != null) {
        TrackEditor(edit, onDone = { editing = null })
    } else TrackDashPage(scroll = false) {
        PageHeader(
            title = "Track library",
            subtitle = "Build and manage timing gates for saved circuits.",
            eyebrow = "Timing locations",
            actions = {
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Import JSON") }
                Button(onClick = { editing = "new" }) { Text("New track") }
            },
        )
        error?.let { StatusChip(it, if (it.startsWith("Import failed") || it.startsWith("Export failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        if (tracks.isEmpty()) {
            EmptyState("No tracks yet", "Create a circuit or import a TrackDash JSON file.", Modifier.fillMaxWidth()) {
                Button(onClick = { editing = "new" }) { Text("Create first track") }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tracks, key = { it.id }) { row ->
                    TrackDashPanel(Modifier.fillMaxWidth()) {
                        SectionTitle(row.name, "Saved timing-gate layout")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editing = row.id }) { Text("Edit") }
                            OutlinedButton(onClick = { sharing = row.json; export.launch("trackdash-track.json") }) { Text("Export") }
                        }
                    }
                }
            }
        }
    }
}

private fun java.io.Reader.readTextBounded(maximum: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(4096)
    while (true) { val count = read(buffer); if (count < 0) break; require(result.length + count <= maximum) { "File too large" }; result.append(buffer, 0, count) }
    return result.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val mapPane: @Composable (Modifier) -> Unit = { modifier ->
        TrackDashPanel(modifier, emphasized = true) {
            SectionTitle("Circuit map", "Tap two points across the racing line to place a gate.")
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("Track name") }, modifier = Modifier.fillMaxWidth())
            CircuitSearch { point -> center = point; latitude = point.latitude.toString(); longitude = point.longitude.toString() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { runCatching { center = GeoPoint(latitude.toDouble(), longitude.toDouble()) }.onFailure { message = "Enter valid coordinates" } }) { Text("Go to coordinates") }
            CircuitMap(gates, center, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp), styleUrl = style) { point ->
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
            Text("The map can remain offline. Endpoint order defines crossing direction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val controlsPane: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackDashPanel {
                SectionTitle("Gate controls", "${gates.size} of 13 gates configured")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CrossingDirection.entries.forEach { value -> FilterChip(direction == value, { direction = value }, { Text(value.name.replace('_', ' ')) }) }
                }
                Button(enabled = gates.size < 13 || drawing, onClick = { drawing = !drawing; first = null; replacing = null; message = if (drawing) "Tap two endpoints across the circuit" else "Drawing cancelled" }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (drawing) "Cancel drawing" else if (gates.isEmpty()) "Draw start / finish" else "Draw sector gate")
                }
                first?.let { Text("First endpoint: %.6f, %.6f".format(it.latitude, it.longitude), style = MaterialTheme.typography.bodySmall) }
                message?.let { StatusChip(it, if (it.startsWith("Save failed") || it.startsWith("Enter") || it.startsWith("Gate must")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
            if (gates.isNotEmpty()) TrackDashPanel {
                SectionTitle("Timing gates")
                gates.forEachIndexed { index, gate ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${index + 1}. ${gate.name}", style = MaterialTheme.typography.titleMedium)
                            Text(gate.direction.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = { replacing = index; first = null; drawing = true; direction = gate.direction; message = "Tap two replacement endpoints" }) { Text("Redraw") }
                                TextButton(onClick = { gates = gates.toMutableList().also { it[index] = gate.copy(direction = CrossingDirection.entries[(gate.direction.ordinal + 1) % 3]) } }) { Text("Direction") }
                                if (index > 1) TextButton(onClick = { gates = gates.toMutableList().also { java.util.Collections.swap(it, index, index - 1) } }) { Text("Up") }
                                TextButton(onClick = { gates = if (index == 0) emptyList() else gates.filterIndexed { i, _ -> i != index } }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            Button(enabled = gates.isNotEmpty() && name.isNotBlank(), onClick = { scope.launch { runCatching {
                val track = Track(if (id == "new") UUID.randomUUID().toString() else id, name.trim(), gates.first(), gates.drop(1))
                dao.saveTrack(TrackRow(track.id, track.name, TrackJson.encode(track))); onDone()
            }.onFailure { message = "Save failed: ${it.message}" } } }, modifier = Modifier.fillMaxWidth()) { Text("Save track") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
    Column(Modifier.fillMaxSize()) {
      TopAppBar(
          title = { Text("Timing gate editor") },
          navigationIcon = { IconButton(onClick = onDone) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to tracks")
          } },
      )
      BoxWithConstraints(Modifier.weight(1f)) {
        val wide = maxWidth >= 840.dp
        if (wide) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            mapPane(Modifier.weight(1.35f).fillMaxHeight())
            controlsPane(Modifier.weight(0.65f).fillMaxHeight().verticalScroll(rememberScrollState()))
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            mapPane(Modifier.fillMaxWidth())
            controlsPane(Modifier.fillMaxWidth())
        }
      }
    }
}
