package dev.trackdash.tracks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.trackdash.data.Preferences
import kotlinx.coroutines.*
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun OfflineMaps() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MapLibre.getInstance(context); OfflineManager.getInstance(context.applicationContext) }
    var regions by remember { mutableStateOf(emptyList<OfflineRegion>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun refresh() { manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
        override fun onList(offlineRegions: Array<OfflineRegion>?) { regions = offlineRegions?.toList().orEmpty() }
        override fun onError(error: String) { message = error }
    }) }
    LaunchedEffect(Unit) { refresh() }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val temporary = withContext(Dispatchers.IO) { File.createTempFile("map-package", ".db", context.cacheDir) }
            try {
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 512L * 1024 * 1024) { "Package exceeds 512 MiB" }; output.write(buffer, 0, count) }
                } } }
                val merged = withContext(NonCancellable) { suspendCancellableCoroutine<Int> { continuation ->
                    manager.mergeOfflineRegions(temporary.absolutePath, object : OfflineManager.MergeOfflineRegionsCallback {
                        override fun onMerge(offlineRegions: Array<OfflineRegion>?) { if (continuation.isActive) continuation.resume(offlineRegions?.size ?: 0) }
                        override fun onError(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException(error)) }
                    })
                } }
                message = "$merged offline regions imported. Select a region to use its style; incomplete packages may still need network resources."
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = "Import failed: ${error.message}" }
            finally { withContext(NonCancellable + Dispatchers.IO) { temporary.delete() }; busy = false }
        }
    }
    Text("Offline map packages", style = MaterialTheme.typography.titleLarge)
    Text("Import a licensed MapLibre offline-region database. MBTiles files are not this format. Track gates and timing remain available without a package.")
    Button(enabled = !busy, onClick = { import.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) }) { Text(if (busy) "Importing…" else "Import map package") }
    regions.forEach { region -> TextButton(onClick = { scope.launch {
        message = runCatching {
            val definition = region.definition as? OfflineTilePyramidRegionDefinition ?: error("Unsupported region definition")
            Preferences(context.applicationContext).saveMapStyle(requireNotNull(definition.styleURL) { "Package has no reusable style URL" }); "Offline region ${region.id} selected"
        }.getOrElse { it.message }
    } }) { Text("Use offline region ${region.id}") } }
    message?.let { Text(it) }
}
