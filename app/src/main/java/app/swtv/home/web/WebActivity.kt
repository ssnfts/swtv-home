package app.swtv.home.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import app.swtv.home.MainActivity
import app.swtv.home.R
import app.swtv.launcher.ui.HudDialogs
import app.swtv.theme.Preferences

/** Private saved-ID entry point; singleTask reuses this instance for subsequent tile launches. */
class WebActivity : Activity() {
    private lateinit var work: WebsiteWork
    private lateinit var surface: WebSurface
    private val config by lazy { Preferences(this).load() }
    private var editor: WebsiteEditor? = null
    private var dialog: AlertDialog? = null
    private var web: WebView? = null
    private var favoriteId: String? = null
    private var address: WebsiteAddress? = null
    private var pageTitle = "Website"
    private val session = WebSession()
    private val foreground get() = session.foreground
    private var resolving = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        work = WebsiteWork(applicationContext)
        surface = WebSurface(this, config, { back() }, { reload() }, { bookmark() }, { options() }, { closeToHome() })
        setContentView(surface.root)
        accept(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); accept(intent); if (foreground) resolve() }
    private fun accept(incoming: Intent) {
        editor?.dismiss(); dialog?.dismiss(); dialog = null; work.detach()
        session.nextRequest(); resolving = false; destroyWeb(); address = null
        favoriteId = try { incoming.getStringExtra(EXTRA_ID)?.takeIf { Regex("[A-Za-z0-9_-]{1,100}").matches(it) } }
            catch (_: RuntimeException) { null }
        surface.origin.text = "Opening saved website…"
    }
    override fun onResume() {
        super.onResume(); session.resume()
        web?.let {
            updateLocation(it.url); updateTitle(it.title)
            it.onResume(); it.resumeTimers()
        } ?: resolve()
    }
    private fun resolve() {
        if (resolving || isFinishing) return
        val id = favoriteId
        if (id == null) { unavailable("This window requires a saved website."); return }
        resolving = true
        val token = session.nextRequest()
        work.submit(WebsiteWork.Command.Snapshot) { result ->
            if (!session.accepts(token)) return@submit
            resolving = false
            val favorite = (result as? WebsiteResult.Success)?.favorites?.entries?.firstOrNull { it.id == id }
            val valid = WebNavigation.address(favorite?.normalizedUrl)
            if (favorite == null || valid == null) {
                unavailable(if (result is WebsiteResult.Failure) WebsiteEditor.message(result.error) else "Saved website no longer exists.")
            } else {
                pageTitle = favorite.title
                createWeb(valid)
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWeb(initial: WebsiteAddress) {
        if (web != null || !foreground) return
        try {
            val view = WebView(this)
            web = view
            view.id = R.id.web_content
            view.settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(true)
                mediaPlaybackRequiresUserGesture = true; setGeolocationEnabled(false)
                safeBrowsingEnabled = true
                builtInZoomControls = true; displayZoomControls = false
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            view.setDownloadListener { _, _, _, _, _ -> notice("Downloads are unavailable in SWTV. Use Open in Internet if needed.") }
            view.webViewClient = SafeWebClient({ if (foreground) updateLocation(it) }, { if (foreground) updateTitle(it) }, { if (foreground) status(it) }, { failed ->
                if (web === failed) { destroyWeb(); address = null; status("Web renderer stopped. Select Reload to reopen the saved website."); surface.toolbar() }
            })
            view.webChromeClient = SafeChromeClient({ if (foreground) updateTitle(it) }, { custom, callback ->
                if (foreground) surface.showFullscreen(custom, callback) else callback.onCustomViewHidden()
            }, { surface.hideFullscreen() })
            surface.content.addView(view, ViewGroup.LayoutParams(-1, -1))
            (surface.pointer.parent as? ViewGroup)?.removeView(surface.pointer)
            surface.content.addView(surface.pointer, ViewGroup.LayoutParams(-1, -1))
            updateLocation(initial.value)
            view.onResume(); view.resumeTimers()
            view.loadUrl(initial.value)
            view.requestFocus()
        } catch (_: RuntimeException) {
            destroyWeb(); status("WebView unavailable. Try Reload or Open in Internet."); surface.toolbar()
        }
    }
    private fun updateLocation(url: String?) {
        address = WebNavigation.address(url)
        pageTitle = "Website"
        renderOrigin()
    }
    private fun updateTitle(title: String?) {
        pageTitle = title.orEmpty().filterNot(Character::isISOControl).take(100).ifBlank { "Website" }
        renderOrigin()
    }
    private fun renderOrigin() {
        val current = address
        // Full HTTPS origin, including nondefault port, is native text independent of page title.
        val origin = current?.value?.let { val uri = java.net.URI(it); "https://${uri.rawAuthority}" }
        surface.origin.text = "${origin ?: "No secure page"}\n$pageTitle"
    }
    private fun status(message: String) { renderOrigin(); surface.origin.append("\n$message") }
    private fun unavailable(message: String) { status(message); surface.toolbar() }
    private fun reload() { if (web == null) resolve() else web?.reload() }
    private fun bookmark() {
        val current = WebNavigation.address(web?.url)
        if (current == null) { notice("Open a valid HTTPS page before bookmarking."); return }
        editor?.dismiss()
        editor = WebsiteEditor(this, config, work).also { it.edit(pageTitle, current.value, finished = { notice("Website saved to Web apps.") }) }
    }
    private fun options() {
        dialog?.dismiss()
        dialog = HudDialogs.builder(this, config).setTitle("Web options")
            .setItems(arrayOf(if (surface.pointer.pointerEnabled) "Disable pointer" else "Enable pointer", "Open in Internet")) { _, which ->
                if (which == 0) {
                    surface.pointer.pointerEnabled = !surface.pointer.pointerEnabled
                    web?.requestFocus()
                } else openInInternet()
            }.show()
    }
    private fun openInInternet() {
        val current = WebNavigation.address(web?.url) ?: address
        if (current == null) { notice("No valid HTTPS page is open."); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.value)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: RuntimeException) { notice("No compatible Internet browser is available.") }
    }
    private fun notice(message: String) {
        if (!foreground) return
        dialog?.dismiss()
        dialog = HudDialogs.builder(this, config).setTitle("Web apps").setMessage(message).setPositiveButton("OK", null).show()
    }
    private fun back() {
        when (WebBack.decide(surface.fullscreen, web?.canGoBack() == true)) {
            WebBack.EXIT_FULLSCREEN -> surface.hideFullscreen()
            WebBack.HISTORY -> web?.goBack()
            WebBack.CLOSE -> closeToHome()
        }
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed() = back()
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN) surface.toolbar()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) back()
            return true
        }
        val current = web
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && event.repeatCount >= 4) {
            surface.toolbar(); return true
        }
        if (current != null && current.hasFocus() && !surface.fullscreen && surface.pointer.key(event, current) { surface.toolbar() }) return true
        return super.dispatchKeyEvent(event)
    }
    private fun closeToHome() {
        destroyWeb()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
    override fun onPause() {
        session.pause(); resolving = false
        editor?.dismiss(); dialog?.dismiss(); dialog = null; work.detach()
        surface.hideFullscreen(); surface.pointer.pointerEnabled = false
        web?.let {
            it.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(media){media.pause();});", null)
            it.onPause(); it.pauseTimers()
        }
        super.onPause()
    }
    private fun destroyWeb() {
        surface.hideFullscreen(); surface.pointer.pointerEnabled = false
        val old = web ?: return
        web = null
        old.stopLoading(); old.onPause()
        (old.parent as? ViewGroup)?.removeView(old)
        old.webChromeClient = null
        old.webViewClient = android.webkit.WebViewClient()
        old.setDownloadListener(null)
        old.destroy()
    }
    override fun onDestroy() { session.close(); destroyWeb(); work.close(); super.onDestroy() }
    companion object {
        private const val EXTRA_ID = "app.swtv.home.web.favorite_id"
        fun intent(context: Context, savedId: String) = Intent(context, WebActivity::class.java).putExtra(EXTRA_ID, savedId)
    }
}
