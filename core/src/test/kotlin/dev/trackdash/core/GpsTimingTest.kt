package dev.trackdash.core

import dev.trackdash.core.gps.*
import dev.trackdash.core.simulation.DemoCircuit
import org.junit.Assert.*
import org.junit.Test

class GpsTimingTest {
    private val gate = TimingGate("sf", "Finish", GeoPoint(0.0, -0.001), GeoPoint(0.0, 0.001), CrossingDirection.RIGHT_TO_LEFT)
    @Test fun `finite crossing interpolates and checks direction`() {
        assertEquals(0.5, Geometry.crossingFraction(GeoPoint(-0.001, 0.0), GeoPoint(0.001, 0.0), gate)!!, 1e-9)
        assertNull(Geometry.crossingFraction(GeoPoint(0.001, 0.0), GeoPoint(-0.001, 0.0), gate))
        assertNull(Geometry.crossingFraction(GeoPoint(-0.001, 0.002), GeoPoint(0.001, 0.002), gate))
        assertNull(Geometry.crossingFraction(GeoPoint(0.001, 0.0), GeoPoint(0.001, 0.001), gate))
        assertNull(Geometry.crossingFraction(GeoPoint(0.001, 0.0), GeoPoint(0.001, 0.0), gate))
    }
    @Test fun `endpoint arrival counted once and leaving gate is not another crossing`() {
        assertEquals(1.0, Geometry.crossingFraction(GeoPoint(-0.001, 0.0), GeoPoint(0.0, 0.0), gate)!!, 1e-9)
        assertNull(Geometry.crossingFraction(GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.0), gate))
    }
    @Test fun `invalid coordinates and degenerate gates rejected`() {
        assertThrows(IllegalArgumentException::class.java) { GeoPoint(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { GeoPoint(0.0, 181.0) }
        assertThrows(IllegalArgumentException::class.java) { TimingGate("x", "x", GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0)) }
    }
    private fun fix(second: Int) = DemoCircuit.fixAt(second * 1_000_000_000L, second * 1_000_000_000L, second * 1000L)
    @Test fun `timestamps remain ordered across signal gaps`() {
        val timer = LapTimer(DemoCircuit.track)
        timer.accept(fix(10))
        timer.markGap()
        timer.accept(fix(9))
        assertEquals(1, timer.rejectedFixes)
        timer.accept(fix(11))
        assertEquals(1, timer.rejectedFixes)
    }
    @Test fun `first crossing starts then consecutive laps and sectors use real geometry`() {
        val timer = LapTimer(DemoCircuit.track)
        val laps = (0..180).flatMap { timer.accept(fix(it)) }
        assertEquals(2, laps.size)
        assertTrue(laps.all { it.valid && it.sectorNanos.size == 3 })
        assertEquals(60_000_000_000L, laps.first().durationNanos)
        assertEquals(laps.first().durationNanos, laps.first().sectorNanos.sum())
        assertNotNull(timer.bestLap)
        assertNotNull(timer.theoreticalBestNanos)
    }
    @Test fun `first crossing alone never produces a lap`() {
        val timer = LapTimer(DemoCircuit.track)
        (0..10).forEach { assertTrue(timer.accept(fix(it)).isEmpty()) }
        assertNotNull(timer.startedAtNanos)
        assertEquals(0, timer.completedLaps)
    }
    @Test fun `dropout invalidates lap and excludes it from best`() {
        val timer = LapTimer(DemoCircuit.track)
        (0..20).forEach { timer.accept(fix(it)) }
        val laps = (30..65).flatMap { timer.accept(fix(it)) }
        assertEquals(1, laps.size)
        assertFalse(laps.single().valid)
        assertNull(timer.bestLap)
        assertNull(timer.theoreticalBestNanos)
    }
    @Test fun `poor accuracy jump and unordered timestamps are rejected`() {
        val timer = LapTimer(DemoCircuit.track)
        timer.accept(fix(0)); timer.accept(fix(1))
        timer.accept(fix(1)); timer.accept(fix(2).copy(horizontalAccuracyMeters = 100.0))
        timer.accept(fix(3)); timer.accept(fix(4).copy(latitude = 44.0))
        assertEquals(3, timer.rejectedFixes)
        assertEquals(0, timer.completedLaps)
    }
    @Test fun `missing sectors and minimum lap duration cannot produce valid best`() {
        val backwards = DemoCircuit.track.copy(sectors = DemoCircuit.track.sectors.reversed())
        val timer = LapTimer(backwards)
        (0..65).forEach { timer.accept(fix(it)) }
        assertFalse(timer.lastLap!!.valid)
        assertNull(timer.bestLap)
        val minimum = LapTimer(DemoCircuit.track, TimingPolicy(minimumLapNanos = 90_000_000_000))
        (0..65).forEach { minimum.accept(fix(it)) }
        assertEquals(0, minimum.completedLaps)
    }
}
