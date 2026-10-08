package dev.trackdash.core

import dev.trackdash.core.simulation.SimulatedVehicleSource
import dev.trackdash.core.telemetry.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleTelemetryRepositoryTest {
    @Test fun `start is idempotent and stop clears live values and releases source`() = runTest {
        var collectors = 0
        val source = object : VehicleSource {
            override fun samples(): Flow<VehicleSample> = flow {
                collectors++
                try {
                    emit(SimulatedVehicleSource.sampleAt(0, 0, dev.trackdash.core.simulation.SimulationScenario.NORMAL))
                    awaitCancellation()
                } finally { collectors-- }
            }
        }
        val repo = VehicleTelemetryRepository(source, backgroundScope)
        repo.start()
        repo.start()
        runCurrent()
        assertEquals(1, collectors)
        assertNotNull(repo.state.value.sample)
        repo.stop()
        runCurrent()
        assertEquals(0, collectors)
        assertNull(repo.state.value.sample)
        assertEquals(AcquisitionStatus.STOPPED, repo.state.value.status)
    }

    @Test fun `restart resets count and continues with exactly one stream`() = runTest {
        val repo = VehicleTelemetryRepository(SimulatedVehicleSource { testScheduler.currentTime * 1_000_000 }, backgroundScope)
        repo.start()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(6, repo.state.value.receivedSamples)
        repo.stop()
        repo.start()
        runCurrent()
        assertEquals(1, repo.state.value.receivedSamples)
        assertEquals(AcquisitionStatus.RUNNING, repo.state.value.status)
    }

    @Test fun `source failure is visible and stale success values are removed`() = runTest {
        val source = object : VehicleSource {
            override fun samples(): Flow<VehicleSample> = flow { throw IllegalStateException("Test source failed") }
        }
        val repo = VehicleTelemetryRepository(source, backgroundScope)
        repo.start()
        runCurrent()
        assertEquals(AcquisitionStatus.ERROR, repo.state.value.status)
        assertEquals("Test source failed", repo.state.value.error)
        assertNull(repo.state.value.sample)
    }

    @Test fun `finite source completion does not leave recording active`() = runTest {
        val source = object : VehicleSource { override fun samples() = flowOf<VehicleSample>() }
        val repo = VehicleTelemetryRepository(source, backgroundScope)
        repo.start()
        runCurrent()
        assertEquals(AcquisitionStatus.STOPPED, repo.state.value.status)
        assertNull(repo.state.value.sample)
    }
}
