package dev.trackdash.core

import dev.trackdash.core.replay.RecordedTelemetryRow
import dev.trackdash.core.replay.ReplayEvent
import dev.trackdash.core.replay.ReplayAlertState
import dev.trackdash.core.replay.SessionReplay
import dev.trackdash.core.simulation.SimulatedVehicleSource
import dev.trackdash.core.simulation.SimulationScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionReplayTest {
    private fun vehicle(id: Long, nanos: Long) = RecordedTelemetryRow(id, nanos, "vehicle",
        Json.encodeToString(SimulatedVehicleSource.sampleAt(0, nanos, SimulationScenario.NORMAL)))

    @Test fun `original payload timestamps and delivery order survive replay`() = runTest {
        val fix = GpsFix(51.0, -1.0, null, 12.0, null, 2.0, "gps", 123, 1_500_000_000, false)
        val rows = listOf(vehicle(1, 1_000_000_000), RecordedTelemetryRow(2, fix.elapsedRealtimeNanos, "gps", Json.encodeToString(fix)),
            vehicle(3, 1_200_000_000), RecordedTelemetryRow(4, 1_500_000_000, "alert", Json.encodeToString("High coolant temperature")))
        val events = SessionReplay.events({ after, _ -> rows.filter { it.id > after } }).toList()
        assertEquals(listOf(0L, 500_000_000L, 500_000_000L, 500_000_000L), events.map { it.positionNanos })
        assertEquals(1_200_000_000, (events[2] as ReplayEvent.Vehicle).sample.elapsedRealtimeNanos)
        assertEquals(fix, (events[1] as ReplayEvent.Gps).fix)
        assertEquals(500L, currentTime)
    }

    @Test fun `replay is deterministic speed scaled paged and bounded through long gaps`() = runTest {
        val rows = listOf(vehicle(1, 0), vehicle(2, 1_000_000_000), vehicle(3, 100_000_000_000))
        var pages = 0
        val read: suspend (Long, Int) -> List<RecordedTelemetryRow> = { after, limit ->
            assertEquals(256, limit); pages++; rows.filter { it.id > after }.take(1)
        }
        val first = SessionReplay.events(read, speed = 2.0).toList()
        assertEquals(2_500L, currentTime)
        assertEquals(4, pages)
        assertEquals(first, SessionReplay.events(read, speed = 2.0).toList())
    }

    @Test fun `metadata is skipped and malformed or oversized telemetry is counted without crashing`() = runTest {
        val rows = listOf(RecordedTelemetryRow(1, 0, "settings-snapshot", "private notes do not enter replay"),
            RecordedTelemetryRow(2, 0, "vehicle", "broken json"),
            RecordedTelemetryRow(3, 0, "gps", "x".repeat(SessionReplay.MAX_PAYLOAD_CHARS + 1)), vehicle(4, 0),
            RecordedTelemetryRow(5, 0, "alert-cleared", Json.encodeToString("High coolant temperature")))
        val events = SessionReplay.events({ after, _ -> rows.filter { it.id > after } }).toList()
        assertEquals(4, events.size)
        assertEquals(2, events.filterIsInstance<ReplayEvent.Malformed>().size)
        assertEquals(ReplayAlertState.CLEARED, (events.last() as ReplayEvent.Alert).state)
    }

    @Test fun `unconfirmed and reconfirmed alerts retain their distinct recorded transition`() = runTest {
        val kinds = listOf("alert", "alert-unconfirmed", "alert-confirmed", "alert-cleared")
        val rows = kinds.mapIndexed { index, kind -> RecordedTelemetryRow(index + 1L, 0, kind,
            Json.encodeToString(if (kind == "alert-unconfirmed") "High coolant temperature · sensor unavailable" else "High coolant temperature")) }
        val events = SessionReplay.events({ after, _ -> rows.filter { it.id > after } }).toList().filterIsInstance<ReplayEvent.Alert>()
        assertEquals(listOf(ReplayAlertState.ACTIVE, ReplayAlertState.UNCONFIRMED, ReplayAlertState.CONFIRMED, ReplayAlertState.CLEARED), events.map { it.state })
        assertTrue(events[1].message.contains("unavailable"))
    }

    @Test fun `cancellation during replay gap stops paging and emission`() = runTest {
        val rows = listOf(vehicle(1, 0), vehicle(2, 100_000_000_000))
        val events = mutableListOf<ReplayEvent>()
        val job = launch { SessionReplay.events({ after, _ -> rows.filter { it.id > after } }).toList(events) }
        runCurrent()
        assertEquals(1, events.size)
        job.cancel(); advanceUntilIdle()
        assertEquals(1, events.size)
    }

    @Test fun `non advancing pages are rejected instead of looping forever`() = runTest {
        var failure: Exception? = null
        try { SessionReplay.events({ _, _ -> listOf(vehicle(1, 0)) }).toList() }
        catch (error: IllegalArgumentException) { failure = error }
        assertNotNull(failure)
    }
}
