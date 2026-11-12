package app.swtv.home.web

import java.net.IDN
import java.net.URI
import java.util.Locale

data class WebsiteAddress(val value: String, val host: String)

sealed class WebsiteUrlResult {
    data class Valid(val address: WebsiteAddress) : WebsiteUrlResult()
    data class Invalid(val reason: WebsiteUrlError) : WebsiteUrlResult()
}

enum class WebsiteUrlError { EMPTY, TOO_LONG, UNSAFE_CHARACTER, INVALID_SCHEME, INVALID_AUTHORITY, INVALID_HOST, INVALID_PORT, MALFORMED }

object WebsiteUrl {
    const val MAX_LENGTH = 2048

    /** No DNS or network operations. Encoded path, query and fragment are retained verbatim. */
    fun normalize(input: String): WebsiteUrlResult {
        fun invalid(reason: WebsiteUrlError) = WebsiteUrlResult.Invalid(reason)
        if (input.length > MAX_LENGTH) return invalid(WebsiteUrlError.TOO_LONG)
        if (input.any { it.isISOControl() || it == '\\' }) return invalid(WebsiteUrlError.UNSAFE_CHARACTER)
        val text = input.trim(' ')
        if (text.isEmpty()) return invalid(WebsiteUrlError.EMPTY)
        if (text.any { it.isWhitespace() }) return invalid(WebsiteUrlError.UNSAFE_CHARACTER)
        if (text.startsWith("//")) return invalid(WebsiteUrlError.INVALID_AUTHORITY)
        val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").find(text)
        val bareHostPort = Regex("^[^/?#:]+\\.[^/?#:]+:[0-9]+(?:[/?#].*)?$").matches(text)
        if (explicitScheme != null && !text.startsWith("https://", ignoreCase = true) && !bareHostPort) {
            return invalid(WebsiteUrlError.INVALID_SCHEME)
        }
        val uri = try { URI(if (explicitScheme == null || bareHostPort) "https://$text" else text) }
        catch (_: Exception) { return invalid(WebsiteUrlError.MALFORMED) }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque) return invalid(WebsiteUrlError.INVALID_SCHEME)
        val authority = uri.rawAuthority ?: return invalid(WebsiteUrlError.INVALID_AUTHORITY)
        if (authority.isEmpty() || '@' in authority || '%' in authority) return invalid(WebsiteUrlError.INVALID_AUTHORITY)
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return invalid(WebsiteUrlError.INVALID_HOST)
            val literal = authority.substring(0, close + 1)
            // URI parses numeric IPv6 literals locally, without name resolution.
            if (uri.host != literal || ':' !in literal) return invalid(WebsiteUrlError.INVALID_HOST)
            host = literal.lowercase(Locale.ROOT)
            val suffix = authority.substring(close + 1)
            if (suffix.isNotEmpty() && !suffix.startsWith(":")) return invalid(WebsiteUrlError.INVALID_AUTHORITY)
            portText = if (suffix.isEmpty()) null else suffix.substring(1)
        } else {
            if (authority.count { it == ':' } > 1) return invalid(WebsiteUrlError.INVALID_AUTHORITY)
            val rawHost = authority.substringBefore(':')
            portText = if (':' in authority) authority.substringAfter(':') else null
            host = try { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
            catch (_: IllegalArgumentException) { return invalid(WebsiteUrlError.INVALID_HOST) }
            if (host.length !in 1..253 || host.endsWith('.') || host.split('.').any {
                    it.length !in 1..63 || !Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?").matches(it)
                }) return invalid(WebsiteUrlError.INVALID_HOST)
            // Chromium treats a numeric final label as an IPv4 candidate, including hex/short forms.
            // Only canonical four-decimal IPv4 may cross this validator/browser boundary.
            val lastLabel = host.substringAfterLast('.')
            if (lastLabel.all { it in '0'..'9' } || Regex("0x[0-9a-f]*").matches(lastLabel)) {
                val parts = host.split('.')
                if (parts.size != 4 || parts.any {
                        !Regex("0|[1-9][0-9]*").matches(it) || it.toIntOrNull() !in 0..255
                    }) {
                    return invalid(WebsiteUrlError.INVALID_HOST)
                }
            }
        }
        val port = portText?.toIntOrNull()
        if (portText != null && (port !in 1..65535 || port.toString() != portText)) return invalid(WebsiteUrlError.INVALID_PORT)
        val value = buildString {
            append("https://"); append(host)
            if (port != null && port != 443) { append(':'); append(port) }
            append(uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/")
            uri.rawQuery?.let { append('?'); append(it) }
            uri.rawFragment?.let { append('#'); append(it) }
        }
        if (value.length > MAX_LENGTH) return invalid(WebsiteUrlError.TOO_LONG)
        return WebsiteUrlResult.Valid(WebsiteAddress(value, host))
    }
}
