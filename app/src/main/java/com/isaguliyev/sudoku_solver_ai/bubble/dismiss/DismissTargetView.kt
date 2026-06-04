package com.isaguliyev.sudoku_solver_ai.bubble.dismiss

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils

/**
 * Shadowed red X dismiss target. Two visual states (idle / removal-ready) with animated transition.
 */
class DismissTargetView(context: Context) : FrameLayout(context) {

    private val normalFill = 0x44E53935.toInt()
    private val highlightFill = 0xCCE53935.toInt()
    private val normalStroke = 0xAAE53935.toInt()
    private val highlightStroke = 0xFFE53935.toInt()

    private val baseElevationPx = BubbleOverlayUtils.dpToPx(context, 8).toFloat()
    private val maxExtraElevationPx = BubbleOverlayUtils.dpToPx(context, 10).toFloat()

    private val orbDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(normalFill)
        setStroke(BubbleOverlayUtils.dpToPx(context, 2), normalStroke)
    }

    private val shadowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x88E53935.toInt()
    }

    private var visualProgress = 0f
    private var progressAnimator: ValueAnimator? = null

    init {
        background = orbDrawable
        applyVisualProgress(0f)
        scaleX = 1f
        scaleY = 1f

        addView(
            TextView(context).apply {
                text = "✕"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun animateRemovalState(active: Boolean) {
        val target = if (active) 1f else 0f
        if (visualProgress == target) return

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(visualProgress, target).apply {
            duration = 250L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                applyVisualProgress(it.animatedValue as Float)
            }
            start()
        }
    }

    fun snapRemovalState(active: Boolean) {
        progressAnimator?.cancel()
        progressAnimator = null
        applyVisualProgress(if (active) 1f else 0f)
    }

    private fun applyVisualProgress(progress: Float) {
        visualProgress = progress.coerceIn(0f, 1f)
        scaleX = 1f
        scaleY = 1f
        elevation = baseElevationPx + maxExtraElevationPx * visualProgress

        orbDrawable.setColor(BubbleOverlayUtils.lerpColor(normalFill, highlightFill, visualProgress))
        orbDrawable.setStroke(
            BubbleOverlayUtils.dpToPx(context, 2),
            BubbleOverlayUtils.lerpColor(normalStroke, highlightStroke, visualProgress)
        )
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (visualProgress > 0.01f) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxRadius = minOf(w, h) / 2f - 4f

            val ringCount = 3
            for (i in 0 until ringCount) {
                val ringIndex = i + 1
                val pullFactor = visualProgress * (0.35f + ringIndex * 0.12f)
                val inset = maxRadius * pullFactor
                val radius = maxRadius - inset
                shadowRingPaint.strokeWidth = 4f + visualProgress * 3f
                shadowRingPaint.alpha = (visualProgress * (80 + ringIndex * 35)).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, radius.coerceAtLeast(8f), shadowRingPaint)
            }
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        progressAnimator = null
        super.onDetachedFromWindow()
    }
}
