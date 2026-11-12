package app.swtv.home.web

import java.util.Collections
import java.util.UUID

data class WebsiteFavorite(val id: String, val title: String, val normalizedUrl: String) {
    val displayHost: String get() = (WebsiteUrl.normalize(normalizedUrl) as WebsiteUrlResult.Valid).address.host
}

enum class WebsiteError { INVALID_ADDRESS, INVALID_TITLE, CAPACITY, DUPLICATE_EDIT, NOT_FOUND, UNREADABLE_DATA, UNKNOWN_SCHEMA, PERSISTENCE_FAILED }

sealed class WebsiteResult {
    data class Success(val favorites: WebsiteFavorites, val favorite: WebsiteFavorite? = null, val changed: Boolean = false) : WebsiteResult()
    data class Failure(val error: WebsiteError) : WebsiteResult()
}

class WebsiteFavorites private constructor(items: List<WebsiteFavorite>) {
    val entries: List<WebsiteFavorite> = Collections.unmodifiableList(ArrayList(items))

    companion object {
        const val MAX_ENTRIES = 32
        const val MAX_TITLE_LENGTH = 100
        fun empty() = WebsiteFavorites(emptyList())
        internal fun restored(entries: List<WebsiteFavorite>) = WebsiteFavorites(entries)
        internal fun validTitle(title: String) = title.length <= MAX_TITLE_LENGTH && title.none { it.isISOControl() }
    }

    fun save(title: String, address: String, id: String? = null): WebsiteResult {
        val index = if (id == null) -1 else entries.indexOfFirst { it.id == id }
        if (id != null && index < 0) return WebsiteResult.Failure(WebsiteError.NOT_FOUND)
        val url = (WebsiteUrl.normalize(address) as? WebsiteUrlResult.Valid)?.address
            ?: return WebsiteResult.Failure(WebsiteError.INVALID_ADDRESS)
        if (!validTitle(title)) return WebsiteResult.Failure(WebsiteError.INVALID_TITLE)
        val resolvedTitle = title.trim().ifEmpty { url.host.take(MAX_TITLE_LENGTH) }
        val duplicate = entries.firstOrNull { it.normalizedUrl == url.value && it.id != id }
        if (duplicate != null) return if (id == null) WebsiteResult.Success(this, duplicate)
            else WebsiteResult.Failure(WebsiteError.DUPLICATE_EDIT)
        if (id == null && entries.size >= MAX_ENTRIES) return WebsiteResult.Failure(WebsiteError.CAPACITY)
        val item = WebsiteFavorite(id ?: UUID.randomUUID().toString(), resolvedTitle, url.value)
        if (index >= 0 && entries[index] == item) return WebsiteResult.Success(this, item)
        val updated = entries.toMutableList()
        if (index < 0) updated.add(item) else updated[index] = item
        return WebsiteResult.Success(WebsiteFavorites(updated), item, true)
    }

    fun remove(id: String): WebsiteResult {
        val item = entries.firstOrNull { it.id == id } ?: return WebsiteResult.Failure(WebsiteError.NOT_FOUND)
        return WebsiteResult.Success(WebsiteFavorites(entries.filterNot { it.id == id }), item, true)
    }

    fun move(id: String, delta: Int): WebsiteResult {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return WebsiteResult.Failure(WebsiteError.NOT_FOUND)
        val target = (index.toLong() + delta.toLong()).coerceIn(0L, entries.lastIndex.toLong()).toInt()
        if (target == index) return WebsiteResult.Success(this, entries[index])
        val updated = entries.toMutableList()
        val item = updated.removeAt(index)
        updated.add(target, item)
        return WebsiteResult.Success(WebsiteFavorites(updated), item, true)
    }
}
