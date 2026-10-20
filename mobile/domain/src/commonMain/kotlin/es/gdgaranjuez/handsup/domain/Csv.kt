package es.gdgaranjuez.handsup.domain

fun csvCell(value: Any?): String {
    var s = value?.toString().orEmpty()
    // Neutralise spreadsheet formula injection from attendee text.
    if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'$s"
    return if (s.any { it in "\",\n\r" }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

fun toCsv(header: List<String>, rows: List<List<Any?>>): String =
    (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvCell(it) } } + "\n"
