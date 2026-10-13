package dev.trackdash.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.trackdash.core.SensorId
import dev.trackdash.core.gps.TimedLap
import dev.trackdash.data.*
import dev.trackdash.tracks.CircuitMap

@Composable
fun AnalysisPanel(session: SessionRow, dao: TrackDashDao, laps: List<TimedLap>) {
    var analysis by remember(session.id) { mutableStateOf<Analysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sensor by remember { mutableStateOf(SensorId.SPEED) }
    var firstLap by remember { mutableStateOf<TimedLap?>(null) }
    var secondLap by remember { mutableStateOf<TimedLap?>(null) }
    var first by remember { mutableStateOf<Analysis?>(null) }
    var second by remember { mutableStateOf<Analysis?>(null) }
    LaunchedEffect(session.id, session.status) { runCatching { SessionAnalysis.load(dao, session.id) }.onSuccess { analysis = it }.onFailure { error = it.message } }
    LaunchedEffect(firstLap, secondLap) {
        first = null; second = null
        runCatching {
            firstLap?.let { first = SessionAnalysis.load(dao, session.id, it.startNanos, it.endNanos) }
            secondLap?.let { second = SessionAnalysis.load(dao, session.id, it.startNanos, it.endNanos) }
        }.onFailure { error = it.message }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Analysis", style = MaterialTheme.typography.titleLarge)
        Text("Duration: ${session.endedMillis?.let { formatTime((it - session.startedMillis) * 1_000_000) } ?: "In progress / interrupted"}")
        val valid = laps.filter { it.valid }
        Text("Average valid lap: ${valid.takeIf { it.isNotEmpty() }?.map { it.durationNanos }?.average()?.toLong()?.let(::formatTime) ?: "—"}")
        error?.let { Text("Analysis error: $it") }
        val loaded = analysis
        if (loaded == null) Text("Loading recorded samples…") else {
            loaded.peaks.forEach { (id, value) -> Text("Peak ${id.name.replace('_', ' ')}: %.1f ${id.unit}".format(value)) }
            if (loaded.malformed > 0) Text("${loaded.malformed} unreadable samples excluded")
            listOf(SensorId.ENGINE_RPM, SensorId.SPEED, SensorId.COOLANT_TEMPERATURE, SensorId.OIL_TEMPERATURE, SensorId.INTAKE_TEMPERATURE, SensorId.THROTTLE).chunked(2).forEach { row -> Row { row.forEach { id -> FilterChip(sensor == id, { sensor = id }, { Text(id.name.replace('_', ' ')) }) } } }
            TraceChart(loaded.series[sensor].orEmpty(), label = "${sensor.name} · ${sensor.unit}")
            loaded.path.firstOrNull()?.let { CircuitMap(emptyList(), it, Modifier.fillMaxWidth().height(260.dp), trace = loaded.path) }
            Text("Recorded warnings (latest 200)")
            loaded.alerts.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            if (laps.size >= 2) {
                Text("Compare laps by elapsed time", style = MaterialTheme.typography.titleLarge)
                laps.forEach { lap -> Row {
                    FilterChip(firstLap == lap, { firstLap = lap }, { Text("A · Lap ${lap.number}") })
                    FilterChip(secondLap == lap, { secondLap = lap }, { Text("B · Lap ${lap.number}") })
                } }
                if (firstLap != null && secondLap != null) {
                    Text("A ${formatTime(firstLap!!.durationNanos)} · B ${formatTime(secondLap!!.durationNanos)}")
                    firstLap!!.sectorNanos.zip(secondLap!!.sectorNanos).forEachIndexed { index, pair -> Text("Sector ${index + 1}: A ${formatTime(pair.first)} / B ${formatTime(pair.second)}") }
                    Text("Green A, amber B. X axis is seconds since each gate crossing; no distance alignment is implied.")
                    TraceChart(first?.series?.get(sensor).orEmpty().map { it.copy(nanos = it.nanos - firstLap!!.startNanos) },
                        second?.series?.get(sensor).orEmpty().map { it.copy(nanos = it.nanos - secondLap!!.startNanos) }, "${sensor.name} · ${sensor.unit}", relative = true)
                }
            }
        }
    }
}

@Composable
private fun TraceChart(first: List<ChartPoint>, second: List<ChartPoint> = emptyList(), label: String, relative: Boolean = false) {
    val all = first + second
    Text(label)
    if (all.size < 2) { Text("Not enough available samples"); return }
    val minimum = all.minOf { it.value }
    val maximum = all.maxOf { it.value }
    val start = if (relative) 0L else all.minOf { it.nanos }
    val end = all.maxOf { it.nanos }
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        fun draw(points: List<ChartPoint>, color: Color) {
            points.zipWithNext().forEach { (a, b) ->
                // Break lines across source dropouts, never imply uninterrupted measurements.
                if (b.nanos - a.nanos <= 5_000_000_000) {
                    fun position(p: ChartPoint) = Offset(((p.nanos - start).toDouble() / (end - start).coerceAtLeast(1) * size.width).toFloat(),
                        (size.height - (p.value - minimum) / (maximum - minimum).coerceAtLeast(1.0) * size.height).toFloat())
                    drawLine(color, position(a), position(b), 3f)
                }
            }
        }
        draw(first, Color(0xff55e6b2)); draw(second, Color(0xffffc857))
    }
    Text("%.1f–%.1f · 0–%.1f seconds · sampled overview".format(minimum, maximum, (end - start) / 1e9), style = MaterialTheme.typography.labelSmall)
}
