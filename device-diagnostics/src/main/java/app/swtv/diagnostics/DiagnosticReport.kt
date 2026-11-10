package app.swtv.diagnostics

data class DiagnosticSnapshot(val values: Map<String, String?>)

object DiagnosticReport {
    fun text(value: String?): String = value?.trim()
        ?.takeUnless { it.isEmpty() || it.equals("unknown", ignoreCase = true) } ?: "Unavailable"

    fun bytes(value: Long?): String = if (value == null || value < 0) "Unavailable"
        else "${value / (1024L * 1024L)} MiB"

    fun format(snapshot: DiagnosticSnapshot): String = snapshot.values.entries.joinToString("\n") {
        "${it.key}: ${text(it.value)}"
    }
}
