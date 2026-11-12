package app.swtv.home.web

object ShareAddress {
    const val MAX_TEXT = 8192
    fun parse(text: CharSequence?): String? {
        if (text == null || text.length > MAX_TEXT) return null
        val input = text.toString().trim()
        (WebsiteUrl.normalize(input) as? WebsiteUrlResult.Valid)?.let { return it.address.value }
        // Only extract an explicit HTTPS token from prose. More than one address/scheme is ambiguous.
        val tokens = input.split(Regex("\\s+"))
        val candidates = tokens.filter { it.contains(":") || it.contains(".") }
        if (candidates.size != 1) return null
        return WebNavigation.address(candidates.single())?.value
    }
}
object WebNavigation {
    fun address(url: String?): WebsiteAddress? {
        if (url == null || !url.startsWith("https://", ignoreCase = true)) return null
        return (WebsiteUrl.normalize(url) as? WebsiteUrlResult.Valid)?.address
    }
}
data class PointerPosition(val x: Int, val y: Int) {
    fun move(dx: Int, dy: Int, width: Int, height: Int) = PointerPosition(
        (x.toLong() + dx).coerceIn(0, (width - 1).coerceAtLeast(0).toLong()).toInt(),
        (y.toLong() + dy).coerceIn(0, (height - 1).coerceAtLeast(0).toLong()).toInt())
    fun escapesUp() = y == 0
}
enum class WebBack {
    EXIT_FULLSCREEN, HISTORY, CLOSE;
    companion object {
        fun decide(fullscreen: Boolean, history: Boolean) = when {
            fullscreen -> EXIT_FULLSCREEN
            history -> HISTORY
            else -> CLOSE
        }
    }
}
