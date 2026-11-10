package app.swtv.launcher.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.swtv.discovery.AppEntry
import app.swtv.discovery.CatalogPicker
import app.swtv.discovery.CatalogRules
import app.swtv.discovery.DockPolicy
import app.swtv.launcher.runtime.Icons
import app.swtv.launcher.runtime.Wallpaper
import app.swtv.launcher.runtime.Weather
import app.swtv.theme.LauncherConfig

/** App-owned service actions, with no dependency on the application implementation. */
data class HomeScreenActions(val about: () -> Unit, val openTvHub: () -> Unit)

/** Full reusable home renderer. Its owner controls lifecycle and shared bounded workers. */
class HomeScreen(
    private val host: LauncherHost,
    private val dockApps: List<AppEntry>,
    private val icons: Icons,
    private val wallpaper: Wallpaper,
    private val weather: Weather,
    private val actions: HomeScreenActions
) {
    private val activity get() = host.activity
    private val config get() = host.config
    private val discovery get() = host.discovery
    private val apps get() = host.apps
    private val inputs get() = host.inputs
    private val dockPackages get() = host.dockPackages
    private val ui = HomeUi(activity, config)
    private var clock: TextView? = null
    private var weatherStatus: TextView? = null
    private var active = true

    fun render(savedFocus: String?) {
        val surface = installSurface(ui)
        activity.setContentView(surface)
        buildStatus(ui)
        buildToolbar(ui)
        buildWatchShortcuts(ui)
        buildModules(ui)
        buildDock(ui)
        buildApps(ui)
        ui.root.addView(ui.text("Hold OK on an app to pin, unpin or hide. Hold OK on a custom module to edit.", 13f))
        ui.root.addView(ui.text("Inputs: connected/standby passthrough only. For tuner channels, use your TV app.", 13f))
        ui.finish(savedFocus)
    }

    fun focusedKey(): String? = ui.focusedKey()

    /** Reject a late wallpaper result after the owner replaces this screen. */
    fun detach() { active = false }

    private fun save(config: LauncherConfig) = host.save(config)
    private fun notice(title: String, message: String = title) = host.notice(title, message)

    private fun installSurface(ui: HomeUi): FrameLayout {
        val frame = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (ui.light) intArrayOf(0xFFF0F6FC.toInt(), 0xFFD9E7F1.toInt())
                else intArrayOf(0xFF080F22.toInt(), 0xFF1C2948.toInt()))
        }
        val scroll = ScrollView(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(ui.root)
        }
        frame.addView(scroll)
        wallpaper.load(config.wallpaperUri) { bitmap ->
            if (active && bitmap != null) frame.background = BitmapDrawable(activity.resources, bitmap)
        }
        return frame
    }

    private fun buildStatus(ui: HomeUi) {
        clock = ui.text("", 28f).also { ui.root.addView(it) }
        weatherStatus = ui.text("", 14f).also { ui.root.addView(it) }
    }

    fun updateClock() {
        val flags = DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR
        clock?.text = DateUtils.formatDateTime(activity, System.currentTimeMillis(), flags)
    }

    fun refreshWeather() {
        val status = weatherStatus ?: return
        weather.refresh(config.weatherEnabled, config.weatherCity) { status.text = it }
    }

    private fun buildToolbar(ui: HomeUi) {
        ui.row(listOf(
            ui.tile("settings", "Settings", { SettingsController(host).show() }),
            ui.tile("system", "System Settings", {
                if (!discovery.launch(Intent(Settings.ACTION_SETTINGS))) notice("System Settings unavailable")
            }),
            ui.tile("about", "About", { actions.about() }),
            ui.tile("diagnostics", "Diagnostics", { host.showDiagnostics() })
        ))
    }

    private fun buildWatchShortcuts(ui: HomeUi) {
        ui.heading("WATCH & CONNECT")
        ui.row(listOf(
            ui.tile("owntv", "OwnTV", {
                if (!discovery.launchPackage(config.iptvPackage)) missingOwnTv()
            }),
            ui.tile("tvhub", "TV Hub", { actions.openTvHub() }),
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
            }, { SettingsController(host).moduleMenu(index) })
        })
    }

    private fun appTile(ui: HomeUi, app: AppEntry, section: String): View =
        ui.tile("$section:${app.packageName}", app.label, { launch(app) }, { appMenu(app) }).also {
            styleAppTile(it as Button)
            icons.bind(it, app.packageName)
        }

    private fun styleAppTile(button: Button) {
        button.minHeight = (104 * activity.resources.displayMetrics.density).toInt()
        button.maxLines = 2
        button.ellipsize = TextUtils.TruncateAt.END
    }

    private fun buildDock(ui: HomeUi) {
        ui.heading("DOCK · Hold OK for pin / hide")
        val pins = dockPackages
        ui.row((0..5).map { slot ->
            val pkg = pins.getOrNull(slot)
            if (pkg == null) {
                ui.tile("slot:$slot", "+ Slot ${slot + 1}", { SettingsController(host).dockSlot(slot) })
                    .also { styleAppTile(it as Button) }
            } else {
                val app = dockApps.firstOrNull { it.packageName == pkg }
                if (app != null) appTile(ui, app, "dock")
                else ui.tile("dock:$pkg", pkg, {
                    if (!discovery.launchPackage(pkg)) notice("App unavailable")
                }, { appMenu(AppEntry(pkg, "", pkg)) }).also { styleAppTile(it as Button) }
            }
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
        val pins = dockPackages
        val pinned = app.packageName in pins
        AlertDialog.Builder(activity).setTitle(app.label)
            .setItems(arrayOf(if (pinned) "Unpin from dock" else "Pin to dock", "Hide app")) { _, which ->
                if (which == 0) {
                    val next = if (pinned) pins - app.packageName else DockPolicy.pin(pins, app.packageName)
                    if (next == null) notice("Dock full", "All six slots are occupied. Open Settings → Dock slots & order to choose a slot to replace or swap.")
                    else save(config.copy(dock = next))
                } else save(config.copy(hidden = config.hidden + app.packageName, dock = pins - app.packageName))
            }.show()
    }

    fun chooseInput() {
        if (inputs.isEmpty()) {
            notice("Inputs unavailable", "No connected or standby passthrough inputs are available. Tuner inputs are unsupported here; use your TV app for tuner channels.")
            return
        }
        val picker = CatalogPicker(inputs) { it.id }
        AlertDialog.Builder(activity).setTitle("Inputs")
            .setItems(picker.choices.map { it.label }.toTypedArray()) { _, index ->
                val selected = picker.resolve(index, discovery.inputs())
                if (selected == null || !discovery.launchInput(selected.id)) notice("Input unavailable")
            }.show()
    }

    fun missingOwnTv() {
        AlertDialog.Builder(activity).setTitle("OwnTV unavailable")
            .setMessage("${config.iptvPackage} is not installed or cannot launch. Open the project releases in your browser or choose an installed fork. No APK is downloaded automatically.")
            .setPositiveButton("Project releases") { _, _ ->
                if (!discovery.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Thedurancode/owntv/releases")))) {
                    notice("No browser available")
                }
            }.setNeutralButton("Choose installed app") { _, _ -> SettingsController(host).iptv() }
            .setNegativeButton("Cancel", null).show()
    }
}
