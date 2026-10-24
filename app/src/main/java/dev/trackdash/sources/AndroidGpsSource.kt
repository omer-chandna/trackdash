package dev.trackdash.sources

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import dev.trackdash.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class AndroidGpsSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    @SuppressLint("MissingPermission")
    override fun fixes() = callbackFlow {
        check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "Enable device location/GPS before starting" }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = runCatching { GpsFix(location.latitude, location.longitude,
                    if (location.hasAltitude()) location.altitude else null,
                    if (location.hasSpeed()) location.speed.toDouble().coerceAtLeast(0.0) else null,
                    if (location.hasBearing()) location.bearing.toDouble() else null,
                    if (location.hasAccuracy()) location.accuracy.toDouble() else 10_000.0,
                    location.provider ?: "gps", location.time, location.elapsedRealtimeNanos, false) }
                fix.fold(onSuccess = {
                    if (trySend(it).isFailure) close(IllegalStateException("GPS delivery buffer overflow"))
                }, onFailure = { close(IllegalArgumentException("Invalid GPS fix", it)) })
            }
            override fun onProviderDisabled(provider: String) { close(IllegalStateException("GPS provider disabled")) }
        }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200L, 0f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }
}
