package app.swtv.home.platform

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.swtv.home.MainActivity
import app.swtv.home.updates.AndroidUpdateIdentity
import app.swtv.updates.ManifestParser
import app.swtv.updates.UpdateCancellation
import app.swtv.updates.UpdateTransport
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** Explicit API29 emulator-only manual interaction driver. Never selected by ordinary intent tests. */
@RunWith(AndroidJUnit4::class)
class UpdateFlowInstrumentedTest {
    @Test fun interactiveUpdate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit test-only install opt-in", args.getString("swtvAllowInstall") == "true" &&
            args.getString("swtvEmulator") == "phase4")
        assumeTrue("API29 emulator only", Build.VERSION.SDK_INT == 29 &&
            Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.contains("generic"))
        val fixture = args.getString("swtvFixture")
        require(fixture == "update" || fixture == "recovery") { "swtvFixture must be update or recovery" }
        val target = instrumentation.targetContext
        val config = requireNotNull(AndroidUpdateIdentity.releaseConfig(target)) { "A configured nondebug release is required" }
        val assets = instrumentation.context.assets
        val manifest = ManifestParser.parse(assets.open("$fixture.json").use { it.readBytes() })
        require(config.acceptsApk(manifest.versionName, manifest.apkUrl))
        val transport = object : UpdateTransport {
            override fun <T> withBody(uri: URI, maxBytes: Long, cancel: UpdateCancellation,
                redirectAllowed: (URI, URI) -> Boolean, read: (InputStream) -> T): T {
                val name = when (uri) {
                    config.feedUri -> "$fixture.json"
                    manifest.apkUrl -> "$fixture.apk"
                    else -> throw IOException("Unknown fixture request")
                }
                return assets.open(name).use { raw ->
                    val input = object : FilterInputStream(raw) {
                        private var received = 0L
                        private fun record(count: Int): Int {
                            cancel.check(); if (count > 0) received += count
                            if (received > maxBytes) throw IOException("Fixture exceeds response budget")
                            return count
                        }
                        override fun read(): Int { cancel.check(); val value = raw.read(); record(if (value < 0) -1 else 1); return value }
                        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                            cancel.check(); return record(raw.read(buffer, offset, count))
                        }
                    }
                    read(input).also { assertEquals("Reader must consume fixture to EOF", -1, input.read()) }
                }
            }
        }
        val activity = instrumentation.startActivitySync(Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync { activity.showUpdatePanel(AndroidUpdateIdentity.engine(target, config, transport)) }
        // The host drives the actual Check / Download / Install and OS permission/confirmation UI.
        // A successful self-update may end this instrumentation process; the host verifies package/version afterward.
        val hold = args.getString("swtvDriverTimeoutMs")?.toLongOrNull() ?: 180_000L
        require(hold in 10_000L..300_000L)
        val until = android.os.SystemClock.elapsedRealtime() + hold
        while (android.os.SystemClock.elapsedRealtime() < until && !activity.isDestroyed) Thread.sleep(200)
    }
}
