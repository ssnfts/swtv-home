package app.swtv.launcher.ui

import android.widget.EditText
import app.swtv.discovery.CatalogRules
import app.swtv.theme.LauncherConfig
import app.swtv.theme.ModuleTile
import app.swtv.discovery.CatalogPicker
import app.swtv.discovery.DockPolicy

class SettingsController(private val host: LauncherHost) {
    private val activity get() = host.activity
    private val config get() = host.config
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
                    host.save(config.copy(columns = it + 3))
                }
                2 -> options("Theme", themes) { host.save(config.copy(theme = themes[it])) }
                3 -> options("Accent", accents) { host.save(config.copy(accent = accents[it])) }
                4 -> host.save(config.copy(reducedMotion = !config.reducedMotion))
                5 -> host.save(config.copy(showPhoneApps = !config.showPhoneApps))
                6 -> unhide()
                7 -> modules()
                8 -> wallpaper()
                9 -> weather()
                10 -> iptv()
                11 -> HudDialogs.builder(activity, config).setTitle("Reset all local configuration?")
                    .setPositiveButton("Reset") { _, _ -> host.save(LauncherConfig()) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun options(title: String, items: List<String>, choose: (Int) -> Unit) {
        if (items.isEmpty()) {
            host.notice(title, "No available choices on this device.")
            return
        }
        HudDialogs.builder(activity, config).setTitle(title).setItems(items.toTypedArray()) { _, index ->
            choose(index)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun edit(title: String, value: String, done: (String) -> Unit) {
        val field = EditText(activity).apply {
            setText(value)
            isSingleLine = true
            selectAll()
        }
        HudDialogs.builder(activity, config).setTitle(title).setView(field)
            .setPositiveButton("Save") { _, _ -> done(field.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
        field.requestFocus()
    }

    fun dockSlot(index: Int) {
        val occupant = host.dockPackages.getOrNull(index)
        options("Dock slot ${index + 1}", listOf("Choose / swap app", "Move left", "Move right", "Clear slot")) {
            val pins = host.dockPackages
            val currentIndex = if (occupant == null) index else pins.indexOf(occupant)
            if (currentIndex < 0 || (occupant == null && index < pins.size)) {
                dockChanged()
                return@options
            }
            when (it) {
                0 -> chooseDockApp(currentIndex)
                1, 2 -> if (occupant != null) {
                    val next = currentIndex + if (it == 1) -1 else 1
                    host.save(config.copy(dock = CatalogRules.movePin(pins, occupant, next)))
                }
                3 -> if (occupant != null) host.save(config.copy(dock = pins - occupant))
            }
        }
    }

    private fun dockChanged() {
        host.notice("Dock changed", "The selected slot changed while the dialog was open. Reopen dock settings to choose a current slot.")
    }

    private fun chooseDockApp(index: Int) {
        val occupant = host.dockPackages.getOrNull(index)
        val picker = CatalogPicker(host.apps) { it.packageName }
        options("Choose dock app", picker.choices.map { it.label }) { selected ->
            val candidate = picker.resolve(selected, host.discovery.apps(config.showPhoneApps))
            if (candidate == null) {
                selectionRemoved()
                return@options
            }
            val pins = host.dockPackages
            val currentIndex = if (occupant == null) index else pins.indexOf(occupant)
            if (currentIndex < 0 || (occupant == null && index < pins.size)) {
                dockChanged()
                return@options
            }
            host.save(config.copy(dock = DockPolicy.assign(pins, currentIndex, candidate.packageName)))
        }
    }

    private fun unhide() {
        val hidden = config.hidden.sorted()
        val labels = hidden.map { pkg -> host.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
        options("Unhide apps", listOf("Unhide all") + labels) {
            val next = if (it == 0) emptySet() else config.hidden - hidden[it - 1]
            host.save(config.copy(hidden = next))
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
        host.notice("Module limit", "Remove a module before adding another.")
        return false
    }

    private fun selectionRemoved() {
        host.notice("Selection unavailable", "This app or input is no longer available. Reopen the picker to choose another.")
    }

    private fun addAppModule() {
        val picker = CatalogPicker(host.apps) { it.packageName }
        options("Installed app", picker.choices.map { it.label }) { index ->
            val app = picker.resolve(index, host.discovery.apps(config.showPhoneApps))
            if (app == null) {
                selectionRemoved()
                return@options
            }
            edit("Shortcut label", app.label) { label ->
                // The catalog can change again while the label editor is open.
                val current = picker.resolve(index, host.discovery.apps(config.showPhoneApps))
                if (current == null) {
                    selectionRemoved()
                    return@edit
                }
                host.save(config.copy(modules = config.modules + ModuleTile(label, current.packageName, "app")))
            }
        }
    }

    private fun addInputModule() {
        val picker = CatalogPicker(host.inputs) { it.id }
        options("Discovered input", picker.choices.map { it.label }) { index ->
            val input = picker.resolve(index, host.discovery.inputs())
            if (input == null) {
                selectionRemoved()
                return@options
            }
            edit("Shortcut label", input.label) { label ->
                val current = picker.resolve(index, host.discovery.inputs())
                if (current == null) {
                    selectionRemoved()
                    return@edit
                }
                host.save(config.copy(modules = config.modules + ModuleTile(label, current.id, "input")))
            }
        }
    }

    fun moduleMenu(index: Int) {
        val module = config.modules.getOrNull(index) ?: return
        options(module.label, listOf("Rename", "Remove")) {
            if (it == 1) {
                host.save(config.copy(modules = config.modules.filterIndexed { i, _ -> i != index }))
            } else edit("Shortcut label", module.label) { label ->
                host.save(config.copy(modules = config.modules.mapIndexed { i, item ->
                    if (i == index) item.copy(label = label) else item
                }))
            }
        }
    }

    fun iptv() {
        val picker = CatalogPicker(host.apps) { it.packageName }
        options("OwnTV · Choose installed app", picker.choices.map { it.label + " · " + it.packageName }) { index ->
            val app = picker.resolve(index, host.discovery.apps(config.showPhoneApps))
            if (app == null) {
                selectionRemoved()
                return@options
            }
            host.save(config.copy(iptvPackage = app.packageName))
        }
    }

    private fun wallpaper() {
        options("Wallpaper", listOf("Built-in HUD background", "Choose image")) {
            if (it == 0) host.save(config.copy(wallpaperUri = "")) else host.pickWallpaper()
        }
    }

    private fun weather() {
        options("Weather · Open-Meteo", listOf(
            "City: ${config.weatherCity.ifBlank { "not set" }}",
            if (config.weatherEnabled) "Disable weather" else "Enable weather"
        )) {
            if (it == 0) {
                edit("Weather city (no location permission)", config.weatherCity) { city ->
                    host.save(config.copy(weatherCity = city,
                        weatherEnabled = config.weatherEnabled && city.isNotBlank()))
                }
            } else if (!config.weatherEnabled && config.weatherCity.isBlank()) {
                host.notice("Choose a city first", "Set your city, then explicitly enable weather.")
            } else host.save(config.copy(weatherEnabled = !config.weatherEnabled))
        }
    }
}
