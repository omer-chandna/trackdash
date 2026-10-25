package dev.trackdash

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.trackdash.core.*
import dev.trackdash.core.simulation.*
import dev.trackdash.data.*
import dev.trackdash.recording.*
import dev.trackdash.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveViewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val graph get() = compose.activity.application as TrackDashApplication

    private suspend fun counts(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        graph.database.dao().sessions().first().size to graph.database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM samples").use { it.moveToFirst(); it.getLong(0) }
    }

    private suspend fun stop() {
        compose.activity.startService(Intent(compose.activity, RecordingService::class.java).setAction("STOP"))
        withTimeout(10_000) { graph.recording.first { !it.active } }
    }

    @Test fun liveViewHasNoWriterSessionGpsOrSharingEvenWhenRecordingSharingIsEnabled() = runBlocking {
        graph.ready.await()
        val before = counts()
        val previousFactory = graph.recordingSources
        val sharing = TelemetrySharingPreferences(compose.activity)
        val previousSharing = sharing.settings.first()
        var attempts = 0
        graph.recordingBatchWriter = { error("Live view must never start the writer") }
        graph.recordingSources = object : RecordingSourceFactory {
            override fun location(context: Context, simulated: Boolean): LocationSource = error("Live view must not request GPS")
            override fun vehicle(context: Context, simulated: Boolean, address: String?, bleCx: Boolean) = object : VehicleSource {
                override fun samples(): Flow<VehicleSample> = flow {
                    if (++attempts == 1) throw IllegalStateException("Synthetic adapter disconnect")
                    emitAll(AndroidRecordingSources.vehicle(context, true, null, false).samples())
                }
            }
        }
        try {
            sharing.save(SharingSettings(true, "https://receiver.invalid/telemetry"))
            compose.activity.startForegroundService(Intent(compose.activity, RecordingService::class.java)
                .putExtra(RecordingService.LIVE_VIEW_EXTRA, true).putExtra("simulated", true)
                .putExtra("mode", "COMBINED_TELEMETRY")) // Service must override a GPS mode.
            val disconnected = withTimeout(10_000) { graph.recording.first { it.active && it.vehicleError != null } }
            assertTrue(disconnected.liveView)
            val running = withTimeout(10_000) { graph.recording.first { it.active && it.vehicle != null && it.vehicleError == null } }
            assertNull(running.sessionId)
            assertFalse(running.usesGps)
            assertNull(running.gps)
            assertFalse(running.sharingEnabled)
            assertNull(running.sharingMessage)
            assertEquals(0L, running.samples)
            assertNull(running.error)
            assertTrue(attempts >= 2)
            assertEquals(before, counts())
            val notification = compose.activity.getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.single { it.id == 17 }.notification
            assertEquals("TrackDash live view", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertEquals("Stop live view", notification.actions.single().title)
            notification.actions.single().actionIntent.send()
            withTimeout(10_000) { graph.recording.first { !it.active } }
            assertEquals("Live view stopped · nothing saved", graph.recording.value.message)
            assertNull(graph.recording.value.vehicle)
            assertEquals(before, counts())
        } finally {
            stop()
            graph.recordingSources = previousFactory
            graph.recordingBatchWriter = null
            sharing.save(previousSharing)
        }
    }

    @Test fun liveViewShowsGaugesWarningsAndShiftSurvivesRotationAndStopsOnSystemBack() = runBlocking {
        graph.ready.await()
        val before = counts()
        val preferences = Preferences(compose.activity)
        val previous = preferences.dashboard("").first()
        try {
            preferences.saveDashboard("", previous.copy(rpmLimit = 5000.0))
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("Live view").assertIsDisplayed()
            compose.onNodeWithText("Set up recording").assertIsDisplayed()
            compose.onNodeWithContentDescription("Try vehicle simulation").assertIsDisplayed()
            captureMonitoringPreview(compose.onRoot(), "home-live-view-landscape.png")
            compose.onNodeWithText("Live view").performScrollTo().performClick()
            compose.onNodeWithText("Prepare live view").assertExists()
            compose.onNodeWithText("Start live view").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithContentDescription("Simulation source").performScrollTo().performClick().assertIsOn()
            compose.onNodeWithText("Start live view").performScrollTo().performClick()
            withTimeout(10_000) { graph.recording.first { it.active && it.vehicle != null } }
            compose.onNodeWithText("SIMULATED · LIVE VIEW · NOT RECORDING").assertIsDisplayed()
            compose.onNodeWithText("Stop and save").assertDoesNotExist()
            compose.onNodeWithText("CURRENT LAP").assertDoesNotExist()
            compose.onNodeWithTag("rpm-value").assertIsDisplayed()
            captureMonitoringPreview(compose.onRoot(), "live-view-landscape.png")
            withTimeout(10_000) { graph.recording.first { it.shiftActive } }
            graph.simulationControls.value = graph.simulationControls.value.copy(vehicleScenario = SimulationScenario.LOW_OIL_PRESSURE)
            withTimeout(10_000) { graph.recording.first { it.alerts.any { alert -> alert.id == "oil-pressure" } } }
            compose.onNodeWithTag("alert-oil-pressure").assertExists()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Stop live view").assertIsDisplayed()
            assertTrue(graph.recording.value.active)
            assertNull(graph.recording.value.sessionId)
            assertEquals(before, counts())
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            withTimeout(10_000) { graph.recording.first { !it.active } }
            compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
            assertEquals(before, counts())
        } finally {
            stop()
            graph.simulationControls.value = SimulationControls()
            preferences.saveDashboard("", previous)
        }
    }

    @Test fun explicitStopEndsLiveViewAndReturnsHomeWithoutHistory() = runBlocking {
        graph.ready.await()
        val before = counts()
        compose.onNodeWithText("Live view").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Simulation source").performScrollTo().performClick()
        compose.onNodeWithText("Start live view").performScrollTo().performClick()
        try {
            withTimeout(10_000) { graph.recording.first { it.active && it.vehicle != null } }
            compose.onNodeWithText("Stop live view").performClick()
            withTimeout(10_000) { graph.recording.first { !it.active } }
            compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
            assertEquals(before, counts())
        } finally { stop() }
    }

    @Test fun liveViewContinuesInBackgroundButVisibleBackStopsIt() = runBlocking {
        graph.ready.await()
        val before = counts()
        compose.onNodeWithText("Live view").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Simulation source").performScrollTo().performClick()
        compose.onNodeWithText("Start live view").performScrollTo().performClick()
        try {
            val running = withTimeout(10_000) { graph.recording.first { it.active && it.vehicle != null } }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            withTimeout(10_000) { graph.recording.first { it.active &&
                (it.vehicle?.elapsedRealtimeNanos ?: 0) > requireNotNull(running.vehicle).elapsedRealtimeNanos + 200_000_000 } }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(before, counts())
            compose.onNodeWithContentDescription("Back").performClick()
            withTimeout(10_000) { graph.recording.first { !it.active } }
            compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
            assertEquals(before, counts())
        } finally { stop() }
    }

    @Test fun liveViewCannotReplaceOrStopAnExistingRecording() = runBlocking {
        graph.ready.await()
        var id: String? = null
        try {
            compose.activity.startForegroundService(Intent(compose.activity, RecordingService::class.java)
                .putExtra("simulated", true).putExtra("mode", "VEHICLE_DASHBOARD"))
            val recording = withTimeout(10_000) { graph.recording.first { it.active && it.samples > 0 } }
            id = requireNotNull(recording.sessionId)
            compose.onNodeWithText("Live view").assertIsNotEnabled()
            compose.activity.startForegroundService(Intent(compose.activity, RecordingService::class.java)
                .putExtra(RecordingService.LIVE_VIEW_EXTRA, true).putExtra("simulated", true))
            val continued = withTimeout(10_000) { graph.recording.first { it.samples > recording.samples } }
            assertEquals(id, continued.sessionId)
            assertFalse(continued.liveView)
            stop()
            assertEquals("COMPLETE", graph.database.dao().session(id)!!.status)
            assertTrue(graph.database.dao().sampleCount(id).first() > 0)
        } finally {
            stop()
            id?.let { graph.database.dao().deleteFinishedSession(it) }
        }
    }
}
