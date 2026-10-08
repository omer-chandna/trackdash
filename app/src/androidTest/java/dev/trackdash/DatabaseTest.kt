package dev.trackdash

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.trackdash.data.*
import dev.trackdash.core.gps.TrackJson
import dev.trackdash.core.simulation.DemoCircuit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DatabaseTest {
    @Test fun tracksSessionsRecoveryAndSamplePaging() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TrackDashDatabase::class.java).build()
        try {
            val dao = database.dao()
            val track = DemoCircuit.track
            dao.saveTrack(TrackRow(track.id, track.name, TrackJson.encode(track)))
            assertEquals(track, TrackJson.decode(dao.track(track.id)!!.json))
            dao.insertSession(SessionRow("s", 1000, mode = "GPS_LAP_TIMER", simulated = true, trackId = track.id, vehicleId = null))
            dao.insertSamples((0..4).map { SampleRow(sessionId = "s", elapsedNanos = it.toLong(), wallMillis = 1000, kind = "gps", payload = "{}") })
            val first = dao.samplePage("s", limit = 2)
            assertEquals(2, first.size)
            assertEquals(3, dao.samplePage("s", first.last().id).size)
            assertEquals(5L, dao.sampleCount("s").first())
            dao.recoverInterrupted()
            assertEquals("INTERRUPTED", dao.session("s")!!.status)
            dao.endSession("s", 2000, "FINISHED")
            assertEquals(2000L, dao.session("s")!!.endedMillis)
            dao.deleteSession("s")
            assertTrue(dao.samplePage("s").isEmpty())
        } finally { database.close() }
    }
}
