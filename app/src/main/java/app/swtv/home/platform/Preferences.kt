package app.swtv.home.platform

import android.content.Context
import app.swtv.home.core.ConfigCodec
import app.swtv.home.core.LauncherConfig

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun load(): LauncherConfig = ConfigCodec.decode(prefs.getString("config", "{}") ?: "{}")

    fun save(config: LauncherConfig): LauncherConfig {
        val text = ConfigCodec.encode(config)
        prefs.edit().putString("config", text).apply()
        return ConfigCodec.decode(text)
    }
}
