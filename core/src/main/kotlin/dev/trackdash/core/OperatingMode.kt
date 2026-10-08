package dev.trackdash.core

enum class OperatingMode(val usesVehicle: Boolean, val usesGps: Boolean) {
    VEHICLE_DASHBOARD(usesVehicle = true, usesGps = false),
    GPS_LAP_TIMER(usesVehicle = false, usesGps = true),
    COMBINED_TELEMETRY(usesVehicle = true, usesGps = true),
}
