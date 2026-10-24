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
import dev.trackdash.core.obd.ObdCapabilityCache
import dev.trackdash.core.obd.vehicleIdentityMismatch
import dev.trackdash.core.sharing.SharingStats
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
    val message: String = "Ready", val error: String? = null, val alerts: List<VehicleAlert> = emptyList(),
    val obd: ObdDiagnostics = ObdDiagnostics(), val rejectedFixes: Int = 0,
    val gpsRateHz: Double = 0.0, val theoreticalBestNanos: Long? = null,
    val dashboard: DashboardPreferences = DashboardPreferences(), val transport: String = "None",
    val shiftActive: Boolean = false, val usesGps: Boolean = true, val usesVehicle: Boolean = false,
    val gpsError: String? = null, val vehicleError: String? = null,
    val sharingEnabled: Boolean = false, val sharingStats: SharingStats = SharingStats(), val sharingMessage: String? = null)

internal fun nextShiftState(current: Boolean, rpm: Double?, limit: Double?): Boolean = when {
    rpm == null || limit == null -> false
    rpm >= limit -> true
    rpm < limit - 200.0 -> false
    else -> current
}

/** Acquisition lives in a foreground service. A bounded channel backpressures producers. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = application as TrackDashApplication
    private var recording: Job? = null
    private var ending = false
    private var timedOut = false
    private var explicitStop = false
    private var interrupted = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { explicitStop = true; if (recording == null) stopSelf() else recording?.cancel(); return START_NOT_STICKY }
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
                graph.recording.value = RecordingState(id, active = true, simulated = simulated, message = "Recording",
                    usesGps = mode.usesGps, usesVehicle = mode.usesVehicle)
                capture(id, mode, simulated, track, intent?.getStringExtra("address"), intent?.getStringExtra("vehicle") ?: "", intent?.getBooleanExtra("bleCx", false) ?: false)
            } catch (_: CancellationException) { /* Explicit stop / service destruction. */ }
            catch (error: Exception) { failure = error.message ?: error.javaClass.simpleName }
            finally {
                ending = true
                withContext(NonCancellable) {
                    if (id != null) runCatching { graph.database.dao().endSession(id, System.currentTimeMillis(),
                        if (timedOut || interrupted) "INTERRUPTED" else if (failure == null) "COMPLETE" else "ERROR") }
                        .onFailure { failure = "Could not finalize session: ${it.message}" }
                    graph.recording.update { it.copy(active = false, shiftActive = false,
                        message = if (timedOut) "Interrupted by Android service time limit" else if (interrupted) "Recording interrupted" else if (failure == null) "Saved" else "Recording failed", error = failure ?: it.error) }
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
        val tone = if (preferences.audible) runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() else null
        var audioAvailable = !preferences.audible || tone != null
        var activeAlerts = emptyMap<String, VehicleAlert>()
        var rpmActive = false
        var vehicleAlerts = emptyList<VehicleAlert>()
        var previousGpsNanos: Long? = null
        val beganNanos = SystemClock.elapsedRealtimeNanos()
        var latestVehicle: VehicleSample? = null
        val queue = Channel<SampleRow>(256)
        // Writer outlives producer cancellation long enough to drain every accepted sample.
        val writer = scope.async(Dispatchers.IO) {
            try {
                for (first in queue) {
                    val batch = mutableListOf(first)
                    while (batch.size < 64) batch += queue.tryReceive().getOrNull() ?: break
                    val writeBatch = graph.recordingBatchWriter
                    if (writeBatch == null) graph.database.dao().insertSamples(batch) else writeBatch(batch)
                    graph.recording.update { it.copy(samples = it.samples + batch.size) }
                }
            } catch (error: Exception) { queue.close(error); throw error }
        }
        suspend fun save(kind: String, nanos: Long, wall: Long, payload: String) = queue.send(SampleRow(sessionId = id, elapsedNanos = nanos, wallMillis = wall, kind = kind, payload = payload))
        val timer = track?.let { LapTimer(it) }
        var sharing: TelemetrySharingSession? = null
        try {
            // Observe storage failures immediately, even if neither source produces another sample.
            launch { writer.await() }
            try {
                sharing = if (mode.usesVehicle) TelemetrySharingSession.create(this@RecordingService, this) else null
                graph.recording.update { it.copy(sharingEnabled = sharing != null) }
                sharing?.let { session -> launch { session.stats.collect { stats ->
                    graph.recording.update { it.copy(sharingStats = stats) }
                } } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                graph.recording.update { it.copy(sharingMessage = "Sharing unavailable; local recording is unaffected") }
            }
            if (track != null) save("track-snapshot", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), TrackJson.encode(track))
            save("settings-snapshot", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), Json.encodeToString(preferences))
            if (mode.usesVehicle) launch {
                val source = graph.recordingSources.vehicle(this@RecordingService, simulated, address, bleCx)
                if (source is SimulatedVehicleSource) launch { graph.simulationControls.collect { source.selectScenario(it.vehicleScenario) } }
                if (source is ObdVehicleSource) launch { source.diagnostics.collect { diagnostics ->
                    graph.recording.update { it.copy(obd = diagnostics) }
                    if (diagnostics.supported.isNotEmpty() && vehicleId.isNotBlank()) {
                        graph.database.dao().vehicle(vehicleId)?.let { row ->
                            check(!vehicleIdentityMismatch(row.vin, diagnostics.vin)) {
                                "Different vehicle detected. Stop and choose the correct profile before recording."
                            }
                            val discovery = diagnostics.discovery
                            val changed = discovery != null && ObdCapabilityCache.decode(row.capabilities)?.discovery != discovery.copy(vin = null)
                            if (changed || diagnostics.vin != null && row.vin != diagnostics.vin)
                                graph.database.dao().saveVehicle(row.copy(
                                    capabilities = if (changed) ObdCapabilityCache.encode(requireNotNull(discovery)) else row.capabilities,
                                    vin = diagnostics.vin ?: row.vin))
                        }
                    }
                } }
                source.samples().recoverAcquisition(onFailure = {
                    graph.recording.update { state -> state.copy(vehicleError = "Vehicle source unavailable; retrying") }
                    save("error", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), Json.encodeToString("Vehicle source unavailable; retrying"))
                }).collect { sample ->
                    latestVehicle = sample
                    graph.recording.update { it.copy(vehicleError = if (sample.connected) null else "Vehicle data unavailable; reconnecting") }
                    save("vehicle", sample.elapsedRealtimeNanos, System.currentTimeMillis(), Json.encodeToString(sample))
                    sharing?.tryOffer(sample)
                }
            }
            if (mode.usesGps) launch {
                val source = graph.recordingSources.location(this@RecordingService, simulated)
                if (source is SimulatedGpsSource) launch {
                    var lastJump = graph.simulationControls.value.gpsJump
                    graph.simulationControls.collect {
                        source.lapSeconds.value = it.lapSeconds; source.dropout.value = it.gpsDropout
                        if (it.gpsJump != lastJump) { source.jump.value = true; lastJump = it.gpsJump }
                    }
                }
                source.fixes().recoverAcquisition(onFailure = {
                    timer?.markGap("GPS source unavailable")
                    graph.recording.update { state -> state.copy(gpsError = "GPS unavailable; retrying", gpsRateHz = 0.0) }
                    save("error", SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), Json.encodeToString("GPS source unavailable; retrying"))
                }).collect { fix ->
                    save("gps", fix.elapsedRealtimeNanos, fix.wallClockMillis, Json.encodeToString(fix))
                    val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1e9
                    if (age in 0.0..3.0) timer!!.accept(fix).forEach { lap ->
                        save("lap", lap.endNanos, fix.wallClockMillis, Json.encodeToString(lap))
                    } else timer!!.markGap("Stale GPS fix")
                    val rate = previousGpsNanos?.let { old -> if (fix.elapsedRealtimeNanos > old) 1e9 / (fix.elapsedRealtimeNanos - old) else 0.0 } ?: 0.0
                    previousGpsNanos = fix.elapsedRealtimeNanos
                    graph.recording.update { it.copy(gps = fix, gpsError = null, lastLap = timer.lastLap, bestLap = timer.bestLap, laps = timer.completedLaps,
                        rejectedFixes = timer.rejectedFixes, gpsRateHz = rate, theoreticalBestNanos = timer.theoreticalBestNanos) }
                }
            }
            launch {
                while (isActive) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val age = graph.recording.value.gps?.let { (now - it.elapsedRealtimeNanos) / 1e9 }
                    if (age != null && age > 3) timer?.markGap()
                    val effective = latestVehicle?.atMonitoringTime(now)
                    vehicleAlerts = effective?.let(alertEngine::evaluate) ?: emptyList()
                    val rpm = effective?.readings?.find { it.sensorId == SensorId.ENGINE_RPM && it.status == SensorStatus.AVAILABLE }?.value
                    rpmActive = nextShiftState(rpmActive, rpm, preferences.rpmLimit)
                    if (effective?.readings?.any { it.status == SensorStatus.STALE } == true) vehicleAlerts = vehicleAlerts + VehicleAlert(
                        "stale-sensors", "Stale vehicle sensor readings", AlertSeverity.WARNING, AlertCategory.DATA_QUALITY)
                    graph.recording.update { it.copy(vehicle = effective, shiftActive = rpmActive) }
                    val currentList = if (preferences.alertsEnabled) buildList {
                        addAll(vehicleAlerts)
                        if (!audioAvailable) add(VehicleAlert("audio-unavailable", "Audible warnings unavailable", AlertSeverity.WARNING, AlertCategory.DATA_QUALITY))
                        if (mode.usesVehicle && effective == null && now - beganNanos > VEHICLE_FRESHNESS_NANOS) add(VehicleAlert(
                            "vehicle-acquiring", "Vehicle data not available yet", AlertSeverity.WARNING, AlertCategory.CONNECTIVITY))
                        if (mode.usesGps && (graph.recording.value.gpsError != null || age != null && age > 3 || age == null && now - beganNanos > 10_000_000_000L)) add(VehicleAlert(
                            "gps-lost", if (age == null) "GPS fix not available yet" else "GPS signal lost", AlertSeverity.WARNING, AlertCategory.CONNECTIVITY))
                        if (mode.usesGps && graph.recording.value.gps?.horizontalAccuracyMeters?.let { it > 25 } == true) add(VehicleAlert(
                            "gps-accuracy", "GPS accuracy too poor for timing", AlertSeverity.WARNING, AlertCategory.DATA_QUALITY))
                        if (rpmActive) add(VehicleAlert(
                            "shift", "RPM above configured limit", AlertSeverity.CRITICAL, AlertCategory.SHIFT, SensorId.ENGINE_RPM))
                    } else emptyList()
                    val currentAlerts = currentList.associateBy { it.id }
                    (currentAlerts.keys - activeAlerts.keys).forEach { key ->
                        save("alert", now, System.currentTimeMillis(), Json.encodeToString(currentAlerts.getValue(key).message))
                    }
                    (activeAlerts.keys - currentAlerts.keys).forEach { key ->
                        save("alert-cleared", now, System.currentTimeMillis(), Json.encodeToString(activeAlerts.getValue(key).message))
                    }
                    currentAlerts.forEach { (key, alert) ->
                        if (activeAlerts[key]?.condition != null && activeAlerts[key]?.condition != alert.condition)
                            save(if (alert.condition == AlertCondition.UNCONFIRMED) "alert-unconfirmed" else "alert-confirmed",
                                now, System.currentTimeMillis(), Json.encodeToString(alert.message))
                    }
                    if ((currentAlerts.keys - activeAlerts.keys).isNotEmpty() && tone != null)
                        audioAvailable = runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300) }.getOrDefault(false)
                    activeAlerts = currentAlerts
                    graph.recording.update { it.copy(alerts = currentList.filterNot { alert -> alert.category == AlertCategory.SHIFT }) }
                    graph.recording.update { it.copy(gpsAgeSeconds = age, currentLapNanos = timer?.startedAtNanos?.let { start -> now - start } ?: 0) }
                    delay(200)
                }
            }
            awaitCancellation()
        } finally {
            sharing?.close()
            queue.close()
            try { withContext(NonCancellable) { writer.await() } } finally { runCatching { tone?.release() } }
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
    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        recording?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        val job = recording
        if (!ending && !explicitStop) interrupted = true
        job?.cancel()
        if (job == null) scope.cancel() else job.invokeOnCompletion { scope.cancel() }
        super.onDestroy()
    }
}

