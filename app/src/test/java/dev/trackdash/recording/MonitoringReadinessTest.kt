package dev.trackdash.recording

import dev.trackdash.core.*
import dev.trackdash.data.DashboardPreferences
import dev.trackdash.settings.WarningLimitsDraft
import org.junit.Assert.*
import org.junit.Test

class MonitoringReadinessTest {
    @Test fun invalidLimitsCannotSaveOrSilentlyDisableMonitoring() {
        val original = DashboardPreferences(coolantLimit = 110.0)
        val draft = WarningLimitsDraft.from(original).copy(coolant = "9999")
        assertFalse(draft.valid)
        assertThrows(IllegalArgumentException::class.java) { draft.applyTo(original) }
        assertEquals(110.0, original.coolantLimit)
        assertNull(draft.copy(coolant = " ").applyTo(original).coolantLimit)
        assertFalse(draft.copy(coolant = "NaN").valid)
        assertFalse(draft.copy(coolant = "100", rpm = "Infinity").valid)
    }

    @Test fun readinessDistinguishesUnscannedUnsupportedUnavailableAndConfigured() {
        val prefs = DashboardPreferences(coolantLimit = 110.0, rpmLimit = 7000.0)
        fun coverage(supported: Set<SensorId>?, available: Set<SensorId>?) =
            monitoringCoverage(prefs, supported, available, false).associate { it.name to it.status }
        assertTrue(coverage(null, null).getValue("Coolant temperature").contains("Not checked"))
        assertTrue(coverage(emptySet(), emptySet()).getValue("Coolant temperature").contains("unsupported"))
        assertTrue(coverage(setOf(SensorId.COOLANT_TEMPERATURE), emptySet()).getValue("Coolant temperature").contains("no current"))
        assertEquals("Available with configured limit", coverage(setOf(SensorId.COOLANT_TEMPERATURE), setOf(SensorId.COOLANT_TEMPERATURE)).getValue("Coolant temperature"))
        assertTrue(coverage(SensorId.entries.toSet(), SensorId.entries.toSet()).getValue("Oil pressure").startsWith("Not monitored"))
    }

    @Test fun stalePoorOrFailedGpsNeverShowsNumericSpeed() {
        val fix = GpsFix(0.0, 0.0, null, 30.0, null, 5.0, "gps", 0, 0, false)
        val state = RecordingState(gps = fix, gpsAgeSeconds = 1.0)
        assertEquals(30.0, gpsSpeedForDisplay(state))
        assertNull(gpsSpeedForDisplay(state.copy(gpsAgeSeconds = 3.001)))
        assertNull(gpsSpeedForDisplay(state.copy(gpsAgeSeconds = -0.1)))
        assertNull(gpsSpeedForDisplay(state.copy(gpsError = "Lost")))
        assertNull(gpsSpeedForDisplay(state.copy(gps = fix.copy(horizontalAccuracyMeters = 30.0))))
    }
}
