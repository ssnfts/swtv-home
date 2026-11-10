package app.swtv.discovery

import java.util.Locale

/** Pure catalog operations; discovery and launching stay in the Android layer. */
object CatalogRules {
    internal const val SELF_PACKAGE = "app.swtv.home"
    internal fun validPackage(value: String): Boolean =
        value != SELF_PACKAGE && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))

    fun deduplicate(entries: List<AppEntry>): List<AppEntry> = entries
        .filter { validPackage(it.packageName) && it.className.isNotBlank() }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })

    fun dock(entries: List<AppEntry>, pins: List<String>, limit: Int = 6): List<AppEntry> {
        val available = deduplicate(entries).associateBy { it.packageName }
        return pins.distinct().mapNotNull { available[it] }.take(limit.coerceIn(0, 6))
    }

    fun visible(entries: List<AppEntry>, hidden: Set<String>): List<AppEntry> =
        deduplicate(entries).filterNot { it.packageName in hidden }

    fun movePin(pins: List<String>, packageName: String, newIndex: Int): List<String> {
        val result = pins.filter { validPackage(it) }.distinct().toMutableList()
        if (!validPackage(packageName)) return result.take(6)
        result.remove(packageName)
        result.add(newIndex.coerceIn(0, result.size), packageName)
        return result.take(6)
    }
}
