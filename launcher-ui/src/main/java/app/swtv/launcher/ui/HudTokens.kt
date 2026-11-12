package app.swtv.launcher.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import app.swtv.theme.LauncherConfig
import app.swtv.theme.ThemePolicy
import java.time.ZonedDateTime
import kotlin.math.roundToInt

data class HudPalette(
    val background: Int, val panel: Int, val raised: Int, val yellow: Int,
    val cyan: Int, val text: Int, val secondary: Int, val alert: Int, val edge: Int
) {
    fun controlText() = ColorStateList(arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_selected),
        intArrayOf()
    ), intArrayOf(secondary, HudTokens.charcoal, HudTokens.charcoal, text))
}

/** Shared by native home controls and app-owned dialogs; all geometry is dp. */
object HudTokens {
    val charcoal = Color.rgb(9, 13, 18)
    val dark = HudPalette(charcoal, 0xFF141C26.toInt(), 0xFF1D2935.toInt(),
        0xFFFCEE09.toInt(), 0xFF00E6F6.toInt(), 0xFFF1F5F7.toInt(),
        0xFFA6B8C8.toInt(), 0xFFFF596D.toInt(), 0xFF506477.toInt())
    val light = HudPalette(0xFFE9EFF2.toInt(), 0xFFF8FAFC.toInt(), 0xFFDCE6EC.toInt(),
        0xFFFCEE09.toInt(), 0xFF006B78.toInt(), 0xFF101C26.toInt(),
        0xFF40576A.toInt(), 0xFFB52239.toInt(), 0xFF677E8E.toInt())
    val heading: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val metadata: Typeface = Typeface.MONOSPACE
    const val focusScale = 1.02f
    const val motionMs = 120L
    const val iconDp = 48

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    fun isLight(config: LauncherConfig) = ThemePolicy.lightTheme(config.theme, ZonedDateTime.now().hour)
    fun palette(config: LauncherConfig) = if (isLight(config)) light else dark
    fun motion(context: Context, config: LauncherConfig) = ThemePolicy.effects(config.reducedMotion,
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice,
        ValueAnimator.areAnimatorsEnabled())

    // The saved accent customizes decorative rails; cyan retains a consistent focus meaning.
    fun rail(config: LauncherConfig) = Color.parseColor(mapOf("cyan" to "#00E6F6",
        "violet" to "#BA9CFF", "blue" to "#70ABFF", "green" to "#64E6AE",
        "amber" to "#FFCC67", "rose" to "#FF96BF")[config.accent] ?: "#00E6F6")
}
