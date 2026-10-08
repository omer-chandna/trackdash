package dev.trackdash.core

import dev.trackdash.core.export.TelemetryCsv
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryCsvTest {
    @Test fun quotesNewlinesMissingAndNumericNegativesArePreserved() {
        assertEquals("\"a,b\",\"a\"\"b\",\"line\nnext\",\"\",\"-20\"\r\n", TelemetryCsv.row("a,b", "a\"b", "line\nnext", null, -20))
    }
    @Test fun spreadsheetFormulasAreText() { assertEquals("\"'=1+1\",\"' @SUM(A1)\"\r\n", TelemetryCsv.row("=1+1", " @SUM(A1)")) }
}
