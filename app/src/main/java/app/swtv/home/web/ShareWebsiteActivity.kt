package app.swtv.home.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import app.swtv.launcher.ui.HudDialogs
import app.swtv.theme.Preferences

/** External text is only a proposed form value. Receiving an intent never writes or opens a page. */
class ShareWebsiteActivity : Activity() {
    private lateinit var work: WebsiteWork
    private var editor: WebsiteEditor? = null
    private var address: String? = null
    private var title = ""
    private var notice: AlertDialog? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        work = WebsiteWork(applicationContext)
        address = try {
            if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
                ShareAddress.parse(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)) else null
        } catch (_: RuntimeException) { null }
        title = try {
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.let {
                if (it.length <= WebsiteFavorites.MAX_TITLE_LENGTH && it.none(Character::isISOControl)) it.toString() else ""
            } ?: ""
        } catch (_: RuntimeException) { "" }
        setContentView(TextView(this).apply { text = "Add to SWTV" })
    }
    override fun onResume() {
        super.onResume()
        val config = Preferences(this).load()
        val accepted = address
        if (accepted == null) {
            notice = HudDialogs.builder(this, config).setTitle("Cannot add this share")
                .setMessage("Share one unambiguous HTTPS address, or use Add website on SWTV.")
                .setPositiveButton("Close") { _, _ -> finish() }
                .setOnCancelListener { finish() }.show()
        } else {
            editor = WebsiteEditor(this, config, work).also {
                it.edit(title, accepted, finished = { finish() }, cancelled = { finish() })
            }
        }
    }
    override fun onPause() { editor?.dismiss(); notice?.dismiss(); notice = null; work.detach(); super.onPause() }
    override fun onDestroy() { work.close(); super.onDestroy() }
}
