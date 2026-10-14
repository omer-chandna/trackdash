package dev.trackdash.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import dev.trackdash.MainActivity
import dev.trackdash.core.*
import dev.trackdash.core.gps.*
import dev.trackdash.core.simulation.*
import dev.trackdash.core.telemetry.*
import android.media.AudioManager
import android.media.ToneGenerator
import dev.trackdash.data.*
import dev.trackdash.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class RecordingState(val sessionId: String? = null, val active: Boolean = false,
    val simulated: Boolean = true, val vehicle: VehicleSample? = null, val gps: GpsFix? = null,
    val currentLapNanos: Long = 0, val lastLap: TimedLap? = null, val bestLap: TimedLap? = null,
    val laps: Int = 0, val samples: Long = 0, val gpsAgeSeconds: Double? = null,
    val message: String = "Ready", val error: String? = null, val alerts: List<String> = emptyList(),
    val obd: ObdDiagnostics = ObdDiagnostics(), val rejectedFixes: Int = 0,
    val gpsRateHz: Double = 0.0, val theoreticalBestNanos: Long? = null,
    val dashboard: DashboardPreferences = DashboardPreferences(), val transport: String = "None")

/** Acquisition lives in a foreground service. A bounded channel backpressures producers. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = application as TrackDashApplication
    private var recording: Job? = null
    private var ending = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { if (recording == null) stopSelf() else recording?.cancel(); return START_NOT_STICKY }
        if (recording != null || ending) return START_NOT_STICKY
        val simulated = intent?.getBooleanExtra("simulated", true) ?: true
        val mode = runCatching { OperatingMode.valueOf(intent?.getStringExtra("mode") ?: "GPS_LAP_TIMER") }.getOrDefault(OperatingMode.GPS_LAP_TIMER)
        try { foreground(simulated, mode) } catch (error: Exception) {
            graph.recording.value = RecordingState(error = error.message); stopSelf(); return START_NOT_STICKY
        }
        recording = scope.launch {
            var id: String? = null
            var failure: String? = null
            try {
                graph.ready.await()
                val track = if (mode.usesGps) {
                    if (simulated) DemoCircuit.track else graph.database.dao().track(requireNotNull(intent?.getStringExtra("track")))?.let { TrackJson.decode(it.json) }
                        ?: error("Choose a saved track")
                } else null
                id = UUID.randomUUID().toString()
                graph.database.dao().insertSession(SessionRow(id, System.currentTimeMillis(), mode = mode.name,
                    simulated = simulated, trackId = track?.id, vehicleId = intent?.getStringExtra("vehicle")))
                graph.recording.value = RecordingState(id, active = true, simulated = simulated, message = "Recording")
                capture(id, mode, simulated, track, intent?.getStringExtra("address"), intent?.getStringExtra("vehicle") ?: "", intent?.getBooleanExtra("bleCx", false) ?: false)
            } catch (_: CancellationException) { /* Explicit stop / service destruction. */ }
            catch (error: Exception) { failure = error.message ?: error.javaClass.simpleName }
            finally {
                ending = true
                withContext(NonCancellable) {
                    if (id != null) runCatching { graph.database.dao().endSession(id, System.currentTimeMillis(), if (failure == null) "COMPLETE" else "ERROR") }
                        .onFailure { failure = "Could not finalize session: ${it.message}" }
                    graph.recording.update { it.copy(active = false, message = if (failure == null) "Saved" else "Recording failed", error = failure ?: it.error) }
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun capture(id: String, mode: OperatingMode, simulated: Boolean, track: Track?, address: String?, vehicleId: String, bleCx: Boolean): Unit = coroutineScope {
        val preferences = Preferences(this@RecordingService).dashboard(vehicleId).first()
        graph.recording.update { it.copy(dashboard = preferences, transport = if (simulated) "Simulation" else if (!mode.usesVehicle) "None" else if (bleCx) "OBDLink CX BLE (experimental)" else "Classic RFCOMM") }
        val alertEngine = VehicleAlertEngine(buildList {
            preferences.coolantLimit?.let { add(TemperatureRule(SensorId.COOLANT_TEMPERATURE, it)) }
            preferences.oilLimit?.let { add(TemperatureRule(SensorId.OIL_TEMPERATURE, it)) }
            preferences.intakeLimit?.let { add(TemperatureRule(SensorId.INTAKE_TEMPERATURE, it)) }
        })
        val tone = if (preferences.audible) ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) else null
        var activeAlerts = emptySet<String>()
        var rpmActive = false
        var rpmSince: Long? = null
        var vehicleAlerts = emptyList<String>()
        var previousGpsNanos: Long? = null
        val queue = Channel<SampleRow>(256)
        // Writer outlives producer cancellation long enough to drain every accepted sample.
        val writer = scope.async(Dispatchers.IO) {
            try {
                for (first in queue) {
                    val batch = mutableListOf(first)
                    while (batch.size < 64) batch += queue.tryReceive().getOrNull() ?: break
                    graph.database.dao().insertSamples(batch)
                    graph.recording.update { it.copy(samples = it.samples + batch.size) }
                }
            } catch (error: Exception) { queue.close(error); throw error }
        }
        suspend fun save(kind: String, nanos: Long, wall: Long, payload: String) = queue.send(SampleRow(sessionId = id, elapsedNanos = nanos, wallMillis = wall, kind = kind, payload = payload))
        val timer = track?.let { LapTimer(it) }
        try {
            if (track != null) save("track-snapshot", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), TrackJson.encode(track))
            save("settings-snapshot", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), Json.encodeToString(preferences))
            if (mode.usesVehicle) launch {
                val source: VehicleSource = if (simulated) SimulatedVehicleSource(SystemClock::elapsedRealtimeNanos)
                    else ObdVehicleSource(this@RecordingService, requireNotNull(address) { "Choose an adapter" }, bleCx)
                if (source is SimulatedVehicleSource) launch { graph.simulationControls.collect { source.selectScenario(it.vehicleScenario) } }
                if (source is ObdVehicleSource) launch { source.diagnostics.collect { diagnostics ->
                    graph.recording.update { it.copy(obd = diagnostics) }
                    if (diagnostics.supported.isNotEmpty() && vehicleId.isNotBlank()) {
                        graph.database.dao().vehicle(vehicleId)?.let { row ->
                            val capabilities = diagnostics.supported.sorted().joinToString { "%02X".format(it) }
                            if (row.capabilities != capabilities || diagnostics.vin != null && row.vin != diagnostics.vin)
                                graph.database.dao().saveVehicle(row.copy(capabilities = capabilities, vin = diagnostics.vin ?: row.vin))
                        }
                    }
                } }
                try { source.samples().collect { sample ->
                    graph.recording.update { it.copy(vehicle = sample) }
                    vehicleAlerts = alertEngine.evaluate(sample).map { it.message }
                    val rpm = sample.readings.find { it.sensorId == SensorId.ENGINE_RPM && it.status == SensorStatus.AVAILABLE }?.value
                    val limit = preferences.rpmLimit
                    if (rpm == null || limit == null || rpm < limit - 200) { rpmActive = false; rpmSince = null }
                    else if (rpm >= limit) {
                        if (rpmSince == null) rpmSince = sample.elapsedRealtimeNanos
                        if (sample.elapsedRealtimeNanos - rpmSince!! >= 300_000_000) rpmActive = true
                    } else if (!rpmActive) rpmSince = null
                    if (rpmActive) vehicleAlerts = vehicleAlerts + "RPM above configured limit"
                    if (sample.readings.any { it.status == SensorStatus.STALE }) vehicleAlerts = vehicleAlerts + "Stale vehicle sensor readings"
                    save("vehicle", sample.elapsedRealtimeNanos, System.currentTimeMillis(), Json.encodeToString(sample))
                } } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (!mode.usesGps) throw error
                    graph.recording.update { it.copy(error = "OBD stopped: ${error.message}; GPS recording continues") }
                    save("error", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), Json.encodeToString("OBD: ${error.message}"))
                }
            }
            if (mode.usesGps) launch {
                val source: LocationSource = if (simulated) SimulatedGpsSource(SystemClock::elapsedRealtimeNanos, System::currentTimeMillis)
                    else AndroidGpsSource(this@RecordingService)
                if (source is SimulatedGpsSource) launch {
                    var lastJump = graph.simulationControls.value.gpsJump
                    graph.simulationControls.collect {
                        source.lapSeconds.value = it.lapSeconds; source.dropout.value = it.gpsDropout
                        if (it.gpsJump != lastJump) { source.jump.value = true; lastJump = it.gpsJump }
                    }
                }
                source.fixes().collect { fix ->
                    save("gps", fix.elapsedRealtimeNanos, fix.wallClockMillis, Json.encodeToString(fix))
                    val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1e9
                    if (age in 0.0..3.0) timer!!.accept(fix).forEach { lap ->
                        save("lap", lap.endNanos, fix.wallClockMillis, Json.encodeToString(lap))
                    } else timer!!.markGap("Stale GPS fix")
                    val rate = previousGpsNanos?.let { old -> if (fix.elapsedRealtimeNanos > old) 1e9 / (fix.elapsedRealtimeNanos - old) else 0.0 } ?: 0.0
                    previousGpsNanos = fix.elapsedRealtimeNanos
                    graph.recording.update { it.copy(gps = fix, lastLap = timer.lastLap, bestLap = timer.bestLap, laps = timer.completedLaps,
                        rejectedFixes = timer.rejectedFixes, gpsRateHz = rate, theoreticalBestNanos = timer.theoreticalBestNanos) }
                }
            }
            launch {
                while (isActive) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val age = graph.recording.value.gps?.let { (now - it.elapsedRealtimeNanos) / 1e9 }
                    if (age != null && age > 3) timer?.markGap()
                    val currentAlerts = if (preferences.alertsEnabled) (vehicleAlerts + if (mode.usesGps && age != null && age > 3) listOf("GPS signal lost") else emptyList()).toSet() else emptySet()
                    (currentAlerts - activeAlerts).forEach { alert -> save("alert", now, System.currentTimeMillis(), Json.encodeToString(alert)) }
                    (activeAlerts - currentAlerts).forEach { alert -> save("alert-cleared", now, System.currentTimeMillis(), Json.encodeToString(alert)) }
                    if ((currentAlerts - activeAlerts).isNotEmpty()) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    activeAlerts = currentAlerts
                    graph.recording.update { it.copy(alerts = currentAlerts.toList()) }
                    graph.recording.update { it.copy(gpsAgeSeconds = age, currentLapNanos = timer?.startedAtNanos?.let { start -> now - start } ?: 0) }
                    delay(200)
                }
            }
            awaitCancellation()
        } finally {
            queue.close()
            try { withContext(NonCancellable) { writer.await() } } finally { tone?.release() }
        }
    }

    private fun foreground(simulated: Boolean, mode: OperatingMode) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("recording", "Recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "recording").setContentTitle("TrackDash recording")
            .setContentText(if (simulated) "SIMULATED DATA" else mode.name.replace('_', ' '))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop and save", stop).build()).build()
        val type = if (simulated) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else
            (if (mode.usesGps) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) or
                (if (mode.usesVehicle) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        startForeground(17, notification, type)
    }
    override fun onTimeout(startId: Int, fgsType: Int) { recording?.cancel() }
    override fun onDestroy() {
        val job = recording
        job?.cancel()
        if (job == null) scope.cancel() else job.invokeOnCompletion { scope.cancel() }
        super.onDestroy()
    }
}

