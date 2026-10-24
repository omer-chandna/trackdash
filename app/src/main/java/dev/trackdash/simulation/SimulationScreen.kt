package dev.trackdash.simulation

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.trackdash.core.*
import dev.trackdash.core.simulation.SimulationScenario
import dev.trackdash.core.telemetry.*
import dev.trackdash.recording.AlertBanners
import dev.trackdash.recording.DashboardAlertLayer
import dev.trackdash.recording.RaceRpmGauge
import dev.trackdash.recording.RallyGaugeGrid
import dev.trackdash.recording.RallyHeader
import dev.trackdash.recording.RallyReading
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(state: SimulationUiState, onStart: () -> Unit, onStop: () -> Unit,
    onScenario: (SimulationScenario) -> Unit, onLimits: (DemoWarningLimits) -> Unit, onBack: () -> Unit) {
    var scenariosOpen by remember { mutableStateOf(false) }
    var limitsOpen by remember { mutableStateOf(false) }
    val running = state.telemetry.status == AcquisitionStatus.RUNNING
    val sample = state.telemetry.sample
    BackHandler(onBack = onBack)
    DashboardAlertLayer(state.alerts, state.shiftActive) {
      Column(Modifier.fillMaxSize()) {
        RallyHeader("SIMULATION · ${if (running) "RUNNING" else "STOPPED"}", onBack) {
            TextButton(onClick = { scenariosOpen = true }, enabled = running) { Text("Scenarios") }
            TextButton(onClick = { limitsOpen = true }) { Text("Warning limits") }
            OutlinedButton(onClick = if (running) onStop else onStart) {
                Text(if (running) "Stop simulation" else "Start simulation")
            }
        }
      BoxWithConstraints(Modifier.weight(1f)) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val columns = if (wide) 4 else 2
        val bodyModifier = if (wide) Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
            else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)
        Column(bodyModifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.telemetry.error?.let { Text("Source error: $it", color = MaterialTheme.colorScheme.error) }
            AlertBanners(state.alerts)
            val rpm = sample?.readings?.firstOrNull { it.sensorId == SensorId.ENGINE_RPM && it.status == SensorStatus.AVAILABLE }?.value
            RaceRpmGauge(rpm, 7000.0, state.shiftActive, Modifier.fillMaxWidth(), compact = wide)
            val tiles = listOf(
                Triple("OIL TEMP", sample.readingText(SensorId.OIL_TEMPERATURE), "°C"),
                Triple("COOLANT", sample.readingText(SensorId.COOLANT_TEMPERATURE), "°C"),
                Triple("INTAKE", sample.readingText(SensorId.INTAKE_TEMPERATURE), "°C"),
                Triple("OIL PRESSURE", sample?.oilPressure?.name ?: "UNAVAILABLE", ""),
                Triple("SPEED", sample.readingText(SensorId.SPEED), "km/h"),
                Triple("THROTTLE", sample.readingText(SensorId.THROTTLE), "%"),
                Triple("ENGINE LOAD", sample.readingText(SensorId.ENGINE_LOAD), "%"),
                Triple("VOLTAGE", sample.readingText(SensorId.VOLTAGE, 1), "V"),
            )
            val readings = tiles.map { (label, value, unit) ->
                val safety = state.alerts.any { alert -> alert.category == AlertCategory.ENGINE_SAFETY && when (label) {
                    "OIL PRESSURE" -> alert.id == "oil-pressure"
                    "OIL TEMP" -> alert.sensor == SensorId.OIL_TEMPERATURE
                    "COOLANT" -> alert.sensor == SensorId.COOLANT_TEMPERATURE
                    "INTAKE" -> alert.sensor == SensorId.INTAKE_TEMPERATURE
                    else -> false
                } }
                RallyReading(label, value, unit, critical = safety)
            }
            RallyGaugeGrid(readings, columns, modifier = if (wide) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                fitHeight = wide, maximumSp = 64)
            if (wide) Text("${state.scenario.label()} · SYNTHETIC DATA · NOT RECORDED",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (!wide) Text("Synthetic demonstration data · ${state.telemetry.receivedSamples} samples · not recorded",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!wide) Text("Demo thresholds are editable examples, not manufacturer or engine-safe limits. GPS simulation is not available yet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
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
