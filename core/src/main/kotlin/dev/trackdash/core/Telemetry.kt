package dev.trackdash.core

import kotlinx.coroutines.flow.Flow

enum class SensorUnit { RPM, CELSIUS, METERS_PER_SECOND, PERCENT, VOLTS }
enum class SensorId(val unit: SensorUnit) {
    ENGINE_RPM(SensorUnit.RPM),
    COOLANT_TEMPERATURE(SensorUnit.CELSIUS),
    OIL_TEMPERATURE(SensorUnit.CELSIUS),
    INTAKE_TEMPERATURE(SensorUnit.CELSIUS),
    SPEED(SensorUnit.METERS_PER_SECOND),
    THROTTLE(SensorUnit.PERCENT),
    ENGINE_LOAD(SensorUnit.PERCENT),
    VOLTAGE(SensorUnit.VOLTS),
}
enum class SensorSource { OBD, SIMULATION }
enum class SensorStatus { AVAILABLE, UNSUPPORTED, UNAVAILABLE, STALE, ERROR }
enum class OilPressureStatus { OK, LOW, UNKNOWN, UNAVAILABLE }
enum class GpsQuality { ACQUIRING, READY, DEGRADED, LOST }

/** Time is elapsed realtime, in nanoseconds; values remain in canonical units. */
data class SensorReading(
    val sensorId: SensorId,
    val value: Double?,
    val elapsedRealtimeNanos: Long,
    val source: SensorSource,
    val status: SensorStatus,
) {
    init {
        require(elapsedRealtimeNanos >= 0)
        require(value == null || value.isFinite())
        require(status != SensorStatus.AVAILABLE || value != null)
        require(status !in setOf(SensorStatus.UNAVAILABLE, SensorStatus.UNSUPPORTED) || value == null)
    }
}

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?,
    val horizontalAccuracyMeters: Double,
    val provider: String,
    val wallClockMillis: Long,
    val elapsedRealtimeNanos: Long,
    val simulated: Boolean,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(altitudeMeters == null || altitudeMeters.isFinite())
        require(speedMetersPerSecond == null || (speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0))
        require(bearingDegrees == null || (bearingDegrees.isFinite() && bearingDegrees >= 0 && bearingDegrees < 360))
        require(horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0)
        require(provider.isNotBlank())
        require(elapsedRealtimeNanos >= 0)
    }
}

/** Cold streams: collection acquires resources; cancellation must release them. */
interface VehicleSource { fun samples(): Flow<VehicleSample> }
interface LocationSource { fun fixes(): Flow<GpsFix> }
