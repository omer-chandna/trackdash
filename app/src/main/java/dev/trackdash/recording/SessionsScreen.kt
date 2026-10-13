package dev.trackdash.recording

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.gps.TimedLap
import dev.trackdash.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionsScreen() {
    val context = LocalContext.current
    val dao = (context.applicationContext as TrackDashApplication).database.dao()
    val sessions by dao.sessions().collectAsStateWithLifecycle(emptyList())
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val id = exportId
        if (uri != null && id != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { SessionExport.write(dao, id, it) }
            }; "CSV exported" }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val session = sessions.find { it.id == selected }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Session history", style = MaterialTheme.typography.headlineLarge) }
        message?.let { item { Text(it) } }
        if (session == null) {
            if (sessions.isEmpty()) item { Text("No recorded sessions") }
            items(sessions, key = { it.id }) { row ->
                OutlinedCard(onClick = { selected = row.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(DateFormat.getDateTimeInstance().format(Date(row.startedMillis)))
                        Text("${if (row.simulated) "SIMULATED" else "DEVICE"} · ${row.mode.replace('_', ' ')}")
                        Text(row.status)
                    }
                }
            }
        } else {
            item { TextButton(onClick = { selected = null }) { Text("Back to sessions") } }
            item { SessionDetails(session, dao, onExport = { exportId = session.id; export.launch("trackdash-${session.id}.csv") }) }
        }
    }
}

@Composable
private fun SessionDetails(session: SessionRow, dao: TrackDashDao, onExport: () -> Unit) {
    val samples by dao.sampleCount(session.id).collectAsStateWithLifecycle(0L)
    val rows by dao.events(session.id, "lap").collectAsStateWithLifecycle(emptyList())
    val laps = remember(rows) { rows.mapNotNull { runCatching { Json.decodeFromString<TimedLap>(it.payload) }.getOrNull() } }
    var notes by rememberSaveable(session.id) { mutableStateOf(session.notes) }
    var noteStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${if (session.simulated) "SIMULATED" else "DEVICE"} · ${session.status}", style = MaterialTheme.typography.titleLarge)
        Text("$samples stored samples · ${laps.size} laps")
        Text("Best valid lap: ${laps.filter { it.valid }.minOfOrNull { it.durationNanos }?.let(::formatTime) ?: "—"}")
        laps.forEach { lap ->
            Text("Lap ${lap.number} · ${formatTime(lap.durationNanos)} · ${if (lap.valid) "Valid" else lap.invalidReasons.joinToString()}")
            Text("Sectors: ${lap.sectorNanos.joinToString { formatTime(it) }}")
        }
        OutlinedTextField(notes, { notes = it.take(10_000) }, label = { Text("Session notes") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { scope.launch { noteStatus = runCatching { dao.setNotes(session.id, notes); "Notes saved" }.getOrElse { "Save failed: ${it.message}" } } }) { Text("Save notes") }
        if (noteStatus.isNotEmpty()) Text(noteStatus)
        Button(onClick = onExport, enabled = session.status != "RECORDING") { Text("Export CSV") }
        AnalysisPanel(session, dao, laps)
    }
}
