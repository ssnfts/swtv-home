package app.swtv.theme

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ThemePolicyTest {
    @Test fun animationsRespectUserPreferenceAndLowMemory() {
        assertTrue(ThemePolicy.effects(false,false,true))
        assertFalse(ThemePolicy.effects(true,false,true))
        assertFalse(ThemePolicy.effects(false,true,true))
        assertFalse(ThemePolicy.effects(true,true,true))
    }

    @Test fun effectsRespectAllThreeSwitches() {
        assertTrue(ThemePolicy.effects(false, false, true))
        assertFalse(ThemePolicy.effects(false, false, false))
        assertFalse(ThemePolicy.effects(true, false, true))
        assertFalse(ThemePolicy.effects(false, true, true))
    }

    @Test fun autoThemeUsesLocalDayAndHonorsExplicitOverrides() {
        assertTrue(ThemePolicy.lightTheme("auto", 6))
        assertTrue(ThemePolicy.lightTheme("auto", 19))
        assertFalse(ThemePolicy.lightTheme("auto", 20))
        assertFalse(ThemePolicy.lightTheme("auto", 5))
        assertTrue(ThemePolicy.lightTheme("light", 23))
        assertFalse(ThemePolicy.lightTheme("dark", 12))
    }

    @Test fun nextBoundaryIsStrictlyFutureIncludingAtBoundary() {
        val morning = ZonedDateTime.parse("2026-11-10T06:00:00+11:00[Australia/Sydney]")
        assertEquals(14 * 60 * 60 * 1000L, ThemePolicy.nextBoundaryDelay(morning))
        val evening = morning.withHour(20)
        assertEquals(10 * 60 * 60 * 1000L, ThemePolicy.nextBoundaryDelay(evening))
        assertEquals(1000L, ThemePolicy.nextBoundaryDelay(morning.withHour(19).withMinute(59).withSecond(59)))
    }
}
