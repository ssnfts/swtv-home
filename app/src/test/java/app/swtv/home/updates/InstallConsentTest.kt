package app.swtv.home.updates

import org.junit.Assert.*
import org.junit.Test

class InstallConsentTest {
    @Test fun installationRequiresEveryConsentAndReadinessCondition() {
        val gate = InstallConsent()
        assertFalse(gate.canStart(false, true))
        assertFalse(gate.canStart(true, false))
        assertTrue(gate.canStart(true, true))
        gate.busy = true
        assertFalse(gate.canStart(true, true))
        gate.busy = false
        gate.foreground = false
        assertFalse(gate.canStart(true, true))
        gate.foreground = true
        gate.open = false
        assertFalse(gate.canStart(true, true))
    }
    @Test fun resultsRequireCurrentSessionAndUnpredictableTokenWhileFlowIsOpen() {
        val gate = InstallConsent()
        assertFalse(gate.acceptsResult(7, null))
        gate.sessionId = 7
        gate.nonce = "random-token"
        assertFalse(gate.canStart(true, true))
        assertFalse(gate.acceptsResult(8, "random-token"))
        assertFalse(gate.acceptsResult(7, "old-token"))
        assertTrue(gate.acceptsResult(7, "random-token"))
        gate.foreground = false
        assertTrue(gate.acceptsResult(7, "random-token")) // Preserve the OS confirmation on pause.
        gate.open = false
        assertFalse(gate.acceptsResult(7, "random-token"))
    }
}
