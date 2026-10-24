package dev.trackdash.core.telemetry

import dev.trackdash.core.*

const val VEHICLE_FRESHNESS_NANOS = 5_000_000_000L
const val GPS_FRESHNESS_NANOS = 3_000_000_000L

/** Presentation/watchdog projection, never a fabricated acquisition or replacement stored sample. */
fun VehicleSample.atMonitoringTime(nowNanos: Long): VehicleSample {
    val deliveryFresh = nowNanos - elapsedRealtimeNanos in 0..VEHICLE_FRESHNESS_NANOS
    val values = readings.map { reading ->
        if (reading.status == SensorStatus.AVAILABLE &&
            (!deliveryFresh || nowNanos - reading.elapsedRealtimeNanos !in 0..VEHICLE_FRESHNESS_NANOS))
            reading.copy(status = SensorStatus.STALE) else reading
    }
    return copy(readings = values, connected = connected && deliveryFresh,
        oilPressure = if (deliveryFresh) oilPressure else OilPressureStatus.UNAVAILABLE,
        gear = if (deliveryFresh) gear else null)
}

fun GpsFix.isMonitoringFresh(nowNanos: Long): Boolean =
    nowNanos - elapsedRealtimeNanos in 0..GPS_FRESHNESS_NANOS && horizontalAccuracyMeters <= 25.0
