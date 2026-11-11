package app.swtv.home.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.swtv.updates.*
import java.io.Closeable
import java.io.File
import java.util.UUID

data class UpdatePanelState(val message: String, val busy: Boolean = false,
    val download: Boolean = false, val install: Boolean = false)

/** One panel owns one bounded worker. Work captures application context, never an Activity. */
class UpdateController(context: Context, private val engine: UpdateEngine?) : Closeable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = UpdateWorker()
    private val installer = this.context.packageManager.packageInstaller
    private val consent = InstallConsent()
    private var cancellation = UpdateCancellation()
    private var generation = 0L
    private var watchdog: Runnable? = null
    private var offered: ReleaseManifest? = null
    private var verified: VerifiedUpdate? = null
    private var committed = false
    private var pendingConfirmation: Intent? = null
    private var callback: ((UpdatePanelState) -> Unit)? = null
    private var confirmation: ((Intent) -> Unit)? = null
    private var state = UpdatePanelState(if (engine == null) "Release updates are not configured." else "Choose Check for updates.")

    init {
        // Abandon only our app's stale sessions after a previous process ended.
        installer.mySessions.filter { it.appPackageName == this.context.packageName }.forEach {
            runCatching { installer.abandonSession(it.sessionId) }
        }
        File(this.context.cacheDir, "release-updates").listFiles()?.filter {
            it.name.startsWith("swtv-update-") && it.name.endsWith(".apk")
        }?.forEach { it.delete() }
        InstallResults.register(this)
    }

    fun observe(update: (UpdatePanelState) -> Unit, requestConfirmation: (Intent) -> Unit) {
        callback = update; confirmation = requestConfirmation; update(state)
    }
    fun configured(): Boolean = engine != null
    fun permissionGranted(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun foreground(value: Boolean) {
        consent.foreground = value
        if (!value && !committed && engine != null) {
            cancelOperation()
            publish(state.copy(busy = false, message = "Paused. Choose an action to retry.",
                download = offered != null && verified == null, install = verified != null))
        }
        if (value && pendingConfirmation != null) {
            val intent = pendingConfirmation!!; pendingConfirmation = null
            confirmation?.invoke(intent)
        }
        if (value && consent.open) callback?.invoke(state)
    }

    fun check() {
        if (!ready()) return
        verified?.close(); verified = null; offered = null
        run("Checking for updates…") { token ->
            val result = engine!!.check(token)
            Completed({ when (result) {
                UpdateCheck.UpToDate -> publish(UpdatePanelState("You have the latest release."))
                is UpdateCheck.Available -> {
                    offered = result.manifest
                    publish(UpdatePanelState("Version ${result.manifest.versionName} · ${result.manifest.apkBytes} bytes is available.", download = true))
                }
            } })
        }
    }

    fun download() {
        if (!ready()) return
        val manifest = offered ?: return
        run("Downloading and verifying…") { token ->
            val update = engine!!.download(manifest, token)
            // A cancelled generation must release a completed result too.
            token.checkOrClose(update)
            Completed({ verified?.close(); verified = update
                publish(UpdatePanelState("Version ${manifest.versionName} verified. Choose Install update.", install = true)) }, update::close)
        }
    }

    fun install() {
        if (!consent.canStart(verified != null, permissionGranted())) {
            if (verified != null && !permissionGranted()) publish(UpdatePanelState(
                "Allow this source in Settings, return here, then choose Install update again.", install = true))
            return
        }
        val update = verified ?: return
        run("Preparing Android installation…") { token ->
            engine!!.revalidate(update, token)
            token.check()
            if (!permissionGranted()) throw SecurityException("Install source permission required")
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName); setSize(update.manifest.apkBytes)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val id = synchronized(sessionLock) {
                token.check()
                activeSession?.let { runCatching { installer.abandonSession(it) } }
                installer.createSession(params).also { activeSession = it }
            }
            var staged = false
            try {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, update.manifest.apkBytes).use { output ->
                        update.file.inputStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) { token.check(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                        }
                        session.fsync(output)
                    }
                }
                token.check()
                staged = true
                Completed({ commit(id) }, { abandonSession(id) })
            } finally {
                if (!staged) abandonSession(id)
                update.close()
            }
        }
    }

    private fun commit(id: Int) {
        verified = null
        if (!consent.open || !consent.foreground || !permissionGranted()) {
            abandonSession(id)
            publish(UpdatePanelState("Installation cancelled. Download again to retry.")); return
        }
        val nonce = UUID.randomUUID().toString()
        consent.sessionId = id; consent.nonce = nonce
        val result = Intent(context, InstallResultReceiver::class.java).apply {
            action = "app.swtv.home.update.INSTALL_RESULT"
            data = Uri.parse("swtv-install://result/$id/$nonce")
            putExtra("swtv.session", id); putExtra("swtv.nonce", nonce)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        try {
            installer.openSession(id).use { it.commit(PendingIntent.getBroadcast(context, id, result, flags).intentSender) }
            committed = true
            publish(UpdatePanelState("Waiting for Android installation confirmation…", busy = true))
        } catch (_: Exception) { abandon(); publish(UpdatePanelState("Android could not start installation. Check for updates to retry.")) }
    }

    internal fun installResult(intent: Intent) {
        val id = intent.getIntExtra("swtv.session", -1)
        if (!consent.acceptsResult(id, intent.getStringExtra("swtv.nonce")) ||
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val request = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (request == null) { abandon(); publish(UpdatePanelState("Android confirmation is unavailable. Check for updates to retry.")) }
                else if (consent.foreground) confirmation?.invoke(request) else pendingConfirmation = request
            }
            PackageInstaller.STATUS_SUCCESS -> { clearSession(); publish(UpdatePanelState("Update installed. Reopen About to see the installed version.")) }
            else -> { abandon(); publish(UpdatePanelState("Installation cancelled or refused by Android. Check for updates to retry.")) }
        }
    }

    private fun ready() = engine != null && consent.open && consent.foreground && !consent.busy && consent.sessionId == null
    private fun publish(next: UpdatePanelState) { state = next; if (consent.open) callback?.invoke(next) }
    private class Completed(val apply: () -> Unit, val discard: () -> Unit = {})
    private fun run(message: String, action: (UpdateCancellation) -> Completed) {
        cancelOperation()
        val token = UpdateCancellation().also { cancellation = it }
        val request = generation
        val timeout = Runnable {
            if (consent.open && generation == request) {
                token.cancel(); generation++; consent.busy = false
                verified?.close(); verified = null
                publish(UpdatePanelState("Update timed out. Choose Check for updates to retry."))
            }
        }.also { watchdog = it }
        main.postDelayed(timeout, 120_000L)
        consent.busy = true
        publish(UpdatePanelState(message, busy = true))
        val accepted = worker.submit {
            try {
                val complete = action(token)
                main.post {
                    main.removeCallbacks(timeout)
                    if (consent.open && generation == request) { consent.busy = false; complete.apply() }
                    else complete.discard()
                }
            } catch (error: Exception) {
                main.post { if (consent.open && generation == request) {
                    main.removeCallbacks(timeout)
                    consent.busy = false
                    verified?.close(); verified = null
                    publish(UpdatePanelState((error as? UpdateException)?.failure?.message ?: "Update operation failed. Try checking again.", download = offered != null))
                } }
            }
        }
        if (!accepted) {
            main.removeCallbacks(timeout); consent.busy = false
            publish(UpdatePanelState("Update worker is busy. Choose Check for updates to retry."))
        }
    }

    private fun cancelOperation() {
        generation++; cancellation.cancel(); worker.cancelQueued(); consent.busy = false
        watchdog?.let(main::removeCallbacks); watchdog = null
    }
    private fun clearSession() {
        synchronized(sessionLock) { if (activeSession == consent.sessionId) activeSession = null }
        consent.sessionId = null; consent.nonce = null; committed = false; pendingConfirmation = null
    }
    private fun abandonSession(id: Int) = synchronized(sessionLock) {
        runCatching { installer.abandonSession(id) }
        if (activeSession == id) activeSession = null
    }
    private fun abandon() { consent.sessionId?.let(::abandonSession); clearSession() }
    override fun close() {
        if (!consent.open) return
        consent.open = false; callback = null; confirmation = null
        cancelOperation(); abandon(); verified?.close(); verified = null
        InstallResults.clear(this); worker.close()
    }

    companion object {
        private val sessionLock = Any()
        private var activeSession: Int? = null
    }
}

private fun UpdateCancellation.checkOrClose(update: VerifiedUpdate) {
    try { check() } catch (error: Exception) { update.close(); throw error }
}
