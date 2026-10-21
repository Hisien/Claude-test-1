package com.scrollmate.app.winddown

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.scrollmate.app.R

/**
 * Full-screen calming overlay drawn above a blocked app during the evening wind-down.
 * Built with plain Views because Compose inside a service window needs its own lifecycle plumbing.
 * Always uses the calm night palette, independent of the app theme.
 */
class WindDownOverlay(
    private val context: Context,
    private val onGoHome: () -> Unit,
    private val onPauseTonight: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var appText: TextView? = null
    private var breathing: AnimatorSet? = null

    val isShowing: Boolean get() = root != null

    fun show(appLabel: String, bedtime: String) {
        val text = if (appLabel.isBlank()) {
            context.getString(R.string.wind_down_overlay_body_generic, bedtime)
        } else {
            context.getString(R.string.wind_down_overlay_body, bedtime, appLabel)
        }
        if (root != null) {
            appText?.text = text
            return
        }
        val view = build(text)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager?.addView(view, params)
            root = view
        } catch (e: Exception) {
            // Overlay permission was revoked meanwhile; the service stops on its next check.
        }
    }

    fun hide() {
        breathing?.cancel()
        breathing = null
        root?.let { v ->
            try {
                windowManager?.removeView(v)
            } catch (e: Exception) {
                // Already removed.
            }
        }
        root = null
        appText = null
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun build(bodyText: String): View {
        val frame = object : FrameLayout(context) {
            // Back button leaves to the home screen instead of revealing the blocked app.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onGoHome()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E1B4B")),
        )
        frame.isFocusable = true
        frame.isFocusableInTouchMode = true
        frame.isClickable = true

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(48), dp(28), dp(48))
        }

        fun label(text: String, sizeSp: Float, color: String, top: Int = 0) = TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER
            setPadding(0, dp(top), 0, 0)
        }

        column.addView(label("🌙", 48f, "#E2E8F0"))
        column.addView(label(context.getString(R.string.wind_down_overlay_title), 26f, "#F1F5F9", top = 12))
        val body = label(bodyText, 16f, "#CBD5E1", top = 12)
        appText = body
        column.addView(body)

        // 4-7-8 breathing circle.
        val circle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#334155"))
                setStroke(dp(3), Color.parseColor("#22C55E"))
            }
            contentDescription = context.getString(R.string.wind_down_overlay_breathing_cd)
        }
        val circleSize = dp(160)
        val circleParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
            topMargin = dp(36)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(circle, circleParams)
        val phase = label(context.getString(R.string.breathing_in), 20f, "#E2E8F0", top = 20)
        column.addView(phase)
        column.addView(label(context.getString(R.string.wind_down_overlay_breathing_hint), 14f, "#94A3B8", top = 4))

        val home = Button(context).apply {
            text = context.getString(R.string.wind_down_overlay_home)
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { onGoHome() }
        }
        column.addView(
            home,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(36)
            },
        )
        val pause = label(context.getString(R.string.wind_down_overlay_pause), 14f, "#94A3B8", top = 16).apply {
            minHeight = dp(48)
            isClickable = true
            setOnClickListener { onPauseTonight() }
        }
        column.addView(pause)

        frame.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        startBreathing(circle, phase)
        return frame
    }

    private fun startBreathing(circle: View, phase: TextView) {
        circle.scaleX = 0.6f
        circle.scaleY = 0.6f
        fun scale(to: Float, ms: Long) = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(circle, View.SCALE_X, to),
                ObjectAnimator.ofFloat(circle, View.SCALE_Y, to),
            )
            duration = ms
            interpolator = LinearInterpolator()
        }
        val inhale = scale(1f, 4_000).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) { phase.setText(R.string.breathing_in) }
            })
        }
        val hold = scale(1f, 7_000).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) { phase.setText(R.string.breathing_hold) }
            })
        }
        val exhale = scale(0.6f, 8_000).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) { phase.setText(R.string.breathing_out) }
            })
        }
        val cycle = AnimatorSet().apply { playSequentially(inhale, hold, exhale) }
        cycle.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && root != null) cycle.start()
            }
        })
        breathing = cycle
        cycle.start()
    }
}
