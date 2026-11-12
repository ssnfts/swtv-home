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
import app.swtv.launcher.ui.HudDialogs
import app.swtv.launcher.ui.DockPresentation
import app.swtv.launcher.ui.LauncherHost
import app.swtv.launcher.ui.HomeScreen
import app.swtv.launcher.ui.HomeScreenActions
import app.swtv.launcher.ui.WebTile
import app.swtv.launcher.ui.WebTileActions
import app.swtv.home.web.*
import app.swtv.diagnostics.DeviceDiagnostics
import app.swtv.diagnostics.DiagnosticReport
import app.swtv.diagnostics.DiagnosticSnapshot
import app.swtv.updates.UpdateEngine
import app.swtv.home.updates.AndroidUpdateIdentity
import app.swtv.home.updates.UpdatePanel
import app.swtv.tvhub.contract.AndroidHubGateway
import app.swtv.tvhub.contract.HubClient
import app.swtv.tvhub.contract.HubLaunchResult
import java.time.ZonedDateTime
import java.util.concurrent.Executor

class MainActivity : Activity(), LauncherHost {
    override val activity: Activity get() = this
    override lateinit var config: LauncherConfig
        private set
    private lateinit var prefs: Preferences
    override val discovery by lazy { Discovery(this) }
    override var apps = emptyList<AppEntry>()
        private set
    private var dockApps = emptyList<AppEntry>()
    override val dockPackages: List<String> get() = DockPresentation.pins(config.dock)
    override var inputs = emptyList<InputEntry>()
        private set
    private lateinit var weather: Weather
    private lateinit var wallpaper: Wallpaper
    private lateinit var diagnostics: LatestResource<DiagnosticSnapshot>
    private var diagnosticsDialog: AlertDialog? = null
    private var diagnosticsRequest = 0L
    private var updatePanel: UpdatePanel? = null
    private val hub by lazy { HubClient(AndroidHubGateway(applicationContext)) }
    private val icons by lazy { Icons(this) }
    private var home: HomeScreen? = null
    private var savedFocus: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private lateinit var websiteWork: WebsiteWork
    private var websiteEditor: WebsiteEditor? = null
    private var websites = emptyList<WebsiteFavorite>()
    private var websiteRequest = 0L
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
        websiteWork = WebsiteWork(applicationContext)
        config = prefs.load()
        weather = Weather(this)
        wallpaper = Wallpaper(this)
        val collector = DeviceDiagnostics(applicationContext)
        val mainHandler = handler
        // Bounded worker retains the application-context collector, never the Activity.
        // cancelPending drops the UI subscriber on dismiss/pause; each opening requests a fresh key.
        diagnostics = LatestResource({ collector.collect() }, Executor { mainHandler.post(it) })
        savedFocus = state?.getString("focus")
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        updatePanel?.foreground(true)
        registerForegroundReceivers()
        refresh()
        refreshWebsites()
    }

    override fun onPause() {
        savedFocus = home?.focusedKey() ?: savedFocus
        foreground = false
        ++websiteRequest
        websiteEditor?.dismiss()
        websiteWork.detach()
        updatePanel?.foreground(false)
        handler.removeCallbacks(minuteTick)
        handler.removeCallbacks(themeBoundary)
        handler.removeCallbacks(refreshAfterPackageChange)
        unregisterForegroundReceivers()
        icons.beginScreen()
        weather.cancel()
        wallpaper.cancelPending()
        diagnostics.cancelPending()
        diagnosticsDialog?.dismiss()
        diagnosticsDialog = null
        super.onPause()
    }

    override fun onDestroy() {
        websiteWork.close()
        updatePanel?.close()
        icons.close()
        wallpaper.close()
        weather.close()
        diagnostics.close()
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
            HomeScreenActions(about = { about() }, openTvHub = { openTvHub() }),
            websites.map { WebTile(it.id, it.title, it.displayHost) },
            WebTileActions(open = { openWebsite(it) }, edit = { editWebsite(it) }, add = { addWebsite() }))
        home = screen
        screen.render(savedFocus)
        handler.removeCallbacks(minuteTick)
        minuteTick.run()
        scheduleThemeBoundary()
    }

    private fun refreshWebsites() {
        val token = ++websiteRequest
        websiteWork.submit(WebsiteWork.Command.Snapshot) { result ->
            if (!foreground || token != websiteRequest) return@submit
            if (result is WebsiteResult.Success) {
                val next = result.favorites.entries
                if (next != websites) { websites = next; refresh() }
            } else if (result is WebsiteResult.Failure) notice("Websites unavailable", WebsiteEditor.message(result.error))
        }
    }
    private fun addWebsite() {
        websiteEditor?.dismiss()
        websiteEditor = WebsiteEditor(this, config, websiteWork).also { it.edit(finished = { refreshWebsites() }) }
    }
    private fun openWebsite(id: String) {
        websiteWork.submit(WebsiteWork.Command.Snapshot) { result ->
            if (!foreground) return@submit
            if (result is WebsiteResult.Success && result.favorites.entries.any { it.id == id })
                startActivity(WebActivity.intent(this, id))
            else notice("Website unavailable", if (result is WebsiteResult.Failure) WebsiteEditor.message(result.error) else "This website was removed.")
        }
    }
    private fun editWebsite(id: String) {
        websiteWork.submit(WebsiteWork.Command.Snapshot) { result ->
            if (!foreground) return@submit
            val favorite = (result as? WebsiteResult.Success)?.favorites?.entries?.firstOrNull { it.id == id }
            if (favorite == null) notice("Website unavailable", if (result is WebsiteResult.Failure) WebsiteEditor.message(result.error) else "This website was removed.")
            else {
                websiteEditor?.dismiss()
                websiteEditor = WebsiteEditor(this, config, websiteWork).also { it.menu(favorite) { refreshWebsites() } }
            }
        }
    }

    private fun refreshCatalog() {
        val snapshot = discovery.appSnapshot(true) ?: return
        dockApps = snapshot
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
        HudDialogs.builder(this, config).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun openTvHub() {
        when (hub.open()) {
            HubLaunchResult.Missing -> notice("TV Hub unavailable", "Reserved for app.swtv.tvhub. Install this separate app when available.")
            HubLaunchResult.Incompatible -> notice("TV Hub incompatible", "The installed TV Hub does not declare a compatible version. This launcher requires TV Hub contract version 1.")
            HubLaunchResult.Unavailable -> notice("TV Hub unavailable", "TV Hub cannot be opened. It needs one enabled, exported home handler for contract version 1.")
            HubLaunchResult.Launched -> Unit
        }
    }

    private fun about() {
        val release = AndroidUpdateIdentity.releaseConfig(applicationContext)
        showUpdatePanel(release?.let { AndroidUpdateIdentity.engine(applicationContext, it) })
    }

    /** Constructor injection for the test APK; exported intents cannot configure updates. */
    fun showUpdatePanel(engine: UpdateEngine?) {
        if (!foreground) return
        updatePanel?.close()
        updatePanel = UpdatePanel(this, engine, config) { updatePanel = null }.also { it.show() }
    }

    override fun showDiagnostics() {
        if (!foreground) return
        diagnosticsDialog?.dismiss()
        val dialog = HudDialogs.builder(this, config).setTitle("Diagnostics")
            .setMessage("Collecting public system values…").setPositiveButton("OK", null).create()
        diagnosticsDialog = dialog
        dialog.setOnDismissListener {
            if (diagnosticsDialog === dialog) {
                diagnostics.cancelPending()
                diagnosticsDialog = null
            }
        }
        dialog.show()
        diagnostics.request((++diagnosticsRequest).toString()) { snapshot ->
            if (foreground && diagnosticsDialog === dialog) {
                dialog.setMessage(snapshot?.let(DiagnosticReport::format) ?: "Diagnostics unavailable.")
            }
        }
    }

    override fun pickWallpaper() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 41)
        } catch (_: Exception) {
            notice("Image picker unavailable", "Use the built-in HUD background on this device.")
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
