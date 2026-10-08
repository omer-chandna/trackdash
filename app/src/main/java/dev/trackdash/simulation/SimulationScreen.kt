package dev.trackdash.simulation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.trackdash.core.*
import dev.trackdash.core.simulation.SimulationScenario
import dev.trackdash.core.telemetry.*
import java.util.Locale

@Composable
fun SimulationScreen(state: SimulationUiState, onStart: () -> Unit, onStop: () -> Unit,
    onScenario: (SimulationScenario) -> Unit, onLimits: (DemoWarningLimits) -> Unit) {
    var scenariosOpen by remember { mutableStateOf(false) }
    var limitsOpen by remember { mutableStateOf(false) }
    val running = state.telemetry.status == AcquisitionStatus.RUNNING
    val sample = state.telemetry.sample
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val columns = if (wide) 4 else 2
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("VEHICLE DEMO", style = if (wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                    Text("SIMULATION · ${if (running) "RUNNING" else "STOPPED"}",
                        color = MaterialTheme.colorScheme.primary, style = if (wide) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                }
                if (wide) {
                    TextButton(onClick = { scenariosOpen = true }, enabled = running) { Text("Scenarios") }
                    TextButton(onClick = { limitsOpen = true }) { Text("Warning limits") }
                }
                Button(onClick = if (running) onStop else onStart) {
                    Text(if (running) "Stop simulation" else "Start simulation")
                }
            }
            if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scenariosOpen = true }, enabled = running) { Text("Scenarios") }
                TextButton(onClick = { limitsOpen = true }) { Text("Warning limits") }
            }
            if (!wide) Text(if (running) "Scenario: ${state.scenario.label()}" else "Stopped · no live readings", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.telemetry.error?.let { Text("Source error: $it", color = MaterialTheme.colorScheme.error) }
            state.alerts.forEach { alert ->
                val color = if (alert.severity == AlertSeverity.CRITICAL) MaterialTheme.colorScheme.error else Color(0xFFFFC66D)
                Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text("${alert.severity} · ${alert.message}", color = color, modifier = Modifier.padding(12.dp))
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(if (wide) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(sample.readingText(SensorId.ENGINE_RPM), fontSize = 48.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("rpm-value"))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("RPM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("GEAR ${sample?.gear ?: "—"}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    val rpm = sample?.readings?.firstOrNull { it.sensorId == SensorId.ENGINE_RPM && it.status == SensorStatus.AVAILABLE }?.value
                    LinearProgressIndicator(progress = { ((rpm ?: 0.0) / 7000.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            val tiles = listOf(
                Triple("OIL TEMP", sample.readingText(SensorId.OIL_TEMPERATURE), "°C"),
                Triple("COOLANT", sample.readingText(SensorId.COOLANT_TEMPERATURE), "°C"),
                Triple("INTAKE", sample.readingText(SensorId.INTAKE_TEMPERATURE), "°C"),
                Triple("OIL PRESSURE", sample?.oilPressure?.name ?: "UNAVAILABLE", "switch · simulated"),
                Triple("SPEED", sample.readingText(SensorId.SPEED), "km/h"),
                Triple("THROTTLE", sample.readingText(SensorId.THROTTLE), "%"),
                Triple("ENGINE LOAD", sample.readingText(SensorId.ENGINE_LOAD), "%"),
                Triple("VOLTAGE", sample.readingText(SensorId.VOLTAGE, 1), "V"),
            )
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value, unit) ->
                        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(if (wide) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = if (value.length > 8) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (value == "LOW") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("tile-$label"))
                                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (wide) Text(if (running) "Scenario: ${state.scenario.label()}" else "Stopped · no live readings", style = MaterialTheme.typography.bodySmall)
            Text("Synthetic demonstration data · ${state.telemetry.receivedSamples} samples received · not recorded",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Demo thresholds are editable examples, not BMW or engine-safe limits. GPS simulation is not available yet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (scenariosOpen) AlertDialog(onDismissRequest = { scenariosOpen = false }, title = { Text("Simulation scenarios") },
        text = {
            Column(Modifier.selectableGroup()) {
                SimulationScenario.entries.forEach { scenario ->
                    Row(Modifier.fillMaxWidth().selectable(selected = state.scenario == scenario, role = Role.RadioButton,
                        onClick = { onScenario(scenario); scenariosOpen = false }).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.scenario == scenario, onClick = null)
                        Text(scenario.label(), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { scenariosOpen = false }) { Text("Close") } })
    if (limitsOpen) WarningLimitsDialog(state.limits, { limitsOpen = false }) { onLimits(it); limitsOpen = false }
}

private fun VehicleSample?.readingText(sensor: SensorId, decimals: Int = 0): String {
    val reading = this?.readings?.firstOrNull { it.sensorId == sensor } ?: return "—"
    val rawValue = reading.value ?: return "—"
    if (reading.status != SensorStatus.AVAILABLE) return "—"
    val value = if (sensor == SensorId.SPEED) rawValue * 3.6 else rawValue
    return String.format(Locale.getDefault(), "%.${decimals}f", value)
}

fun SimulationScenario.label() = when (this) {
    SimulationScenario.NORMAL -> "Normal driving"
    SimulationScenario.OVERHEATING -> "Overheating"
    SimulationScenario.LOW_OIL_PRESSURE -> "Low oil pressure"
    SimulationScenario.DISCONNECTED -> "OBD disconnect"
}

@Composable
private fun WarningLimitsDialog(limits: DemoWarningLimits, onDismiss: () -> Unit, onSave: (DemoWarningLimits) -> Unit) {
    var coolant by remember { mutableStateOf(limits.coolantCelsius.toString()) }
    var oil by remember { mutableStateOf(limits.oilCelsius.toString()) }
    val coolantValue = coolant.toDoubleOrNull()
    val oilValue = oil.toDoubleOrNull()
    val valid = coolantValue != null && coolantValue in -40.0..200.0 && oilValue != null && oilValue in -40.0..200.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Demo warning limits") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("For simulation only. Two-second hold, 3°C hysteresis. Not manufacturer guidance.")
            OutlinedTextField(coolant, { coolant = it }, label = { Text("Coolant limit °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(oil, { oil = it }, label = { Text("Oil limit °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (!valid) Text("Enter finite values from −40 to 200°C.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(DemoWarningLimits(coolantValue!!, oilValue!!)) }) { Text("Apply limits") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
