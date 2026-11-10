package app.swtv.discovery

/** Dialog labels and selections share a snapshot; membership is resolved by stable ID. */
class CatalogPicker<T>(entries: List<T>, private val key: (T) -> String) {
    val choices = entries.toList()

    fun resolve(index: Int, current: List<T>): T? {
        val selected = choices.getOrNull(index) ?: return null
        val selectedKey = key(selected)
        return current.firstOrNull { key(it) == selectedKey }
    }
}
