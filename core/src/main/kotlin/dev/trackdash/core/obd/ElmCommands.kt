package dev.trackdash.core.obd

object ElmCommands {
    fun requireReadOnly(command: String) {
        require(command in setOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0", "ATDP", "ATI") ||
            command.matches(Regex("01[0-9A-F]{2}")) || command == "0902") { "Read-only diagnostic commands only" }
    }
}

/** Shared prompt framing for fragmented socket and GATT reads. */
class ElmResponseBuffer(private val maximum: Int = 32_768) {
    private val buffer = StringBuilder()
    private var finished = false
    fun accept(bytes: ByteArray): String? {
        check(!finished) { "Response already complete" }
        bytes.forEach { byte ->
            val character = (byte.toInt() and 255).toChar()
            if (!finished) {
                if (character == '>') finished = true
                else { check(buffer.length < maximum) { "Adapter response too large" }; buffer.append(character) }
            } else require(character.isWhitespace()) { "Unexpected data after response prompt" }
        }
        return if (finished) buffer.toString() else null
    }
}
