package dev.trackdash.recording

import dev.trackdash.core.SensorId
import dev.trackdash.core.telemetry.AlertCategory
import dev.trackdash.core.telemetry.AlertSeverity
import dev.trackdash.core.telemetry.AlertCondition
import dev.trackdash.core.telemetry.VehicleAlert
import org.junit.Assert.*
import org.junit.Test

class DashboardWarningManagerTest {
    private val pressure = VehicleAlert("oil-pressure", "Low oil pressure", AlertSeverity.CRITICAL, AlertCategory.ENGINE_SAFETY)
    private val oil = VehicleAlert("oil", "Oil temperature above configured limit", AlertSeverity.WARNING,
        AlertCategory.ENGINE_SAFETY, SensorId.OIL_TEMPERATURE)
    private val coolant = VehicleAlert("coolant", "Coolant temperature above configured limit", AlertSeverity.WARNING,
        AlertCategory.ENGINE_SAFETY, SensorId.COOLANT_TEMPERATURE)

    private fun baseline() = DashboardWarningManager().update(emptyList(), false)
    private fun finishWarning(manager: DashboardWarningManager): DashboardWarningManager =
        (0 until 6).fold(manager) { state, _ -> state.advance() }

    @Test fun `simultaneous alerts flash individually with low pressure first`() {
        var manager = baseline().update(listOf(oil, coolant, pressure), false)
        assertEquals(pressure.id, manager.currentId)
        assertEquals(listOf(coolant.id, oil.id), manager.pendingIds)
        repeat(3) {
            assertTrue(manager.pulseVisible)
            manager = manager.advance()
            assertFalse(manager.pulseVisible)
            manager = manager.advance()
        }
        assertEquals(coolant.id, manager.currentId)
        manager = finishWarning(manager)
        assertEquals(oil.id, manager.currentId)
        manager = finishWarning(manager)
        assertFalse(manager.busy)
    }

    @Test fun `new warning joins without restarting the current warning`() {
        val flashing = baseline().update(listOf(oil), false).advance().advance()
        val joined = flashing.update(listOf(oil, pressure, coolant), false)
        assertEquals(oil.id, joined.currentId)
        assertEquals(2, joined.phase)
        assertEquals(flashing.sequence, joined.sequence)
        assertEquals(listOf(pressure.id, coolant.id), joined.pendingIds)
        assertEquals(joined, joined.update(listOf(oil, pressure, coolant), false))
    }

    @Test fun `cleared warnings are removed and can flash again on recurrence`() {
        val first = baseline().update(listOf(pressure, oil, coolant), false)
        val cleared = first.update(listOf(coolant), false)
        assertEquals(coolant.id, cleared.currentId)
        assertTrue(cleared.pendingIds.isEmpty())
        val done = finishWarning(cleared)
        assertFalse(done.update(listOf(coolant), false).busy)
        val recurred = done.update(emptyList(), false).update(listOf(pressure), false)
        assertEquals(pressure.id, recurred.currentId)
        assertEquals(0, recurred.phase)
    }

    @Test fun `existing warnings do not replay when dashboard opens`() {
        val manager = DashboardWarningManager().update(listOf(pressure, oil), true)
        assertFalse(manager.busy)
        assertFalse(manager.update(listOf(pressure, oil), true).busy)
    }

    @Test fun `connectivity and stale data never join the flash queue`() {
        val connectivity = VehicleAlert("gps", "GPS lost", AlertSeverity.WARNING, AlertCategory.CONNECTIVITY)
        val stale = VehicleAlert("stale", "Stale data", AlertSeverity.WARNING, AlertCategory.DATA_QUALITY)
        val manager = baseline().update(listOf(connectivity, stale), false)
        assertFalse(manager.busy)
    }

    @Test fun `engine warnings precede shift and its stale prompt is discarded`() {
        val first = baseline().update(listOf(oil, pressure), true)
        assertEquals(pressure.id, first.currentId)
        assertEquals(listOf(oil.id, SHIFT_WARNING_ID), first.pendingIds)
        val shiftedBack = first.update(listOf(oil, pressure), false)
        assertEquals(listOf(oil.id), shiftedBack.pendingIds)
        val shift = baseline().update(emptyList(), true).advance()
        val takeover = shift.update(listOf(pressure), true)
        assertEquals(pressure.id, takeover.currentId)
        assertFalse(takeover.pendingIds.contains(SHIFT_WARNING_ID))
    }

    @Test fun `long temperature messages get individual readable headlines`() {
        assertEquals("LOW OIL PRESSURE", warningHeadline(pressure))
        assertEquals("HIGH OIL TEMP", warningHeadline(oil))
        assertEquals("HIGH COOLANT TEMP", warningHeadline(coolant))
    }

    @Test fun `unresolved data loss keeps the warning but cancels its flashes`() {
        val active = baseline().update(listOf(oil), false)
        assertTrue(active.pulseVisible)
        val unresolved = oil.copy(condition = AlertCondition.UNCONFIRMED)
        val lost = active.update(listOf(unresolved), false)
        assertFalse(lost.busy)
        assertFalse(lost.update(listOf(oil), false).busy)
        assertFalse(baseline().update(listOf(unresolved), false).busy)
    }
}
