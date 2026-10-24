package dev.trackdash.core.replay

import dev.trackdash.core.GpsFix
import dev.trackdash.core.VehicleSample
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json

/** Existing session payloads, in their original database insertion order. No schema conversion. */
data class RecordedTelemetryRow(val id: Long, val elapsedNanos: Long, val kind: String, val payload: String)

enum class ReplayAlertState { ACTIVE, CLEARED, UNCONFIRMED, CONFIRMED }

sealed interface ReplayEvent {
    val positionNanos: Long
    data class Vehicle(val sample: VehicleSample, override val positionNanos: Long) : ReplayEvent
    data class Gps(val fix: GpsFix, override val positionNanos: Long) : ReplayEvent
    data class Alert(val message: String, val state: ReplayAlertState, override val positionNanos: Long) : ReplayEvent
    data class Malformed(override val positionNanos: Long) : ReplayEvent
}

/**
 * Bounded-memory, cancellable playback of a local recording. Acquisition timestamps are retained;
 * only playback waits are scaled. Late deliveries never move the playback clock backwards.
 * Long gaps are capped at [maxGapMillis] so a dropout cannot trap playback for minutes.
 */
object SessionReplay {
    private val json = Json { ignoreUnknownKeys = true }
    const val PAGE_SIZE = 256
    const val MAX_PAYLOAD_CHARS = 65_536

    fun events(
        readPage: suspend (afterId: Long, limit: Int) -> List<RecordedTelemetryRow>,
        speed: Double = 1.0,
        maxGapMillis: Long = 2_000,
    ): Flow<ReplayEvent> = flow {
        require(speed.isFinite() && speed in 0.25..8.0) { "Replay speed must be 0.25–8×" }
        require(maxGapMillis in 0..5_000) { "Replay gap must be bounded to five seconds" }
        var cursor = 0L
        var firstNanos: Long? = null
        var previousPosition = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = readPage(cursor, PAGE_SIZE)
            require(page.size <= PAGE_SIZE) { "Replay page exceeded its requested bound" }
            if (page.isEmpty()) break
            for (row in page) {
                currentCoroutineContext().ensureActive()
                require(row.id > cursor) { "Replay pages must advance in insertion order" }
                cursor = row.id
                if (row.kind !in setOf("vehicle", "gps", "alert", "alert-cleared", "alert-unconfirmed", "alert-confirmed")) continue
                if (firstNanos == null) firstNanos = row.elapsedNanos.coerceAtLeast(0)
                val position = (row.elapsedNanos.coerceAtLeast(0) - firstNanos).coerceAtLeast(previousPosition)
                val waitMillis = (((position - previousPosition) / 1_000_000.0) / speed)
                    .coerceAtMost(maxGapMillis.toDouble()).toLong()
                delay(waitMillis)
                previousPosition = position
                val event = if (row.payload.length > MAX_PAYLOAD_CHARS) ReplayEvent.Malformed(position)
                else try {
                    when (row.kind) {
                        "vehicle" -> ReplayEvent.Vehicle(json.decodeFromString(row.payload), position)
                        "gps" -> ReplayEvent.Gps(json.decodeFromString(row.payload), position)
                        else -> ReplayEvent.Alert(json.decodeFromString(row.payload), when (row.kind) {
                            "alert-cleared" -> ReplayAlertState.CLEARED
                            "alert-unconfirmed" -> ReplayAlertState.UNCONFIRMED
                            "alert-confirmed" -> ReplayAlertState.CONFIRMED
                            else -> ReplayAlertState.ACTIVE
                        }, position)
                    }
                } catch (_: IllegalArgumentException) { ReplayEvent.Malformed(position) }
                emit(event)
            }
            yield()
        }
    }
}
