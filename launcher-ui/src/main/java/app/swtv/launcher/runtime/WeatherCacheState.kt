package app.swtv.launcher.runtime

/** Identity, request cooldown and display are persisted as one matching snapshot. */
data class WeatherCacheState(
    val city: String = "",
    val attempt: Long = 0,
    val display: String? = null
) {
    fun displayFor(requestCity: String): String? = display.takeIf { city == requestCity }
    fun attemptFor(requestCity: String): Long = if (city == requestCity) attempt else 0L
    fun beginAttempt(requestCity: String, now: Long) =
        WeatherCacheState(requestCity, now, displayFor(requestCity))
    fun complete(requestCity: String, result: String) =
        if (city == requestCity) copy(display = result) else this
}
