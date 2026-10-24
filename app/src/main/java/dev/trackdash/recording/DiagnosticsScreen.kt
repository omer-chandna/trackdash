package dev.trackdash.recording

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.data.TrackDashApplication
import java.util.Locale
import dev.trackdash.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.applicationContext as TrackDashApplication
    val state by graph.recording.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Connection status") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            } },
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp
            val vehicle: @Composable () -> Unit = {
                TrackDashPanel {
                    SectionTitle("Vehicle data", "Adapter and protocol availability")
                    StatusChip(if (!state.active) "Inactive · last session information" else state.obd.state,
                        color = if (state.obd.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("Transport · ${state.transport}")
                    Text("ECU · ${state.obd.ecu.ifBlank { "—" }}")
                    Text("Protocol · ${state.obd.protocol.ifBlank { "—" }}")
                    Text("Firmware · ${state.obd.firmware.ifBlank { "—" }}")
                    Text("VIN availability · ${state.obd.vinStatus}")
                    Text("Available sensor IDs · ${state.obd.supported.sorted().joinToString { "%02X".format(Locale.US, it) }.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    Text("${state.obd.queries} queries · ${state.obd.latencyMillis} ms last latency · ${state.obd.errors} connection errors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.vehicleError?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    state.obd.discovery?.selected?.let { selected ->
                        MonitoringReadinessPanel(state.dashboard, selected.supportedSensors,
                            state.vehicle?.readings?.filter { it.status == dev.trackdash.core.SensorStatus.AVAILABLE }?.map { it.sensorId }?.toSet(), state.simulated,
                            selected.sensors.filter { it.availability == dev.trackdash.core.obd.ObdSensorAvailability.UNKNOWN }.map { it.sensor }.toSet())
                    }
                }
            }
            val gps: @Composable () -> Unit = {
                val gpsStatus = when { !state.active -> "Inactive"; !state.usesGps -> "Not requested"; state.gpsError != null -> "Unavailable · retrying"; state.gps == null -> "Acquiring"; (state.gpsAgeSeconds ?: 0.0) > 3 -> "Lost"; state.gps!!.horizontalAccuracyMeters > 25 -> "Degraded"; else -> "Ready" }
                TrackDashPanel {
                    SectionTitle("GPS", "Fix quality and delivery rate")
                    StatusChip(gpsStatus, color = if (gpsStatus in listOf("Lost", "Degraded")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    state.gps?.let { fix ->
                        Text("${fix.latitude}, ${fix.longitude} · ${fix.provider}")
                        Text("Accuracy ${fix.horizontalAccuracyMeters} m · Age ${state.gpsAgeSeconds} s")
                        Text("Delivered rate %.1f Hz · ${state.rejectedFixes} rejected fixes".format(state.gpsRateHz))
                        Text("Speed ${gpsSpeedForDisplay(state) ?: "—"} m/s · Bearing ${if (gpsStatus == "Ready") fix.bearingDegrees ?: "—" else "—"}°")
                    } ?: Text("No fix is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val recorder: @Composable () -> Unit = {
                TrackDashPanel {
                    SectionTitle("Recorder", "Local capture health")
                    StatusChip(state.message)
                    MetricTile("Stored samples", state.samples.toString(), if (state.simulated) "simulation" else "device", Modifier.fillMaxWidth())
                    Text("Database · ${context.getDatabasePath("trackdash.db").length() / 1024} KiB", style = MaterialTheme.typography.bodyMedium)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.sharingEnabled) Text("Sharing · ${state.sharingStats.sent} sent · ${state.sharingStats.dropped} dropped · ${state.sharingStats.errors} errors",
                        style = MaterialTheme.typography.bodySmall)
                    else Text("Sharing · off", style = MaterialTheme.typography.bodySmall)
                    state.sharingMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            TrackDashPage {
                PageHeader("Connection status", "Live source health for performance monitoring.", eyebrow = "Advanced")
                Text("This page reports adapter, GPS, sensor, and recorder availability. It does not interpret vehicle faults.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { vehicle() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { gps(); recorder() }
                    }
                } else { vehicle(); gps(); recorder() }
                Text("Only standard OBD-II sensors are used. Oil temperature requires advertised support; oil pressure is not monitored. No repair or fault-code commands are sent.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
