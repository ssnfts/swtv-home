package app.swtv.launcher.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class Weather(context: Context) {
    private val prefs = context.getSharedPreferences("weather-cache", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val requests = WeatherRequests()
    @Volatile private var connection: HttpURLConnection? = null

    private fun snapshot(): WeatherCacheState = if (prefs.getInt("schema", 0) != 2) WeatherCacheState() else WeatherCacheState(
        prefs.getString("city", "") ?: "", prefs.getLong("attempt", 0),
        prefs.getString("display", null)
    )

    private fun persist(state: WeatherCacheState) {
        // A single editor updates identity, cooldown and display; null clears an old city's display.
        prefs.edit().putInt("schema", 2).putString("city", state.city).putLong("attempt", state.attempt)
            .putString("display", state.display).apply()
    }

    fun refresh(enabled: Boolean, city: String, done: (String) -> Unit) {
        if (!enabled) {
            cancel()
            done("Weather off · Enable in Settings")
            return
        }
        if (requests.activeCity != null && requests.activeCity != city) cancel()
        val state = snapshot()
        val cached = state.displayFor(city)
        // A periodic check or screen rebuild subscribes to the existing request, preserving its token.
        if (requests.observe(city, done)) {
            done(cached?.plus(" · refreshing") ?: "Loading weather…")
            return
        }
        val now = System.currentTimeMillis()
        if (!WidgetPolicy.shouldFetch(enabled, city, now, state.attemptFor(city))) {
            done(cached ?: "Weather unavailable · retry after 30 minutes")
            return
        }
        val ticket = requests.begin(city, done)
        persist(state.beginAttempt(city, now))
        done(cached?.plus(" · refreshing") ?: "Loading weather…")
        executor.execute {
            val display = try { fetch(city, ticket) } catch (_: Exception) {
                cached?.plus(" · stale / unavailable") ?: "Weather unavailable · Open-Meteo"
            }
            handler.post {
                if (requests.isCurrent(ticket) && snapshot().city == city) {
                    persist(snapshot().complete(city, display))
                    requests.complete(ticket, display)
                }
            }
        }
    }

    private fun fetch(city: String, ticket: WeatherRequests.Ticket): String {
        check(requests.isCurrent(ticket))
        val query = URLEncoder.encode(city, "UTF-8")
        val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=$query")
            .optJSONArray("results")?.optJSONObject(0) ?: error("City not found")
        val latitude = geo.getDouble("latitude")
        val longitude = geo.getDouble("longitude")
        check(requests.isCurrent(ticket))
        val forecast = get("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m")
        return "${geo.getString("name")} · ${forecast.getJSONObject("current").getDouble("temperature_2m")}°C · Open-Meteo"
    }

    private fun get(url: String): JSONObject {
        require(WidgetPolicy.allowedUrl(url))
        val current = URL(url).openConnection() as HttpURLConnection
        connection = current
        try {
            current.connectTimeout = 6000
            current.readTimeout = 6000
            current.instanceFollowRedirects = false
            require(current.responseCode == 200)
            val bytes = current.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 131072)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            current.disconnect()
            connection = null
        }
    }

    fun cancel() {
        requests.cancel()
        connection?.disconnect()
    }

    fun close() {
        cancel()
        executor.shutdownNow()
    }
}
