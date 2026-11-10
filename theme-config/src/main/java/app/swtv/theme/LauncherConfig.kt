package app.swtv.theme

data class ModuleTile(val label: String, val target: String, val kind: String)
data class LauncherConfig(
    val theme: String = "auto", val accent: String = "cyan", val columns: Int = 6,
    val reducedMotion: Boolean = false, val showPhoneApps: Boolean = true,
    val dock: List<String> = emptyList(), val hidden: Set<String> = emptySet(),
    val modules: List<ModuleTile> = emptyList(), val wallpaperUri: String = "",
    val weatherCity: String = "", val weatherEnabled: Boolean = false,
    val iptvPackage: String = "tv.own.owntv"
)
