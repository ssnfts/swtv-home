package app.swtv.launcher.runtime

import java.net.URI

object WidgetPolicy {
    fun shouldFetch(enabled: Boolean, city: String, now: Long, last: Long) =
        enabled && city.isNotBlank() && (last == 0L || now - last >= 1800000L)

    fun allowedUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host in setOf("api.open-meteo.com", "geocoding-api.open-meteo.com") &&
            uri.userInfo == null
    } catch (_: Exception) { false }
}
