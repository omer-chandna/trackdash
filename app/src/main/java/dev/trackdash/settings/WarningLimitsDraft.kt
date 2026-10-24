package dev.trackdash.settings

import dev.trackdash.data.DashboardPreferences

internal data class WarningLimitsDraft(val coolant: String, val oil: String, val intake: String, val rpm: String) {
    val valid: Boolean get() = validLimit(coolant, -40.0..200.0) && validLimit(oil, -40.0..200.0) &&
        validLimit(intake, -40.0..200.0) && validLimit(rpm, 1000.0..20_000.0)

    fun applyTo(preferences: DashboardPreferences): DashboardPreferences {
        require(valid) { "Correct all warning limits before saving" }
        return preferences.copy(coolantLimit = coolant.trim().toDoubleOrNull(), oilLimit = oil.trim().toDoubleOrNull(),
            intakeLimit = intake.trim().toDoubleOrNull(), rpmLimit = rpm.trim().toDoubleOrNull())
    }

    companion object {
        fun from(preferences: DashboardPreferences) = WarningLimitsDraft(preferences.coolantLimit?.toString().orEmpty(),
            preferences.oilLimit?.toString().orEmpty(), preferences.intakeLimit?.toString().orEmpty(), preferences.rpmLimit?.toString().orEmpty())
    }
}

internal fun validLimit(text: String, range: ClosedFloatingPointRange<Double>): Boolean =
    text.isBlank() || text.trim().toDoubleOrNull()?.let { it.isFinite() && it in range } == true
