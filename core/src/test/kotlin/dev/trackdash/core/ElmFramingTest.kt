package dev.trackdash.core

import dev.trackdash.core.obd.*
import org.junit.Assert.*
import org.junit.Test

class ElmFramingTest {
    @Test fun partialNotificationsProduceExactlyOneCompleteResponse() {
        val buffer = ElmResponseBuffer()
        assertNull(buffer.accept("7E8 04 41".toByteArray()))
        assertNull(buffer.accept("0C 1F A0\r".toByteArray()))
        assertEquals("7E8 04 410C 1F A0\r", buffer.accept(">\r".toByteArray()))
    }
    @Test fun oversizedAndTrailingDataAreRejected() {
        assertThrows(IllegalStateException::class.java) { ElmResponseBuffer(3).accept("1234".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { ElmResponseBuffer().accept("OK>BAD".toByteArray()) }
    }
    @Test fun allowlistExcludesFaultClearingAndArbitraryAdapterCommands() {
        listOf("04", "ATSH7E0", "ATPP", "010C\r04").forEach { assertThrows(IllegalArgumentException::class.java) { ElmCommands.requireReadOnly(it) } }
        listOf("010C", "0902", "ATZ", "ATDP").forEach(ElmCommands::requireReadOnly)
    }
}
