package app.swtv.home.platform

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ReviewRegressionTest {
    @Test fun autoThemeUsesLocalDayAndHonorsExplicitOverrides() {
        assertTrue(RuntimePolicy.lightTheme("auto", 6))
        assertTrue(RuntimePolicy.lightTheme("auto", 19))
        assertFalse(RuntimePolicy.lightTheme("auto", 20))
        assertFalse(RuntimePolicy.lightTheme("auto", 5))
        assertTrue(RuntimePolicy.lightTheme("light", 23))
        assertFalse(RuntimePolicy.lightTheme("dark", 12))
    }

    @Test fun nextBoundaryIsStrictlyFutureIncludingAtBoundary() {
        val morning = ZonedDateTime.parse("2026-11-10T06:00:00+11:00[Australia/Sydney]")
        assertEquals(14 * 60 * 60 * 1000L, RuntimePolicy.nextBoundaryDelay(morning))
        val evening = morning.withHour(20)
        assertEquals(10 * 60 * 60 * 1000L, RuntimePolicy.nextBoundaryDelay(evening))
        assertEquals(1000L, RuntimePolicy.nextBoundaryDelay(morning.withHour(19).withMinute(59).withSecond(59)))
    }

    @Test fun citySwitchThenCancelNeverBorrowsPreviousDisplay() {
        val sydney = WeatherCacheState("Sydney", 100, "Sydney · 20°C")
        val perthAttempt = sydney.beginAttempt("Perth", 200)
        // Cancellation means no result commit; a reload must carry matching identity only.
        assertNull(perthAttempt.displayFor("Perth"))
        assertEquals(200L, perthAttempt.attemptFor("Perth"))
        assertNull(perthAttempt.displayFor("Sydney"))
        assertFalse(RuntimePolicy.shouldFetch(true, "Perth", 201, perthAttempt.attemptFor("Perth")))
        assertEquals("Perth · 23°C", perthAttempt.complete("Perth", "Perth · 23°C").displayFor("Perth"))
        assertEquals(perthAttempt, perthAttempt.complete("Sydney", "obsolete"))
    }

    @Test fun iconWorkIsBoundedDeduplicatedAndGenerationSafe() {
        val work = IconWorkPolicy(49)
        val first = work.beginGeneration()
        val tickets = (0 until 120).mapNotNull { work.reserve("pkg.$it", first) }
        assertEquals(49, tickets.size)
        assertEquals(49, work.pendingCount())
        assertNull(work.reserve("pkg.0", first))
        repeat(100) {
            val current = work.beginGeneration()
            assertEquals(0, work.pendingCount())
            assertFalse(work.isCurrent(tickets.first()))
            assertNull(work.reserve("old", first))
            val replacement = work.reserve("pkg.0", current)!!
            work.finish(tickets.first())
            assertTrue(work.isCurrent(replacement))
            assertEquals(1, work.pendingCount())
        }
    }
}
