package app.swtv.home.platform

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
    @Volatile private var generation = 0
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
        val token = ++generation
        if (!enabled) {
            connection?.disconnect()
            done("Weather off · Enable in Settings")
            return
        }
        val state = snapshot()
        val cached = state.displayFor(city)
        val now = System.currentTimeMillis()
        if (!RuntimePolicy.shouldFetch(enabled, city, now, state.attemptFor(city))) {
            done(cached ?: "Weather unavailable · retry after 30 minutes")
            return
        }
        persist(state.beginAttempt(city, now))
        done(cached?.plus(" · refreshing") ?: "Loading weather…")
        executor.execute {
            val display = try { fetch(city, token) } catch (_: Exception) {
                cached?.plus(" · stale / unavailable") ?: "Weather unavailable · Open-Meteo"
            }
            handler.post {
                if (token == generation && snapshot().city == city) {
                    persist(snapshot().complete(city, display))
                    done(display)
                }
            }
        }
    }

    private fun fetch(city: String, token: Int): String {
        check(token == generation)
        val query = URLEncoder.encode(city, "UTF-8")
        val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=$query")
            .optJSONArray("results")?.optJSONObject(0) ?: error("City not found")
        val latitude = geo.getDouble("latitude")
        val longitude = geo.getDouble("longitude")
        check(token == generation)
        val forecast = get("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m")
        return "${geo.getString("name")} · ${forecast.getJSONObject("current").getDouble("temperature_2m")}°C · Open-Meteo"
    }

    private fun get(url: String): JSONObject {
        require(RuntimePolicy.allowedUrl(url))
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
        generation++
        connection?.disconnect()
    }

    fun close() {
        cancel()
        executor.shutdownNow()
    }
}
