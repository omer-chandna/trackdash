package dev.trackdash

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.trackdash.core.*
import dev.trackdash.core.simulation.SimulatedGpsSource
import dev.trackdash.data.TrackDashApplication
import dev.trackdash.recording.RecordingService
import dev.trackdash.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MonitoringReliabilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun combinedRecordingSurvivesGpsSourceFailureAndRecovers() = runBlocking {
        val activity = compose.activity
        val graph = activity.application as TrackDashApplication
        graph.ready.await()
        val previousFactory = graph.recordingSources
        var attempts = 0
        graph.recordingSources = object : RecordingSourceFactory {
            override fun vehicle(context: Context, simulated: Boolean, address: String?, bleCx: Boolean) =
                AndroidRecordingSources.vehicle(context, true, null, false)
            override fun location(context: Context, simulated: Boolean) = object : LocationSource {
                override fun fixes(): Flow<GpsFix> = flow {
                    if (++attempts == 1) throw IllegalStateException("GPS provider disabled")
                    emitAll(SimulatedGpsSource(SystemClock::elapsedRealtimeNanos, System::currentTimeMillis).fixes())
                }
            }
        }
        var sessionId: String? = null
        try {
            activity.startForegroundService(Intent(activity, RecordingService::class.java)
                .putExtra("simulated", true).putExtra("mode", "COMBINED_TELEMETRY"))
            val degraded = withTimeout(15_000) { graph.recording.first { it.active && it.gpsError != null && it.vehicle != null } }
            sessionId = degraded.sessionId
            val recovered = withTimeout(15_000) { graph.recording.first { it.active && it.gpsError == null && it.gps != null && it.samples > degraded.samples } }
            assertEquals(sessionId, recovered.sessionId)
            assertTrue(recovered.vehicle!!.connected)
            assertTrue(attempts >= 2)
        } finally {
            activity.startService(Intent(activity, RecordingService::class.java).setAction("STOP"))
            withTimeout(15_000) { graph.recording.first { !it.active } }
            graph.recordingSources = previousFactory
            sessionId?.let { graph.database.dao().deleteFinishedSession(it) }
        }
    }

    @Test fun invalidThresholdBlocksSaveWithoutClaimingSuccess() {
        compose.onNodeWithText("Vehicles & settings").performClick()
        compose.onNodeWithText("Coolant °C").performScrollTo().performTextReplacement("9999")
        compose.onNodeWithText("Save dashboard & warnings").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Coolant °C").performScrollTo().performTextReplacement("110")
        compose.onNodeWithText("Save dashboard & warnings").performScrollTo().assertIsEnabled()
    }

    @Test fun setupNamesUnmonitoredSensorsBeforeARealConnection() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.onNodeWithText("Vehicle dashboard").performClick()
        compose.onNodeWithText("Set up recording").performClick()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Monitoring readiness").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not monitored — unavailable through standard OBD-II").assertExists()
        compose.onNodeWithText("Start recording").performScrollTo().assertIsNotEnabled()
        captureMonitoringPreview(compose.onRoot(), "monitoring-readiness-landscape.png")
    }

    @Test fun storageFailureStopsRecordingWithoutReportingSaved() = runBlocking {
        val activity = compose.activity
        val graph = activity.application as TrackDashApplication
        graph.ready.await()
        val previousId = graph.recording.value.sessionId
        graph.recordingBatchWriter = { throw java.io.IOException("Synthetic full storage") }
        var sessionId: String? = null
        try {
            activity.startForegroundService(Intent(activity, RecordingService::class.java)
                .putExtra("simulated", true).putExtra("mode", "COMBINED_TELEMETRY"))
            val failed = withTimeout(15_000) { graph.recording.first {
                it.sessionId != null && it.sessionId != previousId && !it.active && it.error != null
            } }
            sessionId = failed.sessionId
            assertEquals("Recording failed", failed.message)
            assertEquals("ERROR", graph.database.dao().session(requireNotNull(sessionId))!!.status)
            assertEquals(0L, failed.samples)
        } finally {
            graph.recordingBatchWriter = null
            activity.startService(Intent(activity, RecordingService::class.java).setAction("STOP"))
            sessionId?.let { graph.database.dao().deleteFinishedSession(it) }
        }
    }
}
