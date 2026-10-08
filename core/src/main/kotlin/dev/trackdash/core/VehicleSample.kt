package dev.trackdash.core

/** A delivery batch, not a claim that physical sensors were acquired simultaneously. */
data class VehicleSample(
    val readings: List<SensorReading>,
    val oilPressure: OilPressureStatus,
    val gear: Int?,
    val connected: Boolean,
    val source: SensorSource,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(elapsedRealtimeNanos >= 0)
        require(gear == null || gear in 1..8)
        require(readings.map { it.sensorId }.distinct().size == readings.size)
        require(readings.all { it.source == source })
        require(connected || (gear == null && oilPressure == OilPressureStatus.UNAVAILABLE))
        require(connected || readings.none { it.status == SensorStatus.AVAILABLE })
    }
}
