package dev.trackdash.core.telemetry

import dev.trackdash.core.*

enum class AlertSeverity { WARNING, CRITICAL }
enum class AlertCategory { ENGINE_SAFETY, CONNECTIVITY, DATA_QUALITY, SHIFT }
enum class AlertCondition { ACTIVE, UNCONFIRMED }
data class VehicleAlert(
    val id: String,
    val message: String,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val sensor: SensorId? = null,
    val condition: AlertCondition = AlertCondition.ACTIVE,
)

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

/** Call on one dispatcher. Only a fresh safe reading clears an established engine warning. */
class VehicleAlertEngine(private val rules: List<TemperatureRule>) {
    private val pendingSince = mutableMapOf<SensorId, Long>()
    private val active = mutableSetOf<SensorId>()
    private var lastTimestamp: Long? = null
    private var pressureActive = false

    init { require(rules.map { it.sensor }.distinct().size == rules.size) }

    fun evaluate(sample: VehicleSample): List<VehicleAlert> {
        val now = sample.elapsedRealtimeNanos
        if (lastTimestamp?.let { now < it } == true) reset()
        lastTimestamp = now
        if (!sample.connected) {
            pendingSince.clear()
            return listOf(VehicleAlert("connection", "Vehicle data unavailable", AlertSeverity.WARNING, AlertCategory.CONNECTIVITY)) +
                active.map { temperatureAlert(it, unconfirmed = true) } +
                if (pressureActive) listOf(pressureAlert(true)) else emptyList()
        }
        val alerts = mutableListOf<VehicleAlert>()
        if (sample.oilPressure == OilPressureStatus.LOW) pressureActive = true
        if (sample.oilPressure == OilPressureStatus.OK) pressureActive = false
        if (pressureActive) alerts += pressureAlert(sample.oilPressure != OilPressureStatus.LOW)
        rules.forEach { rule ->
            val reading = sample.readings.firstOrNull { it.sensorId == rule.sensor }
            val value = reading?.value?.takeIf { reading.status == SensorStatus.AVAILABLE }
            if (value == null) {
                pendingSince.remove(rule.sensor)
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
                alerts += temperatureAlert(rule.sensor, value == null)
            }
        }
        return alerts
    }

    private fun temperatureAlert(sensor: SensorId, unconfirmed: Boolean): VehicleAlert {
        val label = when (sensor) {
            SensorId.COOLANT_TEMPERATURE -> "Coolant"
            SensorId.OIL_TEMPERATURE -> "Oil"
            SensorId.INTAKE_TEMPERATURE -> "Intake"
            else -> sensor.name
        }
        return VehicleAlert(sensor.name, if (unconfirmed) "$label warning unresolved — sensor unavailable" else "$label temperature above configured limit",
            AlertSeverity.WARNING, AlertCategory.ENGINE_SAFETY, sensor,
            if (unconfirmed) AlertCondition.UNCONFIRMED else AlertCondition.ACTIVE)
    }

    private fun pressureAlert(unconfirmed: Boolean) = VehicleAlert("oil-pressure",
        if (unconfirmed) "Low oil pressure unresolved — sensor unavailable" else "Low oil pressure",
        AlertSeverity.CRITICAL, AlertCategory.ENGINE_SAFETY,
        condition = if (unconfirmed) AlertCondition.UNCONFIRMED else AlertCondition.ACTIVE)

    fun reset() {
        pendingSince.clear()
        active.clear()
        lastTimestamp = null
        pressureActive = false
    }
}
