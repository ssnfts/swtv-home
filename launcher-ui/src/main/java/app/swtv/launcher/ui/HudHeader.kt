package app.swtv.launcher.ui

import android.content.Context
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.LinearLayout

/** Static HUD identity and minute-updated status; no continuously animated background. */
class HudHeader(context: Context, private val ui: HomeUi) {
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
        setBackgroundColor(ui.palette.background)
    }
    private val time = ui.text("", 28f).apply { typeface = HudTokens.heading }
    private val date = ui.text("", 13f).apply {
        typeface = HudTokens.metadata
        setTextColor(ui.palette.secondary)
    }
    val weather = ui.text("", 13f).apply {
        setTextColor(ui.palette.secondary)
        visibility = android.view.View.GONE
    }
    init {
        view.addView(ui.text("SWTV", 32f).apply {
            typeface = HudTokens.heading
            letterSpacing = .06f
            setTextColor(if (ui.light) ui.palette.text else ui.palette.yellow)
        }, LinearLayout.LayoutParams(ui.dp(144), -2))
        view.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(date); addView(weather)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        view.addView(time)
    }
    fun updateClock() {
        val now = System.currentTimeMillis()
        time.text = DateUtils.formatDateTime(view.context, now, DateUtils.FORMAT_SHOW_TIME)
        date.text = DateUtils.formatDateTime(view.context, now,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR)
    }
}
