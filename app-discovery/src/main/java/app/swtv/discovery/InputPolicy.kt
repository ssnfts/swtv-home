package app.swtv.discovery

object InputPolicy {
    // TvInputManager's public states: CONNECTED=0, CONNECTED_STANDBY=1, DISCONNECTED=2.
    fun available(passthrough: Boolean, state: Int): Boolean = passthrough && state in 0..1
}
