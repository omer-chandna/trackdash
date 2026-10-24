package dev.trackdash.core

import dev.trackdash.core.sharing.*
import dev.trackdash.core.simulation.SimulatedVehicleSource
import dev.trackdash.core.simulation.SimulationScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetrySharingTest {
    private fun sample(seconds: Long) = SimulatedVehicleSource.sampleAt(0, seconds * 1_000_000_000, SimulationScenario.NORMAL)

    @Test fun `sharing is versioned normalized relative and excludes identifying data`() = runTest {
        val received = mutableListOf<TelemetryEnvelope>()
        val dispatcher = TelemetryShareDispatcher(backgroundScope, TelemetrySender { received += it })
        assertTrue(dispatcher.tryOffer(sample(4)))
        runCurrent()
        assertEquals(0, received.single().elapsedMillis)
        assertEquals(SensorSource.SIMULATION, received.single().source)
        assertTrue(received.single().sensors.all { it.unit == it.sensor.unit })
        val payload = Json.encodeToString(received.single())
        assertTrue(payload.contains("\"version\":1"))
        listOf("latitude", "longitude", "vin", "notes", "session", "authorization", "gear", "wallClock", "vehicleId").forEach {
            assertFalse("Payload must exclude $it", payload.contains(it, ignoreCase = true))
        }
        assertEquals(1, dispatcher.stats.value.sent)
        dispatcher.close()
    }

    @Test fun `stale values are omitted and default cadence is one hertz`() = runTest {
        val received = mutableListOf<TelemetryEnvelope>()
        val dispatcher = TelemetryShareDispatcher(backgroundScope, TelemetrySender { received += it })
        val stale = sample(1).copy(readings = listOf(SensorReading(SensorId.SPEED, 44.0, 0,
            SensorSource.SIMULATION, SensorStatus.STALE)))
        assertTrue(dispatcher.tryOffer(stale))
        assertFalse(dispatcher.tryOffer(stale.copy(elapsedRealtimeNanos = 1_999_000_000)))
        assertTrue(dispatcher.tryOffer(sample(2)))
        runCurrent()
        assertNull(received.first().sensors.single().value)
        assertEquals(1000, received.first().sensors.single().ageMillis)
        assertEquals(1000, received.last().elapsedMillis)
        dispatcher.close()
    }

    @Test fun `full queue drops immediately and never backpressures acquisition`() = runTest {
        val dispatcher = TelemetryShareDispatcher(backgroundScope, TelemetrySender { awaitCancellation() }, capacity = 1)
        assertTrue(dispatcher.tryOffer(sample(0)))
        runCurrent() // First item is being sent; the one-slot queue is now free.
        assertTrue(dispatcher.tryOffer(sample(1)))
        assertFalse(dispatcher.tryOffer(sample(2)))
        assertEquals(1, dispatcher.stats.value.dropped)
        assertEquals(0L, currentTime)
        dispatcher.close()
    }

    @Test fun `sender errors and timeouts are isolated and following samples still send`() = runTest {
        var calls = 0
        val dispatcher = TelemetryShareDispatcher(backgroundScope, TelemetrySender {
            when (++calls) { 1 -> error("Do not expose this receiver secret"); 2 -> awaitCancellation() }
        }, timeoutMillis = 500)
        dispatcher.tryOffer(sample(0)); runCurrent()
        dispatcher.tryOffer(sample(1)); runCurrent(); advanceTimeBy(500); runCurrent()
        dispatcher.tryOffer(sample(2)); runCurrent()
        assertEquals(2, dispatcher.stats.value.errors)
        assertEquals(1, dispatcher.stats.value.sent)
        dispatcher.close()
    }

    @Test fun `close cancels in flight work without retrying or accepting more work`() = runTest {
        var cancelled = false
        val dispatcher = TelemetryShareDispatcher(backgroundScope, TelemetrySender {
            try { awaitCancellation() } finally { cancelled = true }
        })
        dispatcher.tryOffer(sample(0)); runCurrent(); dispatcher.close(); runCurrent()
        assertTrue(cancelled)
        assertFalse(dispatcher.tryOffer(sample(1)))
        assertEquals(0, dispatcher.stats.value.errors)
    }

    @Test fun `endpoint excludes insecure transport embedded credentials and URL tokens`() {
        validateSharingEndpoint("https://monitor.example/api/telemetry")
        listOf("http://monitor.example", "https://user:secret@monitor.example", "https://monitor.example?token=secret",
            "https://monitor.example#token", "https:///missing-host", "file:///tmp/test", "https://monitor.example:70000").forEach {
            assertThrows(IllegalArgumentException::class.java) { validateSharingEndpoint(it) }
        }
    }

    @Test fun `authorization rejects controls and oversized values before touching HTTP`() {
        validateSharingAuthorization("")
        validateSharingAuthorization("Bearer owner-managed-token")
        listOf("Bearer token\r\nInjected: true", "Bearer\tsecret", "Bearer\u0000secret", "x".repeat(4097)).forEach {
            assertThrows(IllegalArgumentException::class.java) { validateSharingAuthorization(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { validateSharingEndpoint("https://monitor.example/" + "x".repeat(2048)) }
    }
}
