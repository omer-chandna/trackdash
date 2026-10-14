package dev.trackdash.tracks

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.trackdash.core.gps.GeoPoint
import dev.trackdash.data.Preferences
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

@Composable
fun CircuitSearch(onSelect: (GeoPoint) -> Unit) {
    val context = LocalContext.current
    val endpoint by remember { Preferences(context.applicationContext).searchEndpoint }.collectAsStateWithLifecycle("")
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Pair<String, GeoPoint>>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (endpoint.isNotBlank()) {
        OutlinedTextField(query, { query = it.take(200) }, label = { Text("Circuit search") })
        Button(enabled = !busy && query.isNotBlank(), onClick = { scope.launch {
            busy = true
            try {
                results = withContext(Dispatchers.IO) {
                    val url = URI.create(endpoint + "?format=jsonv2&limit=5&q=" + URLEncoder.encode(query, "UTF-8")).toURL()
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "TrackDash/0.3 (https://github.com/omer-chandna/trackdash)")
                    try {
                        check(connection.responseCode == 200) { "Search returned HTTP ${connection.responseCode}" }
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                            while (output.size() <= 128_000) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }; output.toByteArray()
                        }
                        require(bytes.size <= 128_000) { "Search response too large" }
                        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonArray.take(5).map { item ->
                            val value = item.jsonObject
                            value.getValue("display_name").jsonPrimitive.content to GeoPoint(value.getValue("lat").jsonPrimitive.content.toDouble(), value.getValue("lon").jsonPrimitive.content.toDouble())
                        }
                    } finally { connection.disconnect() }
                }
                message = if (results.isEmpty()) "No results" else "Search results · © OpenStreetMap contributors / configured provider"
                delay(1000) // User-triggered queries only; no autocomplete or bulk requests.
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = "Search failed: ${error.message}" }
            finally { busy = false }
        } }) { Text(if (busy) "Searching…" else "Search") }
        message?.let { Text(it) }
        results.forEach { (name, point) -> TextButton(onClick = { onSelect(point); results = emptyList() }) { Text(name) } }
    }
}

