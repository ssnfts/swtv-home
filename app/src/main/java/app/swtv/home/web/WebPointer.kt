package app.swtv.home.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import app.swtv.home.R

/** Event-driven overlay; no timer, system input, accessibility service or page script. */
class WebPointer(context: Context) : View(context) {
    var pointerEnabled = false
        set(value) { field = value; visibility = if (value) VISIBLE else GONE; invalidate() }
    private var position = PointerPosition(0, 0)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.CYAN; strokeWidth = 3f }
    init { id = R.id.web_pointer; isFocusable = false; isClickable = false; visibility = GONE }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        position = if (oldw == 0) PointerPosition(w / 2, h / 2) else position.move(0, 0, w, h)
    }
    override fun onDraw(canvas: Canvas) {
        val x = position.x.toFloat(); val y = position.y.toFloat()
        ink.style = Paint.Style.STROKE
        canvas.drawCircle(x, y, 10f, ink)
        canvas.drawLine(x - 15, y, x + 15, y, ink); canvas.drawLine(x, y - 15, x, y + 15, ink)
    }
    fun key(event: KeyEvent, web: WebView, toolbar: () -> Unit): Boolean {
        if (!pointerEnabled) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -24 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 24 to 0
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -24
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 24
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (direction.second < 0 && position.escapesUp()) toolbar()
                else { position = position.move(direction.first, direction.second, web.width, web.height); invalidate() }
            }
            return true
        }
        if (event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP && web.width > 0 && web.height > 0) {
                position = position.move(0, 0, web.width, web.height)
                val time = SystemClock.uptimeMillis()
                for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val touch = MotionEvent.obtain(time, time, action, position.x.toFloat(), position.y.toFloat(), 0)
                    web.dispatchTouchEvent(touch); touch.recycle()
                }
            }
            return true
        }
        return false
    }
}
