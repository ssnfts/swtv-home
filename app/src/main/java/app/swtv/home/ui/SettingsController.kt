package app.swtv.home.ui

import android.app.AlertDialog
import android.widget.EditText
import app.swtv.home.MainActivity
import app.swtv.home.core.CatalogRules
import app.swtv.home.core.LauncherConfig
import app.swtv.home.core.ModuleTile
import app.swtv.home.platform.CatalogPicker
import app.swtv.home.platform.DockPolicy

class SettingsController(private val activity: MainActivity) {
    private val config get() = activity.config
    private val themes = listOf("auto", "light", "dark")
    private val accents = listOf("cyan", "violet", "blue", "green", "amber", "rose")

    fun show() {
        options("Settings", listOf(
            "Dock slots & order", "Grid columns: ${config.columns}", "Theme: ${config.theme}",
            "Accent: ${config.accent}", "Reduced motion: ${config.reducedMotion}",
            "Phone apps in grid: ${config.showPhoneApps}", "Unhide apps", "Custom modules",
            "Wallpaper", "Weather", "OwnTV target", "Reset configuration"
        )) { choice ->
            when (choice) {
                0 -> options("Dock slots", (1..6).map { "Slot $it" }) { dockSlot(it) }
                1 -> options("Grid columns", (3..8).map { it.toString() }) {
                    activity.save(config.copy(columns = it + 3))
                }
                2 -> options("Theme", themes) { activity.save(config.copy(theme = themes[it])) }
                3 -> options("Accent", accents) { activity.save(config.copy(accent = accents[it])) }
                4 -> activity.save(config.copy(reducedMotion = !config.reducedMotion))
                5 -> activity.save(config.copy(showPhoneApps = !config.showPhoneApps))
                6 -> unhide()
                7 -> modules()
                8 -> wallpaper()
                9 -> weather()
                10 -> iptv()
                11 -> AlertDialog.Builder(activity).setTitle("Reset all local configuration?")
                    .setPositiveButton("Reset") { _, _ -> activity.save(LauncherConfig()) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun options(title: String, items: List<String>, choose: (Int) -> Unit) {
        if (items.isEmpty()) {
            activity.notice(title, "No available choices on this device.")
            return
        }
        AlertDialog.Builder(activity).setTitle(title).setItems(items.toTypedArray()) { _, index ->
            choose(index)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun edit(title: String, value: String, done: (String) -> Unit) {
        val field = EditText(activity).apply {
            setText(value)
            isSingleLine = true
            selectAll()
        }
        AlertDialog.Builder(activity).setTitle(title).setView(field)
            .setPositiveButton("Save") { _, _ -> done(field.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
        field.requestFocus()
    }

    fun dockSlot(index: Int) {
        val occupant = activity.dockPackages.getOrNull(index)
        options("Dock slot ${index + 1}", listOf("Choose / swap app", "Move left", "Move right", "Clear slot")) {
            val pins = activity.dockPackages
            val currentIndex = if (occupant == null) index else pins.indexOf(occupant)
            if (currentIndex < 0 || (occupant == null && index < pins.size)) {
                dockChanged()
                return@options
            }
            when (it) {
                0 -> chooseDockApp(currentIndex)
                1, 2 -> if (occupant != null) {
                    val next = currentIndex + if (it == 1) -1 else 1
                    activity.save(config.copy(dock = CatalogRules.movePin(pins, occupant, next)))
                }
                3 -> if (occupant != null) activity.save(config.copy(dock = pins - occupant))
            }
        }
    }

    private fun dockChanged() {
        activity.notice("Dock changed", "The selected slot changed while the dialog was open. Reopen dock settings to choose a current slot.")
    }

    private fun chooseDockApp(index: Int) {
        val occupant = activity.dockPackages.getOrNull(index)
        val picker = CatalogPicker(activity.apps) { it.packageName }
        options("Choose dock app", picker.choices.map { it.label }) { selected ->
            val candidate = picker.resolve(selected, activity.discovery.apps(config.showPhoneApps))
            if (candidate == null) {
                selectionRemoved()
                return@options
            }
            val pins = activity.dockPackages
            val currentIndex = if (occupant == null) index else pins.indexOf(occupant)
            if (currentIndex < 0 || (occupant == null && index < pins.size)) {
                dockChanged()
                return@options
            }
            activity.save(config.copy(dock = DockPolicy.assign(pins, currentIndex, candidate.packageName)))
        }
    }

    private fun unhide() {
        val hidden = config.hidden.sorted()
        val labels = hidden.map { pkg -> activity.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
        options("Unhide apps", listOf("Unhide all") + labels) {
            val next = if (it == 0) emptySet() else config.hidden - hidden[it - 1]
            activity.save(config.copy(hidden = next))
        }
    }

    private fun modules() {
        options("Custom modules", listOf("Add app shortcut", "Add input shortcut") + config.modules.map { it.label }) {
            when (it) {
                0 -> if (canAddModule()) addAppModule()
                1 -> if (canAddModule()) addInputModule()
                else -> moduleMenu(it - 2)
            }
        }
    }

    private fun canAddModule(): Boolean {
        if (config.modules.size < 8) return true
        activity.notice("Module limit", "Remove a module before adding another.")
        return false
    }

    private fun selectionRemoved() {
        activity.notice("Selection unavailable", "This app or input is no longer available. Reopen the picker to choose another.")
    }

    private fun addAppModule() {
        val picker = CatalogPicker(activity.apps) { it.packageName }
        options("Installed app", picker.choices.map { it.label }) { index ->
            val app = picker.resolve(index, activity.discovery.apps(config.showPhoneApps))
            if (app == null) {
                selectionRemoved()
                return@options
            }
            edit("Shortcut label", app.label) { label ->
                // The catalog can change again while the label editor is open.
                val current = picker.resolve(index, activity.discovery.apps(config.showPhoneApps))
                if (current == null) {
                    selectionRemoved()
                    return@edit
                }
                activity.save(config.copy(modules = config.modules + ModuleTile(label, current.packageName, "app")))
            }
        }
    }

    private fun addInputModule() {
        val picker = CatalogPicker(activity.inputs) { it.id }
        options("Discovered input", picker.choices.map { it.label }) { index ->
            val input = picker.resolve(index, activity.discovery.inputs())
            if (input == null) {
                selectionRemoved()
                return@options
            }
            edit("Shortcut label", input.label) { label ->
                val current = picker.resolve(index, activity.discovery.inputs())
                if (current == null) {
                    selectionRemoved()
                    return@edit
                }
                activity.save(config.copy(modules = config.modules + ModuleTile(label, current.id, "input")))
            }
        }
    }

    fun moduleMenu(index: Int) {
        val module = config.modules.getOrNull(index) ?: return
        options(module.label, listOf("Rename", "Remove")) {
            if (it == 1) {
                activity.save(config.copy(modules = config.modules.filterIndexed { i, _ -> i != index }))
            } else edit("Shortcut label", module.label) { label ->
                activity.save(config.copy(modules = config.modules.mapIndexed { i, item ->
                    if (i == index) item.copy(label = label) else item
                }))
            }
        }
    }

    fun iptv() {
        val picker = CatalogPicker(activity.apps) { it.packageName }
        options("OwnTV · Choose installed app", picker.choices.map { it.label + " · " + it.packageName }) { index ->
            val app = picker.resolve(index, activity.discovery.apps(config.showPhoneApps))
            if (app == null) {
                selectionRemoved()
                return@options
            }
            activity.save(config.copy(iptvPackage = app.packageName))
        }
    }

    private fun wallpaper() {
        options("Wallpaper", listOf("Built-in time gradient", "Choose image")) {
            if (it == 0) activity.save(config.copy(wallpaperUri = "")) else activity.pickWallpaper()
        }
    }

    private fun weather() {
        options("Weather · Open-Meteo", listOf(
            "City: ${config.weatherCity.ifBlank { "not set" }}",
            if (config.weatherEnabled) "Disable weather" else "Enable weather"
        )) {
            if (it == 0) {
                edit("Weather city (no location permission)", config.weatherCity) { city ->
                    activity.save(config.copy(weatherCity = city,
                        weatherEnabled = config.weatherEnabled && city.isNotBlank()))
                }
            } else if (!config.weatherEnabled && config.weatherCity.isBlank()) {
                activity.notice("Choose a city first", "Set your city, then explicitly enable weather.")
            } else activity.save(config.copy(weatherEnabled = !config.weatherEnabled))
        }
    }
}
