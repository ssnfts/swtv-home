package app.swtv.home.web

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object WebsiteCodec {
    const val MAX_DOCUMENT_LENGTH = 512 * 1024

    /** Bad documents fail as a whole: never discard entries or interpret future schemas as empty. */
    fun decode(text: String?): WebsiteResult {
        if (text == null) return WebsiteResult.Success(WebsiteFavorites.empty())
        val unreadable = WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA)
        if (text.length > MAX_DOCUMENT_LENGTH || !StrictJson(text).valid()) return unreadable
        return try {
            val parser = JSONTokener(text)
            val document = parser.nextValue() as? JSONObject ?: return unreadable
            if (parser.nextClean() != '\u0000') return unreadable
            val version = document.opt("schemaVersion") as? Int ?: return unreadable
            if (version != 1) return WebsiteResult.Failure(WebsiteError.UNKNOWN_SCHEMA)
            val array = document.opt("entries") as? JSONArray ?: return unreadable
            if (array.length() > WebsiteFavorites.MAX_ENTRIES) return unreadable
            val entries = ArrayList<WebsiteFavorite>()
            val ids = HashSet<String>()
            val urls = HashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.opt(index) as? JSONObject ?: return unreadable
                val id = item.opt("id") as? String ?: return unreadable
                val title = item.opt("title") as? String ?: return unreadable
                val url = item.opt("url") as? String ?: return unreadable
                if (!Regex("[A-Za-z0-9_-]{1,100}").matches(id) || !ids.add(id)) return unreadable
                if (title.isBlank() || title != title.trim() || !WebsiteFavorites.validTitle(title)) return unreadable
                val normalized = (WebsiteUrl.normalize(url) as? WebsiteUrlResult.Valid)?.address?.value ?: return unreadable
                if (url != normalized || !urls.add(normalized)) return unreadable
                entries.add(WebsiteFavorite(id, title, normalized))
            }
            WebsiteResult.Success(WebsiteFavorites.restored(entries))
        } catch (_: Exception) { unreadable }
    }

    fun encode(favorites: WebsiteFavorites): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("entries", JSONArray().apply {
            favorites.entries.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("url", item.normalizedUrl)
            }) }
        })
    }.toString()
}

/** JSONObject is deliberately lenient on Android. Validate JSON syntax and nesting before parsing. */
private class StrictJson(private val text: String) {
    private var position = 0
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
    private fun take(char: Char): Boolean {
        whitespace()
        if (position >= text.length || text[position] != char) return false
        position++
        return true
    }
    fun valid(): Boolean = value(0) && run { whitespace(); position == text.length }

    private fun value(depth: Int): Boolean {
        whitespace()
        if (depth > 6 || position >= text.length) return false
        return when (text[position]) {
            '{' -> {
                position++
                if (take('}')) true else {
                    val keys = HashSet<String>()
                    do {
                        whitespace()
                        val start = position
                        if (!string()) return false
                        val key = JSONTokener(text.substring(start, position)).nextValue() as String
                        if (!keys.add(key) || !take(':') || !value(depth + 1)) return false
                    } while (take(','))
                    take('}')
                }
            }
            '[' -> {
                position++
                if (take(']')) true else {
                    do { if (!value(depth + 1)) return false } while (take(','))
                    take(']')
                }
            }
            '"' -> string()
            else -> {
                val start = position
                while (position < text.length && text[position] !in " \t\r\n,]}:") position++
                val token = text.substring(start, position)
                token in setOf("true", "false", "null") || number.matches(token)
            }
        }
    }

    private fun string(): Boolean {
        if (position >= text.length || text[position++] != '"') return false
        while (position < text.length) {
            val char = text[position++]
            if (char == '"') return true
            if (char < ' ') return false
            if (char == '\\') {
                if (position >= text.length) return false
                when (text[position++]) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                    'u' -> {
                        if (position + 4 > text.length || text.substring(position, position + 4).any { it !in "0123456789abcdefABCDEF" }) return false
                        position += 4
                    }
                    else -> return false
                }
            }
        }
        return false
    }
}
