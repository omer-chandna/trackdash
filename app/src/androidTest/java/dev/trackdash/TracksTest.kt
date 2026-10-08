package dev.trackdash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class TracksTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun savedTrackLoadsItsGatesAndCanBeReusedAfterEditing() {
        compose.onNodeWithText("Tracks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demo circuit").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Edit").onFirst().performClick()
        compose.onNodeWithText("Track name").assertTextContains("Demo circuit")
        compose.onNodeWithText("Save track").performScrollTo().performClick()
        compose.onNodeWithText("Demo circuit").assertIsDisplayed()
    }
}
