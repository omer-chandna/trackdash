package dev.trackdash.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.trackdash.core.telemetry.AlertCategory
import dev.trackdash.core.telemetry.VehicleAlert
import kotlinx.coroutines.delay
import kotlin.math.floor

private const val RPM_LIGHTS = 12

internal fun rpmLightCount(rpm: Double?, limit: Double?): Int {
    if (rpm == null || limit == null || limit <= 0.0) return 0
    return floor((rpm / limit).coerceIn(0.0, 1.0) * RPM_LIGHTS).toInt()
}

internal fun rpmLightZone(index: Int): String {
    val threshold = (index + 1).toDouble() / RPM_LIGHTS
    return when {
        threshold <= 0.75 -> "green"
        threshold <= 0.90 -> "yellow"
        else -> "red"
    }
}

@Composable
fun RaceRpmGauge(
    rpm: Double?,
    limit: Double?,
    shiftActive: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val illuminated = rpmLightCount(rpm, limit)
    Surface(modifier, color = Color(0xFF0C0C0C), shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color(0xFF414141))) {
        Row(Modifier.fillMaxWidth().height(if (compact) 84.dp else 108.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RallyNumber(rpm?.let { "%.0f".format(it) } ?: "—", Modifier.weight(0.45f),
                maximumSp = if (compact) 64 else 80, tag = "rpm-value")
            Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (shiftActive) "SHIFT" else "RPM", color = if (shiftActive) MaterialTheme.colorScheme.error else Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dotSize = (maxWidth / 15).coerceAtMost(18.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(RPM_LIGHTS) { index ->
                            val activeColor = when (rpmLightZone(index)) {
                                "green" -> Color(0xFF29E369)
                                "yellow" -> Color(0xFFFFD600)
                                else -> MaterialTheme.colorScheme.error
                            }
                            Box(Modifier.size(dotSize).background(
                                if (index < illuminated) activeColor else Color(0xFF303030), CircleShape,
                            ).testTag("rpm-light-$index"))
                        }
                    }
                }
                if (limit == null) Text("Set Shift RPM in Settings",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AlertBanners(alerts: List<VehicleAlert>) {
    alerts.forEach { alert ->
        val safety = alert.category == AlertCategory.ENGINE_SAFETY
        val background = if (safety) MaterialTheme.colorScheme.error else Color(0xFFFFC66D)
        Surface(
            color = if (safety) background else background.copy(alpha = 0.15f),
            contentColor = if (safety) MaterialTheme.colorScheme.onError else background,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag("alert-${alert.id}"),
        ) {
            Text(alert.message.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun DashboardAlertLayer(
    alerts: List<VehicleAlert>,
    shiftActive: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    var manager by rememberSaveable(stateSaver = DashboardWarningManager.Saver) { mutableStateOf(DashboardWarningManager()) }
    LaunchedEffect(alerts, shiftActive) {
        manager = manager.update(alerts, shiftActive)
    }
    val currentId = manager.currentId
    val sequence = manager.sequence
    LaunchedEffect(currentId, sequence) {
        if (currentId == null) return@LaunchedEffect
        while (manager.currentId == currentId && manager.sequence == sequence) {
            delay(WARNING_PHASE_MILLIS)
            if (manager.currentId == currentId && manager.sequence == sequence) manager = manager.advance()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (shiftActive && !manager.busy) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).zIndex(1f).testTag("shift-steady"),
            ) {
                Text("SHIFT", fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp))
            }
        }
        if (manager.pulseVisible) {
            if (currentId == SHIFT_WARNING_ID && shiftActive) {
                Takeover("SHIFT", "shift-flash")
            } else {
                alerts.firstOrNull { it.id == currentId && it.category == AlertCategory.ENGINE_SAFETY }?.let { alert ->
                    Takeover(warningHeadline(alert), "safety-flash")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Takeover(text: String, tag: String) {
    Box(
        Modifier.matchParentSize().background(MaterialTheme.colorScheme.error).zIndex(2f).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onError, fontSize = if (tag == "shift-flash") 96.sp else 56.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(24.dp))
    }
}
