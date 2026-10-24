package dev.trackdash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.trackdash.core.simulation.SimulatedVehicleSource
import dev.trackdash.core.simulation.SimulationScenario
import dev.trackdash.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SessionReplayUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun savedReplayIsClearlyLabeledReadOnlyAndBackReturnsToDetails() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        val graph = compose.activity.application as TrackDashApplication
        val dao = graph.database.dao()
        val id = UUID.randomUUID().toString()
        val session = SessionRow(id, System.currentTimeMillis(), 1L, "REPLAY_UI_TEST", true, null, null, "COMPLETE", "Private original notes")
        runBlocking {
            graph.ready.await()
            dao.insertSession(session)
            dao.insertSamples(listOf(
                SampleRow(sessionId = id, elapsedNanos = 0, wallMillis = 0, kind = "vehicle", payload = Json.encodeToString(
                    SimulatedVehicleSource.sampleAt(0, 0, SimulationScenario.NORMAL))),
                SampleRow(sessionId = id, elapsedNanos = 0, wallMillis = 0, kind = "alert-unconfirmed",
                    payload = Json.encodeToString("High coolant temperature · sensor unavailable")),
            ))
        }
        try {
            compose.onNodeWithContentDescription("Sessions").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Replay ui test").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Replay ui test").performClick()
            compose.onNodeWithText("Replay session").performScrollTo().performClick()
            compose.onNodeWithText("REPLAY · SAVED DATA, NOT LIVE").assertIsDisplayed()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Replay from start").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Replay from start").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Replay finished.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("RECORDED EVENT", substring = true).assertExists()
            captureMonitoringPreview(compose.onRoot(), "session-replay-landscape.png")
            assertFalse(graph.recording.value.active)
            assertEquals(session, runBlocking { dao.session(id) })
            assertEquals(2, runBlocking { dao.samplePage(id) }.size)
            compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Session details").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Session details").assertIsDisplayed()
        } finally { runBlocking { dao.deleteSession(id) } }
    }
}
