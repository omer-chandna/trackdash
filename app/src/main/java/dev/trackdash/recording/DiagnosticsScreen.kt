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
                    StatusChip(state.obd.state.toString(), if (state.obd.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("Transport · ${state.transport}")
                    Text("ECU · ${state.obd.ecu.ifBlank { "—" }}")
                    Text("Protocol · ${state.obd.protocol.ifBlank { "—" }}")
                    Text("Firmware · ${state.obd.firmware.ifBlank { "—" }}")
                    Text("VIN availability · ${state.obd.vinStatus}")
                    Text("Available sensor IDs · ${state.obd.supported.sorted().joinToString { "%02X".format(Locale.US, it) }.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    Text("${state.obd.queries} queries · ${state.obd.latencyMillis} ms last latency · ${state.obd.errors} connection errors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val gps: @Composable () -> Unit = {
                val gpsStatus = when { !state.active -> "Inactive"; state.gps == null -> "Acquiring"; (state.gpsAgeSeconds ?: 0.0) > 3 -> "Lost"; state.gps!!.horizontalAccuracyMeters > 25 -> "Degraded"; else -> "Ready" }
                TrackDashPanel {
                    SectionTitle("GPS", "Fix quality and delivery rate")
                    StatusChip(gpsStatus, if (gpsStatus in listOf("Lost", "Degraded")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    state.gps?.let { fix ->
                        Text("${fix.latitude}, ${fix.longitude} · ${fix.provider}")
                        Text("Accuracy ${fix.horizontalAccuracyMeters} m · Age ${state.gpsAgeSeconds} s")
                        Text("Delivered rate %.1f Hz · ${state.rejectedFixes} rejected fixes".format(state.gpsRateHz))
                        Text("Speed ${fix.speedMetersPerSecond ?: "—"} m/s · Bearing ${fix.bearingDegrees ?: "—"}°")
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
                Text("Enhanced sensors and adapter support depend on verified device protocols. Generic OBD may not expose oil temperature or oil-pressure status.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
