package dev.trackdash.sources

import android.content.Context
import android.os.SystemClock
import dev.trackdash.core.LocationSource
import dev.trackdash.core.VehicleSource
import dev.trackdash.core.simulation.SimulatedGpsSource
import dev.trackdash.core.simulation.SimulatedVehicleSource

/** One replaceable acquisition seam for deterministic service tests; never selected by remote input. */
interface RecordingSourceFactory {
    fun vehicle(context: Context, simulated: Boolean, address: String?, bleCx: Boolean): VehicleSource
    fun location(context: Context, simulated: Boolean): LocationSource
}

object AndroidRecordingSources : RecordingSourceFactory {
    override fun vehicle(context: Context, simulated: Boolean, address: String?, bleCx: Boolean): VehicleSource =
        if (simulated) SimulatedVehicleSource(SystemClock::elapsedRealtimeNanos)
        else ObdVehicleSource(context, requireNotNull(address) { "Choose an adapter" }, bleCx)

    override fun location(context: Context, simulated: Boolean): LocationSource =
        if (simulated) SimulatedGpsSource(SystemClock::elapsedRealtimeNanos, System::currentTimeMillis)
        else AndroidGpsSource(context)
}
