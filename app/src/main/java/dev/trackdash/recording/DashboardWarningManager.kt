package dev.trackdash.recording

import androidx.compose.runtime.saveable.listSaver
import dev.trackdash.core.SensorId
import dev.trackdash.core.telemetry.AlertCategory
import dev.trackdash.core.telemetry.AlertSeverity
import dev.trackdash.core.telemetry.AlertCondition
import dev.trackdash.core.telemetry.VehicleAlert

internal const val SHIFT_WARNING_ID = "dashboard:shift"
internal const val WARNING_PHASE_MILLIS = 250L
private const val LAST_PULSE_PHASE = 5 // On/off repeated three times.

/** Presentation only: telemetry alert messages and recorded/exported events remain unchanged. */
internal data class DashboardWarningManager(
    val initialized: Boolean = false,
    val knownSafetyIds: List<String> = emptyList(),
    val knownShift: Boolean = false,
    val pendingIds: List<String> = emptyList(),
    val currentId: String? = null,
    val phase: Int = 0,
    val sequence: Long = 0,
) {
    val pulseVisible: Boolean get() = currentId != null && phase % 2 == 0
    val busy: Boolean get() = currentId != null || pendingIds.isNotEmpty()

    fun update(alerts: List<VehicleAlert>, shiftActive: Boolean): DashboardWarningManager {
        val safety = alerts.filter { it.category == AlertCategory.ENGINE_SAFETY }.distinctBy { it.id }
        val activeIds = safety.map { it.id }
        // Establish a baseline when opening an already-active dashboard; do not replay old warnings.
        if (!initialized) return copy(initialized = true, knownSafetyIds = activeIds, knownShift = shiftActive)

        val eligible = safety.filter { it.condition == AlertCondition.ACTIVE }.map { it.id }.toSet() +
            if (shiftActive) setOf(SHIFT_WARNING_ID) else emptySet()
        var current = currentId?.takeIf { it in eligible }
        val newSafety = safety.filter { it.id !in knownSafetyIds && it.condition == AlertCondition.ACTIVE }.map { it.id }
        val candidates = (pendingIds.filter { it in eligible } + newSafety +
            if (shiftActive && !knownShift) listOf(SHIFT_WARNING_ID) else emptyList())
            .distinct().filter { it != current }
        val byId = safety.associateBy { it.id }
        val pending = candidates.sortedWith(compareBy<String>(
            { if (it == SHIFT_WARNING_ID) 1 else 0 },
            { if (byId[it]?.severity == AlertSeverity.CRITICAL) 0 else 1 },
            { if (it == "oil-pressure") 0 else 1 },
            { it },
        ))
        // A safety warning takes over from SHIFT. It never interrupts another engine warning.
        // SHIFT retains its steady prompt afterward rather than replaying an interrupted flash cycle.
        if (current == SHIFT_WARNING_ID && pending.any { it != SHIFT_WARNING_ID }) current = null
        val next = current ?: pending.firstOrNull()
        val changed = next != currentId
        return copy(
            knownSafetyIds = activeIds,
            knownShift = shiftActive,
            pendingIds = pending.filter { it != next },
            currentId = next,
            phase = if (changed) 0 else phase,
            sequence = if (changed) sequence + 1 else sequence,
        )
    }

    fun advance(): DashboardWarningManager = when {
        currentId == null -> this
        phase < LAST_PULSE_PHASE -> copy(phase = phase + 1)
        else -> copy(currentId = pendingIds.firstOrNull(), pendingIds = pendingIds.drop(1), phase = 0, sequence = sequence + 1)
    }

    companion object {
        val Saver = listSaver<DashboardWarningManager, Any>(
            save = { listOf(it.initialized, ArrayList(it.knownSafetyIds), it.knownShift,
                ArrayList(it.pendingIds), it.currentId.orEmpty(), it.phase, it.sequence) },
            restore = { DashboardWarningManager(
                initialized = it[0] as Boolean,
                knownSafetyIds = (it[1] as List<*>).filterIsInstance<String>(),
                knownShift = it[2] as Boolean,
                pendingIds = (it[3] as List<*>).filterIsInstance<String>(),
                currentId = (it[4] as String).ifEmpty { null },
                phase = it[5] as Int,
                sequence = it[6] as Long,
            ) },
        )
    }
}

internal fun warningHeadline(alert: VehicleAlert): String = when {
    alert.id == "oil-pressure" -> "LOW OIL PRESSURE"
    alert.sensor == SensorId.OIL_TEMPERATURE -> "HIGH OIL TEMP"
    alert.sensor == SensorId.COOLANT_TEMPERATURE -> "HIGH COOLANT TEMP"
    alert.sensor == SensorId.INTAKE_TEMPERATURE -> "HIGH INTAKE TEMP"
    else -> alert.message.uppercase()
}
