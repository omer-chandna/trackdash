package dev.trackdash

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.trackdash.core.SensorId
import dev.trackdash.core.simulation.DemoCircuit
import dev.trackdash.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AnalysisTest {
    @Test fun gpsSpeedSeriesKeepsAllPeaksWhileBoundingChartMemory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TrackDashDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.insertSession(SessionRow("analysis", 0, mode = "GPS_LAP_TIMER", simulated = true, trackId = null, vehicleId = null))
            (0 until 25).forEach { batch -> dao.insertSamples((0 until 200).map { index ->
                val number = batch * 200 + index
                val fix = DemoCircuit.fixAt(number * 200_000_000L, number * 200_000_000L, number * 200L).copy(speedMetersPerSecond = number.toDouble())
                SampleRow(sessionId = "analysis", elapsedNanos = fix.elapsedRealtimeNanos, wallMillis = fix.wallClockMillis, kind = "gps", payload = Json.encodeToString(fix))
            }) }
            val analysis = SessionAnalysis.load(dao, "analysis")
            assertEquals(4999.0, analysis.peaks[SensorId.SPEED]!!, 0.0)
            assertTrue(analysis.series.getValue(SensorId.SPEED).size in 100..2000)
            assertTrue(analysis.path.size <= 2000)
            assertEquals(0, analysis.malformed)
            val lap = SessionAnalysis.load(dao, "analysis", 1_000_000_000, 2_000_000_000)
            assertTrue(lap.series.getValue(SensorId.SPEED).all { it.nanos in 1_000_000_000..2_000_000_000 })
        } finally { db.close() }
    }
}
