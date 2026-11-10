package app.swtv.home.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.Button
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One running decode, at most 48 queued packages, and weak current-screen UI bindings. */
class Icons(context: Context) {
    private val appContext = context.applicationContext
    private val cache = LruCache<String, Bitmap>(48)
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(48)
    )
    private val work = IconWorkPolicy(49)
    private val handler = Handler(Looper.getMainLooper())
    private val bindings = mutableMapOf<String, MutableList<WeakReference<Button>>>()
    private var generation = work.beginGeneration()
    private var closed = false

    /** Called on rebuild/pause; old work has no view references and cannot publish its result. */
    fun beginScreen() {
        generation = work.beginGeneration()
        executor.queue.clear()
        bindings.clear()
    }

    fun invalidate() { cache.evictAll() }

    fun bind(button: Button, pkg: String) {
        request(button, pkg)
        // Work rejected under pressure is retried when the user focuses this tile.
        val previous = button.onFocusChangeListener
        button.setOnFocusChangeListener { view, focused ->
            previous?.onFocusChange(view, focused)
            if (focused) request(view as Button, pkg)
        }
    }

    private fun request(button: Button, pkg: String) {
        if (closed) return
        cache.get(pkg)?.let { apply(button, it); return }
        bindings[pkg]?.let { targets ->
            targets.removeAll { it.get() == null }
            if (targets.none { it.get() === button } && targets.size < 2) {
                targets.add(WeakReference(button))
            }
            return
        }
        val ticket = work.reserve(pkg, generation) ?: return
        bindings[pkg] = mutableListOf(WeakReference(button))
        try {
            // This closure holds only package/ticket and this loader, never a Button or Activity.
            executor.execute { decode(ticket) }
        } catch (_: RejectedExecutionException) {
            bindings.remove(pkg)
            work.finish(ticket)
        }
    }

    private fun decode(ticket: IconWorkPolicy.Ticket) {
        if (!work.isCurrent(ticket)) return
        val image = try {
            val icon = appContext.packageManager.getApplicationIcon(ticket.packageName)
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
                icon.setBounds(0, 0, 64, 64)
                icon.draw(Canvas(it))
            }
        } catch (_: Exception) { null }
        handler.post {
            if (work.isCurrent(ticket) && !closed) {
                if (image != null) {
                    cache.put(ticket.packageName, image)
                    bindings.remove(ticket.packageName)?.forEach { target ->
                        target.get()?.let { apply(it, image) }
                    }
                } else bindings.remove(ticket.packageName)
            }
            work.finish(ticket)
        }
    }

    private fun apply(button: Button, image: Bitmap) {
        val drawable = BitmapDrawable(appContext.resources, image)
        val size = (40 * appContext.resources.displayMetrics.density).toInt()
        drawable.setBounds(0, 0, size, size)
        button.setCompoundDrawables(null, drawable, null, null)
        button.compoundDrawablePadding = 8
    }

    fun close() {
        closed = true
        beginScreen()
        executor.shutdownNow()
        cache.evictAll()
    }
}
