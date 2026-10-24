package dev.trackdash.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardPresentationTest {
    @Test fun `rpm lights fill against the configured redline`() {
        assertEquals(0, rpmLightCount(null, 7000.0))
        assertEquals(0, rpmLightCount(3500.0, null))
        assertEquals(0, rpmLightCount(0.0, 7000.0))
        assertEquals(9, rpmLightCount(5250.0, 7000.0))
        assertEquals(10, rpmLightCount(6300.0, 7000.0))
        assertEquals(12, rpmLightCount(7000.0, 7000.0))
        assertEquals(12, rpmLightCount(9000.0, 7000.0))
    }

    @Test fun `rpm light colors follow green yellow and red zones`() {
        assertEquals("green", rpmLightZone(0))
        assertEquals("green", rpmLightZone(8))
        assertEquals("yellow", rpmLightZone(9))
        assertEquals("red", rpmLightZone(10))
        assertEquals("red", rpmLightZone(11))
    }

    @Test fun `shift state activates at limit and clears below hysteresis`() {
        assertEquals(false, nextShiftState(false, 6999.0, 7000.0))
        assertEquals(true, nextShiftState(false, 7000.0, 7000.0))
        assertEquals(true, nextShiftState(true, 6850.0, 7000.0))
        assertEquals(false, nextShiftState(true, 6799.0, 7000.0))
        assertEquals(false, nextShiftState(true, 7200.0, null))
    }
}
