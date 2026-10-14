package dev.trackdash.recording

import androidx.activity.compose.LocalActivity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.trackdash.core.*
import dev.trackdash.data.DashboardPreferences

@Composable
fun LiveGauges(sample: VehicleSample, preferences: DashboardPreferences, active: Boolean) {
    val readings = preferences.gauges.mapNotNull { sensor -> sample.readings.find { it.sensorId == sensor } }
        .filter { !preferences.hideUnsupported || it.status != SensorStatus.UNSUPPORTED }
    GaugeGrid(readings, preferences)
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

@Composable
private fun GaugeGrid(readings: List<SensorReading>, preferences: DashboardPreferences) {
    BoxWithConstraints {
        val columns = if (maxWidth > 480.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            readings.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { reading ->
                    val (value, unit) = displayValue(reading, preferences)
                    val shift = reading.sensorId == SensorId.ENGINE_RPM && reading.status == SensorStatus.AVAILABLE && preferences.rpmLimit?.let { reading.value!! >= it } == true
                    Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = if (shift) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(12.dp)) {
                            Text(reading.sensorId.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                            Text(value, style = if (preferences.largeGauges) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge)
                            Text(unit, style = MaterialTheme.typography.labelMedium)
                            if (preferences.bars && reading.status == SensorStatus.AVAILABLE) {
                                val maximum = when (reading.sensorId.unit) { SensorUnit.RPM -> preferences.rpmLimit ?: 8000.0; SensorUnit.CELSIUS -> 160.0; SensorUnit.METERS_PER_SECOND -> 80.0; SensorUnit.VOLTS -> 16.0; else -> 100.0 }
                                LinearProgressIndicator(progress = { (reading.value!! / maximum).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            } }
        }
    }
}

private fun displayValue(reading: SensorReading, prefs: DashboardPreferences): Pair<String, String> {
    val unit = when (reading.sensorId.unit) {
        SensorUnit.CELSIUS -> if (prefs.fahrenheit) "°F" else "°C"
        SensorUnit.METERS_PER_SECOND -> if (prefs.mph) "mph" else "km/h"
        else -> reading.sensorId.unit.name
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
