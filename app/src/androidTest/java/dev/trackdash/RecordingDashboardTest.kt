package dev.trackdash

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.trackdash.data.TrackDashApplication
import dev.trackdash.recording.RecordingService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordingDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun landscapeKeepsLiveGaugesAndStopVisible() {
        val graph = compose.activity.application as TrackDashApplication
        compose.onNodeWithText("Set up recording").performClick()
        compose.onNodeWithText("COMBINED TELEMETRY").performClick()
        compose.onNodeWithText("Start recording").performScrollTo().performClick()
        try {
            compose.waitUntil(15_000) { graph.recording.value.vehicle != null && graph.recording.value.active }
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("Stop and save").assertIsDisplayed()
            compose.onNodeWithText("RPM").assertIsDisplayed()
            compose.onNodeWithText("OIL TEMPERATURE").assertIsDisplayed()
            compose.onNodeWithTag("tile-OIL TEMPERATURE").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
            compose.onNodeWithTag("rpm-value").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
            assertTrue(graph.recording.value.active)
        } finally { compose.activity.startService(Intent(compose.activity, RecordingService::class.java).setAction("STOP")) }
        compose.waitUntil(15_000) { !graph.recording.value.active }
        val id = graph.recording.value.sessionId
        kotlinx.coroutines.runBlocking { if (id != null) graph.database.dao().deleteSession(id) }
    }
}
