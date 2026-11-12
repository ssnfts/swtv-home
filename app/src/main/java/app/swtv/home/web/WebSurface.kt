package app.swtv.home.web

import android.app.Activity
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.*
import app.swtv.home.R
import app.swtv.launcher.ui.HudControls
import app.swtv.launcher.ui.HudTokens
import app.swtv.theme.LauncherConfig

/** Native controls remain separate from untrusted page content and HTML fullscreen ownership. */
class WebSurface(activity: Activity, config: LauncherConfig, back: () -> Unit, reload: () -> Unit,
    bookmark: () -> Unit, options: () -> Unit, close: () -> Unit) {
    val root = FrameLayout(activity)
    private val normal = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val content = FrameLayout(activity)
    val pointer = WebPointer(activity)
    val origin = TextView(activity).apply {
        id = R.id.web_origin; textSize = 17f; maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        setTextColor(HudTokens.palette(config).text); typeface = HudTokens.body
        setPadding(16, 6, 16, 6)
    }
    val backButton: Button
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    val fullscreen get() = customView != null
    init {
        root.setBackgroundColor(HudTokens.palette(config).background)
        root.addView(normal, FrameLayout.LayoutParams(-1, -1))
        normal.addView(origin)
        val toolbar = LinearLayout(activity)
        fun button(id: Int, label: String, action: () -> Unit) = Button(activity).apply {
            this.id = id; text = label; HudControls.button(this, config, scale = false)
            setOnClickListener { action() }
            toolbar.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        backButton = button(R.id.web_back, "Back", back)
        button(R.id.web_reload, "Reload", reload)
        button(R.id.web_bookmark, "Bookmark", bookmark)
        button(R.id.web_options, "Options", options)
        button(R.id.web_close, "Close", close)
        normal.addView(toolbar)
        normal.addView(TextView(activity).apply {
            text = "MENU or hold ↑: controls · Pointer: ↑ at top returns to controls"
            textSize = 12f; setTextColor(HudTokens.palette(config).secondary); setPadding(16, 0, 16, 4)
        })
        normal.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    fun toolbar() { hideFullscreen(); backButton.requestFocus() }
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        pointer.pointerEnabled = false
        customView = view; customCallback = callback
        normal.visibility = View.GONE
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
    }
    fun hideFullscreen() {
        val view = customView ?: return
        customView = null
        (view.parent as? ViewGroup)?.removeView(view)
        normal.visibility = View.VISIBLE
        val callback = customCallback; customCallback = null
        callback?.onCustomViewHidden()
    }
}
