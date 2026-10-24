package dev.trackdash.recording

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.trackdash.core.SensorId
import dev.trackdash.data.DashboardPreferences
import dev.trackdash.ui.SectionTitle

data class MonitoringCoverage(val name: String, val status: String)

fun monitoringCoverage(preferences: DashboardPreferences, supported: Set<SensorId>?, available: Set<SensorId>?, simulated: Boolean,
    unknown: Set<SensorId> = emptySet()): List<MonitoringCoverage> {
    fun status(sensor: SensorId, configured: Boolean): String = when {
        !preferences.alertsEnabled -> "Warnings disabled"
        !configured -> "Not monitored — set a limit"
        simulated -> "Simulation only"
        supported == null -> "Not checked — discover sensors"
        sensor in unknown -> "Not monitored — sensor support unknown; retry check"
        sensor !in supported -> "Not monitored — unsupported by this ECU"
        available == null || sensor !in available -> "Not monitored — no current reading"
        else -> "Available with configured limit"
    }
    return listOf(
        MonitoringCoverage("Coolant temperature", status(SensorId.COOLANT_TEMPERATURE, preferences.coolantLimit != null)),
        MonitoringCoverage("Oil temperature", status(SensorId.OIL_TEMPERATURE, preferences.oilLimit != null)),
        MonitoringCoverage("Intake temperature", status(SensorId.INTAKE_TEMPERATURE, preferences.intakeLimit != null)),
        MonitoringCoverage("Shift light", when {
            preferences.rpmLimit == null -> "Not monitored — set Shift RPM"
            simulated -> "Simulation only"
            supported == null -> "Not checked — discover sensors"
            SensorId.ENGINE_RPM in unknown -> "Not monitored — RPM support unknown; retry check"
            SensorId.ENGINE_RPM !in supported -> "Not monitored — RPM unsupported"
            available == null || SensorId.ENGINE_RPM !in available -> "Not monitored — no current RPM"
            else -> "Available with configured limit"
        }),
        MonitoringCoverage("Oil pressure", if (simulated) "Simulation only" else "Not monitored — unavailable through standard OBD-II"),
    )
}

@Composable
fun MonitoringReadinessPanel(preferences: DashboardPreferences, supported: Set<SensorId>?, available: Set<SensorId>?, simulated: Boolean,
    unknown: Set<SensorId> = emptySet()) {
    Column {
        SectionTitle("Monitoring readiness", "Availability is not a vehicle-health assessment. Factory warning systems remain essential.")
        monitoringCoverage(preferences, supported, available, simulated, unknown).forEach { item ->
            Text(item.name, style = MaterialTheme.typography.labelLarge)
            Text(item.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Limits are snapshotted when recording starts. Configure them in Settings before driving.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
