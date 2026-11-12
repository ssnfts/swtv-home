package app.swtv.launcher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import app.swtv.theme.LauncherConfig

object HudControls {
    fun button(button: Button, config: LauncherConfig, scale: Boolean = true) {
        val context = button.context
        val palette = HudTokens.palette(config)
        val animate = HudTokens.motion(context, config)
        button.apply {
            isAllCaps = false
            typeface = HudTokens.body
            textSize = 15f
            setTextColor(palette.controlText())
            backgroundTintList = null
            background = HudPanel(context, palette)
            stateListAnimator = if (animate && scale) motion(this) else null
            elevation = 0f
            minimumWidth = 0
            minimumHeight = HudTokens.dp(context, 40)
            setPadding(HudTokens.dp(context, 12), HudTokens.dp(context, 8),
                HudTokens.dp(context, 12), HudTokens.dp(context, 8))
            setOnFocusChangeListener { view, focused ->
                if (!animate || !scale) {
                    val target = if (focused && scale) HudTokens.focusScale else 1f
                    view.scaleX = target; view.scaleY = target
                }
                if (focused) view.post {
                    // Scroll the expanded outline into view, including row gutters.
                    val margin = HudTokens.dp(context, 8)
                    view.requestRectangleOnScreen(Rect(-margin, -margin,
                        view.width + margin, view.height + margin), !animate)
                }
            }
        }
    }

    private fun motion(button: Button) = StateListAnimator().apply {
        fun scaleTo(value: Float) = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(button, View.SCALE_X, value),
                ObjectAnimator.ofFloat(button, View.SCALE_Y, value))
            duration = HudTokens.motionMs
            interpolator = DecelerateInterpolator()
        }
        addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), scaleTo(1f))
        addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused), scaleTo(HudTokens.focusScale))
        addState(intArrayOf(), scaleTo(1f))
    }
}
