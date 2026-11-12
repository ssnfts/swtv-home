package app.swtv.launcher.ui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
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
    private val actions: HomeScreenActions,
    private val websites: List<WebTile> = emptyList(),
    private val webActions: WebTileActions? = null
) {
    private val activity get() = host.activity
    private val config get() = host.config
    private val discovery get() = host.discovery
    private val apps get() = host.apps
    private val inputs get() = host.inputs
    private val dockPackages get() = host.dockPackages
    private val ui = HomeUi(activity, config)
    private var header: HudHeader? = null
    private var active = true

    fun render(savedFocus: String?) {
        val surface = installSurface(ui)
        activity.setContentView(surface)
        buildStatus(ui)
        buildToolbar(ui)
        buildWatchShortcuts(ui)
        buildModules(ui)
        buildDock(ui)
        buildWebsites(ui)
        buildApps(ui)
        ui.root.addView(ui.text("Hold OK on apps for pin / hide. Hold OK on custom modules to edit.", 13f).apply {
            setTextColor(ui.palette.secondary)
            setBackgroundColor(ui.palette.background)
        })
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
            setBackgroundColor(ui.palette.background)
        }
        val scroll = ScrollView(activity).apply {
            clipChildren = false
            clipToPadding = false
            isSmoothScrollingEnabled = HudTokens.motion(activity, config)
            addView(ui.root)
        }
        frame.addView(scroll)
        wallpaper.load(config.wallpaperUri) { bitmap ->
            if (active && bitmap != null) frame.background = BitmapDrawable(activity.resources, bitmap)
        }
        return frame
    }

    private fun buildStatus(ui: HomeUi) {
        header = HudHeader(activity, ui).also { ui.root.addView(it.view) }
    }

    fun updateClock() { header?.updateClock() }

    fun refreshWeather() {
        val status = header?.weather ?: return
        weather.refresh(config.weatherEnabled, config.weatherCity) {
            if (active) {
                status.text = it
                status.visibility = if (config.weatherEnabled && it.isNotBlank()) View.VISIBLE else View.GONE
            }
        }
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
            ui.tile("inputs", "HDMI / Inputs", { chooseInput() }),
            ui.tile("tvhub", "TV Hub · Reserved", { actions.openTvHub() })
        ).onEach { (it as Button).apply {
            minHeight = ui.dp(64)
            typeface = HudTokens.heading
            textSize = 21f
        } })
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
            styleAppTile(it as Button, compact = section == "dock")
            if (section != "dock") icons.bind(it, app.packageName)
        }

    private fun styleAppTile(button: Button, compact: Boolean = false) {
        button.minHeight = ui.dp(if (compact) 56 else 120)
        button.textSize = if (compact) 14f else 16f
        button.maxLines = 2
        button.ellipsize = TextUtils.TruncateAt.END
    }

    private fun buildDock(ui: HomeUi) {
        ui.heading("FAVORITES")
        ui.row(DockPresentation.entries(dockPackages).map { entry ->
            val pkg = entry.packageName
            if (pkg == null) {
                ui.tile(entry.key, "+ Add favorite", { SettingsController(host).dockSlot(entry.slot) })
                    .also { styleAppTile(it as Button, compact = true) }
            } else {
                val app = dockApps.firstOrNull { it.packageName == pkg }
                if (app != null) appTile(ui, app, "dock")
                else ui.tile(entry.key, "Unavailable · $pkg", {
                    if (!discovery.launchPackage(pkg)) SettingsController(host).dockSlot(entry.slot)
                }, { SettingsController(host).dockSlot(entry.slot) }).also {
                    styleAppTile(it as Button, compact = true)
                    it.contentDescription = "Unavailable app $pkg. Choose to replace or clear favorite."
                }
            }
        }, columns = 6)
    }

    private fun buildApps(ui: HomeUi) {
        ui.heading("Apps")
        val visible = CatalogRules.visible(apps, config.hidden)
        if (visible.isEmpty()) {
            ui.root.addView(ui.text("No visible apps · Use Settings to show phone apps or unhide apps").apply {
                setBackgroundColor(ui.palette.background)
            })
        }
        visible.chunked(config.columns).forEach { row ->
            ui.row(row.map { appTile(ui, it, "app") }, columns = config.columns)
        }
    }

    private fun buildWebsites(ui: HomeUi) {
        val callbacks = webActions ?: return
        ui.heading("Web apps")
        val tiles = websites.map { site ->
            ui.tile(site.key, "${site.title}\n${site.host}", { callbacks.open(site.id) }, { callbacks.edit(site.id) }).apply {
                contentDescription = "${site.title}, ${site.host}. Hold OK to edit website."
            }
        } + ui.tile("web-add", "+ Add website", callbacks.add)
        tiles.chunked(config.columns).forEach { ui.row(it, config.columns) }
    }

    private fun launch(app: AppEntry) {
        if (!discovery.launchApp(app)) {
            notice("App unavailable", app.label + " could not be opened. Refresh or choose another app.")
        }
    }

    fun appMenu(app: AppEntry) {
        val pins = dockPackages
        val pinned = app.packageName in pins
        HudDialogs.builder(activity, config).setTitle(app.label)
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
        HudDialogs.builder(activity, config).setTitle("Inputs")
            .setItems(picker.choices.map { it.label }.toTypedArray()) { _, index ->
                val selected = picker.resolve(index, discovery.inputs())
                if (selected == null || !discovery.launchInput(selected.id)) notice("Input unavailable")
            }.show()
    }

    fun missingOwnTv() {
        HudDialogs.builder(activity, config).setTitle("OwnTV unavailable")
            .setMessage("${config.iptvPackage} is not installed or cannot launch. Open the project releases in your browser or choose an installed fork. No APK is downloaded automatically.")
            .setPositiveButton("Project releases") { _, _ ->
                if (!discovery.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ahXN00/OwnTV/releases")))) {
                    notice("No browser available")
                }
            }.setNeutralButton("Choose installed app") { _, _ -> SettingsController(host).iptv() }
            .setNegativeButton("Cancel", null).show()
    }
}
