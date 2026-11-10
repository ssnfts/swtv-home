package app.swtv.discovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvInputManager

data class InputEntry(val id: String, val label: String, val passthrough: Boolean, val state: Int)

class Discovery(private val context: Context) {
    fun apps(phone: Boolean): List<AppEntry> = appSnapshot(phone) ?: emptyList()

    fun appSnapshot(phone: Boolean): List<AppEntry>? = try { queryApps(phone) } catch (_: Exception) { null }

    private fun queryApps(phone: Boolean): List<AppEntry> {
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER) +
            if (phone) listOf(Intent.CATEGORY_LAUNCHER) else emptyList()
        return CatalogRules.deduplicate(categories.flatMap { category ->
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(category), 0
            ).map {
                AppEntry(it.activityInfo.packageName, it.activityInfo.name,
                    it.loadLabel(context.packageManager).toString())
            }
        })
    }

    fun inputs(): List<InputEntry> = try {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        manager.tvInputList.map {
            InputEntry(it.id, it.loadLabel(context).toString(), it.isPassthroughInput, manager.getInputState(it.id))
        }.filter { InputPolicy.available(it.passthrough, it.state) }
    } catch (_: Exception) {
        emptyList()
    }

    fun launchApp(app: AppEntry): Boolean = launch(
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun launchPackage(pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return launch(intent)
    }

    fun launchInput(id: String): Boolean {
        // Saved modules are revalidated on every launch; tuner/missing/disconnected IDs never hand off.
        if (inputs().none { it.id == id }) return false
        return launch(InputIntents.passthrough(id))
    }

    fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
