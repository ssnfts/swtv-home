package app.swtv.home.updates

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class UpdateWorker : Closeable {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1))
    fun submit(action: () -> Unit): Boolean = try {
        executor.execute(action); true
    } catch (_: RejectedExecutionException) { false }
    fun cancelQueued() { executor.queue.clear() }
    override fun close() { executor.shutdownNow() }
}
