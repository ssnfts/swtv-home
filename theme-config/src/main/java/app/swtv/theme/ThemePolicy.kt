package app.swtv.theme

import java.time.Duration
import java.time.ZonedDateTime

object ThemePolicy {
    fun effects(reduced: Boolean, lowRam: Boolean, system: Boolean) = !reduced && !lowRam && system

    /** Device-local daytime is 06:00 inclusive through 20:00 exclusive. */
    fun lightTheme(theme: String, localHour: Int): Boolean = when (theme) {
        "light" -> true
        "dark" -> false
        else -> localHour in 6 until 20
    }

    fun nextBoundaryDelay(now: ZonedDateTime): Long {
        val date = now.toLocalDate()
        val next = when {
            now.hour < 6 -> date.atTime(6, 0)
            now.hour < 20 -> date.atTime(20, 0)
            else -> date.plusDays(1).atTime(6, 0)
        }.atZone(now.zone)
        return Duration.between(now, next).toMillis().coerceAtLeast(1L)
    }
}
