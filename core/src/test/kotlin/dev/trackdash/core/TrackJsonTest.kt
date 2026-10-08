package dev.trackdash.core

import dev.trackdash.core.gps.TrackJson
import dev.trackdash.core.simulation.DemoCircuit
import org.junit.Assert.*
import org.junit.Test

class TrackJsonTest {
    @Test fun `track export round trips endpoints direction and sectors`() {
        assertEquals(DemoCircuit.track, TrackJson.decode(TrackJson.encode(DemoCircuit.track)))
    }
    @Test fun `unknown schema invalid geometry and oversized input rejected`() {
        val text = TrackJson.encode(DemoCircuit.track)
        assertThrows(IllegalArgumentException::class.java) { TrackJson.decode(text.replace("\"version\": 1", "\"version\": 99")) }
        assertThrows(IllegalArgumentException::class.java) { TrackJson.decode("x".repeat(128_001)) }
        assertThrows(IllegalArgumentException::class.java) { TrackJson.decode("{}") }
    }
}
