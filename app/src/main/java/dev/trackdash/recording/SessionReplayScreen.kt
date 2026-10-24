package dev.trackdash.recording

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.trackdash.core.GpsFix
import dev.trackdash.core.SensorId
import dev.trackdash.core.SensorStatus
import dev.trackdash.core.VehicleSample
import dev.trackdash.core.replay.RecordedTelemetryRow
import dev.trackdash.core.replay.ReplayEvent
import dev.trackdash.core.replay.SessionReplay
import dev.trackdash.data.DashboardPreferences
import dev.trackdash.data.TrackDashApplication
import dev.trackdash.ui.TrackDashPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Local, read-only playback: never starts a service, changes saved data, or sends telemetry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReplayScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as TrackDashApplication).database.dao()
    val scope = rememberCoroutineScope()
    var preferences by remember(sessionId) { mutableStateOf(DashboardPreferences()) }
    var vehicle by remember(sessionId) { mutableStateOf<VehicleSample?>(null) }
    var gps by remember(sessionId) { mutableStateOf<GpsFix?>(null) }
    var shift by remember(sessionId) { mutableStateOf(false) }
    var position by remember(sessionId) { mutableLongStateOf(0) }
    var malformed by remember(sessionId) { mutableIntStateOf(0) }
    var running by remember(sessionId) { mutableStateOf(false) }
    var ready by remember(sessionId) { mutableStateOf(false) }
    var speed by remember(sessionId) { mutableDoubleStateOf(1.0) }
    var status by remember(sessionId) { mutableStateOf("Loading saved recording…") }
    var messages by remember(sessionId) { mutableStateOf(emptyList<String>()) }
    var job by remember(sessionId) { mutableStateOf<Job?>(null) }
    var generation by remember(sessionId) { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    DisposableEffect(sessionId) { onDispose { job?.cancel() } }
    LaunchedEffect(sessionId) {
        try {
            val session = dao.session(sessionId)
            if (session == null || session.status == "RECORDING") {
                status = "Stop and save a recording before replaying it."
            } else {
                dao.events(sessionId, "settings-snapshot").first().firstOrNull()?.let { row ->
                    preferences = runCatching { Json.decodeFromString<DashboardPreferences>(row.payload) }.getOrDefault(DashboardPreferences())
                }
                ready = true
                status = "Ready. Long gaps are shortened to two seconds."
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { status = "This recording could not be opened for replay." }
    }

    fun start() {
        val playbackGeneration = ++generation
        job?.cancel()
        vehicle = null; gps = null; shift = false; position = 0; malformed = 0; messages = emptyList()
        running = true; status = "Playing saved data at ${speed.toInt()}×"
        job = scope.launch {
            try {
                var frames = 0L
                SessionReplay.events({ afterId, limit -> withContext(Dispatchers.IO) {
                    dao.samplePage(sessionId, afterId, limit).map { RecordedTelemetryRow(it.id, it.elapsedNanos, it.kind, it.payload) }
                } }, speed = speed).collect { event ->
                    position = event.positionNanos
                    when (event) {
                        is ReplayEvent.Vehicle -> {
                            frames++
                            vehicle = event.sample
                            val rpm = event.sample.readings.firstOrNull { it.sensorId == SensorId.ENGINE_RPM && it.status == SensorStatus.AVAILABLE }?.value
                            shift = nextShiftState(shift, rpm, preferences.rpmLimit)
                        }
                        is ReplayEvent.Gps -> { frames++; gps = event.fix }
                        is ReplayEvent.Alert -> messages = (messages + "${formatTime(event.positionNanos)} · ${event.state.name} · ${event.message}").takeLast(20)
                        is ReplayEvent.Malformed -> malformed++
                    }
                }
                status = if (frames == 0L) "No readable vehicle or GPS samples in this recording." else "Replay finished. Original recording is unchanged."
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { status = "Replay stopped because this recording could not be read." }
            finally { if (playbackGeneration == generation) running = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Session replay") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to session details") }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackDashPanel {
                Text("REPLAY · SAVED DATA, NOT LIVE", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                Text("Local playback only. Nothing is recorded, changed or shared. Playback preserves original sensor timestamps and delivery order.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (running) { job?.cancel(); status = "Replay stopped. Start again to replay from the beginning." }
                        else start()
                    }, enabled = ready) { Text(if (running) "Stop replay" else "Replay from start") }
                    listOf(1.0, 2.0, 4.0).forEach { rate ->
                        FilterChip(selected = speed == rate, onClick = { speed = rate }, enabled = !running,
                            label = { Text("${rate.toInt()}×") })
                    }
                }
                Text("${formatTime(position)} · $status")
                if (malformed > 0) Text("$malformed malformed saved payloads skipped", color = MaterialTheme.colorScheme.error)
            }
            messages.forEach { Text("RECORDED EVENT · $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
            vehicle?.let { LiveGauges(it, preferences, emptyList(), shift) }
            gps?.let { fix ->
                Text("RECORDED GPS", style = MaterialTheme.typography.titleMedium)
                RallyGaugeGrid(listOf(
                    RallyReading("GPS SPEED", fix.speedMetersPerSecond?.let { "%.0f".format(it * if (preferences.mph) 2.2369362921 else 3.6) } ?: "—", if (preferences.mph) "mph" else "km/h"),
                    RallyReading("GPS ACCURACY", "%.1f".format(fix.horizontalAccuracyMeters), "m"),
                ), columns = 2)
            }
        }
    }
}
