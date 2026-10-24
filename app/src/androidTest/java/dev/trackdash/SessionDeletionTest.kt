package dev.trackdash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.trackdash.data.SessionRow
import dev.trackdash.data.TrackDashApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SessionDeletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun listAndDetailDeletionRequireConfirmationAndActiveSessionIsProtected() {
        val dao = (compose.activity.application as TrackDashApplication).database.dao()
        val listId = UUID.randomUUID().toString()
        val detailId = UUID.randomUUID().toString()
        val activeId = UUID.randomUUID().toString()
        runBlocking {
            dao.insertSession(SessionRow(listId, System.currentTimeMillis(), 2L, "LIST_DELETE_TEST", true, null, null, "FINISHED"))
            dao.insertSession(SessionRow(detailId, System.currentTimeMillis() + 1, 3L, "DETAIL_DELETE_TEST", true, null, null, "INTERRUPTED"))
            dao.insertSession(SessionRow(activeId, System.currentTimeMillis() + 2, mode = "ACTIVE_DELETE_TEST", simulated = true, trackId = null, vehicleId = null))
        }
        try {
            compose.onNodeWithContentDescription("Sessions").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("List delete test").fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithContentDescription("Delete list delete test session").performClick()
            compose.onNodeWithText("Delete this session?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertNotNull(runBlocking { dao.session(listId) })

            compose.onNodeWithContentDescription("Delete list delete test session").performClick()
            compose.onNodeWithText("Delete permanently").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.session(listId) == null } }

            compose.onNodeWithText("Detail delete test").performClick()
            compose.onNodeWithText("Session details").assertIsDisplayed()
            compose.onNodeWithContentDescription("Delete detail delete test session").performClick()
            compose.onNodeWithText("Delete permanently").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.session(detailId) == null } }
            compose.onNodeWithText("Session history").assertIsDisplayed()

            compose.onNodeWithText("Active delete test").performClick()
            compose.onNodeWithContentDescription("Stop and save before deleting active delete test session").assertIsNotEnabled()
        } finally {
            runBlocking {
                dao.deleteSession(listId)
                dao.deleteSession(detailId)
                dao.deleteSession(activeId)
            }
        }
    }
}
