package dev.trackdash.core.sharing

import dev.trackdash.core.SensorId
import dev.trackdash.core.SensorSource
import dev.trackdash.core.SensorStatus
import dev.trackdash.core.SensorUnit
import dev.trackdash.core.VehicleSample
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/** No coordinates, VIN, wall-clock time, vehicle/session identifiers, notes or authorization. */
@Serializable
data class TelemetryEnvelope(
    val schema: String,
    val version: Int,
    val elapsedMillis: Long,
    val source: SensorSource,
    val connected: Boolean,
    val sensors: List<SharedSensor>,
)

@Serializable
data class SharedSensor(val sensor: SensorId, val unit: SensorUnit, val value: Double?,
    val status: SensorStatus, val ageMillis: Long)

data class SharingStats(val sent: Long = 0, val dropped: Long = 0, val errors: Long = 0)

fun interface TelemetrySender { suspend fun send(envelope: TelemetryEnvelope) }

/** Strict transport boundary: no embedded credentials, URL tokens, redirects or cleartext HTTP. */
fun validateSharingEndpoint(endpoint: String) {
    require(endpoint.length <= 2_048) { "Receiver URL must be at most 2048 characters" }
    val uri = try { URI(endpoint) } catch (_: Exception) { throw IllegalArgumentException("Use a valid HTTPS receiver URL") }
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        (uri.port == -1 || uri.port in 1..65535)) {
        "Use an HTTPS receiver URL without credentials, query parameters or fragments"
    }
}

fun validateSharingAuthorization(authorization: String) {
    require(authorization.length <= 4_096 && authorization.all { it.code in 32..126 }) {
        "Authorization must be a single printable header value of at most 4096 characters"
    }
}

/**
 * Best-effort, bounded, non-blocking fan-out. A network failure can never cancel the recording.
 * Nothing is persisted/retried; full queues drop new samples and expose a counter. At most 1 Hz
 * by default, measured in source acquisition time rather than a wall clock.
 */
class TelemetryShareDispatcher(
    scope: CoroutineScope,
    private val sender: TelemetrySender,
    capacity: Int = 8,
    private val minimumIntervalMillis: Long = 1_000,
    timeoutMillis: Long = 5_000,
) : AutoCloseable {
    private val queue: Channel<TelemetryEnvelope>
    private val sharingScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val mutableStats = MutableStateFlow(SharingStats())
    val stats = mutableStats.asStateFlow()
    private var firstNanos: Long? = null
    private var previousOfferedNanos: Long? = null

    init {
        require(capacity in 1..64)
        require(minimumIntervalMillis in 200..60_000)
        require(timeoutMillis in 1..10_000)
        queue = Channel(capacity)
        sharingScope.launch {
            for (envelope in queue) {
                try {
                    withTimeout(timeoutMillis) { sender.send(envelope) }
                    mutableStats.update { it.copy(sent = it.sent + 1) }
                } catch (error: CancellationException) {
                    // A per-request timeout is an error; owner cancellation ends the worker.
                    if (error is kotlinx.coroutines.TimeoutCancellationException)
                        mutableStats.update { it.copy(errors = it.errors + 1) }
                    else throw error
                } catch (_: Exception) { mutableStats.update { it.copy(errors = it.errors + 1) } }
            }
        }
    }

    @Synchronized
    fun tryOffer(sample: VehicleSample): Boolean {
        val last = previousOfferedNanos
        if (last != null && sample.elapsedRealtimeNanos - last < minimumIntervalMillis * 1_000_000) return false
        previousOfferedNanos = sample.elapsedRealtimeNanos
        if (firstNanos == null) firstNanos = sample.elapsedRealtimeNanos
        val envelope = TelemetryEnvelope(
            schema = "dev.trackdash.telemetry", version = 1,
            elapsedMillis = (sample.elapsedRealtimeNanos - requireNotNull(firstNanos)).coerceAtLeast(0) / 1_000_000,
            source = sample.source, connected = sample.connected,
            sensors = sample.readings.sortedBy { it.sensorId.ordinal }.map {
                SharedSensor(it.sensorId, it.sensorId.unit,
                    it.value.takeIf { _ -> it.status == SensorStatus.AVAILABLE }, it.status,
                    ((sample.elapsedRealtimeNanos - it.elapsedRealtimeNanos).coerceAtLeast(0)) / 1_000_000)
            },
        )
        return queue.trySend(envelope).isSuccess.also { accepted ->
            if (!accepted) mutableStats.update { it.copy(dropped = it.dropped + 1) }
        }
    }

    override fun close() { queue.close(); sharingScope.cancel() }
}
