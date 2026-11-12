package app.swtv.home.updates

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import app.swtv.launcher.ui.HudDialogs
import app.swtv.launcher.ui.HudTokens
import app.swtv.theme.LauncherConfig
import app.swtv.updates.UpdateEngine
import java.io.Closeable

/** The Activity owns this panel only while open; controller callbacks are dropped on close. */
class UpdatePanel(private val activity: Activity, engine: UpdateEngine?, private val config: LauncherConfig, private val onClose: () -> Unit) : Closeable {
    private fun dp(value: Int) = HudTokens.dp(activity, value)
    private val controller = UpdateController(activity.applicationContext, engine)
    private val status = TextView(activity).apply { textSize = 18f; setPadding(dp(8), dp(8), dp(8), dp(16)) }
    private val check = Button(activity).apply { text = "Check for updates"; setOnClickListener { controller.check() } }
    private val download = Button(activity).apply { text = "Download update"; setOnClickListener { controller.download() } }
    private val install = Button(activity).apply { text = "Install update"; setOnClickListener { controller.install() } }
    private val settings = Button(activity).apply {
        text = "Allow this source in Settings"
        setOnClickListener {
            try { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
            catch (_: Exception) { status.text = "Android source settings are unavailable. Contact the device owner." }
        }
    }
    private val dialog: AlertDialog
    private var closed = false

    init {
        val identity = AndroidUpdateIdentity(activity).installed()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(TextView(activity).apply {
                text = "Installed ${identity?.versionName ?: "unknown"} (${identity?.versionCode ?: "unknown"})\nOriginal native TV launcher. OwnTV is a separate application."
                textSize = 17f
            })
            addView(status)
            listOf(check, download, install, settings).forEach {
                addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }
        dialog = HudDialogs.builder(activity, config).setTitle("SWTV Home · About and updates")
            .setView(ScrollView(activity).apply {
                isSmoothScrollingEnabled = HudTokens.motion(activity, config)
                addView(content)
            })
            .setNegativeButton("Close", null).create()
        dialog.setOnDismissListener { close() }
    }

    fun show() {
        dialog.show()
        controller.observe({ state ->
            val hadFocus = listOf(check, download, install, settings).any { it.hasFocus() }
            status.text = state.message
            check.isEnabled = controller.configured() && !state.busy
            download.isEnabled = state.download && !state.busy
            install.isEnabled = state.install && !state.busy
            settings.isEnabled = state.install && !state.busy && !controller.permissionGranted()
            val buttons = listOf(check, download, install, settings)
            buttons.forEach { it.isFocusable = it.isEnabled }
            if (hadFocus && buttons.none { it.hasFocus() }) focusAction()
        }, { request ->
            try { activity.startActivity(request) }
            catch (_: Exception) { close() }
        })
        focusAction()
    }
    private fun focusAction() {
        (listOf(check, download, install, settings).firstOrNull { it.isEnabled }
            ?: dialog.getButton(AlertDialog.BUTTON_NEGATIVE)).requestFocus()
    }
    fun foreground(value: Boolean) { if (!closed) controller.foreground(value) }
    override fun close() {
        if (closed) return
        closed = true; controller.close(); dialog.dismiss(); onClose()
    }
}
