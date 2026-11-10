package app.swtv.theme

import android.content.Context

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun load(): LauncherConfig = ConfigCodec.decode(prefs.getString("config", "{}") ?: "{}")

    fun save(config: LauncherConfig): LauncherConfig {
        val text = ConfigCodec.encode(config)
        prefs.edit().putString("config", text).apply()
        return ConfigCodec.decode(text)
    }
}
