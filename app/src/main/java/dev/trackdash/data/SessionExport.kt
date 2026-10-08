package dev.trackdash.data

import dev.trackdash.core.*
import dev.trackdash.core.export.TelemetryCsv
import kotlinx.serialization.json.Json
import java.io.Writer

/** Pages from Room so exports remain bounded regardless of session length. */
object SessionExport {
    suspend fun write(dao: TrackDashDao, id: String, out: Writer) {
        val session = requireNotNull(dao.session(id)) { "Session no longer exists" }
        require(session.status != "RECORDING") { "Stop recording before exporting" }
        out.write(TelemetryCsv.header)
        out.write(TelemetryCsv.row(id, "metadata", null, session.startedMillis, if (session.simulated) "SIMULATION" else "DEVICE", null, null, null, session.status, null, null, null,
            "mode=${session.mode}; track=${session.trackId}; vehicle=${session.vehicleId}; notes=${session.notes}"))
        var cursor = 0L
        while (true) {
            val page = dao.samplePage(id, cursor)
            if (page.isEmpty()) break
            page.forEach { row ->
                when (row.kind) {
                    "vehicle" -> {
                        val sample = Json.decodeFromString<VehicleSample>(row.payload)
                        sample.readings.forEach { reading -> out.write(TelemetryCsv.row(id, "vehicle", reading.elapsedRealtimeNanos, row.wallMillis, reading.source,
                            reading.sensorId, reading.value, reading.sensorId.unit, reading.status, null, null, null, null)) }
                        out.write(TelemetryCsv.row(id, "discrete", sample.elapsedRealtimeNanos, row.wallMillis, sample.source, "oil_pressure_switch", null, null, sample.oilPressure, null, null, null, "connected=${sample.connected}; gear=${sample.gear}"))
                    }
                    "gps" -> {
                        val fix = Json.decodeFromString<GpsFix>(row.payload)
                        out.write(TelemetryCsv.row(id, "gps", fix.elapsedRealtimeNanos, fix.wallClockMillis, fix.provider, "speed", fix.speedMetersPerSecond, "METERS_PER_SECOND", null,
                            fix.latitude, fix.longitude, fix.horizontalAccuracyMeters, "altitude_m=${fix.altitudeMeters}; bearing_deg=${fix.bearingDegrees}; simulated=${fix.simulated}"))
                    }
                    else -> out.write(TelemetryCsv.row(id, row.kind, row.elapsedNanos, row.wallMillis, null, null, null, null, null, null, null, null, row.payload))
                }
            }
            cursor = page.last().id
        }
        out.flush()
    }
}
