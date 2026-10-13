package dev.trackdash.core

import dev.trackdash.core.obd.VinDecoder
import org.junit.Assert.*
import org.junit.Test

class VinDecoderTest {
    // Elm Electronics ELM327DSL, pages 44–46: documented example VIN, expanded with ATH1 PCI headers.
    private val fixture = "7E8 10 14 49 02 01 31 44 34\r7E8 21 47 50 30 30 52 35 35\r7E8 22 42 31 32 33 34 35 36"
    @Test fun documentedCanVinReassemblesWithoutMixingEcus() {
        val second = fixture.replace("7E8", "7E9")
        assertEquals(mapOf("7E8" to "1D4GP00R55B123456", "7E9" to "1D4GP00R55B123456"), VinDecoder.decode(fixture + "\r" + second))
    }
    @Test fun truncatedOutOfOrderAndInvalidVinsAreRejected() {
        listOf(fixture.substringBeforeLast('\r'), fixture.replace("7E8 21", "7E8 23"), fixture.replace("31 44 34", "31 49 34")).forEach {
            assertThrows(IllegalArgumentException::class.java) { VinDecoder.decode(it) }
        }
        assertTrue(VinDecoder.decode("NO DATA").isEmpty())
    }
}
