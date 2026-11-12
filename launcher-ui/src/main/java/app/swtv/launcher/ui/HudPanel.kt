package app.swtv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Flat opaque cut-corner surface. The entire focus stroke stays inside its bounds. */
class HudPanel(context: Context, private val palette: HudPalette, private val raised: Boolean = false) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var enabled = true
    private var focused = false
    private var active = false
    private var hovered = false

    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        enabled = android.R.attr.state_enabled in state
        focused = enabled && android.R.attr.state_focused in state
        active = enabled && (android.R.attr.state_pressed in state || android.R.attr.state_selected in state)
        hovered = enabled && android.R.attr.state_hovered in state
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val stroke = (if (focused || active) 2.5f else 1f) * density
        val inset = stroke / 2f
        val left = bounds.left + inset
        val top = bounds.top + inset
        val right = bounds.right - inset
        val bottom = bounds.bottom - inset
        val cut = minOf(8f * density, (right - left) / 4, (bottom - top) / 4)
        path.reset()
        path.moveTo(left + cut, top); path.lineTo(right, top)
        path.lineTo(right, bottom - cut); path.lineTo(right - cut, bottom)
        path.lineTo(left, bottom); path.lineTo(left, top + cut); path.close()
        paint.style = Paint.Style.FILL
        paint.color = if (active) palette.yellow else if (raised) palette.raised else palette.panel
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = if (focused || active || hovered) palette.cyan else palette.edge
        canvas.drawPath(path, paint)
        // A second, inset corner marker makes selection distinguishable beyond its colour.
        if (focused || active) {
            paint.color = if (active) HudTokens.charcoal else palette.yellow
            canvas.drawLine(left + cut + 3 * density, top + 5 * density,
                left + cut + 19 * density, top + 5 * density, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is no longer used")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
