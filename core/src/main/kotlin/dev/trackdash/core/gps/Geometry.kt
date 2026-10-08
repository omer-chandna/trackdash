package dev.trackdash.core.gps

import kotlin.math.*

object Geometry {
    private const val EARTH_METERS = 6_371_008.8
    private data class XY(val x: Double, val y: Double) {
        operator fun minus(other: XY) = XY(x - other.x, y - other.y)
        fun cross(other: XY) = x * other.y - y * other.x
    }
    private fun project(point: GeoPoint, origin: GeoPoint): XY {
        val longitudeDelta = (point.longitude - origin.longitude + 540.0) % 360.0 - 180.0
        return XY(Math.toRadians(longitudeDelta) * EARTH_METERS * cos(Math.toRadians(origin.latitude)),
            Math.toRadians(point.latitude - origin.latitude) * EARTH_METERS)
    }
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val p = project(b, a)
        return hypot(p.x, p.y)
    }

    /** Local metric finite-segment intersection. (0,1] excludes a repeated fix on a gate. */
    fun crossingFraction(previous: GeoPoint, current: GeoPoint, gate: TimingGate): Double? {
        val p = project(previous, gate.start)
        val movement = project(current, gate.start) - p
        val gateVector = project(gate.end, gate.start)
        val determinant = movement.cross(gateVector)
        if (abs(determinant) < 1e-8) return null
        val t = (XY(0.0, 0.0) - p).cross(gateVector) / determinant
        val u = (XY(0.0, 0.0) - p).cross(movement) / determinant
        if (t <= 1e-9 || t > 1.0 || u < 0.0 || u > 1.0) return null
        val sideChange = gateVector.cross(movement)
        if (gate.direction == CrossingDirection.RIGHT_TO_LEFT && sideChange <= 0) return null
        if (gate.direction == CrossingDirection.LEFT_TO_RIGHT && sideChange >= 0) return null
        return t
    }
}
