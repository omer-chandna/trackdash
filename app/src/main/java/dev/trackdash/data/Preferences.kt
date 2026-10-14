package dev.trackdash.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.trackdash.core.SensorId
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("settings")
@Serializable
data class DashboardPreferences(val fahrenheit: Boolean = false, val mph: Boolean = false,
    val keepAwake: Boolean = true, val fullscreen: Boolean = false, val hideUnsupported: Boolean = true,
    val gauges: List<SensorId> = listOf(SensorId.ENGINE_RPM, SensorId.SPEED, SensorId.COOLANT_TEMPERATURE, SensorId.OIL_TEMPERATURE),
    val largeGauges: Boolean = true, val bars: Boolean = false, val alertsEnabled: Boolean = true,
    val audible: Boolean = false, val coolantLimit: Double? = null, val oilLimit: Double? = null,
    val intakeLimit: Double? = null, val rpmLimit: Double? = null)

class Preferences(private val context: Context) {
    private fun profileKey(vehicleId: String) = stringPreferencesKey("profile:$vehicleId")
    fun dashboard(vehicleId: String) = context.dataStore.data.map { prefs ->
        prefs[profileKey(vehicleId)]?.let { Json.decodeFromString<DashboardPreferences>(it) } ?: DashboardPreferences()
    }
    suspend fun saveDashboard(vehicleId: String, value: DashboardPreferences) {
        context.dataStore.edit { it[profileKey(vehicleId)] = Json.encodeToString(value) }
    }
    val mapStyle = context.dataStore.data.map { it[stringPreferencesKey("mapStyle")] ?: "" }
    val searchEndpoint = context.dataStore.data.map { it[stringPreferencesKey("searchEndpoint")] ?: "" }
    suspend fun saveSearchEndpoint(url: String) {
        require(url.isBlank() || url.startsWith("https://") && '?' !in url && '#' !in url) { "Use an HTTPS Nominatim search endpoint without query parameters" }
        context.dataStore.edit { it[stringPreferencesKey("searchEndpoint")] = url }
    }
    suspend fun saveMapStyle(url: String) {
        require(url.isBlank() || url.startsWith("https://")) { "Use an HTTPS MapLibre style URL" }
        context.dataStore.edit { it[stringPreferencesKey("mapStyle")] = url }
    }
}
