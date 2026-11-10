package app.swtv.launcher.runtime

import org.junit.Assert.*
import org.junit.Test

class WeatherCacheStateTest {
    @Test fun citySwitchThenCancelNeverBorrowsPreviousDisplay() {
        val sydney = WeatherCacheState("Sydney", 100, "Sydney · 20°C")
        val perthAttempt = sydney.beginAttempt("Perth", 200)
        // Cancellation means no result commit; a reload must carry matching identity only.
        assertNull(perthAttempt.displayFor("Perth"))
        assertEquals(200L, perthAttempt.attemptFor("Perth"))
        assertNull(perthAttempt.displayFor("Sydney"))
        assertFalse(WidgetPolicy.shouldFetch(true, "Perth", 201, perthAttempt.attemptFor("Perth")))
        assertEquals("Perth · 23°C", perthAttempt.complete("Perth", "Perth · 23°C").displayFor("Perth"))
        assertEquals(perthAttempt, perthAttempt.complete("Sydney", "obsolete"))
    }
}
