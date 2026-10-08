package dev.trackdash.simulation

import androidx.lifecycle.ViewModelStore
import dev.trackdash.core.OilPressureStatus
import dev.trackdash.core.simulation.*
import dev.trackdash.core.telemetry.AcquisitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model() = SimulationViewModel(SimulatedVehicleSource { dispatcher.scheduler.currentTime * 1_000_000 })
        .also { store.put("simulation", it) }

    private fun testModel(block: suspend TestScope.(SimulationViewModel) -> Unit) = runTest(dispatcher) {
        try { block(model()) } finally {
            // End the long-lived ViewModel scope before runTest drains its scheduler.
            store.clear()
            runCurrent()
        }
    }

    @Test fun `explicit start and stop own data lifecycle`() = testModel { model ->
        runCurrent()
        assertEquals(AcquisitionStatus.STOPPED, model.state.value.telemetry.status)
        model.start()
        runCurrent()
        assertEquals(AcquisitionStatus.RUNNING, model.state.value.telemetry.status)
        assertNotNull(model.state.value.telemetry.sample)
        model.stop()
        runCurrent()
        assertNull(model.state.value.telemetry.sample)
        val count = model.state.value.telemetry.receivedSamples
        advanceTimeBy(1000)
        assertEquals(count, model.state.value.telemetry.receivedSamples)
    }

    @Test fun `low pressure and disconnect warnings recover without healthy stale data`() = testModel { model ->
        model.start()
        model.selectScenario(SimulationScenario.LOW_OIL_PRESSURE)
        runCurrent()
        assertEquals("oil-pressure", model.state.value.alerts.single().id)
        model.selectScenario(SimulationScenario.DISCONNECTED)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(OilPressureStatus.UNAVAILABLE, model.state.value.telemetry.sample!!.oilPressure)
        assertEquals("connection", model.state.value.alerts.single().id)
        model.selectScenario(SimulationScenario.NORMAL)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(model.state.value.alerts.isEmpty())
    }

    @Test fun `editable demo limits apply after configured hold time`() = testModel { model ->
        model.start()
        model.selectScenario(SimulationScenario.OVERHEATING)
        runCurrent()
        assertTrue(model.state.value.alerts.isEmpty())
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(2, model.state.value.alerts.size)
        model.setWarningLimits(DemoWarningLimits(150.0, 160.0))
        advanceTimeBy(3000)
        runCurrent()
        assertTrue(model.state.value.alerts.isEmpty())
    }

    @Test fun `restart clears scenario warnings and count`() = testModel { model ->
        model.start()
        model.selectScenario(SimulationScenario.LOW_OIL_PRESSURE)
        advanceTimeBy(500)
        runCurrent()
        model.stop()
        model.start()
        runCurrent()
        assertEquals(SimulationScenario.NORMAL, model.state.value.scenario)
        assertTrue(model.state.value.alerts.isEmpty())
        assertEquals(1, model.state.value.telemetry.receivedSamples)
    }

    @Test fun `cleared ViewModel stops the underlying collector`() = testModel { model ->
        model.start()
        runCurrent()
        val count = model.state.value.telemetry.receivedSamples
        store.clear()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(count, model.state.value.telemetry.receivedSamples)
    }
}
