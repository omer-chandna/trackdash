package dev.trackdash.core

import dev.trackdash.core.obd.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ObdProtocolTest {
    @Test fun `echoes and multiple ECUs retain separate responses`() {
        val frames = ObdParser.frames("010C\r7E8 04 41 0C 1A F8\r7E9 04 41 0C 10 00\r>", "010C")
        val rpm = StandardPids.all.single { it.code == 12 }
        assertEquals(2, frames.size)
        assertEquals(1726.0, rpm.value(frames[0]), 0.0)
        assertEquals(1024.0, rpm.value(frames[1]), 0.0)
    }
    @Test fun `all initial PIDs decode known payloads in canonical units`() {
        val expected = mapOf(0x04 to 100.0, 0x05 to 215.0, 0x0c to 16383.75, 0x0d to 255/3.6,
            0x0e to 63.5, 0x0f to 215.0, 0x10 to 655.35, 0x11 to 100.0, 0x42 to 65.535, 0x46 to 215.0, 0x5c to 215.0)
        StandardPids.all.forEach { assertEquals(expected[it.code]!!, it.value(EcuFrame("7E8", listOf(0x41,it.code,255,255))), 1e-8) }
    }
    @Test fun `invalid hex truncated and unsupported framing are rejected`() {
        for (text in listOf("7E804410CZZFF", "7E804410C", "41C", "abcd")) {
            assertThrows(IllegalArgumentException::class.java) { ObdParser.frames(text,"010C") }
        }
        assertTrue(ObdParser.frames("NO DATA\r>", "010C").isEmpty())
    }
    @Test fun `discovery stops without continuation bit and preserves ECU maps`() = runTest {
        val queries = mutableListOf<String>()
        val transport = object : ElmTransport {
            override suspend fun exchange(command: String, timeoutMillis: Long): String {
                queries += command
                return "7E806410008180000\r7E906410000080000\r>"
            }
            override fun close() {}
        }
        val result = ElmProtocol(transport).discover()
        assertEquals(listOf("0100"), queries)
        assertTrue(0x0c in result.getValue("7E8"))
        assertFalse(0x0c in result.getValue("7E9"))
    }
}
