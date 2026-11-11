package app.swtv.home.updates

internal class InstallConsent {
    var open = true
    var foreground = true
    var busy = false
    var sessionId: Int? = null
    var nonce: String? = null
    fun canStart(verified: Boolean, permission: Boolean): Boolean =
        open && foreground && !busy && sessionId == null && verified && permission
    fun acceptsResult(id: Int, token: String?): Boolean =
        open && sessionId == id && nonce != null && nonce == token
}
