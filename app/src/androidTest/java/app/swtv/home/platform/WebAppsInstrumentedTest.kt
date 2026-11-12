package app.swtv.home.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.swtv.home.R
import app.swtv.home.web.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** All target access is below the explicit API29 task-emulator guard. No fixture resets a store. */
@RunWith(AndroidJUnit4::class)
class WebAppsInstrumentedTest {
    private fun target(): Context {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit webapps fixture opt-in", args.getString("swtvFixtureOptIn") == "true" && args.getString("swtvEmulator") == "webapps")
        assumeTrue("API29 generic emulator only", Build.VERSION.SDK_INT == 29 &&
            Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.contains("generic"))
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
    private fun ui(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun awaitUi(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            var done = false; ui { done = condition() }; if (done) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for native viewer state")
    }
    private fun launch(intent: Intent): Activity = InstrumentationRegistry.getInstrumentation()
        .startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun store(context: Context) = WebsiteStore(SharedPreferencesWebsiteStorage(context))
    private fun snapshot(store: WebsiteStore) = (store.snapshot() as WebsiteResult.Success).favorites.entries
    private fun fixture(store: WebsiteStore): WebsiteFavorite {
        val result = store.save("W2 fixture", "https://example.invalid/w2-${UUID.randomUUID()}") as WebsiteResult.Success
        assertTrue(result.changed)
        return result.favorite!!
    }
    private fun cleanup(store: WebsiteStore, favorite: WebsiteFavorite) {
        assertTrue(store.remove(favorite.id) is WebsiteResult.Success)
    }
    @Test fun componentBoundariesAndInvalidSavedId() {
        val context = target()
        val pm = context.packageManager
        assertFalse(pm.getActivityInfo(ComponentName(context, WebActivity::class.java), 0).exported)
        assertTrue(pm.getActivityInfo(ComponentName(context, ShareWebsiteActivity::class.java), 0).exported)
        val viewIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/")).setPackage(context.packageName)
        assertTrue(pm.queryIntentActivities(viewIntent, 0).isEmpty())
        val activity = launch(WebActivity.intent(context, "missing-${UUID.randomUUID()}"))
        try {
            awaitUi { activity.findViewById<TextView>(R.id.web_origin).text.contains("no longer exists") }
            ui { assertNull(activity.findViewById<WebView>(R.id.web_content)) }
        } finally { ui { activity.finish() } }
    }
    @Test fun savedIdViewerSettingsToolbarAndRendererRecovery() {
        val context = target(); val store = store(context); val saved = fixture(store)
        var launched: Activity? = null
        try {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            val activity = launch(WebActivity.intent(context, saved.id))
            launched = activity
            awaitUi { activity.findViewById<WebView>(R.id.web_content) != null }
            ui {
                val web = activity.findViewById<WebView>(R.id.web_content)
                assertTrue(web.settings.javaScriptEnabled); assertTrue(web.settings.domStorageEnabled)
                assertFalse(web.settings.allowFileAccess); assertFalse(web.settings.allowContentAccess)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                assertFalse(web.settings.javaScriptCanOpenWindowsAutomatically)
                assertTrue(web.settings.mediaPlaybackRequiresUserGesture)
                assertTrue(web.settings.safeBrowsingEnabled)
                web.loadDataWithBaseURL("https://example.com/w2/", "<html><head><title>Controlled fixture</title></head><body><a href='https://example.com/next'>Next</a><button>Focus target</button></body></html>", "text/html", "UTF-8", null)
            }
            awaitUi { activity.findViewById<TextView>(R.id.web_origin).text.contains("Controlled fixture") }
            key(KeyEvent.KEYCODE_MENU)
            ui {
                assertTrue(activity.findViewById<View>(R.id.web_back).hasFocus())
                // Chromium debug URL belongs only to the explicitly guarded test APK.
                activity.findViewById<WebView>(R.id.web_content).loadUrl("chrome://crash")
            }
            awaitUi { activity.findViewById<WebView>(R.id.web_content) == null &&
                activity.findViewById<TextView>(R.id.web_origin).text.contains("renderer stopped") }
            ui { activity.findViewById<View>(R.id.web_reload).performClick() }
            awaitUi { activity.findViewById<WebView>(R.id.web_content) != null }
            ui {
                fun count(view: View): Int = (if (view is WebView) 1 else 0) +
                    if (view is ViewGroup) (0 until view.childCount).sumOf { count(view.getChildAt(it)) } else 0
                assertEquals(1, count(activity.window.decorView))
            }
        } finally { ui { launched?.finish() }; cleanup(store, saved) }
    }
    @Test fun shareRequiresSaveAndInvalidFormsRemainOpen() {
        val context = target(); val store = store(context); val before = snapshot(store)
        val address = "https://example.invalid/share-${UUID.randomUUID()}"
        val intent = Intent(context, ShareWebsiteActivity::class.java).setAction(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, address)
        var activity = launch(intent)
        try {
            var form: View? = null
            // Dialog windows are separate roots. Use the instrumentation's UI window hierarchy via decor lookup.
            awaitUi {
                form = dialogRoot(activity)
                form?.findViewById<EditText>(R.id.website_address) != null
            }
            assertEquals(before, snapshot(store))
            ui { form!!.findViewById<View>(android.R.id.button2).performClick() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(before, snapshot(store))
            activity = launch(intent)
            awaitUi { form = dialogRoot(activity); form?.findViewById<EditText>(R.id.website_address) != null }
            ui {
                form!!.findViewById<EditText>(R.id.website_address).setText("javascript:bad")
                form!!.findViewById<View>(R.id.website_save).performClick()
            }
            awaitUi { form!!.findViewById<TextView>(R.id.website_error).text.isNotEmpty() }
            assertEquals(before, snapshot(store))
            ui {
                assertTrue(form!!.isShown)
                form!!.findViewById<EditText>(R.id.website_address).setText(address)
                form!!.findViewById<EditText>(R.id.website_title).setText("Shared fixture")
                form!!.findViewById<View>(R.id.website_save).performClick()
            }
            val deadline = System.currentTimeMillis() + 10000
            while (snapshot(store).none { it.normalizedUrl == address } && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val saved = snapshot(store).single { it.normalizedUrl == address }
            assertEquals("Shared fixture", saved.title)
        } finally {
            ui { activity.finish() }
            // Remove only this test's unique address, including if an assertion failed after the authorized save.
            snapshot(store).filter { it.normalizedUrl == address }.forEach { cleanup(store, it) }
            assertEquals(before, snapshot(store))
        }
    }
    @Test fun repeatedSavedIdAndRecreationKeepOneViewer() {
        val context = target(); val store = store(context)
        val first = fixture(store)
        var second: WebsiteFavorite? = null
        var activity: Activity? = null
        try {
            val secondSaved = fixture(store)
            second = secondSaved
            activity = launch(WebActivity.intent(context, first.id))
            awaitUi { activity!!.findViewById<WebView>(R.id.web_content) != null }
            var oldWeb: WebView? = null
            ui {
                oldWeb = activity!!.findViewById(R.id.web_content)
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, WebActivity.intent(context, secondSaved.id))
            }
            awaitUi { activity!!.findViewById<WebView>(R.id.web_content)?.let { it !== oldWeb && it.url == secondSaved.normalizedUrl } == true }
            val oldActivity = activity
            ui { activity!!.recreate() }
            awaitUi {
                val next = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<WebActivity>().singleOrNull()
                if (next != null && next !== oldActivity && next.findViewById<WebView>(R.id.web_content) != null) {
                    activity = next; true
                } else false
            }
            ui {
                assertTrue(oldActivity!!.isDestroyed)
                assertEquals(secondSaved.normalizedUrl, activity!!.findViewById<WebView>(R.id.web_content).url)
                val webViews = android.view.inspector.WindowInspector.getGlobalWindowViews().sumOf { countWebViews(it) }
                assertEquals(1, webViews)
            }
        } finally {
            ui { activity?.finish() }
            second?.let { cleanup(store, it) }; cleanup(store, first)
        }
    }
    @Test fun sameDocumentHistoryKeepsNativeAndBookmarkTitle() = controlledPage(
        "<title>Stable document title</title><body>Same document history fixture</body>"
    ) { activity, web ->
        assertEquals("\"https://example.com/w2/changed#same-document\"",
            evaluate(web, "history.pushState({}, '', '/w2/changed#same-document'); location.href;"))
        ui {
            // Some providers additionally emit onPageFinished for pushState. Exercise the
            // history callback alone too: it must not depend on that optional later callback.
            web.webViewClient.doUpdateVisitedHistory(web, web.url, false)
            assertTrue(activity.findViewById<TextView>(R.id.web_origin).text.contains("Stable document title"))
            assertEquals("Stable document title", web.title)
            // WebView83 keeps loadDataWithBaseURL's virtual history URL even after the
            // DOM pushState above. This fixture tests title retention, not real-load URL updates.
            assertEquals("Controlled page must expose its HTTPS history URL to native bookmarks",
                "https://example.com/w2/", web.url)
            assertTrue(activity.findViewById<View>(R.id.web_bookmark).performClick())
        }
        awaitUi { dialogRoot(activity) != null }
        ui {
            val form = dialogRoot(activity)!!
            assertEquals("Stable document title", form.findViewById<EditText>(R.id.website_title).text.toString())
            assertEquals("https://example.com/w2/", form.findViewById<EditText>(R.id.website_address).text.toString())
            form.findViewById<View>(android.R.id.button2).performClick()
        }
    }
    @Test fun remotePointerDispatchesBoundedTouchesToControlledPage() = controlledPage("""
        <title>Pointer fixture</title>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;overflow:hidden}button{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
        <button onclick="window.hits=(window.hits||0)+1">Pointer target</button>
    """.trimIndent()) { activity, web ->
        val touches = mutableListOf<Pair<Float, Float>>()
        ui {
            web.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) touches.add(event.x to event.y)
                false
            }
        }
        enablePointer(activity)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitScript(web, "window.hits === 1")
        var horizontalSteps = 0
        var verticalSteps = 0
        ui {
            horizontalSteps = web.width / 24 + 2
            verticalSteps = web.height / 24 + 2
        }
        repeat(horizontalSteps) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        repeat(verticalSteps) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitScript(web, "window.hits === 2")
        ui {
            assertEquals(2, touches.size)
            assertTrue(touches.all { (x, y) -> x >= 0 && x < web.width && y >= 0 && y < web.height })
            assertEquals((web.width - 1).toFloat(), touches.last().first, 0f)
            assertEquals((web.height - 1).toFloat(), touches.last().second, 0f)
        }
        repeat(verticalSteps) { key(KeyEvent.KEYCODE_DPAD_UP) }
        ui {
            assertTrue(activity.findViewById<View>(R.id.web_back).hasFocus())
            assertEquals(1, countWebViews(activity.window.decorView))
        }
    }
    @Test fun controlledHtmlFullscreenBackAndMenuRestoreNativeControls() = controlledPage("""
        <title>Fullscreen fixture</title>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;overflow:hidden}button{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
        <button onclick="this.requestFullscreen()">Enter HTML fullscreen</button>
    """.trimIndent()) { activity, web ->
        enablePointer(activity)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitUi { !activity.findViewById<View>(R.id.web_origin).isShown }
        awaitScript(web, "document.fullscreenElement !== null")
        ui {
            assertFalse(activity.findViewById<WebPointer>(R.id.web_pointer).pointerEnabled)
        }
        key(KeyEvent.KEYCODE_BACK)
        awaitUi { activity.findViewById<View>(R.id.web_origin).isShown }
        awaitScript(web, "document.fullscreenElement === null")
        ui { assertFalse(activity.isFinishing); assertSame(web, activity.findViewById(R.id.web_content)) }
        enablePointer(activity)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitUi { !activity.findViewById<View>(R.id.web_origin).isShown }
        key(KeyEvent.KEYCODE_MENU)
        awaitUi { activity.findViewById<View>(R.id.web_origin).isShown }
        awaitScript(web, "document.fullscreenElement === null")
        ui {
            assertTrue(activity.findViewById<View>(R.id.web_back).hasFocus())
            assertSame(web, activity.findViewById(R.id.web_content))
            assertEquals(1, countWebViews(activity.window.decorView))
        }
    }
    private fun controlledPage(html: String, test: (Activity, WebView) -> Unit) {
        val context = target(); val store = store(context); val saved = fixture(store)
        var launched: Activity? = null
        try {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            val activity = launch(WebActivity.intent(context, saved.id)); launched = activity
            awaitUi { activity.findViewById<WebView>(R.id.web_content) != null }
            lateinit var web: WebView
            ui {
                web = activity.findViewById(R.id.web_content)
                // A native bookmark reads WebView.url, so provide a real HTTPS history URL
                // as well as the document's base origin instead of the null/about:blank default.
                web.loadDataWithBaseURL("https://example.com/w2/", html, "text/html", "UTF-8", "https://example.com/w2/")
            }
            val expectedTitle = Regex("<title>(.*?)</title>").find(html)!!.groupValues[1]
            awaitScript(web, "document.readyState === 'complete' && document.title === ${org.json.JSONObject.quote(expectedTitle)}")
            awaitUi { activity.findViewById<TextView>(R.id.web_origin).text.contains(expectedTitle) }
            test(activity, web)
        } finally { ui { launched?.finish() }; cleanup(store, saved) }
    }
    private fun evaluate(web: WebView, script: String): String? {
        val ready = java.util.concurrent.CountDownLatch(1)
        var value: String? = null
        ui { web.evaluateJavascript(script) { value = it; ready.countDown() } }
        assertTrue("Timed out awaiting controlled page script", ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        return value
    }
    private fun awaitScript(web: WebView, expression: String) {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(50)
        }
        fail("Controlled page assertion did not become true: $expression")
    }
    private fun key(code: Int) {
        // Use Android's input path, including exiting touch mode, as a TV remote does.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
    }
    private fun enablePointer(activity: Activity) {
        ui { activity.findViewById<View>(R.id.web_options).performClick() }
        awaitUi { optionsList(activity) != null }
        ui {
            val list = optionsList(activity)!!
            assertEquals("Enable pointer", list.adapter.getItem(0).toString())
            list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
            assertTrue(activity.findViewById<WebPointer>(R.id.web_pointer).pointerEnabled)
            assertTrue(activity.findViewById<WebView>(R.id.web_content).hasFocus())
        }
    }
    private fun optionsList(activity: Activity): ListView? {
        fun find(view: View): ListView? {
            if (view is ListView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull {
            it.findViewById<TextView>(android.R.id.title)?.text == "Web options" && ownedWindow(it, activity)
        }?.let(::find)
    }
    private fun countWebViews(view: View): Int = (if (view is WebView) 1 else 0) +
        (if (view is ViewGroup) (0 until view.childCount).sumOf { countWebViews(view.getChildAt(it)) } else 0)
    private fun dialogRoot(activity: Activity): View? {
        // Public API29 inspection of this process's windows; no hidden API or production endpoint.
        return android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { root ->
            root.findViewById<View>(R.id.website_save) != null && ownedWindow(root, activity)
        }
    }
    private fun ownedWindow(view: View, activity: Activity): Boolean {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is Activity) context = context.baseContext
        return context === activity
    }
}
