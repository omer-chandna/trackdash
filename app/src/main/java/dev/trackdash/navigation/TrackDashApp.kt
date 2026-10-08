package dev.trackdash.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import dev.trackdash.core.OperatingMode
import dev.trackdash.main.HomeViewModel

private enum class Destination(val label: String, val symbol: String) {
    HOME("Home", "H"), TRACKS("Tracks", "T"), SESSIONS("Sessions", "S"), DIAGNOSTICS("Diagnostics", "D"),
}

@Composable
fun TrackDashApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val selected = entry?.destination?.route ?: Destination.HOME.name
    fun navigate(destination: Destination) {
        nav.navigate(destination.name) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Scaffold(
            bottomBar = {
                if (!wide) NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination.name,
                            onClick = { navigate(destination) },
                            icon = { Text(destination.symbol) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = selected == destination.name,
                            onClick = { navigate(destination) },
                            icon = { Text(destination.symbol) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                NavHost(nav, startDestination = Destination.HOME.name, modifier = Modifier.weight(1f)) {
                    composable(Destination.HOME.name) { HomeScreen() }
                    composable(Destination.TRACKS.name) {
                        Page("Track library", "Your next circuit") {
                            StatusCard("No saved tracks", "Track saving and the timing-gate editor are planned. GPS timing will work independently of map tiles.")
                        }
                    }
                    composable(Destination.SESSIONS.name) {
                        Page("Session history", "Every lap has a story") {
                            StatusCard("No recorded sessions", "Recording and analysis are planned. This foundation build does not collect or store telemetry.")
                        }
                    }
                    composable(Destination.DIAGNOSTICS.name) {
                        Page("Diagnostics", "Source status") {
                            StatusCard("OBD · Disconnected", "No adapter transport is installed in this build.")
                            StatusCard("GPS · Inactive", "Location acquisition has not started. No GPS quality assessment is available.")
                            StatusCard("Recorder · Inactive", "No samples are being recorded.")
                            StatusCard("Simulation · Not available", "Simulated driving will be available in a later version.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(model: HomeViewModel = viewModel()) {
    val selectedName by model.mode.collectAsStateWithLifecycle()
    val selected = OperatingMode.valueOf(selectedName)
    Page("TRACKDASH", "Prepare for your next session") {
        Text("PROJECT FOUNDATION · 0.1", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Choose an operating mode", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperatingMode.entries.forEach { mode ->
                Surface(
                    color = if (mode == selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = mode == selected, role = Role.RadioButton,
                        onClick = { model.selectMode(mode) },
                    ),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Column {
                            Text(mode.title(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(mode.description(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        StatusCard("Selected: ${selected.title()}", "${selected.description()} Session capture is not available in this preview.")
        Text("No live or simulated data is being acquired.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun OperatingMode.title() = when (this) {
    OperatingMode.VEHICLE_DASHBOARD -> "Vehicle dashboard"
    OperatingMode.GPS_LAP_TIMER -> "GPS lap timer"
    OperatingMode.COMBINED_TELEMETRY -> "Combined telemetry"
}

private fun OperatingMode.description() = when (this) {
    OperatingMode.VEHICLE_DASHBOARD -> "Vehicle sensors without GPS timing."
    OperatingMode.GPS_LAP_TIMER -> "Lap timing without an OBD adapter."
    OperatingMode.COMBINED_TELEMETRY -> "Vehicle sensors and GPS timing together."
}

@Composable
private fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
