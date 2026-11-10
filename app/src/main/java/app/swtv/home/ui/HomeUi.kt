package app.swtv.home.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.swtv.home.core.LauncherConfig
import app.swtv.home.platform.RuntimePolicy
import java.time.ZonedDateTime

class HomeUi(private val activity: Activity, val config: LauncherConfig) {
    val light = RuntimePolicy.lightTheme(config.theme, ZonedDateTime.now().hour)
    val ink = if (light) Color.rgb(15, 28, 48) else Color.rgb(236, 244, 255)
    val accent = Color.parseColor(mapOf(
        "cyan" to "#48DDEB", "violet" to "#BA9CFF", "blue" to "#70ABFF",
        "green" to "#64E6AE", "amber" to "#FFCC67", "rose" to "#FF96BF"
    )[config.accent] ?: "#48DDEB")
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(36, 24, 36, 24)
        clipChildren = false
        clipToPadding = false
    }
    private val controls = mutableListOf<Pair<String, View>>()
    private val rows = mutableListOf<List<View>>()
    private val animate = RuntimePolicy.effects(
        config.reducedMotion,
        (activity.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice,
        ValueAnimator.areAnimatorsEnabled()
    )

    fun text(value: String, size: Float = 18f) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(ink)
        setPadding(4, 6, 4, 6)
    }

    fun heading(value: String) { root.addView(text(value, 18f)) }

    fun tile(key: String, label: String, action: () -> Unit, longAction: (() -> Unit)? = null): View =
        Button(activity).apply {
            id = View.generateViewId()
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(ink)
            contentDescription = label
            isFocusable = true
            setPadding(12, 12, 12, 12)
            minHeight = 60
            background = surface(false)
            setOnClickListener { action() }
            longAction?.let { setOnLongClickListener { it(); true } }
            setOnFocusChangeListener { view, focused ->
                view.background = surface(focused)
                if (animate) {
                    view.animate().scaleX(if (focused) 1.04f else 1f)
                        .scaleY(if (focused) 1.04f else 1f)
                        .alpha(if (focused) 1f else .92f).setDuration(120).start()
                }
            }
            controls.add(key to this)
        }

    private fun surface(focused: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (light) intArrayOf(0xF0FFFFFF.toInt(), 0xE6DDE9F5.toInt())
        else intArrayOf(0xE6293C59.toInt(), 0xDC15253C.toInt())
    ).apply {
        cornerRadius = 18f
        setStroke(if (focused) 4 else 1,
            if (focused) accent else if (light) 0xFF879BB2.toInt() else 0xFF49617C.toInt())
    }

    fun row(views: List<View>) {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            clipChildren = false
            clipToPadding = false
            // Room for the 1.04 focus scale, even at the first/last tile and toolbar edge.
            setPadding(8, 6, 8, 6)
        }
        views.forEach { view ->
            line.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(6, 5, 6, 5)
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
        val key = RuntimePolicy.focus(saved, controls.map { it.first })
        controls.firstOrNull { it.first == key }?.second?.requestFocus()
    }

    fun focusedKey() = controls.firstOrNull { it.second.hasFocus() }?.first
}
