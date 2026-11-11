package app.swtv.home.updates

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.swtv.updates.UpdateEngine
import java.io.Closeable

/** The Activity owns this panel only while open; controller callbacks are dropped on close. */
class UpdatePanel(private val activity: Activity, engine: UpdateEngine?, private val onClose: () -> Unit) : Closeable {
    private val controller = UpdateController(activity.applicationContext, engine)
    private val status = TextView(activity).apply { textSize = 20f; setPadding(12, 12, 12, 20) }
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
            orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16)
            addView(TextView(activity).apply {
                text = "Installed ${identity?.versionName ?: "unknown"} (${identity?.versionCode ?: "unknown"})\nOriginal native TV launcher. OwnTV is a separate application."
                textSize = 17f
            })
            addView(status)
            listOf(check, download, install, settings).forEach { addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        }
        dialog = AlertDialog.Builder(activity).setTitle("SWTV Home · About and updates").setView(content)
            .setNegativeButton("Close", null).create()
        dialog.setOnDismissListener { close() }
    }

    fun show() {
        dialog.show()
        controller.observe({ state ->
            status.text = state.message
            check.isEnabled = controller.configured() && !state.busy
            download.isEnabled = state.download && !state.busy
            install.isEnabled = state.install && !state.busy
            settings.isEnabled = state.install && !state.busy && !controller.permissionGranted()
        }, { request ->
            try { activity.startActivity(request) }
            catch (_: Exception) { close() }
        })
        check.requestFocus()
    }
    fun foreground(value: Boolean) { if (!closed) controller.foreground(value) }
    override fun close() {
        if (closed) return
        closed = true; controller.close(); dialog.dismiss(); onClose()
    }
}
