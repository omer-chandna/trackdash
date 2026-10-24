package dev.trackdash.settings

import android.content.res.AssetManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.trackdash.ui.PageHeader
import dev.trackdash.ui.SectionTitle
import dev.trackdash.ui.TrackDashPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All attribution is bundled with the APK; viewing it never needs a network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutOpenSourceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showComponents by rememberSaveable { mutableStateOf(false) }
    var componentFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var document by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { bundledLicenseFiles(context.assets) } }
            .onSuccess { componentFiles = it }
            .onFailure { error = "Bundled notices could not be opened." }
    }
    LaunchedEffect(selected) {
        document = null
        error = null
        val path = selected ?: return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                require(path.startsWith("opensource/") && ".." !in path)
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
        }.onSuccess { document = it }.onFailure { error = "This bundled notice could not be opened." }
    }
    val back = { if (selected != null) selected = null else onBack() }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (selected == null) "About & open source" else "Open-source licenses") },
            navigationIcon = { IconButton(onClick = back) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            } },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (selected == null) {
                item {
                    PageHeader("TrackDash 0.3", "Open-source performance monitoring", eyebrow = "Apache-2.0")
                }
                item {
                    TrackDashPanel(Modifier.fillMaxWidth()) {
                        SectionTitle("Development software", "Hardware qualification is still pending.")
                        Text("TrackDash supplements factory instruments; it is not an engine-protection or repair system. No manufacturer limits are assumed.")
                        Text("Copyright 2026 Omer Chandna and TrackDash contributors", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    TrackDashPanel(Modifier.fillMaxWidth()) {
                        SectionTitle("Offline licenses & notices", "These files are included in this APK.")
                        listOf(
                            "Project license" to "LICENSE",
                            "Project notice" to "NOTICE",
                            "Third-party notices" to "THIRD_PARTY_NOTICES.md",
                            "Runtime dependency inventory" to "runtime-license-metadata.tsv",
                            "MapLibre native notices" to "MapLibre-Native-Android-13.6.1.txt",
                            "MapLibre gestures notices" to "MapLibre-Gestures-Android-0.0.4.txt",
                        ).forEach { (label, file) ->
                            OutlinedButton(onClick = { selected = "opensource/$file" }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                        Text("Map tiles, styles and provider data retain their own terms and attribution.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    TextButton(onClick = { showComponents = !showComponents }) {
                        Text(if (showComponents) "Hide bundled component files" else "Show bundled component files")
                    }
                }
                if (showComponents) {
                    items(componentFiles.size, key = { componentFiles[it] }) { index ->
                        val path = componentFiles[index]
                        TextButton(onClick = { selected = path }, modifier = Modifier.fillMaxWidth()) {
                            Text(path.removePrefix("opensource/embedded/"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item { Text(selected!!.substringAfterLast('/'), style = MaterialTheme.typography.titleLarge) }
                item {
                    when {
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        document == null -> CircularProgressIndicator()
                        else -> Text(document!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
      }
    }
}

internal fun bundledLicenseFiles(assets: AssetManager): List<String> {
    val result = mutableListOf<String>()
    fun visit(path: String) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) result += path
        else children.forEach { visit("$path/$it") }
    }
    visit("opensource/embedded")
    return result.sorted()
}
