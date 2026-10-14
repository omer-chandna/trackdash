package dev.trackdash.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.data.TrackDashApplication
import java.util.Locale

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val state by graph.recording.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineLarge)
        Text("OBD · ${state.obd.state}", style = MaterialTheme.typography.titleLarge)
        Text("Transport: ${state.transport} · ECU: ${state.obd.ecu.ifBlank { "—" }}")
        Text("Protocol: ${state.obd.protocol.ifBlank { "—" }}")
        Text("Firmware: ${state.obd.firmware.ifBlank { "—" }} · VIN: ${state.obd.vinStatus}")
        Text("Supported PIDs: ${state.obd.supported.sorted().joinToString { "%02X".format(Locale.US, it) }.ifBlank { "—" }}")
        Text("Queries: ${state.obd.queries} · Last latency: ${state.obd.latencyMillis} ms · Errors: ${state.obd.errors}")
        Text("GPS · ${when { !state.active -> "Inactive"; state.gps == null -> "Acquiring"; (state.gpsAgeSeconds ?: 0.0) > 3 -> "Lost"; state.gps!!.horizontalAccuracyMeters > 25 -> "Degraded"; else -> "Ready" }}", style = MaterialTheme.typography.titleLarge)
        state.gps?.let { fix ->
            Text("${fix.latitude}, ${fix.longitude} · ${fix.provider}")
            Text("Accuracy ${fix.horizontalAccuracyMeters} m · Age ${state.gpsAgeSeconds} s")
            Text("Delivered rate %.1f Hz · Rejected fixes ${state.rejectedFixes}".format(state.gpsRateHz))
            Text("Speed ${fix.speedMetersPerSecond ?: "—"} m/s · Bearing ${fix.bearingDegrees ?: "—"}°")
        }
        Text("Recorder · ${state.message}", style = MaterialTheme.typography.titleLarge)
        Text("${state.samples} stored samples · Source: ${if (state.simulated) "SIMULATION" else "DEVICE"}")
        Text("Database size: ${context.getDatabasePath("trackdash.db").length() / 1024} KiB (excludes pending WAL pages)")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("BMW enhanced sensors and BLE adapter drivers require verified device protocols. Oil-pressure switch status remains unavailable for generic OBD.")
    }
}
