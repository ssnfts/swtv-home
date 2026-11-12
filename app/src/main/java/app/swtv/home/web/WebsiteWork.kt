package app.swtv.home.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/** Only immutable commands enter the worker. UI subscribers are removed on pause/close.
 * shutdown drains already authorized commits; it never interrupts SharedPreferences.commit(). */
class WebsiteWork(context: Context) : AutoCloseable {
    sealed class Command {
        object Snapshot : Command()
        data class Save(val title: String, val address: String, val id: String? = null) : Command()
        data class Remove(val id: String) : Command()
        data class Move(val id: String, val delta: Int) : Command()
    }
    private val store = WebsiteStore(SharedPreferencesWebsiteStorage(context.applicationContext))
    private val handler = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(16), { runnable -> Thread(runnable, "swtv-websites") })
    private val callbacks = mutableMapOf<Long, (WebsiteResult) -> Unit>()
    private var next = 0L
    fun submit(command: Command, callback: (WebsiteResult) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val token = ++next
        callbacks[token] = callback
        try {
            executor.execute {
                val result = when (command) {
                    Command.Snapshot -> store.snapshot()
                    is Command.Save -> store.save(command.title, command.address, command.id)
                    is Command.Remove -> store.remove(command.id)
                    is Command.Move -> store.move(command.id, command.delta)
                }
                handler.post { callbacks.remove(token)?.invoke(result) }
            }
        } catch (_: RejectedExecutionException) {
            callbacks.remove(token)?.invoke(WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED))
        }
    }
    fun detach() { callbacks.clear() }
    override fun close() { detach(); executor.shutdown() }
}
