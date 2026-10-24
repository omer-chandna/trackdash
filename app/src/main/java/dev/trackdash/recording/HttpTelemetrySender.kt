package dev.trackdash.recording

import android.content.Context
import dev.trackdash.core.VehicleSample
import dev.trackdash.core.sharing.TelemetryEnvelope
import dev.trackdash.core.sharing.TelemetrySender
import dev.trackdash.core.sharing.TelemetryShareDispatcher
import dev.trackdash.core.sharing.validateSharingEndpoint
import dev.trackdash.core.sharing.validateSharingAuthorization
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One bounded HTTPS POST. No redirects, response-body retention, cookies, retries or URL secrets. */
class HttpTelemetrySender(private val endpoint: String, private val authorization: String = "",
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) : TelemetrySender {
    init {
        validateSharingEndpoint(endpoint)
        validateSharingAuthorization(authorization)
    }

    override suspend fun send(envelope: TelemetryEnvelope) = runInterruptible(Dispatchers.IO) {
        val payload = Json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val connection = openConnection(URL(endpoint))
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (authorization.isNotEmpty()) connection.setRequestProperty("Authorization", authorization)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            if (connection.responseCode !in 200..299) throw IOException("Receiver rejected telemetry")
            // The receiver only acknowledges delivery. Do not parse or log an untrusted response.
        } finally { connection.disconnect() }
    }
}

/** Created once at recording start. It is never constructed by replay and never owns acquisition. */
class TelemetrySharingSession private constructor(private val dispatcher: TelemetryShareDispatcher) : AutoCloseable {
    val stats = dispatcher.stats
    fun tryOffer(sample: VehicleSample): Boolean = dispatcher.tryOffer(sample)
    override fun close() = dispatcher.close()

    companion object {
        suspend fun create(context: Context, scope: CoroutineScope): TelemetrySharingSession? {
            val settings = TelemetrySharingPreferences(context).settings.first()
            if (!settings.enabled) return null
            return TelemetrySharingSession(TelemetryShareDispatcher(scope,
                HttpTelemetrySender(settings.endpoint, settings.authorization)))
        }
    }
}
