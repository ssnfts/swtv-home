package app.swtv.launcher.runtime

import org.junit.Assert.*
import org.junit.Test

class WidgetPolicyTest {
    @Test fun cacheAndFailureCooldownPreventStorm() {
        assertFalse(WidgetPolicy.shouldFetch(true, "Sydney", 1000, 999))
        assertTrue(WidgetPolicy.shouldFetch(true, "Sydney", 1801000, 999))
        assertFalse(WidgetPolicy.shouldFetch(false, "Sydney", 9999999, 0))
        assertFalse(WidgetPolicy.shouldFetch(true, " ", 9999999, 0))
    }

    @Test fun onlyFixedHttpsServicesAllowed() {
        assertTrue(WidgetPolicy.allowedUrl("https://api.open-meteo.com/v1/forecast"))
        assertFalse(WidgetPolicy.allowedUrl("http://api.open-meteo.com"))
        assertFalse(WidgetPolicy.allowedUrl("https://api.open-meteo.com.evil.test"))
    }
}
