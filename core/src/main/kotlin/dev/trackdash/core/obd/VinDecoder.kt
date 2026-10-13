package dev.trackdash.core.obd

/** ISO-TP VIN replies with ATH1, grouped by ECU and strictly sequenced. */
object VinDecoder {
    private data class Message(val expected: Int, val bytes: MutableList<Int>, var sequence: Int = 1)
    fun decode(response: String): Map<String, String> {
        val messages = mutableMapOf<String, Message>()
        response.uppercase().split('\r', '\n', '>').forEach { line ->
            val raw = line.filterNot(Char::isWhitespace)
            if (raw.isBlank() || raw in setOf("0902", "NODATA", "?")) return@forEach
            require(raw.all { it in "0123456789ABCDEF" }) { "Invalid VIN response" }
            val headerLength = when { raw.startsWith("18") -> 8; raw.length >= 3 && raw.take(3).toInt(16) in 0x7e8..0x7ef -> 3; else -> error("VIN framing is not supported") }
            val ecu = raw.take(headerLength)
            val payload = raw.drop(headerLength)
            require(payload.length % 2 == 0 && payload.length >= 4)
            val bytes = payload.chunked(2).map { it.toInt(16) }
            when (bytes[0] shr 4) {
                1 -> {
                    val size = ((bytes[0] and 15) shl 8) + bytes[1]
                    require(size == 20 && ecu !in messages) { "Invalid VIN length or duplicate first frame" }
                    messages[ecu] = Message(size, bytes.drop(2).toMutableList())
                }
                2 -> {
                    val message = requireNotNull(messages[ecu]) { "VIN continuation without first frame" }
                    require(bytes[0] and 15 == message.sequence && message.bytes.size < message.expected) { "VIN sequence mismatch" }
                    message.bytes.addAll(bytes.drop(1)); message.sequence = (message.sequence + 1) % 16
                }
                else -> error("Unsupported VIN frame")
            }
        }
        return messages.mapValues { (_, message) ->
            require(message.bytes.size >= message.expected && message.bytes.take(3) == listOf(0x49, 0x02, 0x01)) { "Truncated or incorrect VIN response" }
            val vin = message.bytes.take(message.expected).drop(3).map(Int::toChar).joinToString("")
            require(vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) { "Invalid VIN characters" }
            vin
        }
    }
}
