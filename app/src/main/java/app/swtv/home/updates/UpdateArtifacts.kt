package app.swtv.home.updates

import java.io.Closeable

/** Main-thread UI owns the slot; a staging worker must claim and then close its own artifact. */
internal class UpdateArtifacts<T : Closeable> : Closeable {
    private var current: T? = null
    @Synchronized fun peek(): T? = current
    @Synchronized fun replace(value: T) { current?.close(); current = value }
    @Synchronized fun claim(expected: T, checkActive: () -> Unit): T? {
        checkActive()
        return current?.takeIf { it === expected }?.also { current = null }
    }
    @Synchronized override fun close() { current?.close(); current = null }
}
