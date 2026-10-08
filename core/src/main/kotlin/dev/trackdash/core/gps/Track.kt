package dev.trackdash.core.gps

enum class CrossingDirection { EITHER, RIGHT_TO_LEFT, LEFT_TO_RIGHT }
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && latitude in -85.0..85.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
}
data class TimingGate(val id: String, val name: String, val start: GeoPoint, val end: GeoPoint,
    val direction: CrossingDirection = CrossingDirection.EITHER) {
    init { require(id.isNotBlank()); require(name.isNotBlank()); require(Geometry.distance(start, end) in 1.0..500.0) }
}
data class Track(val id: String, val name: String, val startFinish: TimingGate,
    val sectors: List<TimingGate> = emptyList(), val notes: String = "") {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(sectors.size <= 12)
        require((listOf(startFinish) + sectors).map { it.id }.distinct().size == sectors.size + 1)
    }
}
