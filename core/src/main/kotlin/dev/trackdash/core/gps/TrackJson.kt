package dev.trackdash.core.gps

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class TrackDocument(val version: Int = 1, val track: Track)
object TrackJson {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    fun encode(track: Track): String = json.encodeToString(TrackDocument(track = track))
    fun decode(text: String): Track {
        require(text.length <= 128_000) { "Track file too large" }
        val document = json.decodeFromString<TrackDocument>(text)
        require(document.version == 1) { "Unsupported track schema" }
        return document.track
    }
}
