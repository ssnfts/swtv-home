package app.swtv.home.web

class WebSession {
    var foreground = false
        private set
    private var closed = false
    private var generation = 0L
    fun resume() { if (!closed) foreground = true }
    fun pause() { foreground = false; ++generation }
    fun close() { pause(); closed = true }
    fun nextRequest() = ++generation
    fun accepts(token: Long) = foreground && !closed && generation == token
}
