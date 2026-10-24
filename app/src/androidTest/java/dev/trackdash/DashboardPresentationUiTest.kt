package dev.trackdash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import dev.trackdash.core.SensorId
import dev.trackdash.core.telemetry.AlertCategory
import dev.trackdash.core.telemetry.AlertSeverity
import dev.trackdash.core.telemetry.VehicleAlert
import dev.trackdash.recording.AlertBanners
import dev.trackdash.recording.DashboardAlertLayer
import dev.trackdash.ui.TrackDashTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class DashboardPresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun engineSafetyPulsesButConnectionWarningsDoNot() {
        compose.mainClock.autoAdvance = false
        var alerts by mutableStateOf(emptyList<VehicleAlert>())
        compose.setContent {
            TrackDashTheme {
                DashboardAlertLayer(alerts, false) { Column { AlertBanners(alerts) } }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle {
            alerts = listOf(VehicleAlert("coolant", "Coolant hot", AlertSeverity.WARNING,
                AlertCategory.ENGINE_SAFETY, SensorId.COOLANT_TEMPERATURE))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("safety-flash").assertExists()
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
        compose.onNodeWithTag("alert-coolant").assertExists()

        compose.runOnIdle { alerts = emptyList() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            alerts = listOf(VehicleAlert("gps", "GPS signal lost", AlertSeverity.WARNING, AlertCategory.CONNECTIVITY))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
    }

    @Test fun shiftPulsesThenLeavesSteadyPrompt() {
        compose.mainClock.autoAdvance = false
        var shift by mutableStateOf(false)
        compose.setContent {
            TrackDashTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DashboardAlertLayer(emptyList(), shift) {}
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle { shift = true }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("shift-flash").assertExists()
        savePreview("shift-flash", "shift-flash.png")
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("shift-flash").assertDoesNotExist()
        compose.onNodeWithTag("shift-steady").assertExists()
        savePreview(null, "shift-steady.png")
    }

    @Test fun simultaneousEngineWarningsFlashOneAtATime() {
        compose.mainClock.autoAdvance = false
        var alerts by mutableStateOf(emptyList<VehicleAlert>())
        compose.setContent {
            TrackDashTheme { DashboardAlertLayer(alerts, false) { Column { AlertBanners(alerts) } } }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle { alerts = listOf(oil, coolant, pressure) }
        compose.mainClock.advanceTimeBy(32)
        val startedAt = compose.mainClock.currentTime
        fun advanceTo(time: Long) { compose.mainClock.advanceTimeBy((time - compose.mainClock.currentTime).coerceAtLeast(0)) }
        listOf("LOW OIL PRESSURE", "HIGH COOLANT TEMP", "HIGH OIL TEMP").forEachIndexed { warning, headline ->
            repeat(3) { pulse ->
                val cycleStart = startedAt + warning * 1_500L + pulse * 500L
                // Sample inside each phase, leaving room for Compose's next render frame at the boundaries.
                advanceTo(cycleStart + 100)
                compose.onNodeWithTag("safety-flash").onChild().assertTextEquals(headline)
                compose.onNodeWithTag("shift-flash").assertDoesNotExist()
                advanceTo(cycleStart + 350)
                compose.onNodeWithTag("safety-flash").assertDoesNotExist()
            }
        }
        advanceTo(startedAt + 4_700)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
        compose.onNodeWithTag("alert-oil-pressure").assertExists()
        compose.onNodeWithTag("alert-oil").assertExists()
        compose.onNodeWithTag("alert-coolant").assertExists()
    }

    @Test fun restorationResumesTheWarningCycleWithoutReplayingItOrLosingTheQueue() {
        compose.mainClock.autoAdvance = false
        var alerts by mutableStateOf(emptyList<VehicleAlert>())
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TrackDashTheme { DashboardAlertLayer(alerts, false) {} }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle { alerts = listOf(pressure, oil) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("safety-flash").onChild().assertTextEquals("LOW OIL PRESSURE")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_300)
        compose.onNodeWithTag("safety-flash").onChild().assertTextEquals("HIGH OIL TEMP")
    }

    @Test fun engineWarningFinishesBeforeShiftFlashes() {
        compose.mainClock.autoAdvance = false
        var alerts by mutableStateOf(emptyList<VehicleAlert>())
        var shift by mutableStateOf(false)
        compose.setContent {
            TrackDashTheme { DashboardAlertLayer(alerts, shift) {} }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle { alerts = listOf(pressure); shift = true }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("safety-flash").onChild().assertTextEquals("LOW OIL PRESSURE")
        compose.onNodeWithTag("shift-flash").assertDoesNotExist()
        compose.onNodeWithTag("shift-steady").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("safety-flash").assertDoesNotExist()
        compose.onNodeWithTag("shift-flash").assertExists()
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag("shift-flash").assertDoesNotExist()
        compose.onNodeWithTag("shift-steady").assertExists()
        compose.runOnIdle { shift = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("shift-steady").assertDoesNotExist()
    }

    private val pressure = VehicleAlert("oil-pressure", "Low oil pressure", AlertSeverity.CRITICAL, AlertCategory.ENGINE_SAFETY)
    private val oil = VehicleAlert("oil", "Oil temperature above configured limit", AlertSeverity.WARNING,
        AlertCategory.ENGINE_SAFETY, SensorId.OIL_TEMPERATURE)
    private val coolant = VehicleAlert("coolant", "Coolant temperature above configured limit", AlertSeverity.WARNING,
        AlertCategory.ENGINE_SAFETY, SensorId.COOLANT_TEMPERATURE)

    private fun savePreview(tag: String?, name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureWarningPreviews") != "true") return
        val bitmap = (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(requireNotNull(directory), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
