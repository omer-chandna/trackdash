package dev.trackdash

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Explicit test-only screenshot capture; never includes a user's recordings or credentials. */
internal fun captureMonitoringPreview(node: SemanticsNodeInteraction, name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureMonitoringPreviews") != "true") return
    val bitmap = node.captureToImage().asAndroidBitmap()
    val folder = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
    File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
