package app.swtv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.swtv.discovery.*
import app.swtv.theme.LauncherConfig
import app.swtv.theme.Preferences
import app.swtv.theme.ThemePolicy
import app.swtv.launcher.runtime.*
import app.swtv.launcher.ui.LauncherHost
import app.swtv.launcher.ui.HomeScreen
import app.swtv.launcher.ui.HomeScreenActions
import java.time.ZonedDateTime

class MainActivity : Activity(), LauncherHost {
    override val activity: Activity get() = this
    override lateinit var config: LauncherConfig
        private set
    private lateinit var prefs: Preferences
    override val discovery by lazy { Discovery(this) }
    override var apps = emptyList<AppEntry>()
        private set
    private var dockApps = emptyList<AppEntry>()
    private var dockCatalogReady = false
    override val dockPackages: List<String> get() = DockPolicy.canonical(
        config.dock, if (dockCatalogReady) dockApps.map { it.packageName }.toSet() else null
    )
    override var inputs = emptyList<InputEntry>()
        private set
    private lateinit var weather: Weather
    private lateinit var wallpaper: Wallpaper
    private val icons by lazy { Icons(this) }
    private var home: HomeScreen? = null
    private var savedFocus: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var packageReceiverRegistered = false
    private var timeReceiverRegistered = false

    private val refreshAfterPackageChange = Runnable { if (foreground) refresh() }
    private val themeBoundary = Runnable { if (foreground) refresh() }
    private val minuteTick = object : Runnable {
        override fun run() {
            if (!foreground) return
            home?.updateClock()
            home?.refreshWeather()
            // Align to device wall-clock minutes without continuously rewriting the same clock text.
            handler.postDelayed(this, 60000L - System.currentTimeMillis() % 60000L)
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!foreground) return
            icons.invalidate()
            icons.beginScreen()
            handler.removeCallbacks(refreshAfterPackageChange)
            handler.postDelayed(refreshAfterPackageChange, 150L)
        }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (foreground) refresh() // Re-evaluate local time, date, zone and next boundary.
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = Preferences(this)
        config = prefs.load()
        weather = Weather(this)
        wallpaper = Wallpaper(this)
        savedFocus = state?.getString("focus")
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        registerForegroundReceivers()
        refresh()
    }

    override fun onPause() {
        savedFocus = home?.focusedKey() ?: savedFocus
        foreground = false
        handler.removeCallbacks(minuteTick)
        handler.removeCallbacks(themeBoundary)
        handler.removeCallbacks(refreshAfterPackageChange)
        unregisterForegroundReceivers()
        icons.beginScreen()
        weather.cancel()
        wallpaper.cancelPending()
        super.onPause()
    }

    override fun onDestroy() {
        icons.close()
        wallpaper.close()
        weather.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("focus", home?.focusedKey())
        super.onSaveInstanceState(out)
    }

    private fun registerForegroundReceivers() {
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (!packageReceiverRegistered) {
            registerForeground(packageReceiver, packages)
            packageReceiverRegistered = true
        }
        if (!timeReceiverRegistered) {
            registerForeground(timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            })
            timeReceiverRegistered = true
        }
    }

    private fun registerForeground(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    private fun unregisterForegroundReceivers() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageReceiver)
            packageReceiverRegistered = false
        }
        if (timeReceiverRegistered) {
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
    }

    override fun save(config: LauncherConfig) {
        this.config = prefs.save(config)
        refresh()
    }

    fun refresh() {
        savedFocus = home?.focusedKey() ?: savedFocus
        home?.detach()
        icons.beginScreen()
        refreshCatalog()
        inputs = discovery.inputs()
        val screen = HomeScreen(this, dockApps, icons, wallpaper, weather,
            HomeScreenActions(about = { about() }, openTvHub = { openTvHub() }))
        home = screen
        screen.render(savedFocus)
        handler.removeCallbacks(minuteTick)
        minuteTick.run()
        scheduleThemeBoundary()
    }

    private fun refreshCatalog() {
        val snapshot = discovery.appSnapshot(true) ?: return
        dockApps = snapshot
        dockCatalogReady = true
        // Read-only refresh never rewrites pins: updates can temporarily remove an app.
        // Every dock edit commits the same effective sequence used to draw these slots.
        // The phone-app setting filters the grid only: existing phone-app pins remain usable.
        val grid = if (config.showPhoneApps) snapshot else discovery.appSnapshot(false)
        if (grid != null) apps = grid
    }

    private fun scheduleThemeBoundary() {
        handler.removeCallbacks(themeBoundary)
        if (foreground && config.theme == "auto") {
            handler.postDelayed(themeBoundary, ThemePolicy.nextBoundaryDelay(ZonedDateTime.now()))
        }
    }

    override fun notice(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun openTvHub() {
        if (!discovery.launchPackage("app.swtv.tvhub")) {
            notice("TV Hub unavailable", "Reserved for app.swtv.tvhub. Install this separate app when available.")
        }
    }

    private fun about() {
        notice("SWTV Home · ${BuildConfig.VERSION_NAME}", "Original native TV launcher. OwnTV is a separate application. Weather: Open-Meteo, enabled only by you. Update release feed is not configured until Phase 4.")
    }

    override fun pickWallpaper() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 41)
        } catch (_: Exception) {
            notice("Image picker unavailable", "Use the built-in time-based gradient on this device.")
        }
    }

    @Deprecated("Platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    save(config.copy(wallpaperUri = uri.toString()))
                } catch (_: Exception) {
                    notice("Image unavailable", "The image provider did not grant persistent access.")
                }
            }
        }
    }
}
