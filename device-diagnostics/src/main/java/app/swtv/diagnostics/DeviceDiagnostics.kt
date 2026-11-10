package app.swtv.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.view.Display

/** A fresh, read-only snapshot. Call off the UI thread only after a user requests diagnostics. */
class DeviceDiagnostics(context: Context) {
    private val context = context.applicationContext

    fun collect(): DiagnosticSnapshot {
        val manager = reading { context.getSystemService(ActivityManager::class.java) }
        val memory = reading {
            ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
                .takeIf { manager != null }
        }
        val pss = reading { manager?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            ?.firstOrNull()?.totalPss?.takeIf { it > 0 }?.toLong()?.times(1024L) }
        // dataDir is the app's filesystem; these values are not physical flash or app disk usage.
        val storage = reading { StatFs(context.applicationInfo.dataDir) }
        val metrics = reading { context.resources.displayMetrics }
        val mode = reading { context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.mode }
        return DiagnosticSnapshot(linkedMapOf(
            "Manufacturer" to Build.MANUFACTURER,
            "Model" to Build.MODEL,
            "Board" to Build.BOARD,
            "ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
            "Android API" to Build.VERSION.SDK_INT.toString(),
            "Display logical pixels" to metrics?.let { "${it.widthPixels} × ${it.heightPixels}" },
            "Display density" to metrics?.let { "${it.densityDpi} dpi" },
            "Display current mode" to mode?.let { "${it.physicalWidth} × ${it.physicalHeight} at ${it.refreshRate} Hz" },
            "OS memory total" to DiagnosticReport.bytes(memory?.totalMem),
            "OS memory available" to DiagnosticReport.bytes(memory?.availMem),
            "Process PSS" to DiagnosticReport.bytes(pss),
            "App data filesystem total" to DiagnosticReport.bytes(reading { storage?.totalBytes }),
            "App data filesystem available" to DiagnosticReport.bytes(reading { storage?.availableBytes })
        ))
    }

    private fun <T> reading(block: () -> T?): T? = try { block() } catch (_: Exception) { null }
}
