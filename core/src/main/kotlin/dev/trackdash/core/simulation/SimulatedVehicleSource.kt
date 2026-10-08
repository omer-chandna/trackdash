package dev.trackdash.core.simulation

import dev.trackdash.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

enum class SimulationScenario { NORMAL, OVERHEATING, LOW_OIL_PRESSURE, DISCONNECTED }

/** Synthetic demo values, not an N52 model, vehicle limits, or diagnostic evidence. */
class SimulatedVehicleSource(private val elapsedRealtimeNanos: () -> Long) : VehicleSource {
    private val scenario = MutableStateFlow(SimulationScenario.NORMAL)

    fun selectScenario(value: SimulationScenario) { scenario.value = value }

    override fun samples(): Flow<VehicleSample> = flow {
        val startedAt = elapsedRealtimeNanos()
        while (currentCoroutineContext().isActive) {
            val now = elapsedRealtimeNanos()
            emit(sampleAt((now - startedAt).coerceAtLeast(0), now, scenario.value))
            delay(SAMPLE_PERIOD_MILLIS)
        }
    }

    companion object {
        const val SAMPLE_PERIOD_MILLIS = 100L

        /** Pure replay kernel; trajectory time and actual acquisition time are separate. */
        fun sampleAt(elapsedNanos: Long, acquiredNanos: Long, scenario: SimulationScenario): VehicleSample {
            require(elapsedNanos >= 0 && acquiredNanos >= 0)
            val cycleSeconds = (elapsedNanos / 1_000_000_000.0) % 24.0
            val throttle = 55.0 + 35.0 * sin(cycleSeconds * 2.0 * PI / 24.0)
            val gear = 2 + (cycleSeconds / 6.0).toInt()
            val rpm = 2400.0 + (cycleSeconds % 6.0) / 6.0 * 4000.0
            val hot = scenario == SimulationScenario.OVERHEATING
            val connected = scenario != SimulationScenario.DISCONNECTED
            val values = mapOf(
                SensorId.ENGINE_RPM to rpm,
                SensorId.COOLANT_TEMPERATURE to if (hot) 126.0 else 91.0 + 2.0 * sin(cycleSeconds / 4.0),
                SensorId.OIL_TEMPERATURE to if (hot) 146.0 else 108.0 + 3.0 * sin(cycleSeconds / 6.0),
                SensorId.INTAKE_TEMPERATURE to 32.0 + throttle / 20.0,
                SensorId.SPEED to 12.0 + 32.0 * (0.5 + 0.5 * sin(cycleSeconds * 2.0 * PI / 24.0)),
                SensorId.THROTTLE to throttle,
                SensorId.ENGINE_LOAD to throttle * 0.9,
                SensorId.VOLTAGE to 13.8 + 0.2 * sin(cycleSeconds),
                SensorId.IGNITION_TIMING to 15.0 + throttle / 10.0,
                SensorId.MASS_AIR_FLOW to 10.0 + throttle,
                SensorId.AMBIENT_TEMPERATURE to 24.0,
            )
            return VehicleSample(
                readings = values.map { (sensor, value) ->
                    SensorReading(sensor, if (connected) value else null, acquiredNanos, SensorSource.SIMULATION,
                        if (connected) SensorStatus.AVAILABLE else SensorStatus.UNAVAILABLE)
                },
                oilPressure = when {
                    !connected -> OilPressureStatus.UNAVAILABLE
                    scenario == SimulationScenario.LOW_OIL_PRESSURE -> OilPressureStatus.LOW
                    else -> OilPressureStatus.OK
                },
                gear = if (connected) gear else null,
                connected = connected,
                source = SensorSource.SIMULATION,
                elapsedRealtimeNanos = acquiredNanos,
            )
        }
    }
}
