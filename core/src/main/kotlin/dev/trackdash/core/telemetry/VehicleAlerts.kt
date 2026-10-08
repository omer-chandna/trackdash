package dev.trackdash.core.telemetry

import dev.trackdash.core.*

enum class AlertSeverity { WARNING, CRITICAL }
data class VehicleAlert(val id: String, val message: String, val severity: AlertSeverity)

/** No real-vehicle default: limits must be supplied by the user or verified profile. */
data class TemperatureRule(
    val sensor: SensorId,
    val thresholdCelsius: Double,
    val hysteresisCelsius: Double = 3.0,
    val minimumDurationNanos: Long = 2_000_000_000,
) {
    init {
        require(sensor.unit == SensorUnit.CELSIUS)
        require(thresholdCelsius.isFinite())
        require(hysteresisCelsius.isFinite() && hysteresisCelsius >= 0)
        require(minimumDurationNanos >= 0)
    }
}

/** Call on one dispatcher. Invalid/missing data resets pending and active thresholds. */
class VehicleAlertEngine(private val rules: List<TemperatureRule>) {
    private val pendingSince = mutableMapOf<SensorId, Long>()
    private val active = mutableSetOf<SensorId>()
    private var lastTimestamp: Long? = null

    init { require(rules.map { it.sensor }.distinct().size == rules.size) }

    fun evaluate(sample: VehicleSample): List<VehicleAlert> {
        val now = sample.elapsedRealtimeNanos
        if (lastTimestamp?.let { now < it } == true) reset()
        lastTimestamp = now
        if (!sample.connected) {
            reset()
            return listOf(VehicleAlert("connection", "Vehicle data unavailable", AlertSeverity.WARNING))
        }
        val alerts = mutableListOf<VehicleAlert>()
        if (sample.oilPressure == OilPressureStatus.LOW) {
            alerts += VehicleAlert("oil-pressure", "Low oil-pressure switch", AlertSeverity.CRITICAL)
        }
        rules.forEach { rule ->
            val reading = sample.readings.firstOrNull { it.sensorId == rule.sensor }
            val value = reading?.value?.takeIf { reading.status == SensorStatus.AVAILABLE }
            if (value == null) {
                pendingSince.remove(rule.sensor)
                active.remove(rule.sensor)
            } else if (rule.sensor in active) {
                if (value <= rule.thresholdCelsius - rule.hysteresisCelsius) {
                    active.remove(rule.sensor)
                    pendingSince.remove(rule.sensor)
                }
            } else if (value >= rule.thresholdCelsius) {
                val since = pendingSince.getOrPut(rule.sensor) { now }
                if (now - since >= rule.minimumDurationNanos) active += rule.sensor
            } else {
                pendingSince.remove(rule.sensor)
            }
            if (rule.sensor in active) {
                val label = when (rule.sensor) {
                    SensorId.COOLANT_TEMPERATURE -> "Coolant"
                    SensorId.OIL_TEMPERATURE -> "Oil"
                    SensorId.INTAKE_TEMPERATURE -> "Intake"
                    else -> rule.sensor.name
                }
                alerts += VehicleAlert(rule.sensor.name, "$label temperature above configured limit", AlertSeverity.WARNING)
            }
        }
        return alerts
    }

    fun reset() {
        pendingSince.clear()
        active.clear()
        lastTimestamp = null
    }
}
