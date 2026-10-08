package dev.trackdash.core.obd

import dev.trackdash.core.SensorId

interface ElmTransport { suspend fun exchange(command: String, timeoutMillis: Long = 2500): String; fun close() }
data class EcuFrame(val ecu: String, val bytes: List<Int>)

object ObdParser {
    /** ATH1 responses: 11/29-bit CAN or three-byte ISO headers; no ECU concatenation. */
    fun frames(response: String, command: String): List<EcuFrame> = response.uppercase().split('\r', '\n', '>').mapNotNull { line ->
        val raw = line.replace(" ", "").trim()
        if (raw.isEmpty() || raw == command.replace(" ", "").uppercase() || raw == "NODATA" || raw.startsWith("SEARCHING")) return@mapNotNull null
        require(raw.all { it in "0123456789ABCDEF" }) { "Malformed OBD response: non-hex data" }
        val (ecu, payload) = when {
            raw.startsWith("41") || raw.startsWith("49") -> "headerless" to raw
            raw.length >= 7 && raw.take(3).toInt(16) in 0x700..0x7ff -> raw.take(3) to raw.drop(3)
            raw.length >= 12 && raw.startsWith("18") -> raw.take(8) to raw.drop(8)
            raw.length >= 10 && (raw.drop(6).startsWith("41") || raw.drop(6).startsWith("49")) -> raw.take(6) to raw.drop(6)
            else -> throw IllegalArgumentException("Unsupported ECU framing")
        }
        require(payload.length % 2 == 0) { "Odd hex payload" }
        var bytes = payload.chunked(2).map { it.toInt(16) }
        if (ecu.length == 3 || ecu.length == 8) {
            // Single CAN frame: PCI length 1..7; multi-frame VIN handled separately.
            val size = bytes.first()
            require(size in 1..7 && bytes.size >= size + 1) { "Unsupported or truncated CAN frame" }
            bytes = bytes.drop(1).take(size)
        }
        EcuFrame(ecu, bytes)
    }

    fun supported(frame: EcuFrame, base: Int): Set<Int> {
        val b = frame.bytes
        require(b.size >= 6 && b[0] == 0x41 && b[1] == base)
        val bitmap = b.drop(2).take(4).fold(0L) { bits, byte -> (bits shl 8) or byte.toLong() }
        return (1..32).filter { bitmap and (1L shl (32 - it)) != 0L }.map { base + it }.toSet()
    }
}

data class Pid(val code: Int, val sensor: SensorId, val bytes: Int, val intervalMillis: Long, val decode: (Int, Int) -> Double) {
    val command get() = "01%02X".format(code)
    fun value(frame: EcuFrame): Double {
        require(frame.bytes.size >= 2 + bytes && frame.bytes.take(2) == listOf(0x41, code)) { "Wrong or truncated PID payload" }
        return decode(frame.bytes[2], frame.bytes.getOrElse(3) { 0 })
    }
}
object StandardPids {
    val all = listOf(
        Pid(0x04, SensorId.ENGINE_LOAD, 1, 500) { a, _ -> a * 100.0 / 255 },
        Pid(0x05, SensorId.COOLANT_TEMPERATURE, 1, 1000) { a, _ -> a - 40.0 },
        Pid(0x0c, SensorId.ENGINE_RPM, 2, 100) { a, b -> (a * 256 + b) / 4.0 },
        Pid(0x0d, SensorId.SPEED, 1, 200) { a, _ -> a / 3.6 },
        Pid(0x0e, SensorId.IGNITION_TIMING, 1, 1000) { a, _ -> a / 2.0 - 64 },
        Pid(0x0f, SensorId.INTAKE_TEMPERATURE, 1, 1000) { a, _ -> a - 40.0 },
        Pid(0x10, SensorId.MASS_AIR_FLOW, 2, 500) { a, b -> (a * 256 + b) / 100.0 },
        Pid(0x11, SensorId.THROTTLE, 1, 200) { a, _ -> a * 100.0 / 255 },
        Pid(0x42, SensorId.VOLTAGE, 2, 2000) { a, b -> (a * 256 + b) / 1000.0 },
        Pid(0x46, SensorId.AMBIENT_TEMPERATURE, 1, 2000) { a, _ -> a - 40.0 },
        Pid(0x5c, SensorId.OIL_TEMPERATURE, 1, 1000) { a, _ -> a - 40.0 },
    )
}

class ElmProtocol(private val transport: ElmTransport) {
    suspend fun initialize() {
        for (command in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0")) {
            val response = transport.exchange(command, if (command == "ATZ") 5000 else 2500)
            require(!response.contains('?') && !response.contains("ERROR", ignoreCase = true)) { "Adapter rejected $command" }
        }
    }
    suspend fun discover(): Map<String, Set<Int>> {
        val byEcu = mutableMapOf<String, MutableSet<Int>>()
        var base = 0
        do {
            val command = "01%02X".format(base)
            val frames = ObdParser.frames(transport.exchange(command, 8000), command)
            frames.forEach { frame -> byEcu.getOrPut(frame.ecu) { mutableSetOf() }.addAll(ObdParser.supported(frame, base)) }
            base += 32
        } while (base <= 0xe0 && byEcu.values.any { base in it })
        return byEcu.mapValues { it.value.toSet() }
    }
    suspend fun read(pid: Pid, ecu: String): Double? = ObdParser.frames(transport.exchange(pid.command), pid.command)
        .firstOrNull { it.ecu == ecu && it.bytes.take(2) == listOf(0x41, pid.code) }?.let(pid::value)
}
