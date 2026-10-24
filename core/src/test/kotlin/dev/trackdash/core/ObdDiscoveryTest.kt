package dev.trackdash.core

import dev.trackdash.core.obd.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObdDiscoveryTest {
    @Test fun `known different VIN cannot silently replace a saved vehicle identity`() {
        assertTrue(vehicleIdentityMismatch("1HGCM82633A004352", "2HGCM82633A004352"))
        assertFalse(vehicleIdentityMismatch(" 1hgcm82633a004352 ", "1HGCM82633A004352"))
        assertFalse(vehicleIdentityMismatch(null, "1HGCM82633A004352"))
        assertFalse(vehicleIdentityMismatch("1HGCM82633A004352", null))
    }
    private class FakeTransport(private val replies: Map<String, String>) : ElmTransport {
        val commands = mutableListOf<String>()
        var closed = false
        override suspend fun exchange(command: String, timeoutMillis: Long): String {
            ElmCommands.requireReadOnly(command)
            commands += command
            return replies[command] ?: when {
                command == "ATI" -> "ELM327 fixture"
                command == "ATDP" -> "ISO 15765-4 CAN"
                command.startsWith("AT") -> "OK"
                else -> "NO DATA"
            }
        }
        override fun close() { closed = true }
    }
    private fun frame(ecu: String, vararg bytes: Int) = ecu + "%02X".format(bytes.size) + bytes.joinToString("") { "%02X".format(it) }
    private fun support(ecu: String, base: Int = 0, vararg pids: Int): String {
        val bitmap = pids.fold(0L) { bits, pid -> bits or (1L shl (32 - (pid - base))) }
        return frame(ecu, 0x41, base, (bitmap shr 24 and 255).toInt(), (bitmap shr 16 and 255).toInt(),
            (bitmap shr 8 and 255).toInt(), (bitmap and 255).toInt())
    }
    private fun ObdEcuCapabilities.status(sensor: SensorId) = sensors.single { it.sensor == sensor }.availability

    @Test fun `selects usable engine ECU rather than first sorted responder and reads each PID once`() = runTest {
        val transport = FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x01) + "\r" + support("7E9", 0, 0x05, 0x0c),
            "0105" to frame("7E9", 0x41, 0x05, 100),
            "010C" to frame("7E9", 0x41, 0x0c, 0x1a, 0xf8),
        ))
        val result = ObdDiscovery.connectAndScan { transport }
        assertEquals("7E9", result.selectedEcu)
        assertEquals(setOf(SensorId.ENGINE_RPM, SensorId.COOLANT_TEMPERATURE), result.selected!!.availableSensors)
        assertEquals(2, result.ecus.size)
        assertEquals(1, transport.commands.count { it == "010C" })
        assertTrue(transport.closed)
    }

    @Test fun `RPM capable ECU wins over other decoded sensors and responses are attributed per ECU`() = runTest {
        val transport = FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x05, 0x0c) + "\r" + support("7E9", 0, 0x0c),
            "0105" to frame("7E8", 0x41, 0x05, 100),
            "010C" to frame("7E9", 0x41, 0x0c, 0, 0),
        ))
        val result = ObdDiscovery.inspect(transport)
        assertEquals("7E9", result.selectedEcu)
        assertEquals(ObdSensorAvailability.UNAVAILABLE, result.ecus.first().status(SensorId.ENGINE_RPM))
        assertEquals(ObdSensorAvailability.AVAILABLE, result.selected!!.status(SensorId.ENGINE_RPM))
        assertEquals(1, transport.commands.count { it == "010C" })
    }

    @Test fun `advertised no data is unavailable while unadvertised and unqueried sensors are unsupported`() = runTest {
        val result = ObdDiscovery.inspect(FakeTransport(mapOf("0100" to support("7E8", 0, 0x0c))))
        assertNull(result.selectedEcu)
        val ecu = result.ecus.single()
        assertEquals(ObdSensorAvailability.UNAVAILABLE, ecu.status(SensorId.ENGINE_RPM))
        assertEquals(ObdSensorAvailability.UNSUPPORTED, ecu.status(SensorId.COOLANT_TEMPERATURE))
        assertEquals(ObdSensorAvailability.UNSUPPORTED, ecu.status(SensorId.OIL_TEMPERATURE))
    }

    @Test fun `missing advertised continuation page is unknown not unsupported`() = runTest {
        val transport = FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x05, 0x20),
            "0105" to frame("7E8", 0x41, 0x05, 120),
        ))
        val result = ObdDiscovery.inspect(transport)
        assertTrue("0120" in transport.commands)
        assertEquals(ObdSensorAvailability.UNKNOWN, result.selected!!.status(SensorId.OIL_TEMPERATURE))
        assertEquals(ObdSensorAvailability.UNSUPPORTED, result.selected!!.status(SensorId.ENGINE_RPM))
    }

    @Test fun `continuation responses preserve ECU page knowledge and discover oil temperature only when advertised`() = runTest {
        val transport = FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x0c, 0x20) + "\r" + support("7E9", 0, 0x05),
            "0120" to support("7E8", 0x20, 0x40),
            "0140" to support("7E8", 0x40, 0x5c),
            "010C" to frame("7E8", 0x41, 0x0c, 0x10, 0),
            "0105" to frame("7E9", 0x41, 0x05, 100),
            "015C" to frame("7E8", 0x41, 0x5c, 150),
        ))
        val result = ObdDiscovery.inspect(transport)
        assertEquals(ObdSensorAvailability.AVAILABLE, result.selected!!.status(SensorId.OIL_TEMPERATURE))
        assertEquals(setOf(0, 0x20, 0x40), result.selected!!.supportedPages)
        assertEquals(ObdSensorAvailability.UNSUPPORTED, result.ecus.last().status(SensorId.OIL_TEMPERATURE))
        assertEquals(1, transport.commands.count { it == "015C" })
    }

    @Test fun `malformed probe is unknown and does not hide other usable sensors`() = runTest {
        val result = ObdDiscovery.inspect(FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x05, 0x0c),
            "010C" to "7E804410CZZFF",
            "0105" to frame("7E8", 0x41, 0x05, 100),
        )))
        assertEquals("7E8", result.selectedEcu)
        assertEquals(ObdSensorAvailability.UNKNOWN, result.selected!!.status(SensorId.ENGINE_RPM))
        assertEquals(ObdSensorAvailability.AVAILABLE, result.selected!!.status(SensorId.COOLANT_TEMPERATURE))
    }

    @Test fun `scan failure closes owned connection`() = runTest {
        val transport = FakeTransport(emptyMap())
        try { ObdDiscovery.connectAndScan { transport }; fail("Expected no responding ECU") }
        catch (_: IllegalArgumentException) { assertTrue(transport.closed) }
    }

    @Test fun `transport loss during final VIN query fails check instead of reporting connected`() = runTest {
        var closed = false
        val delegate = FakeTransport(mapOf("0100" to support("7E8", 0, 0x0c), "010C" to frame("7E8", 0x41, 0x0c, 0, 0)))
        val transport = object : ElmTransport {
            override suspend fun exchange(command: String, timeoutMillis: Long): String {
                if (command == "0902") throw IOException("Socket closed")
                return delegate.exchange(command, timeoutMillis)
            }
            override fun close() { closed = true }
        }
        try { ObdDiscovery.connectAndScan { transport }; fail("Expected transport failure") }
        catch (_: IOException) { assertTrue(closed) }
    }

    @Test fun `compatibility report excludes private identifiers and raw replies by default`() = runTest {
        val result = ObdDiscovery.inspect(FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x0c), "010C" to frame("7E8", 0x41, 0x0c, 0, 0),
        ))).copy(vin = "1HGCM82633A004352", firmware = "private raw serial", protocol = "ISO 15765-4 CAN private raw reply")
        val report = ObdCompatibilityReports.encode(result, 1234, false)
        assertTrue(report.contains("ISO_15765_CAN"))
        assertTrue(report.contains("USER_REPORTED_NOT_VERIFIED"))
        assertTrue(report.contains("ENGINE_RPM"))
        assertFalse(report.contains("1HGCM82633A004352"))
        assertFalse(report.contains("private raw"))
        assertFalse(report.contains("firmware"))
        assertTrue(report.contains("\"oilPressureMonitored\": false"))
        val described = ObdCompatibilityReports.encode(result, 1234, false, "  Test car  ", "Test adapter")
        assertTrue(described.contains("\"vehicleDescription\": \"Test car\""))
    }

    @Test fun `cancelled scan closes owned connection and cancellation is not swallowed`() = runTest {
        var closed = false
        val transport = object : ElmTransport {
            override suspend fun exchange(command: String, timeoutMillis: Long): String = awaitCancellation()
            override fun close() { closed = true }
        }
        val scan = launch { ObdDiscovery.connectAndScan { transport } }
        runCurrent()
        scan.cancelAndJoin()
        assertTrue(closed)
        assertTrue(scan.isCancelled)
    }

    @Test fun `cache roundtrip preserves capability facts and date but not VIN and rejects legacy or future cache`() = runTest {
        val result = ObdDiscovery.inspect(FakeTransport(mapOf(
            "0100" to support("7E8", 0, 0x0c), "010C" to frame("7E8", 0x41, 0x0c, 0, 0),
        ))).copy(vin = "1HGCM82633A004352")
        val encoded = ObdCapabilityCache.encode(result, 1234)
        val cached = ObdCapabilityCache.decode(encoded)!!
        assertEquals(1234L, cached.checkedAtMillis)
        assertEquals(result.ecus, cached.discovery.ecus)
        assertNull(cached.discovery.vin)
        assertFalse(encoded.contains("1HGCM82633A004352"))
        assertNull(ObdCapabilityCache.decode("0C, 05, 5C"))
        assertNull(ObdCapabilityCache.decode(encoded.replace("\"version\":1", "\"version\":2")))
    }
}
