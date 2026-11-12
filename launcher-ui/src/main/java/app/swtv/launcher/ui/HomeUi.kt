package app.swtv.launcher.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.swtv.theme.LauncherConfig

class HomeUi(private val activity: Activity, val config: LauncherConfig) {
    val light = HudTokens.isLight(config)
    val palette = HudTokens.palette(config)
    val ink = palette.text
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(16), dp(24), dp(24))
        clipChildren = false
        clipToPadding = false
    }
    private val controls = mutableListOf<Pair<String, View>>()
    private val rows = mutableListOf<List<View>>()
    fun dp(value: Int) = HudTokens.dp(activity, value)

    fun text(value: String, size: Float = 16f) = TextView(activity).apply {
        text = value
        textSize = size
        typeface = HudTokens.body
        setTextColor(ink)
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    fun heading(value: String) {
        val line = LinearLayout(activity).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        line.addView(View(activity).apply { setBackgroundColor(HudTokens.rail(config)) },
            LinearLayout.LayoutParams(dp(3), dp(16)))
        line.addView(text(value.uppercase(), 16f).apply {
            typeface = HudTokens.heading
            letterSpacing = .08f
            setBackgroundColor(palette.background)
        })
        root.addView(line)
    }

    fun tile(key: String, label: String, action: () -> Unit, longAction: (() -> Unit)? = null): View =
        Button(activity).apply {
            id = View.generateViewId()
            text = label
            contentDescription = label
            isFocusable = true
            HudControls.button(this, config)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { action() }
            longAction?.let { setOnLongClickListener { it(); true } }
            controls.add(key to this)
        }

    /** Fixed column cells keep short dock/grid rows compact instead of stretching one app. */
    fun row(views: List<View>, columns: Int = views.size) {
        if (views.isEmpty()) return
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        views.forEach { view ->
            line.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        repeat((columns - views.size).coerceAtLeast(0)) {
            line.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        root.addView(line)
        rows.add(views)
    }

    fun finish(saved: String?) {
        rows.forEachIndexed { row, views ->
            views.forEachIndexed { column, view ->
                view.nextFocusLeftId = views.getOrNull(column - 1)?.id ?: view.id
                view.nextFocusRightId = views.getOrNull(column + 1)?.id ?: view.id
                view.nextFocusUpId = rows.getOrNull(row - 1)?.let {
                    it[column.coerceAtMost(it.lastIndex)]
                }?.id ?: view.id
                view.nextFocusDownId = rows.getOrNull(row + 1)?.let {
                    it[column.coerceAtMost(it.lastIndex)]
                }?.id ?: view.id
            }
        }
        val key = FocusPolicy.focus(saved, controls.map { it.first })
        controls.firstOrNull { it.first == key }?.second?.requestFocus()
    }

    fun focusedKey() = controls.firstOrNull { it.second.hasFocus() }?.first
}
