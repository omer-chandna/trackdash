package dev.trackdash

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun start() {
        compose.onNodeWithText("Try vehicle simulation").performClick()
        compose.onNodeWithText("SIMULATION · RUNNING").assertIsDisplayed()
        compose.waitUntil(5000) { rpm() != "—" }
    }
    private fun rpm() = compose.onNodeWithTag("rpm-value").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    private fun scenario(name: String) {
        compose.onNodeWithText("Scenarios").performScrollTo().performClick()
        compose.onNodeWithText(name).performClick()
    }
    private fun waitForText(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun liveReadingsStopAndRestartWithExplicitSimulationLabel() {
        start()
        val initial = rpm()
        compose.waitUntil(5000) { rpm() != initial }
        compose.onNodeWithText("Stop simulation").performClick()
        compose.onNodeWithTag("rpm-value").assertTextEquals("—")
        compose.onNodeWithText("SIMULATION · STOPPED").assertIsDisplayed()
        compose.onNodeWithText("Start simulation").performClick()
        compose.waitUntil(5000) { rpm() != "—" }
    }

    @Test fun pressureAndDisconnectFaultsClearHealthyValuesAndRecover() {
        start()
        scenario("Low oil pressure")
        waitForText("CRITICAL · Low oil-pressure switch")
        compose.onNodeWithTag("tile-OIL PRESSURE").assertTextEquals("LOW")
        scenario("OBD disconnect")
        waitForText("WARNING · Vehicle data unavailable")
        compose.onNodeWithTag("rpm-value").assertTextEquals("—")
        compose.onNodeWithTag("tile-OIL PRESSURE").assertTextEquals("UNAVAILABLE")
        scenario("Normal driving")
        compose.waitUntil(5000) { rpm() != "—" }
        compose.onNodeWithTag("tile-OIL PRESSURE").assertTextEquals("OK")
    }

    @Test fun overheatingAndEditableThresholdsAreFunctional() {
        start()
        scenario("Overheating")
        waitForText("WARNING · Coolant temperature above configured limit")
        compose.onNodeWithText("Warning limits").performScrollTo().performClick()
        compose.onNodeWithText("Coolant limit °C").performTextReplacement("bad")
        compose.onNodeWithText("Apply limits").assertIsNotEnabled()
        compose.onNodeWithText("Coolant limit °C").performTextReplacement("150")
        compose.onNodeWithText("Oil limit °C").performTextReplacement("160")
        compose.onNodeWithText("Apply limits").performClick()
        compose.onNodeWithText("WARNING · Coolant temperature above configured limit").assertDoesNotExist()
    }

    @Test fun rotationRetainsScenarioAndBackgroundingStopsDemo() {
        start()
        scenario("Low oil pressure")
        waitForText("CRITICAL · Low oil-pressure switch")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        waitForText("CRITICAL · Low oil-pressure switch")
        compose.onNodeWithText("SIMULATION · RUNNING").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("SIMULATION · STOPPED").assertIsDisplayed()
        compose.onNodeWithTag("rpm-value").assertTextEquals("—")
    }
}
