package app.swtv.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    @Test fun completeProviderReportsUnconfiguredWithoutNetwork() {
        assertEquals(UpdateState.Unconfigured, UnconfiguredUpdateProvider().state())
    }

    @Test fun reportsSuppliedActualBuildIdentityAndReleaseState() {
        val report = UpdateReport.format(BuildIdentity("app.swtv.home", "0.2.0", 2L, "debug"),
            UnconfiguredUpdateProvider().state())
        assertTrue(report.contains("app.swtv.home"))
        assertTrue(report.contains("Version: 0.2.0 (2)"))
        assertTrue(report.contains("Build: debug"))
        assertTrue(report.contains("Release updates are not configured."))
        assertFalse(report.contains("Phase 4"))
    }

    @Test fun identityIsNotReplacedByHardCodedVersion() {
        val report = UpdateReport.format(BuildIdentity("other.id", "9.7", 41L, "release"), UpdateState.Unconfigured)
        assertTrue(report.contains("other.id"))
        assertTrue(report.contains("9.7 (41)"))
        assertTrue(report.contains("release"))
    }
}
