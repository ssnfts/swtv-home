package app.swtv.launcher.ui

import app.swtv.discovery.DockPolicy

/** Presentation preserves the stored sequence, including temporarily missing packages. */
object DockPresentation {
    data class Entry(val slot: Int, val packageName: String?) {
        val key: String get() = packageName?.let { "dock:$it" } ?: "slot:$slot"
    }

    fun pins(stored: List<String>): List<String> = DockPolicy.canonical(stored, null)

    fun entries(stored: List<String>): List<Entry> {
        val pins = pins(stored)
        val occupied = pins.mapIndexed { slot, pkg -> Entry(slot, pkg) }
        return if (pins.size < 6) occupied + Entry(pins.size, null) else occupied
    }
}
