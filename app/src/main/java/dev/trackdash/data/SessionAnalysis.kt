package dev.trackdash.data

import dev.trackdash.core.*
import dev.trackdash.core.gps.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class ChartPoint(val nanos: Long, val value: Double)
data class Analysis(val series: Map<SensorId, List<ChartPoint>> = emptyMap(),
    val peaks: Map<SensorId, Double> = emptyMap(), val path: List<GeoPoint> = emptyList(),
    val alerts: List<String> = emptyList(), val malformed: Int = 0)

/** Exact streaming maxima; chart/path samples are deterministically decimated to bound memory. */
object SessionAnalysis {
    suspend fun load(dao: TrackDashDao, id: String, startNanos: Long = 0, endNanos: Long = Long.MAX_VALUE): Analysis = withContext(Dispatchers.IO) {
        val series = SensorId.entries.associateWith { mutableListOf<ChartPoint>() }
        val peaks = mutableMapOf<SensorId, Double>()
        val path = mutableListOf<GeoPoint>()
        val gpsSpeed = mutableListOf<ChartPoint>()
        var gpsPeak: Double? = null
        val alerts = ArrayDeque<String>()
        var malformed = 0
        var cursor = 0L
        var stride = 1L
        var count = 0L
        fun add(sensor: SensorId, nanos: Long, value: Double) {
            peaks[sensor] = maxOf(peaks[sensor] ?: -Double.MAX_VALUE, value)
            if (count % stride == 0L) series.getValue(sensor).add(ChartPoint(nanos, value))
        }
        while (true) {
            val page = dao.samplePage(id, cursor)
            if (page.isEmpty()) break
            page.forEach { row -> if (row.elapsedNanos in startNanos..endNanos) {
                count++
                runCatching { when (row.kind) {
                    "vehicle" -> Json.decodeFromString<VehicleSample>(row.payload).readings.filter { it.status == SensorStatus.AVAILABLE && it.elapsedRealtimeNanos in startNanos..endNanos }
                        .forEach { add(it.sensorId, it.elapsedRealtimeNanos, it.value!!) }
                    "gps" -> Json.decodeFromString<GpsFix>(row.payload).let { fix ->
                        if (count % stride == 0L) path += GeoPoint(fix.latitude, fix.longitude)
                        // Keep GPS speed separate from OBD speed in source storage; GPS is used for this session overview only when no vehicle speed exists.
                        fix.speedMetersPerSecond?.let {
                            gpsPeak = maxOf(gpsPeak ?: 0.0, it)
                            if (count % stride == 0L) gpsSpeed += ChartPoint(fix.elapsedRealtimeNanos, it)
                        }
                    }
                    "alert" -> { if (alerts.size >= 200) alerts.removeFirst(); alerts.addLast(Json.decodeFromString(row.payload)) }
                } }.onFailure { malformed++ }
                if (path.size > 2000 || gpsSpeed.size > 2000 || series.values.any { it.size > 2000 }) {
                    series.values.forEach { points -> val keep = points.filterIndexed { index, _ -> index % 2 == 0 }; points.clear(); points.addAll(keep) }
                    val keep = path.filterIndexed { index, _ -> index % 2 == 0 }; path.clear(); path.addAll(keep)
                    val speedKeep = gpsSpeed.filterIndexed { index, _ -> index % 2 == 0 }; gpsSpeed.clear(); gpsSpeed.addAll(speedKeep)
                    stride *= 2
                }
            } }
            cursor = page.last().id
        }
        if (series.getValue(SensorId.SPEED).isEmpty()) {
            series.getValue(SensorId.SPEED).addAll(gpsSpeed)
            gpsPeak?.let { peaks[SensorId.SPEED] = it }
        }
        Analysis(series.mapValues { it.value.distinctBy { point -> point.nanos }.sortedBy { point -> point.nanos } }, peaks, path, alerts.toList(), malformed)
    }
}
