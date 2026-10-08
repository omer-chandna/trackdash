package dev.trackdash.core.export

/** RFC 4180 fields; textual spreadsheet formulas are neutralized on export. */
object TelemetryCsv {
    val header = row("session_id", "kind", "elapsed_ns", "wall_ms", "source", "sensor", "value", "unit", "status", "latitude", "longitude", "accuracy_m", "detail")
    fun row(vararg fields: Any?): String = fields.joinToString(",") { field ->
        var text = field?.toString() ?: ""
        if (field is String && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) text = "'$text"
        "\"${text.replace("\"", "\"\"")}\""
    } + "\r\n"
}
