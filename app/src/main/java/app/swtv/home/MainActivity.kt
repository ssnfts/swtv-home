package app.swtv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.swtv.home.core.AppEntry
import app.swtv.home.core.CatalogRules
import app.swtv.home.core.LauncherConfig
import app.swtv.home.platform.*
import app.swtv.home.ui.HomeUi
import app.swtv.home.ui.SettingsController
import java.time.ZonedDateTime

class MainActivity : Activity() {
    lateinit var config: LauncherConfig
        private set
    private lateinit var prefs: Preferences
    val discovery by lazy { Discovery(this) }
    var apps = emptyList<AppEntry>()
        private set
    var inputs = emptyList<InputEntry>()
        private set
    private lateinit var weather: Weather
    private lateinit var wallpaper: Wallpaper
    private val icons by lazy { Icons(this) }
    private var home: HomeUi? = null
    private var savedFocus: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null
    private var weatherStatus: TextView? = null
    private var foreground = false
    private var packageReceiverRegistered = false
    private var timeReceiverRegistered = false

    private val refreshAfterPackageChange = Runnable { if (foreground) refresh() }
    private val themeBoundary = Runnable { if (foreground) refresh() }
    private val minuteTick = object : Runnable {
        override fun run() {
            if (!foreground) return
            updateClock()
            refreshWeather()
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

    fun save(next: LauncherConfig) {
        config = prefs.save(next)
        refresh()
    }

    fun refresh() {
        savedFocus = home?.focusedKey() ?: savedFocus
        icons.beginScreen()
        apps = discovery.apps(config.showPhoneApps)
        inputs = discovery.inputs()
        val ui = HomeUi(this, config)
        home = ui
        installSurface(ui)
        buildStatus(ui)
        buildToolbar(ui)
        buildWatchShortcuts(ui)
        buildModules(ui)
        buildDock(ui)
        buildApps(ui)
        ui.root.addView(ui.text("Hold OK on an app to pin, unpin or hide. Hold OK on a custom module to edit.", 13f))
        ui.finish(savedFocus)
        handler.removeCallbacks(minuteTick)
        minuteTick.run()
        scheduleThemeBoundary()
    }

    private fun installSurface(ui: HomeUi) {
        val frame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (ui.light) intArrayOf(0xFFF0F6FC.toInt(), 0xFFD9E7F1.toInt())
                else intArrayOf(0xFF080F22.toInt(), 0xFF1C2948.toInt()))
        }
        val scroll = ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(ui.root)
        }
        frame.addView(scroll)
        setContentView(frame)
        wallpaper.load(config.wallpaperUri) { bitmap ->
            if (home === ui && bitmap != null) frame.background = BitmapDrawable(resources, bitmap)
        }
    }

    private fun buildStatus(ui: HomeUi) {
        clock = ui.text("", 28f).also { ui.root.addView(it) }
        weatherStatus = ui.text("", 14f).also { ui.root.addView(it) }
    }

    private fun updateClock() {
        val flags = DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR
        clock?.text = DateUtils.formatDateTime(this, System.currentTimeMillis(), flags)
    }

    private fun refreshWeather() {
        val status = weatherStatus ?: return
        weather.refresh(config.weatherEnabled, config.weatherCity) { status.text = it }
    }

    private fun scheduleThemeBoundary() {
        handler.removeCallbacks(themeBoundary)
        if (foreground && config.theme == "auto") {
            handler.postDelayed(themeBoundary, RuntimePolicy.nextBoundaryDelay(ZonedDateTime.now()))
        }
    }

    private fun buildToolbar(ui: HomeUi) {
        ui.row(listOf(
            ui.tile("settings", "Settings", { SettingsController(this).show() }),
            ui.tile("system", "System Settings", {
                if (!discovery.launch(Intent(Settings.ACTION_SETTINGS))) notice("System Settings unavailable")
            }),
            ui.tile("about", "About", { about() })
        ))
    }

    private fun buildWatchShortcuts(ui: HomeUi) {
        ui.heading("WATCH & CONNECT")
        ui.row(listOf(
            ui.tile("owntv", "OwnTV", {
                if (!discovery.launchPackage(config.iptvPackage)) missingOwnTv()
            }),
            ui.tile("tvhub", "TV Hub", {
                if (!discovery.launchPackage("app.swtv.tvhub")) {
                    notice("TV Hub unavailable", "Reserved for app.swtv.tvhub. Install this separate app when available.")
                }
            }),
            ui.tile("inputs", "Inputs", { chooseInput() })
        ))
    }

    private fun buildModules(ui: HomeUi) {
        if (config.modules.isEmpty()) return
        ui.row(config.modules.mapIndexed { index, module ->
            ui.tile("module:${module.kind}:${module.target}", module.label, {
                val opened = if (module.kind == "app") discovery.launchPackage(module.target)
                    else discovery.launchInput(module.target)
                if (!opened) notice("Module unavailable")
            }, { SettingsController(this).moduleMenu(index) })
        })
    }

    private fun appTile(ui: HomeUi, app: AppEntry, section: String): View =
        ui.tile("$section:${app.packageName}", app.label, { launch(app) }, { appMenu(app) }).also {
            icons.bind(it as Button, app.packageName)
        }

    private fun buildDock(ui: HomeUi) {
        ui.heading("DOCK · Hold OK for pin / hide")
        val pins = CatalogRules.dock(apps, config.dock)
        ui.row((0..5).map { slot ->
            pins.getOrNull(slot)?.let { appTile(ui, it, "dock") }
                ?: ui.tile("slot:$slot", "+ Slot ${slot + 1}", { SettingsController(this).dockSlot(slot) })
        })
    }

    private fun buildApps(ui: HomeUi) {
        ui.heading("Apps")
        val visible = CatalogRules.visible(apps, config.hidden)
        if (visible.isEmpty()) {
            ui.root.addView(ui.text("No visible apps · Use Settings to show phone apps or unhide apps"))
        }
        visible.chunked(config.columns).forEach { row ->
            ui.row(row.map { appTile(ui, it, "app") })
        }
    }

    private fun launch(app: AppEntry) {
        if (!discovery.launchApp(app)) {
            notice("App unavailable", app.label + " could not be opened. Refresh or choose another app.")
        }
    }

    fun appMenu(app: AppEntry) {
        val pinned = app.packageName in config.dock
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf(if (pinned) "Unpin from dock" else "Pin to dock", "Hide app")) { _, which ->
                if (which == 0) {
                    val next = if (pinned) config.dock - app.packageName
                        else CatalogRules.movePin(config.dock, app.packageName, config.dock.size)
                    save(config.copy(dock = next))
                } else save(config.copy(hidden = config.hidden + app.packageName, dock = config.dock - app.packageName))
            }.show()
    }

    fun chooseInput() {
        if (inputs.isEmpty()) {
            notice("Inputs unavailable", "No TV inputs were reported by this device.")
            return
        }
        AlertDialog.Builder(this).setTitle("Inputs")
            .setItems(inputs.map { it.label }.toTypedArray()) { _, index ->
                if (!discovery.launchInput(inputs[index].id)) notice("Input unavailable")
            }.show()
    }

    fun missingOwnTv() {
        AlertDialog.Builder(this).setTitle("OwnTV unavailable")
            .setMessage("${config.iptvPackage} is not installed or cannot launch. Open the project releases in your browser or choose an installed fork. No APK is downloaded automatically.")
            .setPositiveButton("Project releases") { _, _ ->
                if (!discovery.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Thedurancode/owntv/releases")))) {
                    notice("No browser available")
                }
            }.setNeutralButton("Choose installed app") { _, _ -> SettingsController(this).iptv() }
            .setNegativeButton("Cancel", null).show()
    }

    fun notice(title: String, message: String = title) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun about() {
        notice("SWTV Home · 0.1.0", "Original native TV launcher. OwnTV is a separate application. Weather: Open-Meteo, enabled only by you. Update release feed is not configured until Phase 4.")
    }

    fun pickWallpaper() {
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
