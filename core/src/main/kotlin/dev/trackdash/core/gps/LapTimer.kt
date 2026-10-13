package dev.trackdash.core.gps

import dev.trackdash.core.GpsFix
import kotlin.math.roundToLong

@kotlinx.serialization.Serializable
data class TimingPolicy(val maximumAccuracyMeters: Double = 25.0, val maximumGapNanos: Long = 3_000_000_000,
    val maximumSpeedMetersPerSecond: Double = 100.0, val minimumLapNanos: Long = 5_000_000_000,
    val minimumLapDistanceMeters: Double = 100.0, val cooldownNanos: Long = 3_000_000_000) {
    init {
        require(maximumAccuracyMeters.isFinite() && maximumAccuracyMeters > 0)
        require(maximumGapNanos > 0 && minimumLapNanos > 0 && cooldownNanos >= 0)
        require(maximumSpeedMetersPerSecond.isFinite() && maximumSpeedMetersPerSecond > 0)
        require(minimumLapDistanceMeters.isFinite() && minimumLapDistanceMeters >= 0)
    }
}
@kotlinx.serialization.Serializable
data class TimedLap(val number: Int, val startNanos: Long, val endNanos: Long,
    val sectorNanos: List<Long>, val invalidReasons: Set<String>) {
    val durationNanos get() = endNanos - startNanos
    val valid get() = invalidReasons.isEmpty()
}

/** Bounded state only. Callers persist returned laps; GPS/OBD/maps are independent. */
class LapTimer(val track: Track, private val policy: TimingPolicy = TimingPolicy()) {
    private var previous: GpsFix? = null
    private var latestTimestamp: Long? = null
    var startedAtNanos: Long? = null; private set
    var lastLap: TimedLap? = null; private set
    var bestLap: TimedLap? = null; private set
    var completedLaps = 0; private set
    var rejectedFixes = 0; private set
    var nextSector = 0; private set
    private var distance = 0.0
    private var sectorStarted = 0L
    private var sectorTimes = mutableListOf<Long>()
    private val invalid = mutableSetOf<String>()
    private val lastCrossing = mutableMapOf<String, Long>()
    private val bestSectors = MutableList<Long?>(track.sectors.size + 1) { null }
    val theoreticalBestNanos: Long? get() = if (bestSectors.all { it != null }) bestSectors.sumOf { it!! } else null

    fun markGap(reason: String = "GPS signal gap") {
        if (startedAtNanos != null) invalid += reason
        previous = null
    }

    fun accept(fix: GpsFix): List<TimedLap> {
        if (latestTimestamp?.let { fix.elapsedRealtimeNanos <= it } == true) {
            rejectedFixes++; return emptyList()
        }
        latestTimestamp = fix.elapsedRealtimeNanos
        if (fix.horizontalAccuracyMeters > policy.maximumAccuracyMeters || fix.latitude !in -85.0..85.0) {
            rejectedFixes++; markGap("Poor GPS accuracy"); return emptyList()
        }
        val old = previous
        if (old != null && fix.elapsedRealtimeNanos <= old.elapsedRealtimeNanos) {
            rejectedFixes++; return emptyList()
        }
        previous = fix
        if (old == null) return emptyList()
        val a = GeoPoint(old.latitude, old.longitude)
        val b = GeoPoint(fix.latitude, fix.longitude)
        val duration = fix.elapsedRealtimeNanos - old.elapsedRealtimeNanos
        val travel = Geometry.distance(a, b)
        if (duration > policy.maximumGapNanos || travel / (duration / 1e9) > policy.maximumSpeedMetersPerSecond) {
            rejectedFixes++
            if (startedAtNanos != null) invalid += "GPS gap or implausible jump"
            return emptyList()
        }
        val crossings = (listOf(track.startFinish) + track.sectors).mapNotNull { gate ->
            Geometry.crossingFraction(a, b, gate)?.let { gate to it }
        }.sortedBy { it.second }
        val completed = mutableListOf<TimedLap>()
        var lastFraction = 0.0
        crossings.forEach { (gate, fraction) ->
            distance += travel * (fraction - lastFraction)
            lastFraction = fraction
            val time = old.elapsedRealtimeNanos + (duration * fraction).roundToLong()
            if (lastCrossing[gate.id]?.let { time - it < policy.cooldownNanos } == true) return@forEach
            lastCrossing[gate.id] = time
            if (gate.id == track.startFinish.id) {
                val start = startedAtNanos
                if (start != null) {
                    if (time - start < policy.minimumLapNanos || distance < policy.minimumLapDistanceMeters) return@forEach
                    if (nextSector != track.sectors.size) invalid += "Missing or unordered sectors"
                    val lap = TimedLap(++completedLaps, start, time, sectorTimes + (time - sectorStarted), invalid.toSet())
                    lastLap = lap
                    if (lap.valid) {
                        if (bestLap == null || lap.durationNanos < bestLap!!.durationNanos) bestLap = lap
                        lap.sectorNanos.forEachIndexed { index, value -> bestSectors[index] = minOf(bestSectors[index] ?: Long.MAX_VALUE, value) }
                    }
                    completed += lap
                }
                startedAtNanos = time; sectorStarted = time; distance = 0.0
                nextSector = 0; sectorTimes.clear(); invalid.clear()
            } else if (startedAtNanos != null) {
                if (track.sectors.getOrNull(nextSector)?.id == gate.id) {
                    sectorTimes += time - sectorStarted
                    sectorStarted = time
                    nextSector++
                } else invalid += "Missing or unordered sectors"
            }
        }
        distance += travel * (1.0 - lastFraction)
        return completed
    }
}
