package app.swtv.home.web

import android.content.Context

/** A successful write means the complete replacement string was committed durably. */
interface WebsiteStringStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

class WebsiteStore(private val storage: WebsiteStringStorage) {
    // One process-wide lock covers the complete read/modify/commit across repository/adapter instances.
    // This app has one process; this is not a multi-process SharedPreferences protocol.
    private companion object { val lock = Any() }

    /** Synchronous disk commit: call mutations on a worker thread, then render the returned snapshot. */
    fun snapshot(): WebsiteResult = synchronized(lock) { read() }
    fun save(title: String, address: String, id: String? = null): WebsiteResult = mutate { it.save(title, address, id) }
    fun remove(id: String): WebsiteResult = mutate { it.remove(id) }
    fun move(id: String, delta: Int): WebsiteResult = mutate { it.move(id, delta) }

    private fun read(): WebsiteResult = try { WebsiteCodec.decode(storage.read()) }
        catch (_: Exception) { WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA) }

    private fun mutate(change: (WebsiteFavorites) -> WebsiteResult): WebsiteResult = synchronized(lock) {
        val current = read()
        if (current is WebsiteResult.Failure) return@synchronized current
        val result = change((current as WebsiteResult.Success).favorites)
        if (result !is WebsiteResult.Success || !result.changed) return@synchronized result
        val committed = try { storage.write(WebsiteCodec.encode(result.favorites)) } catch (_: Exception) { false }
        if (committed) result else WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED)
    }
}

/** Separate private file; never reads or writes the existing home/config preferences. */
class SharedPreferencesWebsiteStorage(context: Context) : WebsiteStringStorage {
    private val preferences = context.applicationContext.getSharedPreferences("swtv_websites", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("favorites", null)
    override fun write(value: String): Boolean = preferences.edit().putString("favorites", value).commit()
}
