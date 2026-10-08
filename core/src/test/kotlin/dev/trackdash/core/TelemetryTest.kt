package dev.trackdash.core

import org.junit.Assert.*
import org.junit.Test

class TelemetryTest {
    @Test fun `GPS-only mode does not require vehicle telemetry`() {
        assertFalse(OperatingMode.GPS_LAP_TIMER.usesVehicle)
        assertTrue(OperatingMode.GPS_LAP_TIMER.usesGps)
        assertFalse(OperatingMode.VEHICLE_DASHBOARD.usesGps)
        assertTrue(OperatingMode.COMBINED_TELEMETRY.usesVehicle)
        assertTrue(OperatingMode.COMBINED_TELEMETRY.usesGps)
    }

    @Test fun `missing sensor remains explicitly unavailable`() {
        val reading = reading(null, SensorStatus.UNAVAILABLE)
        assertNull(reading.value)
        assertEquals(SensorUnit.CELSIUS, reading.sensorId.unit)
    }

    @Test fun `available reading requires a finite value`() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { reading(value, SensorStatus.AVAILABLE) }
        }
        assertEquals(92.0, reading(92.0, SensorStatus.AVAILABLE).value!!, 0.0)
    }

    @Test fun `unsupported reading cannot masquerade as a measurement`() {
        assertThrows(IllegalArgumentException::class.java) { reading(0.0, SensorStatus.UNSUPPORTED) }
    }

    @Test fun `stale reading retains acquisition time and last value`() {
        val stale = reading(92.0, SensorStatus.STALE)
        assertEquals(123L, stale.elapsedRealtimeNanos)
        assertEquals(92.0, stale.value!!, 0.0)
    }

    @Test fun `fix rejects invalid coordinates accuracy and monotonic time`() {
        val fix = fix()
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0, -91.0)) {
            assertThrows(IllegalArgumentException::class.java) { fix.copy(latitude = invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { fix.copy(longitude = 181.0) }
        assertThrows(IllegalArgumentException::class.java) { fix.copy(horizontalAccuracyMeters = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { fix.copy(elapsedRealtimeNanos = -1) }
        assertThrows(IllegalArgumentException::class.java) { fix.copy(speedMetersPerSecond = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { fix.copy(bearingDegrees = 360.0) }
    }

    @Test fun `wall clock changes do not change acquisition time`() {
        val fix = fix()
        assertEquals(fix.elapsedRealtimeNanos, fix.copy(wallClockMillis = 0).elapsedRealtimeNanos)
        assertNull(fix.altitudeMeters)
    }

    private fun reading(value: Double?, status: SensorStatus) = SensorReading(
        SensorId.COOLANT_TEMPERATURE, value, 123, SensorSource.OBD, status,
    )

    private fun fix() = GpsFix(43.0, -79.0, null, null, null, 5.0, "gps", 1000, 123, false)
}
