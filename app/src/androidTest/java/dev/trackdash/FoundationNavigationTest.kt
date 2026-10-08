package dev.trackdash

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun destinationsShowHonestEmptyStatesAndBackReturnsHome() {
        compose.onNodeWithText("Tracks").performClick()
        compose.onNodeWithText("No saved tracks").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
        compose.onNodeWithText("Sessions").performClick()
        compose.onNodeWithText("No recorded sessions").assertIsDisplayed()
        compose.onNodeWithText("Diagnostics").performClick()
        compose.onNodeWithText("OBD · Disconnected").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("GPS lap timer").assertIsSelected()
    }

    @Test fun modeSurvivesNavigationAndActivityRecreation() {
        compose.onNodeWithText("Vehicle dashboard").performClick()
        compose.onNodeWithText("Vehicle dashboard").assertIsSelected()
        compose.onNodeWithText("Tracks").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Vehicle dashboard").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Vehicle dashboard").assertIsSelected()
    }

    @Test fun landscapeNavigationRemainsUsable() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.onNodeWithText("Tracks").performClick()
        compose.onNodeWithText("No saved tracks").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Combined telemetry").performScrollTo().performClick()
        compose.onNodeWithText("Combined telemetry").assertIsSelected()
    }
}
