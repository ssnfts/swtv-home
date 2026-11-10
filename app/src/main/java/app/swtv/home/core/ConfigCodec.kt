package app.swtv.home.core

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, defensive persistence. Unknown schemas are never interpreted as current data. */
object ConfigCodec {
    private val themes = setOf("auto", "light", "dark")
    private val accents = setOf("cyan", "violet", "blue", "green", "amber", "rose")

    fun encode(config: LauncherConfig): String = document(decode(document(config).toString())).toString()

    fun decode(text: String): LauncherConfig {
        val defaults = LauncherConfig()
        val json = try { JSONObject(text) } catch (_: Exception) { return defaults }
        if (json.opt("schemaVersion") != 1) return defaults
        fun string(key: String, default: String): String = (json.opt(key) as? String) ?: default
        fun boolean(key: String, default: Boolean): Boolean = (json.opt(key) as? Boolean) ?: default
        fun packages(key: String): List<String> = values(json.optJSONArray(key))
            .filterIsInstance<String>().filter { CatalogRules.validPackage(it) }.distinct()
        val modules = values(json.optJSONArray("modules")).mapNotNull { raw ->
            val item = raw as? JSONObject ?: return@mapNotNull null
            val label = item.opt("label") as? String ?: return@mapNotNull null
            val target = item.opt("target") as? String ?: return@mapNotNull null
            val kind = item.opt("kind") as? String ?: return@mapNotNull null
            if (label.isBlank() || label.length > 100 || !validTarget(kind, target)) null
            else ModuleTile(label, target, kind)
        }.distinctBy { it.kind to it.target }.take(8)
        return LauncherConfig(
            theme = string("theme", defaults.theme).takeIf { it in themes } ?: defaults.theme,
            accent = string("accent", defaults.accent).takeIf { it in accents } ?: defaults.accent,
            columns = (json.opt("columns") as? Int)?.coerceIn(3, 8) ?: defaults.columns,
            reducedMotion = boolean("reducedMotion", defaults.reducedMotion),
            showPhoneApps = boolean("showPhoneApps", defaults.showPhoneApps),
            dock = packages("dock").take(6), hidden = packages("hidden").toSet(), modules = modules,
            wallpaperUri = string("wallpaperUri", defaults.wallpaperUri),
            weatherCity = string("weatherCity", defaults.weatherCity),
            weatherEnabled = boolean("weatherEnabled", defaults.weatherEnabled),
            iptvPackage = string("iptvPackage", defaults.iptvPackage)
                .takeIf { CatalogRules.validPackage(it) } ?: defaults.iptvPackage
        )
    }

    // Device membership is checked by the settings picker against installed apps/discovered inputs.
    private fun validTarget(kind: String, target: String): Boolean = when (kind) {
        "app" -> CatalogRules.validPackage(target)
        "input" -> target.length <= 512 && target.matches(
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+/[A-Za-z0-9_.$]+(?:/[A-Za-z0-9_.:-]+)*")
        )
        else -> false
    }

    private fun values(array: JSONArray?): List<Any?> =
        if (array == null) emptyList() else (0 until array.length()).map { array.opt(it) }

    private fun document(config: LauncherConfig): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("theme", config.theme); put("accent", config.accent); put("columns", config.columns)
        put("reducedMotion", config.reducedMotion); put("showPhoneApps", config.showPhoneApps)
        put("dock", JSONArray(config.dock)); put("hidden", JSONArray(config.hidden.sorted()))
        put("modules", JSONArray().apply {
            config.modules.forEach { tile -> put(JSONObject().apply {
                put("label", tile.label); put("target", tile.target); put("kind", tile.kind)
            }) }
        })
        put("wallpaperUri", config.wallpaperUri); put("weatherCity", config.weatherCity)
        put("weatherEnabled", config.weatherEnabled); put("iptvPackage", config.iptvPackage)
    }
}
