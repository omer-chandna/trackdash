package dev.trackdash.core

import dev.trackdash.core.simulation.SimulatedVehicleSource
import dev.trackdash.core.simulation.SimulationScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedVehicleTest {
    @Test fun `trajectory is deterministic and explicitly simulated`() {
        val a = SimulatedVehicleSource.sampleAt(3_000_000_000, 9_000_000_000, SimulationScenario.NORMAL)
        assertEquals(a, SimulatedVehicleSource.sampleAt(3_000_000_000, 9_000_000_000, SimulationScenario.NORMAL))
        assertEquals(SensorId.entries.toSet(), a.readings.map { it.sensorId }.toSet())
        assertTrue(a.readings.all { it.source == SensorSource.SIMULATION && it.elapsedRealtimeNanos == 9_000_000_000 })
        assertEquals(OilPressureStatus.OK, a.oilPressure)
        assertTrue(a.connected)
    }

    @Test fun `gear shifts and speed vary without leaving plausible demo bounds`() {
        val frames = (0..240).map {
            SimulatedVehicleSource.sampleAt(it * 100_000_000L, it * 100_000_000L, SimulationScenario.NORMAL)
        }
        assertTrue(frames.map { it.gear }.distinct().size >= 4)
        assertTrue(frames.map { frame -> frame.readings.first { it.sensorId == SensorId.SPEED }.value }.distinct().size > 100)
        assertTrue(frames.flatMap { it.readings }.all { it.status == SensorStatus.AVAILABLE && it.value!!.isFinite() })
        assertTrue(frames.all { frame -> frame.readings.first { it.sensorId == SensorId.ENGINE_RPM }.value!! in 0.0..7000.0 })
    }

    @Test fun `fault scenarios are explicit and disconnect clears all measurements`() {
        fun frame(scenario: SimulationScenario) = SimulatedVehicleSource.sampleAt(0, 0, scenario)
        val hot = frame(SimulationScenario.OVERHEATING)
        assertTrue(hot.readings.first { it.sensorId == SensorId.COOLANT_TEMPERATURE }.value!! > 120)
        assertEquals(OilPressureStatus.LOW, frame(SimulationScenario.LOW_OIL_PRESSURE).oilPressure)
        val lost = frame(SimulationScenario.DISCONNECTED)
        assertFalse(lost.connected)
        assertNull(lost.gear)
        assertEquals(OilPressureStatus.UNAVAILABLE, lost.oilPressure)
        assertTrue(lost.readings.all { it.value == null && it.status == SensorStatus.UNAVAILABLE })
    }

    @Test fun `source emits at ten hertz and stops when collector cancels`() = runTest {
        val source = SimulatedVehicleSource { testScheduler.currentTime * 1_000_000 }
        val frames = mutableListOf<VehicleSample>()
        val job = launch { source.samples().toList(frames) }
        runCurrent()
        assertEquals(1, frames.size)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(11, frames.size)
        source.selectScenario(SimulationScenario.DISCONNECTED)
        advanceTimeBy(100)
        runCurrent()
        assertFalse(frames.last().connected)
        job.cancel()
        runCurrent()
        val count = frames.size
        advanceTimeBy(1000)
        assertEquals(count, frames.size)
    }

    @Test fun `new collection replays trajectory but uses fresh acquisition time`() = runTest {
        val source = SimulatedVehicleSource { testScheduler.currentTime * 1_000_000 }
        val first = source.samples().take(2).toList()
        advanceTimeBy(1000)
        val second = source.samples().take(2).toList()
        assertEquals(first.map { it.readings.map { r -> r.value } }, second.map { it.readings.map { r -> r.value } })
        assertTrue(second.first().elapsedRealtimeNanos > first.last().elapsedRealtimeNanos)
    }
}
