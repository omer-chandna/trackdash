package dev.trackdash.simulation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.trackdash.core.SensorId
import dev.trackdash.core.simulation.*
import dev.trackdash.core.telemetry.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Demo-only defaults; these are deliberately not vehicle-profile safety limits. */
data class DemoWarningLimits(val coolantCelsius: Double = 110.0, val oilCelsius: Double = 130.0) {
    init { require(coolantCelsius in -40.0..200.0 && oilCelsius in -40.0..200.0) }
    fun rules() = listOf(
        TemperatureRule(SensorId.COOLANT_TEMPERATURE, coolantCelsius),
        TemperatureRule(SensorId.OIL_TEMPERATURE, oilCelsius),
    )
}

data class SimulationUiState(
    val telemetry: VehicleTelemetryState = VehicleTelemetryState(),
    val scenario: SimulationScenario = SimulationScenario.NORMAL,
    val limits: DemoWarningLimits = DemoWarningLimits(),
    val alerts: List<VehicleAlert> = emptyList(),
    val shiftActive: Boolean = false,
)

class SimulationViewModel(private val source: SimulatedVehicleSource) : ViewModel() {
    private val repository = VehicleTelemetryRepository(source, viewModelScope)
    private val mutableState = MutableStateFlow(SimulationUiState())
    val state = mutableState.asStateFlow()
    private var alerts = VehicleAlertEngine(mutableState.value.limits.rules())
    private var shiftActive = false

    init {
        viewModelScope.launch {
            repository.state.collect { telemetry ->
                val warnings = telemetry.sample?.let(alerts::evaluate) ?: emptyList<VehicleAlert>().also { alerts.reset() }
                val rpm = telemetry.sample?.readings?.firstOrNull {
                    it.sensorId == SensorId.ENGINE_RPM && it.status == dev.trackdash.core.SensorStatus.AVAILABLE
                }?.value
                shiftActive = when {
                    rpm == null || rpm < 6800.0 -> false
                    rpm >= 7000.0 -> true
                    else -> shiftActive
                }
                mutableState.value = mutableState.value.copy(telemetry = telemetry, alerts = warnings, shiftActive = shiftActive)
            }
        }
    }

    fun start() {
        if (repository.state.value.status == AcquisitionStatus.RUNNING) return
        source.selectScenario(SimulationScenario.NORMAL)
        mutableState.value = mutableState.value.copy(scenario = SimulationScenario.NORMAL)
        alerts.reset()
        shiftActive = false
        repository.start()
    }

    fun stop() {
        repository.stop()
        source.selectScenario(SimulationScenario.NORMAL)
        alerts.reset()
        shiftActive = false
        mutableState.value = mutableState.value.copy(
            telemetry = repository.state.value, scenario = SimulationScenario.NORMAL, alerts = emptyList(), shiftActive = false,
        )
    }

    fun selectScenario(scenario: SimulationScenario) {
        if (repository.state.value.status != AcquisitionStatus.RUNNING) return
        source.selectScenario(scenario)
        mutableState.value = mutableState.value.copy(scenario = scenario)
    }

    fun setWarningLimits(limits: DemoWarningLimits) {
        alerts = VehicleAlertEngine(limits.rules())
        mutableState.value = mutableState.value.copy(limits = limits, alerts = emptyList())
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SimulationViewModel(SimulatedVehicleSource(SystemClock::elapsedRealtimeNanos)) }
        }
    }
}
