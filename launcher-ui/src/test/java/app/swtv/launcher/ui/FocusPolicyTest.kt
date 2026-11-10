package app.swtv.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class FocusPolicyTest {
    @Test fun focusRestoresOnlyExistingKey() {
        assertEquals("b", FocusPolicy.focus("b", listOf("a", "b")))
        assertEquals("a", FocusPolicy.focus("gone", listOf("a", "b")))
    }
}
