package dev.trackdash.core

import dev.trackdash.core.telemetry.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringFreshnessTest {
    private fun sample() = VehicleSample(listOf(SensorReading(SensorId.ENGINE_RPM, 7000.0, 0,
        SensorSource.OBD, SensorStatus.AVAILABLE)), OilPressureStatus.UNAVAILABLE, null, true, SensorSource.OBD, 0)

    @Test fun `watchdog expires values without another acquisition`() {
        assertEquals(SensorStatus.AVAILABLE, sample().atMonitoringTime(5_000_000_000).readings.single().status)
        val stale = sample().atMonitoringTime(5_000_000_001)
        assertFalse(stale.connected)
        assertEquals(SensorStatus.STALE, stale.readings.single().status)
        assertEquals(7000.0, stale.readings.single().value)
        assertEquals(0L, stale.elapsedRealtimeNanos)
    }

    @Test fun `source retries do not cancel a healthy sibling and cancellation stops retries`() = runTest {
        var attempts = 0
        var failures = 0
        var healthy = 0
        val broken = backgroundScope.launch {
            flow<Int> { attempts++; error("GPS disabled") }.recoverAcquisition(100) { failures++ }.collect()
        }
        backgroundScope.launch { while (isActive) { healthy++; delay(10) } }
        advanceTimeBy(250)
        assertEquals(3, attempts)
        assertEquals(3, failures)
        assertTrue(healthy > 10)
        broken.cancelAndJoin()
        advanceTimeBy(500)
        assertEquals(3, attempts)
    }

    @Test fun `source can resume after a failure`() = runTest {
        var attempts = 0
        val result = flow { if (++attempts == 1) error("temporary") else emit(42) }
            .recoverAcquisition(1) {}.first()
        assertEquals(42, result)
        assertEquals(2, attempts)
    }

    @Test fun `persistence consumer failure is not retried as acquisition`() = runTest {
        var retries = 0
        try {
            flowOf(1).recoverAcquisition(1) { retries++ }.collect { error("disk full") }
            fail("Expected consumer failure")
        } catch (error: IllegalStateException) { assertEquals("disk full", error.message) }
        assertEquals(0, retries)
    }
}
