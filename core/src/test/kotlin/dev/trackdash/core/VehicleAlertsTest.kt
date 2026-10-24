package dev.trackdash.core

import dev.trackdash.core.simulation.*
import dev.trackdash.core.telemetry.*
import org.junit.Assert.*
import org.junit.Test

class VehicleAlertsTest {
    private fun engine() = VehicleAlertEngine(listOf(TemperatureRule(SensorId.COOLANT_TEMPERATURE, 110.0)))
    private fun sample(seconds: Long, temperature: Double, status: SensorStatus = SensorStatus.AVAILABLE): VehicleSample {
        val nanos = seconds * 1_000_000_000
        return SimulatedVehicleSource.sampleAt(nanos, nanos, SimulationScenario.NORMAL).copy(
            readings = listOf(SensorReading(SensorId.COOLANT_TEMPERATURE, temperature, nanos, SensorSource.SIMULATION, status)),
        )
    }

    @Test fun `temperature must remain above threshold for configured duration`() {
        val engine = engine()
        assertTrue(engine.evaluate(sample(0, 111.0)).isEmpty())
        assertTrue(engine.evaluate(sample(1, 111.0)).isEmpty())
        val alert = engine.evaluate(sample(2, 111.0)).single()
        assertEquals(AlertCategory.ENGINE_SAFETY, alert.category)
        assertEquals(SensorId.COOLANT_TEMPERATURE, alert.sensor)
    }

    @Test fun `brief excursion resets the hold time`() {
        val engine = engine()
        engine.evaluate(sample(0, 111.0))
        engine.evaluate(sample(1, 109.0))
        assertTrue(engine.evaluate(sample(2, 111.0)).isEmpty())
        assertTrue(engine.evaluate(sample(3, 111.0)).isEmpty())
        assertEquals(1, engine.evaluate(sample(4, 111.0)).size)
    }

    @Test fun `hysteresis prevents chatter until threshold minus margin`() {
        val engine = engine()
        engine.evaluate(sample(0, 111.0))
        engine.evaluate(sample(2, 111.0))
        assertEquals(1, engine.evaluate(sample(3, 108.0)).size)
        assertTrue(engine.evaluate(sample(4, 107.0)).isEmpty())
    }

    @Test fun `stale data cannot trigger a temperature warning`() {
        val engine = engine()
        engine.evaluate(sample(0, 120.0))
        assertTrue(engine.evaluate(sample(3, 120.0, SensorStatus.STALE)).isEmpty())
        assertTrue(engine.evaluate(sample(4, 120.0)).isEmpty())
    }

    @Test fun `unknown pressure is never reported as healthy or low`() {
        val engine = VehicleAlertEngine(emptyList())
        for (pressure in listOf(OilPressureStatus.UNKNOWN, OilPressureStatus.UNAVAILABLE, OilPressureStatus.OK)) {
            assertTrue(engine.evaluate(sample(0, 90.0).copy(oilPressure = pressure)).isEmpty())
        }
        val alert = engine.evaluate(sample(0, 90.0).copy(oilPressure = OilPressureStatus.LOW)).single()
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertEquals(AlertCategory.ENGINE_SAFETY, alert.category)
    }

    @Test fun `disconnect resets thresholds and produces only connection warning`() {
        val engine = engine()
        engine.evaluate(sample(0, 120.0))
        engine.evaluate(sample(2, 120.0))
        val disconnected = engine.evaluate(SimulatedVehicleSource.sampleAt(0, 3_000_000_000, SimulationScenario.DISCONNECTED)).single()
        assertEquals("connection", disconnected.id)
        assertEquals(AlertCategory.CONNECTIVITY, disconnected.category)
        assertTrue(engine.evaluate(sample(4, 120.0)).isEmpty())
    }

    @Test fun `backward timestamps restart pending duration`() {
        val engine = engine()
        engine.evaluate(sample(10, 120.0))
        assertTrue(engine.evaluate(sample(0, 120.0)).isEmpty())
        assertTrue(engine.evaluate(sample(1, 120.0)).isEmpty())
    }

    @Test fun `rules reject non-temperature sensors and invalid configuration`() {
        assertThrows(IllegalArgumentException::class.java) { TemperatureRule(SensorId.SPEED, 110.0) }
        assertThrows(IllegalArgumentException::class.java) { TemperatureRule(SensorId.COOLANT_TEMPERATURE, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { TemperatureRule(SensorId.COOLANT_TEMPERATURE, 110.0, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { TemperatureRule(SensorId.COOLANT_TEMPERATURE, 110.0, minimumDurationNanos = -1) }
    }
}
