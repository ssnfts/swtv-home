package app.swtv.launcher.ui

object FocusPolicy {
    fun focus(saved: String?, keys: List<String>) = saved?.takeIf { it in keys } ?: keys.firstOrNull()
}
