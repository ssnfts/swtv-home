package app.swtv.home.platform

/** One sequence drives rendered slots and edits. A null catalog means discovery is unknown. */
object DockPolicy {
    fun canonical(pins: List<String>, available: Set<String>?): List<String> =
        pins.distinct().filter { available == null || it in available }.take(6)

    fun assign(pins: List<String>, slot: Int, selected: String): List<String> {
        val result = pins.distinct().take(6).toMutableList()
        if (slot !in 0..5) return result
        val existing = result.indexOf(selected)
        val occupant = result.getOrNull(slot)
        if (existing == slot) return result
        if (occupant != null && existing >= 0) {
            result[existing] = occupant
            result[slot] = selected
        } else if (occupant != null) {
            result[slot] = selected
        } else {
            result.remove(selected)
            result.add(selected)
        }
        return result
    }

    /** null explicitly reports a full dock; never silently truncates the user's selection. */
    fun pin(pins: List<String>, selected: String): List<String>? {
        val canonical = pins.distinct().take(6)
        if (selected in canonical) return canonical
        return if (canonical.size < 6) canonical + selected else null
    }
}
