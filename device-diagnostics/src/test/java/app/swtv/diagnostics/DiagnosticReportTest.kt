package app.swtv.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun missingTextIsUnavailable() {
        listOf(null, "", "  ", "unknown", " UNKNOWN ").forEach {
            assertEquals("Unavailable", DiagnosticReport.text(it))
        }
        assertEquals("Model A", DiagnosticReport.text(" Model A "))
    }

    @Test fun missingOrInvalidSizesAreUnavailable() {
        assertEquals("Unavailable", DiagnosticReport.bytes(null))
        assertEquals("Unavailable", DiagnosticReport.bytes(-1))
        assertEquals("0 MiB", DiagnosticReport.bytes(0))
        assertEquals("1536 MiB", DiagnosticReport.bytes(1610612736L))
    }

    @Test fun reportKeepsLabelsAndMarksMissingReadings() {
        assertEquals("Model: Unavailable\nApp storage available: 10 MiB", DiagnosticReport.format(
            DiagnosticSnapshot(linkedMapOf("Model" to null, "App storage available" to "10 MiB"))))
    }
}
