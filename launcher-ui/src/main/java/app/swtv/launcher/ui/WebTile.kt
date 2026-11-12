package app.swtv.launcher.ui

/** Display-only data. URL policy, repository and WebView remain in the application. */
data class WebTile(val id: String, val title: String, val host: String) {
    val key: String get() = "web:$id"
}
data class WebTileActions(val open: (String) -> Unit, val edit: (String) -> Unit, val add: () -> Unit)
