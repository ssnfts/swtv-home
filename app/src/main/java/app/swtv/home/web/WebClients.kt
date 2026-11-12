package app.swtv.home.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.webkit.*
import java.io.ByteArrayInputStream

/** Each top-level load uses the same HTTPS authority policy as saved addresses. */
class SafeWebClient(
    private val location: (String?) -> Unit,
    private val title: (String?) -> Unit,
    private val error: (String) -> Unit,
    private val rendererGone: (WebView) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val blocked = WebNavigation.address(request.url.toString()) == null
        if (blocked && request.isForMainFrame) error("Only valid HTTPS pages can open here.")
        return blocked
    }
    @Deprecated("Legacy WebView callback")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = WebNavigation.address(url) == null
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        if (request.isForMainFrame && WebNavigation.address(request.url.toString()) == null)
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) else null
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (WebNavigation.address(url) == null) { view.stopLoading(); location(null); error("Unsupported page address.") }
        else location(url)
    }
    override fun onPageFinished(view: WebView, url: String?) { location(view.url); title(view.title) }
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        location(url)
        // Same-document history need not produce another title/page-finished callback.
        title(view.title)
    }
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel(); this.error("Secure connection failed. Certificate errors cannot be bypassed.")
    }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) this.error("Page unavailable. Try Reload or Open in Internet.")
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) error("Website returned an error (${errorResponse.statusCode}).")
    }
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        rendererGone(view); return true
    }
}

class SafeChromeClient(private val title: (String?) -> Unit,
    private val showFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    private val hideFullscreen: () -> Unit) : WebChromeClient() {
    override fun onReceivedTitle(view: WebView, title: String?) { this.title(title) }
    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message) = false
    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean {
        filePathCallback.onReceiveValue(null); return true
    }
    override fun onShowCustomView(view: View, callback: CustomViewCallback) = showFullscreen(view, callback)
    override fun onHideCustomView() = hideFullscreen()
}
