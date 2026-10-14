package dev.trackdash.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.trackdash.core.simulation.SimulationScenario
import dev.trackdash.data.SimulationControls

@Composable
fun RecordingDashboard(state: RecordingState, controls: SimulationControls,
    onControls: (SimulationControls) -> Unit, onStop: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.simulated) "SIMULATED DATA · RECORDING" else "DEVICE DATA · RECORDING", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text("${state.samples} samples saved", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onStop) { Text("Stop and save") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.alerts.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("CURRENT LAP", style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(state.currentLapNanos), style = MaterialTheme.typography.headlineLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Last  ${state.lastLap?.let { formatTime(it.durationNanos) } ?: "—"}")
                    Text("Best  ${state.bestLap?.let { formatTime(it.durationNanos) } ?: "—"}")
                    Text("${state.laps} laps · Theoretical ${state.theoreticalBestNanos?.let(::formatTime) ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state.gps != null) Text("GPS ±%.1f m · age %.1f s · %.1f Hz".format(state.gps.horizontalAccuracyMeters, state.gpsAgeSeconds ?: 0.0, state.gpsRateHz), style = MaterialTheme.typography.labelSmall)
            state.lastLap?.takeUnless { it.valid }?.let { Text("Invalid lap: ${it.invalidReasons.joinToString()}", color = MaterialTheme.colorScheme.error) }
            state.vehicle?.let { sample ->
                LiveGauges(sample, state.dashboard, true)
                Text("${if (sample.connected) "Connected" else "Disconnected"} · Oil-pressure switch: ${sample.oilPressure}", style = MaterialTheme.typography.labelSmall)
            }
            if (state.simulated) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide simulation controls" else "Simulation controls") }
                if (expanded) {
                    Text("Lap pace: ${controls.lapSeconds.toInt()} seconds")
                    Slider(controls.lapSeconds.toFloat(), { onControls(controls.copy(lapSeconds = it.toDouble())) }, valueRange = 20f..120f)
                    Row { Switch(controls.gpsDropout, { onControls(controls.copy(gpsDropout = it)) }); Text("GPS dropout", Modifier.padding(12.dp)) }
                    TextButton(onClick = { onControls(controls.copy(gpsJump = controls.gpsJump + 1)) }) { Text("Inject GPS jump") }
                    SimulationScenario.entries.forEach { scenario -> FilterChip(controls.vehicleScenario == scenario, { onControls(controls.copy(vehicleScenario = scenario)) }, { Text(scenario.name.replace('_', ' ')) }) }
                }
            }
        }
    }
}
