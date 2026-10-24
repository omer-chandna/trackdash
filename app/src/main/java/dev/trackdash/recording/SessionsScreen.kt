package dev.trackdash.recording

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.gps.TimedLap
import dev.trackdash.data.*
import dev.trackdash.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen() {
    val context = LocalContext.current
    val dao = (context.applicationContext as TrackDashApplication).database.dao()
    val sessions by dao.sessions().collectAsStateWithLifecycle(emptyList())
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionRow?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val id = exportId
        if (uri != null && id != null) scope.launch {
            val message = runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { SessionExport.write(dao, id, it) }
            }; "CSV exported" }.getOrElse { "Export failed: ${it.message}" }
            snackbar.showSnackbar(message)
        }
    }
    val selected = sessions.find { it.id == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null }
    LaunchedEffect(selectedId, sessions) { if (selectedId != null && selected == null) selectedId = null }

    fun requestExport(session: SessionRow) {
        exportId = session.id
        export.launch("trackdash-${session.id}.csv")
    }
    fun confirmDelete(session: SessionRow) {
        deleteTarget = null
        scope.launch {
            val result = runCatching { dao.deleteFinishedSession(session.id) }
            val message = result.fold(
                onSuccess = { count ->
                    if (count == 1) {
                        if (selectedId == session.id) selectedId = null
                        "Session deleted"
                    } else "Active sessions must be stopped and saved before deletion"
                },
                onFailure = { "Delete failed: ${it.message}" },
            )
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                TrackDashPage(scroll = false) {
                    PageHeader("Session history", "Review laps, notes, and captured telemetry.", eyebrow = "Local recordings")
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SessionList(sessions, selectedId, { selectedId = it.id }, { deleteTarget = it },
                            Modifier.widthIn(min = 320.dp, max = 390.dp).fillMaxHeight())
                        if (selected != null) {
                            TrackDashPanel(Modifier.weight(1f).fillMaxHeight(), emphasized = true) {
                                SessionDetailHeader(selected, onBack = { selectedId = null }, onDelete = { deleteTarget = selected })
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    item { SessionDetails(selected, dao, onExport = { requestExport(selected) }) }
                                }
                            }
                        } else {
                            EmptyState("Select a session", "Choose a saved session to inspect laps, notes, and telemetry.", Modifier.weight(1f))
                        }
                    }
                }
            } else if (selected != null) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("Session details") },
                        navigationIcon = { IconButton(onClick = { selectedId = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to sessions")
                        } },
                        actions = { DeleteSessionButton(selected, onDelete = { deleteTarget = selected }) },
                    )
                    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { SessionDetails(selected, dao, onExport = { requestExport(selected) }) }
                    }
                }
            } else {
                TrackDashPage(scroll = false) {
                    PageHeader("Session history", "Review laps, notes, and captured telemetry.", eyebrow = "Local recordings")
                    SessionList(sessions, selectedId, { selectedId = it.id }, { deleteTarget = it }, Modifier.weight(1f))
                }
            }
        }
    }

    deleteTarget?.let { session ->
        ConfirmDeleteDialog(
            title = "Delete this session?",
            body = "${sessionLabel(session)} will be permanently deleted with its telemetry samples, laps, events, and notes.",
            onConfirm = { confirmDelete(session) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionRow>, selectedId: String?, onSelect: (SessionRow) -> Unit,
    onDelete: (SessionRow) -> Unit, modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        EmptyState("No recorded sessions", "Completed sessions will appear here with lap and telemetry details.", modifier)
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sessions, key = { it.id }) { session ->
            Surface(
                onClick = { onSelect(session) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                color = if (selectedId == session.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selectedId == session.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sessionLabel(session), style = MaterialTheme.typography.titleMedium)
                        Text(modeLabel(session.mode),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChip(if (session.simulated) "Simulated" else "Device")
                            StatusChip(session.status, if (session.status == "RECORDING") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeleteSessionButton(session) { onDelete(session) }
                }
            }
        }
    }
}

@Composable
private fun DeleteSessionButton(session: SessionRow, onDelete: () -> Unit) {
    val label = modeLabel(session.mode).lowercase()
    IconButton(
        onClick = onDelete,
        enabled = session.status != "RECORDING",
        modifier = Modifier.semantics {
            contentDescription = if (session.status == "RECORDING")
                "Stop and save before deleting $label session" else "Delete $label session"
        },
    ) {
        Icon(Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = if (session.status == "RECORDING") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SessionDetailHeader(session: SessionRow, onBack: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to sessions") }
        Column(Modifier.weight(1f)) {
            Text("Session details", style = MaterialTheme.typography.titleLarge)
            Text(sessionLabel(session), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DeleteSessionButton(session, onDelete)
    }
}

private fun sessionLabel(session: SessionRow): String = DateFormat.getDateTimeInstance().format(Date(session.startedMillis))
private fun modeLabel(mode: String): String = mode.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }

@Composable
private fun SessionDetails(session: SessionRow, dao: TrackDashDao, onExport: () -> Unit) {
    val samples by dao.sampleCount(session.id).collectAsStateWithLifecycle(0L)
    val rows by dao.events(session.id, "lap").collectAsStateWithLifecycle(emptyList())
    val laps = remember(rows) { rows.mapNotNull { runCatching { Json.decodeFromString<TimedLap>(it.payload) }.getOrNull() } }
    var notes by rememberSaveable(session.id) { mutableStateOf(session.notes) }
    var noteStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (session.simulated) "SIMULATED SESSION" else "DEVICE SESSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(session.status, style = MaterialTheme.typography.headlineMedium)
            }
            StatusChip("$samples samples")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Laps", laps.size.toString(), "total", Modifier.weight(1f))
            MetricTile("Best valid", laps.filter { it.valid }.minOfOrNull { it.durationNanos }?.let(::formatTime) ?: "—", "lap", Modifier.weight(1f))
        }
        if (laps.isNotEmpty()) {
            SectionTitle("Lap breakdown")
            laps.forEach { lap ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Lap ${lap.number} · ${formatTime(lap.durationNanos)}", style = MaterialTheme.typography.titleMedium)
                        Text(if (lap.valid) "Valid lap" else "Invalid · ${lap.invalidReasons.joinToString()}",
                            color = if (lap.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        if (lap.sectorNanos.isNotEmpty()) Text("Sectors · ${lap.sectorNanos.joinToString { formatTime(it) }}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        SectionTitle("Session notes")
        OutlinedTextField(notes, { notes = it.take(10_000) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { noteStatus = runCatching { dao.setNotes(session.id, notes); "Notes saved" }.getOrElse { "Save failed: ${it.message}" } } }) { Text("Save notes") }
            OutlinedButton(onClick = onExport, enabled = session.status != "RECORDING") {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
            }
        }
        if (session.status == "RECORDING") Text("Stop and save this recording before exporting or deleting it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (noteStatus.isNotEmpty()) Text(noteStatus, color = MaterialTheme.colorScheme.primary)
        AnalysisPanel(session, dao, laps)
    }
}
