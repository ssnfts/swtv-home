package app.swtv.home.web

import android.content.Context
import android.content.SharedPreferences
import java.util.IdentityHashMap

/** A successful write means the complete replacement string was committed durably. */
interface WebsiteStringStorage {
    /** Adapters for the same backing store must return the same stable identity object. */
    val coordinationKey: Any get() = this
    fun read(): String?
    fun write(value: String): Boolean
}

class WebsiteStore(private val storage: WebsiteStringStorage) {
    // One process-wide lock covers the complete read/modify/commit across repository/adapter instances.
    // This app has one process; this is not a multi-process SharedPreferences protocol.
    private companion object {
        val lock = Any()
        // Keep the last confirmed document, not a failed write's possibly changed readable cache.
        val failedWrites = IdentityHashMap<Any, FailedWrite>()
    }
    private data class FailedWrite(val confirmedText: String?)

    /** Synchronous disk commit: call mutations on a worker thread, then render the returned snapshot. */
    fun snapshot(): WebsiteResult = synchronized(lock) { read() }
    fun save(title: String, address: String, id: String? = null): WebsiteResult = mutate { it.save(title, address, id) }
    fun remove(id: String): WebsiteResult = mutate { it.remove(id) }
    fun move(id: String, delta: Int): WebsiteResult = mutate { it.move(id, delta) }

    private fun confirmedText(): String? {
        val pending = failedWrites[storage.coordinationKey]
        return if (pending != null) pending.confirmedText else storage.read()
    }

    private fun read(): WebsiteResult = try { WebsiteCodec.decode(confirmedText()) }
        catch (_: Exception) { WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA) }

    private fun mutate(change: (WebsiteFavorites) -> WebsiteResult): WebsiteResult = synchronized(lock) {
        val key = storage.coordinationKey
        val text = try { confirmedText() } catch (_: Exception) {
            return@synchronized WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA)
        }
        val current = WebsiteCodec.decode(text)
        if (current is WebsiteResult.Failure) return@synchronized current
        val result = change((current as WebsiteResult.Success).favorites)
        if (result !is WebsiteResult.Success || (!result.changed && !failedWrites.containsKey(key))) return@synchronized result
        val committed = try { storage.write(WebsiteCodec.encode(result.favorites)) } catch (_: Exception) { false }
        if (committed) {
            failedWrites.remove(key)
            result
        } else {
            if (!failedWrites.containsKey(key)) failedWrites[key] = FailedWrite(text)
            WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED)
        }
    }
}

/** Separate private file; never reads or writes the existing home/config preferences. */
class SharedPreferencesWebsiteStorage internal constructor(private val preferences: SharedPreferences) : WebsiteStringStorage {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("swtv_websites", Context.MODE_PRIVATE))
    override val coordinationKey: Any get() = preferences
    override fun read(): String? = preferences.getString("favorites", null)
    override fun write(value: String): Boolean {
        // A failed commit can have changed Android's cache. An equal putString retry may skip disk.
        // Flipping this owned marker guarantees a changed map entry and a fresh disk-write attempt.
        val nextToggle = !preferences.getBoolean("_commit_toggle", false)
        return preferences.edit().putString("favorites", value).putBoolean("_commit_toggle", nextToggle).commit()
    }
}
