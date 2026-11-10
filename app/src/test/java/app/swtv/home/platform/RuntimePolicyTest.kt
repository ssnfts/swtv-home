package app.swtv.home.platform

import org.junit.Assert.*
import org.junit.Test

class RuntimePolicyTest {
    @Test fun effectsRespectAllThreeSwitches() {
        assertTrue(RuntimePolicy.effects(false, false, true))
        assertFalse(RuntimePolicy.effects(false, false, false))
        assertFalse(RuntimePolicy.effects(true, false, true))
        assertFalse(RuntimePolicy.effects(false, true, true))
    }

    @Test fun cacheAndFailureCooldownPreventStorm() {
        assertFalse(RuntimePolicy.shouldFetch(true, "Sydney", 1000, 999))
        assertTrue(RuntimePolicy.shouldFetch(true, "Sydney", 1801000, 999))
        assertFalse(RuntimePolicy.shouldFetch(false, "Sydney", 9999999, 0))
        assertFalse(RuntimePolicy.shouldFetch(true, " ", 9999999, 0))
    }

    @Test fun onlyFixedHttpsServicesAllowed() {
        assertTrue(RuntimePolicy.allowedUrl("https://api.open-meteo.com/v1/forecast"))
        assertFalse(RuntimePolicy.allowedUrl("http://api.open-meteo.com"))
        assertFalse(RuntimePolicy.allowedUrl("https://api.open-meteo.com.evil.test"))
    }

    @Test fun focusRestoresOnlyExistingKey() {
        assertEquals("b", RuntimePolicy.focus("b", listOf("a", "b")))
        assertEquals("a", RuntimePolicy.focus("gone", listOf("a", "b")))
    }
}
