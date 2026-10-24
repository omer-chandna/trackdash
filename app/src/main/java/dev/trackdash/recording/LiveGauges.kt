package dev.trackdash.recording

import androidx.activity.compose.LocalActivity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.trackdash.core.*
import dev.trackdash.core.telemetry.AlertCategory
import dev.trackdash.core.telemetry.VehicleAlert
import dev.trackdash.data.DashboardPreferences

@Composable
fun LiveGauges(sample: VehicleSample, preferences: DashboardPreferences, alerts: List<VehicleAlert>, shiftActive: Boolean,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val readings = preferences.gauges.mapNotNull { sensor -> sample.readings.find { it.sensorId == sensor } }
        .filter { !preferences.hideUnsupported || it.status != SensorStatus.UNSUPPORTED }
    val rpm = readings.firstOrNull { it.sensorId == SensorId.ENGINE_RPM }
    val metrics = readings.filterNot { it.sensorId == SensorId.ENGINE_RPM }.map { reading ->
        val (value, unit) = displayValue(reading, preferences)
        val safety = alerts.any { it.category == AlertCategory.ENGINE_SAFETY && it.sensor == reading.sensorId }
        val maximum = when (reading.sensorId.unit) {
            SensorUnit.CELSIUS -> 160.0
            SensorUnit.METERS_PER_SECOND -> 80.0
            SensorUnit.VOLTS -> 16.0
            else -> 100.0
        }
        RallyReading(reading.sensorId.name.replace('_', ' '), value, unit, critical = safety,
            progress = if (preferences.bars && reading.status == SensorStatus.AVAILABLE)
                (requireNotNull(reading.value) / maximum).toFloat().coerceIn(0f, 1f) else null)
    } + if (sample.oilPressure != OilPressureStatus.UNAVAILABLE) listOf(
        RallyReading("OIL PRESSURE", sample.oilPressure.name,
            critical = alerts.any { it.id == "oil-pressure" && it.category == AlertCategory.ENGINE_SAFETY })
    ) else emptyList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        if (SensorId.ENGINE_RPM in preferences.gauges) {
            RaceRpmGauge(
                rpm = rpm?.value?.takeIf { rpm.status == SensorStatus.AVAILABLE },
                limit = preferences.rpmLimit,
                shiftActive = shiftActive,
                modifier = Modifier.fillMaxWidth(),
                compact = compact,
            )
        }
        BoxWithConstraints(if (compact) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 600.dp) 4 else 2
            RallyGaugeGrid(metrics, columns, Modifier.fillMaxSize(), fitHeight = compact,
                maximumSp = if (preferences.largeGauges) 64 else 48)
        }
    }
}

@Composable
fun RecordingDisplayPolicy(preferences: DashboardPreferences, active: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, preferences.keepAwake, preferences.fullscreen, active) {
        val window = activity?.window
        if (active && preferences.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (active && preferences.fullscreen && window != null) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun displayValue(reading: SensorReading, prefs: DashboardPreferences): Pair<String, String> {
    val unit = when (reading.sensorId.unit) {
        SensorUnit.CELSIUS -> if (prefs.fahrenheit) "°F" else "°C"
        SensorUnit.METERS_PER_SECOND -> if (prefs.mph) "mph" else "km/h"
        SensorUnit.PERCENT -> "%"
        SensorUnit.VOLTS -> "V"
        SensorUnit.DEGREES -> "°"
        SensorUnit.GRAMS_PER_SECOND -> "g/s"
        SensorUnit.RPM -> "RPM"
    }
    if (reading.status != SensorStatus.AVAILABLE) return reading.status.name to unit
    val value = requireNotNull(reading.value)
    val converted = when (reading.sensorId.unit) {
        SensorUnit.CELSIUS -> if (prefs.fahrenheit) value * 1.8 + 32 else value
        SensorUnit.METERS_PER_SECOND -> value * if (prefs.mph) 2.2369362921 else 3.6
        else -> value
    }
    return "%.0f".format(converted) to unit
}
