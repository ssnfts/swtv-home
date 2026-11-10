package app.swtv.launcher.ui

import android.app.Activity
import app.swtv.discovery.AppEntry
import app.swtv.discovery.Discovery
import app.swtv.discovery.InputEntry
import app.swtv.theme.LauncherConfig

/** Activity context and launcher state/callbacks needed by the settings UI. */
interface LauncherHost {
    val activity: Activity
    val config: LauncherConfig
    val apps: List<AppEntry>
    val inputs: List<InputEntry>
    val dockPackages: List<String>
    val discovery: Discovery
    fun save(config: LauncherConfig)
    fun notice(title: String, message: String = title)
    fun pickWallpaper()
    fun showDiagnostics()
}
