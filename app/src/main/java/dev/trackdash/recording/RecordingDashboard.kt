package dev.trackdash.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.trackdash.core.simulation.SimulationScenario
import dev.trackdash.data.SimulationControls

internal fun gpsSpeedForDisplay(state: RecordingState): Double? = state.gps?.takeIf {
    state.gpsError == null && state.gpsAgeSeconds?.let { age -> age in 0.0..3.0 } == true && it.horizontalAccuracyMeters <= 25.0
}?.speedMetersPerSecond

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDashboard(state: RecordingState, controls: SimulationControls,
    onControls: (SimulationControls) -> Unit, onStop: () -> Unit, onBack: () -> Unit) {
    var controlsOpen by remember { mutableStateOf(false) }
    DashboardAlertLayer(state.alerts, state.shiftActive) {
        Column(Modifier.fillMaxSize()) {
            RallyHeader(if (state.liveView) (if (state.simulated) "SIMULATED · " else "") + "LIVE VIEW · NOT RECORDING"
                else if (state.simulated) "SIMULATED · RECORDING" else "LIVE · RECORDING", onBack) {
                if (state.simulated) IconButton(onClick = { controlsOpen = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Simulation controls")
                }
                OutlinedButton(onClick = onStop) { Text(if (state.liveView) "Stop live view" else "Stop and save") }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight && maxHeight >= 280.dp
                if (wide) LandscapeDashboard(state)
                else PortraitDashboard(state, onOpenControls = { controlsOpen = true })
            }
        }
    }
    if (controlsOpen) SimulationControlsDialog(controls, onControls, vehicleOnly = state.liveView) { controlsOpen = false }
}

@Composable
private fun LandscapeDashboard(state: RecordingState) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AlertBanners(state.alerts)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!state.liveView && (state.gps != null || state.vehicle == null)) Column(Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RallyMetric(RallyReading("CURRENT LAP", formatTime(state.currentLapNanos), tag = "current-lap-value"),
                    Modifier.fillMaxWidth().weight(1.3f), maximumSp = 80)
                RallyMetric(RallyReading("LAST LAP", state.lastLap?.let { formatTime(it.durationNanos) } ?: "—"),
                    Modifier.fillMaxWidth().weight(1f), maximumSp = 64)
                RallyMetric(RallyReading("BEST LAP", state.bestLap?.let { formatTime(it.durationNanos) } ?: "—"),
                    Modifier.fillMaxWidth().weight(1f), maximumSp = 64)
            }
            if (state.vehicle != null) {
                LiveGauges(state.vehicle, state.dashboard, state.alerts, state.shiftActive,
                    modifier = Modifier.weight(if (state.liveView) 1f else 0.60f).fillMaxHeight(), compact = true)
            } else if (state.liveView) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("Connecting to vehicle…") }
            } else {
                val speed = gpsSpeedForDisplay(state)?.times(if (state.dashboard.mph) 2.2369362921 else 3.6)
                RallyGaugeGrid(listOf(
                    RallyReading("SPEED", speed?.let { "%.0f".format(it) } ?: "—", if (state.dashboard.mph) "mph" else "km/h"),
                    RallyReading("LAPS", state.laps.toString()),
                    RallyReading("THEORETICAL BEST", state.theoreticalBestNanos?.let(::formatTime) ?: "—"),
                ), columns = 1, modifier = Modifier.weight(0.60f).fillMaxHeight(), fitHeight = true, maximumSp = 80)
            }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        MonitoringStatus(state)
        state.lastLap?.takeUnless { it.valid }?.let { Text("Invalid lap: ${it.invalidReasons.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        Text(if (state.liveView) "VIEW ONLY · NO TELEMETRY SAVED · SHARING OFF"
            else "${state.laps} LAPS · ${state.samples} SAMPLES${state.gps?.let { " · GPS ±%.1f m".format(it.horizontalAccuracyMeters) } ?: ""}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PortraitDashboard(state: RecordingState, onOpenControls: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AlertBanners(state.alerts)
        MonitoringStatus(state)
        if (state.usesGps) RallyMetric(RallyReading("GPS SPEED", gpsSpeedForDisplay(state)?.times(if (state.dashboard.mph) 2.2369362921 else 3.6)?.let { "%.0f".format(it) } ?: "—",
            if (state.dashboard.mph) "mph" else "km/h"), Modifier.fillMaxWidth().height(112.dp))
        if (!state.liveView) {
            RallyMetric(RallyReading("CURRENT LAP", formatTime(state.currentLapNanos)), Modifier.fillMaxWidth().height(144.dp), maximumSp = 80)
            RallyMetric(RallyReading("LAST LAP", state.lastLap?.let { formatTime(it.durationNanos) } ?: "—"), Modifier.fillMaxWidth().height(112.dp))
            RallyMetric(RallyReading("BEST LAP", state.bestLap?.let { formatTime(it.durationNanos) } ?: "—"), Modifier.fillMaxWidth().height(112.dp))
        } else Text("VIEW ONLY · NO TELEMETRY SAVED · SHARING OFF", style = MaterialTheme.typography.labelMedium)
        state.vehicle?.let { LiveGauges(it, state.dashboard, state.alerts, state.shiftActive) }
        if (state.simulated) OutlinedButton(onClick = onOpenControls) { Text("Simulation controls") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MonitoringStatus(state: RecordingState) {
    val status = buildList {
        if (state.usesGps) add(when {
            state.gpsError != null -> "GPS UNAVAILABLE · RETRYING"
            state.gps == null -> "GPS ACQUIRING"
            state.gpsAgeSeconds?.let { it !in 0.0..3.0 } == true -> "GPS STALE"
            state.gps.horizontalAccuracyMeters > 25 -> "GPS DEGRADED"
            else -> "GPS READY"
        })
        if (state.usesVehicle && !state.simulated) add("OIL PRESSURE NOT MONITORED")
        state.vehicleError?.let { add(it.uppercase()) }
        if (state.sharingEnabled && (state.sharingStats.errors > 0 || state.sharingStats.dropped > 0)) add("SHARING DEGRADED · LOCAL CAPTURE ACTIVE")
    }.joinToString(" · ")
    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
}

@Composable
private fun SimulationControlsDialog(controls: SimulationControls, onControls: (SimulationControls) -> Unit,
    vehicleOnly: Boolean = false, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulation controls") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!vehicleOnly) {
                Text("Lap pace: ${controls.lapSeconds.toInt()} seconds")
                Slider(controls.lapSeconds.toFloat(), { onControls(controls.copy(lapSeconds = it.toDouble())) }, valueRange = 20f..120f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(controls.gpsDropout, { onControls(controls.copy(gpsDropout = it)) })
                    Text("GPS dropout", Modifier.padding(start = 10.dp))
                }
                OutlinedButton(onClick = { onControls(controls.copy(gpsJump = controls.gpsJump + 1)) }) { Text("Inject GPS jump") }
              }
                Text("Vehicle scenario", style = MaterialTheme.typography.titleMedium)
                SimulationScenario.entries.forEach { scenario ->
                    FilterChip(controls.vehicleScenario == scenario,
                        { onControls(controls.copy(vehicleScenario = scenario)) },
                        { Text(scenario.name.replace('_', ' ')) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
