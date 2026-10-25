package dev.trackdash.recording

import org.junit.Assert.*
import org.junit.Test

class LiveViewPolicyTest {
    @Test fun liveViewNeverClaimsThatItSavedASession() {
        assertEquals("Live view stopped · nothing saved", monitoringEndMessage(true, false, false, null))
        assertEquals("Live view failed · nothing saved", monitoringEndMessage(true, false, false, "Adapter failed"))
        assertEquals("Live view interrupted · nothing saved", monitoringEndMessage(true, false, true, null))
        assertFalse(monitoringEndMessage(true, true, false, null).contains("Saved"))
    }

    @Test fun recordingEndMessagesRemainCompatible() {
        assertEquals("Saved", monitoringEndMessage(false, false, false, null))
        assertEquals("Recording failed", monitoringEndMessage(false, false, false, "Storage full"))
        assertEquals("Recording interrupted", monitoringEndMessage(false, false, true, null))
    }
}
