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
        compose.onNodeWithContentDescription("Tracks").performClick()
        compose.onNodeWithText("Track library").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("TRACKDASH").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sessions").performClick()
        compose.onNodeWithText("Session history").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Vehicles & settings").performClick()
        compose.onNodeWithText("Connection status").performScrollTo().performClick()
        compose.onNodeWithText("Vehicle data").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("GPS lap timer") and hasClickAction()).assertIsSelected()
    }

    @Test fun modeSurvivesNavigationAndActivityRecreation() {
        compose.onNodeWithText("Vehicle dashboard").performClick()
        compose.onNode(hasText("Vehicle dashboard") and hasClickAction()).assertIsSelected()
        compose.onNodeWithContentDescription("Tracks").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNode(hasText("Vehicle dashboard") and hasClickAction()).assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("Vehicle dashboard") and hasClickAction()).assertIsSelected()
    }

    @Test fun landscapeNavigationRemainsUsable() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Collapse navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("TrackDash").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracks").performClick()
        compose.onNodeWithText("Track library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Vehicles & settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Track library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Combined telemetry").performScrollTo().performClick()
        compose.onNode(hasText("Combined telemetry") and hasClickAction()).assertIsSelected()
    }
}

