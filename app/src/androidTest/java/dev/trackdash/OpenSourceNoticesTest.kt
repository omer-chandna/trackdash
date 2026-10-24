package dev.trackdash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import dev.trackdash.settings.AboutOpenSourceScreen
import dev.trackdash.settings.bundledLicenseFiles
import dev.trackdash.ui.TrackDashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OpenSourceNoticesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun packagedLicenseTextsAreAvailableOffline() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun text(file: String) = assets.open("opensource/$file").bufferedReader().use { it.readText() }
        assertTrue(text("LICENSE").contains("Version 2.0, January 2004"))
        assertTrue(text("NOTICE").contains("Omer Chandna"))
        assertTrue(text("THIRD_PARTY_NOTICES.md").contains("BSD-3-Clause"))
        assertTrue(text("MapLibre-Native-Android-13.6.1.txt").contains("Copyright"))
        assertTrue(text("MapLibre-Gestures-Android-0.0.4.txt").contains("BSD 2-Clause"))
        val embedded = bundledLicenseFiles(assets)
        assertTrue("Runtime notices must be packaged", embedded.isNotEmpty())
        assertTrue("DataStore protobuf BSD notice must be preserved", embedded.any {
            "datastore-preferences-external-protobuf" in it && "LICENSE" in it
        })
    }

    @Test fun licenseViewerAndBackWorkWithoutNetwork() {
        var returned = false
        compose.setContent { TrackDashTheme { AboutOpenSourceScreen { returned = true } } }
        if (InstrumentationRegistry.getArguments().getString("captureMonitoringPreviews") == "true") {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val folder = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
            File(folder, "open-source-notices.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Project license").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Version 2.0, January 2004", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("TrackDash 0.3").assertExists()
        assertFalse(returned)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(returned) }
    }
}
