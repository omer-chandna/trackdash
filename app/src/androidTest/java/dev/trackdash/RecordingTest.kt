package dev.trackdash

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.trackdash.data.TrackDashApplication
import dev.trackdash.data.SessionExport
import dev.trackdash.recording.RecordingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class RecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun combinedRecordingPersistsBothSourcesAndDrainsOnStop() = runBlocking {
        val activity = compose.activity
        val graph = activity.application as TrackDashApplication
        graph.ready.await()
        graph.simulationControls.value = dev.trackdash.data.SimulationControls(lapSeconds = 20.0,
            vehicleScenario = dev.trackdash.core.simulation.SimulationScenario.LOW_OIL_PRESSURE)
        activity.startForegroundService(Intent(activity, RecordingService::class.java)
            .putExtra("simulated", true).putExtra("mode", "COMBINED_TELEMETRY"))
        val running = withTimeout(15_000) { graph.recording.first { it.active && it.samples >= 10 } }
        try {
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            withTimeout(10_000) { graph.recording.first { it.samples > running.samples + 10 } }
            withTimeout(60_000) { graph.recording.first { it.laps >= 1 && it.bestLap != null } }
        } finally { activity.startService(Intent(activity, RecordingService::class.java).setAction("STOP")) }
        val stopped = withTimeout(15_000) { graph.recording.first { !it.active && it.sessionId == running.sessionId } }
        assertNull(stopped.error)
        val dao = graph.database.dao()
        val id = requireNotNull(stopped.sessionId)
        assertEquals("COMPLETE", dao.session(id)!!.status)
        val samples = dao.samplePage(id)
        assertTrue(samples.any { it.kind == "gps" })
        assertTrue(samples.any { it.kind == "vehicle" })
        assertEquals(stopped.samples, dao.sampleCount(id).first())
        assertTrue(dao.events(id, "lap").first().isNotEmpty())
        assertTrue(dao.events(id, "alert").first().any { it.payload.contains("Low oil-pressure") })
        val csv = StringWriter()
        SessionExport.write(dao, id, csv)
        assertTrue(csv.toString().contains("SIMULATION"))
        assertTrue(csv.toString().contains("elapsed_ns"))
        dao.deleteSession(id)
        graph.simulationControls.value = dev.trackdash.data.SimulationControls()
    }
}
