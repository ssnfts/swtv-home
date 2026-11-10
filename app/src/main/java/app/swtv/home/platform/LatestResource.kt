package app.swtv.home.platform

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One decode plus one latest replacement; only the latest subscriber is retained. */
class LatestResource<T>(
    private val decode: (String) -> T?,
    private val foreground: Executor,
    private val discard: (T) -> Unit = {}
) {
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), ThreadPoolExecutor.DiscardOldestPolicy())
    @Volatile private var generation = 0
    private var activeKey: String? = null
    private var callback: ((T?) -> Unit)? = null
    private var cachedKey: String? = null
    private var cached: T? = null
    private var hasCache = false
    private var closed = false

    @Synchronized fun request(key: String, done: (T?) -> Unit) {
        if (closed) return
        if (hasCache && cachedKey == key) {
            cancelPending()
            done(cached)
            return
        }
        if (activeKey == key) {
            callback = done
            return
        }
        cancelPending()
        activeKey = key
        callback = done
        val token = generation
        worker.execute {
            if (token != generation) return@execute
            val result = try { decode(key) } catch (_: Exception) { null }
            foreground.execute { complete(key, token, result) }
        }
    }

    @Synchronized private fun complete(key: String, token: Int, result: T?) {
        if (closed || token != generation) {
            result?.let(discard)
            return
        }
        cachedKey = key
        cached = result
        hasCache = true
        activeKey = null
        val target = callback
        callback = null
        target?.invoke(result)
    }

    fun pendingCount() = worker.queue.size

    @Synchronized fun cancelPending() {
        generation++
        activeKey = null
        callback = null
        worker.queue.clear()
    }

    @Synchronized fun close() {
        closed = true
        cancelPending()
        worker.shutdownNow()
        // Existing UI drawables can still reference a cached bitmap; release ownership without recycling it.
        cached = null
        cachedKey = null
        hasCache = false
    }
}
