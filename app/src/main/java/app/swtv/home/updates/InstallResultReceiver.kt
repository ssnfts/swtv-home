package app.swtv.home.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.lang.ref.WeakReference

/** Manifest registration exists in release only. No receiver accepts a download location. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { InstallResults.deliver(intent) }
}

internal object InstallResults {
    private var current = WeakReference<UpdateController>(null)
    fun register(controller: UpdateController) {
        current.get()?.takeUnless { it === controller }?.close()
        current = WeakReference(controller)
    }
    fun clear(controller: UpdateController) { if (current.get() === controller) current.clear() }
    fun deliver(intent: Intent) { current.get()?.installResult(intent) }
}
