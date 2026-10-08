package dev.trackdash.core.simulation

import dev.trackdash.core.*
import dev.trackdash.core.gps.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlin.math.*

object DemoCircuit {
    private const val CENTER_LAT = 43.0
    private const val CENTER_LON = -79.0
    private const val RADIUS = 150.0
    private fun point(angle: Double, radius: Double) = GeoPoint(
        CENTER_LAT + sin(angle) * radius / 111_195.0,
        CENTER_LON + cos(angle) * radius / (111_195.0 * cos(Math.toRadians(CENTER_LAT))),
    )
    private fun gate(id: String, angle: Double) = TimingGate(id, id, point(angle, RADIUS - 25), point(angle, RADIUS + 25), CrossingDirection.RIGHT_TO_LEFT)
    val track = Track("demo-circle-v1", "Demo circuit", gate("Start / Finish", 0.0),
        listOf(gate("Sector 1", 2 * PI / 3), gate("Sector 2", 4 * PI / 3)))

    fun fixAt(elapsedNanos: Long, acquiredNanos: Long, wallMillis: Long, lapSeconds: Double = 60.0): GpsFix {
        require(lapSeconds in 20.0..300.0)
        val angle = elapsedNanos / 1e9 / lapSeconds * 2 * PI - 0.1
        val point = point(angle, RADIUS)
        return GpsFix(point.latitude, point.longitude, null, 2 * PI * RADIUS / lapSeconds,
            ((450.0 - Math.toDegrees(angle + PI / 2)) % 360 + 360) % 360,
            3.0, "simulation", wallMillis, acquiredNanos, true)
    }
}

class SimulatedGpsSource(private val monotonicNanos: () -> Long, private val wallMillis: () -> Long) : LocationSource {
    val lapSeconds = MutableStateFlow(60.0)
    val dropout = MutableStateFlow(false)
    val jump = MutableStateFlow(false)
    override fun fixes(): Flow<GpsFix> = flow {
        var elapsed = 0L
        var previous = monotonicNanos()
        while (currentCoroutineContext().isActive) {
            val now = monotonicNanos()
            // Integrate phase so changing pace does not teleport the car around the circuit.
            elapsed += ((now - previous) * 60.0 / lapSeconds.value.coerceIn(20.0, 300.0)).toLong()
            previous = now
            if (!dropout.value) {
                val fix = DemoCircuit.fixAt(elapsed, now, wallMillis())
                emit(if (jump.value) fix.copy(latitude = fix.latitude + 0.1) else fix)
                jump.value = false
            }
            delay(200)
        }
    }
}
