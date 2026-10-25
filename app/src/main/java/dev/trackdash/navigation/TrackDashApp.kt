package dev.trackdash.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import dev.trackdash.core.OperatingMode
import dev.trackdash.main.HomeViewModel
import dev.trackdash.simulation.SimulationViewModel
import dev.trackdash.simulation.SimulationScreen
import dev.trackdash.core.telemetry.AcquisitionStatus
import dev.trackdash.recording.RecordingScreen
import dev.trackdash.recording.SessionsScreen
import dev.trackdash.tracks.TracksScreen
import dev.trackdash.settings.SettingsScreen
import dev.trackdash.ui.PageHeader
import dev.trackdash.ui.SectionTitle
import dev.trackdash.ui.StatusChip
import dev.trackdash.ui.TrackDashPage
import dev.trackdash.ui.TrackDashPanel

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home), TRACKS("Tracks", Icons.Outlined.Route), SESSIONS("Sessions", Icons.Outlined.Timer),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDashApp(simulation: SimulationViewModel) {
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
        val primary = Destination.entries.any { it.name == selected }
        val shellRoute = primary || selected == "settings"
        Scaffold(
            bottomBar = {
                if (!wide && primary) NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination.name,
                            onClick = { navigate(destination) },
                            icon = { DestinationIcon(destination) },
                        )
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && shellRoute) TrackDashRail(
                    selected = selected,
                    onNavigate = ::navigate,
                    onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                )
                NavHost(nav, startDestination = Destination.HOME.name, modifier = Modifier.weight(1f)) {
                    composable(Destination.HOME.name) {
                        HomeScreen(onSettings = { nav.navigate("settings") },
                            onLiveView = { nav.navigate("live-view") { launchSingleTop = true } },
                            onRecord = { mode -> nav.navigate("recording/${mode.name}") }, onOpenDemo = {
                            simulation.start()
                            nav.navigate("simulation") { launchSingleTop = true }
                        })
                    }
                    composable("simulation") {
                        val state by simulation.state.collectAsStateWithLifecycle()
                        SimulationScreen(state, simulation::start, simulation::stop, simulation::selectScenario,
                            simulation::setWarningLimits, onBack = { simulation.stop(); nav.popBackStack() })
                    }
                    composable("recording/{mode}") { entry -> RecordingScreen(
                        entry.arguments?.getString("mode") ?: OperatingMode.GPS_LAP_TIMER.name,
                        onBack = { nav.popBackStack() },
                    ) }
                    composable("live-view") { RecordingScreen(
                        initialMode = OperatingMode.VEHICLE_DASHBOARD.name,
                        liveView = true,
                        onBack = { nav.popBackStack() },
                    ) }
                    composable("settings") { SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onConnectionStatus = { nav.navigate("connection-status") },
                        onOpenSource = { nav.navigate("open-source") },
                    ) }
                    composable("open-source") {
                        dev.trackdash.settings.AboutOpenSourceScreen(onBack = { nav.popBackStack() })
                    }
                    composable("connection-status") {
                        dev.trackdash.recording.ConnectionStatusScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Destination.TRACKS.name) {
                        TracksScreen()
                    }
                    composable(Destination.SESSIONS.name) {
                        SessionsScreen(onReplay = { id -> nav.navigate("replay/$id") })
                    }
                    composable("replay/{sessionId}") { replay ->
                        dev.trackdash.recording.SessionReplayScreen(requireNotNull(replay.arguments?.getString("sessionId")),
                            onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackDashRail(selected: String, onNavigate: (Destination) -> Unit, onSettings: () -> Unit) {
    Surface(
        modifier = Modifier.width(72.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxHeight().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.padding(bottom = 18.dp),
            ) {
                Icon(Icons.Outlined.Speed, contentDescription = "TrackDash", tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Destination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = selected == destination.name,
                    onClick = { onNavigate(destination) },
                    icon = { DestinationIcon(destination) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Settings") } },
                state = rememberTooltipState(),
            ) {
                NavigationRailItem(
                    selected = selected == "settings",
                    onClick = onSettings,
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationIcon(destination: Destination) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(destination.label) } },
        state = rememberTooltipState(),
    ) {
        Icon(destination.icon, contentDescription = destination.label)
    }
}

@Composable
private fun HomeScreen(onOpenDemo: () -> Unit, onRecord: (OperatingMode) -> Unit, onSettings: () -> Unit,
    onLiveView: () -> Unit, model: HomeViewModel = viewModel()) {
    val selectedName by model.mode.collectAsStateWithLifecycle()
    val selected = OperatingMode.valueOf(selectedName)
    val graph = LocalContext.current.applicationContext as dev.trackdash.data.TrackDashApplication
    val monitoring by graph.recording.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        TrackDashPage {
            PageHeader(
                title = "TRACKDASH",
                subtitle = "Performance telemetry built for the next session.",
                eyebrow = "Telemetry & lap timing · 0.3",
                actions = {
                    if (!wide) IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Vehicles & settings") }
                },
            )
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModePanel(selected, model::selectMode, Modifier.weight(1.25f), compact = true)
                    LaunchPanel(selected, onOpenDemo, onRecord, onSettings, onLiveView, monitoring, Modifier.weight(0.75f), compact = true)
                }
            } else {
                LaunchPanel(selected, onOpenDemo, onRecord, onSettings, onLiveView, monitoring)
                ModePanel(selected, model::selectMode)
            }
        }
    }
}

@Composable
private fun ModePanel(selected: OperatingMode, onSelect: (OperatingMode) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    TrackDashPanel(modifier) {
        SectionTitle("Recording mode", if (compact) null else "Used when you choose Set up recording. Live view uses vehicle sensors only.")
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperatingMode.entries.forEach { mode ->
                Surface(
                    color = if (mode == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (mode == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(mode.title(), style = MaterialTheme.typography.titleMedium)
                            if (!compact) Text(mode.description(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchPanel(selected: OperatingMode, onOpenDemo: () -> Unit, onRecord: (OperatingMode) -> Unit,
    onSettings: () -> Unit, onLiveView: () -> Unit, monitoring: dev.trackdash.recording.RecordingState,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    TrackDashPanel(modifier, emphasized = true, compact = compact) {
        val viewing = monitoring.active && monitoring.liveView
        val recording = monitoring.active && !monitoring.liveView
        StatusChip(if (viewing) "Live view active · not recording" else if (recording) "Recording active" else "Ready")
        SectionTitle("Live or recorded", if (compact) null else "View gauges without saving, or record the selected mode.")
        Button(enabled = !recording, onClick = onLiveView, modifier = Modifier.fillMaxWidth()) {
            Text(if (viewing) "Resume live view" else "Live view")
        }
        OutlinedButton(enabled = !viewing, onClick = { onRecord(selected) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (recording) "Resume recording" else "Set up recording")
        }
        if (viewing || recording) Text(if (viewing) "Stop live view before recording." else "Stop and save before starting live view.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenDemo, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Try vehicle simulation" }) {
            Text(if (compact) "Try simulation" else "Try vehicle simulation")
        }
        if (!compact) {
            TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.End)) { Text("Vehicles & settings") }
            Text("Simulation lets you explore gauges and warning scenarios without a car or adapter.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
