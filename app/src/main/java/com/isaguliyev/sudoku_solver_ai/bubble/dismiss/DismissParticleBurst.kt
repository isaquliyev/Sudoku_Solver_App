package com.isaguliyev.sudoku_solver_ai.bubble.dismiss

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One-shot burst of mini Sudoku-cell squares on bubble implode.
 */
class DismissParticleBurst(
    context: Context,
    centerX: Float,
    centerY: Float,
    primaryColor: Int,
    private val onFinished: () -> Unit
) : View(context) {

    private data class Particle(
        val angle: Float,
        val speed: Float,
        val size: Float,
        val color: Int,
        val spin: Float
    )

    private val particles = List(12) {
        Particle(
            angle = Random.nextFloat() * 360f,
            speed = 80f + Random.nextFloat() * 160f,
            size = 8f + Random.nextFloat() * 14f,
            color = listOf(
                primaryColor,
                0xFFE53935.toInt(),
                0xFFFF6F00.toInt(),
                0xFF1E88E5.toInt()
            ).random(),
            spin = Random.nextFloat() * 720f
        )
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
                if (progress >= 1f) {
                    onFinished()
                }
            }
            start()
        }
    }

    fun cancelBurst() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
        for (p in particles) {
            val rad = Math.toRadians(p.angle.toDouble())
            val dist = p.speed * progress
            val px = cx + cos(rad).toFloat() * dist
            val py = cy + sin(rad).toFloat() * dist
            paint.color = p.color
            paint.alpha = alpha
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(p.spin * progress)
            canvas.drawRect(-p.size / 2, -p.size / 2, p.size / 2, p.size / 2, paint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        cancelBurst()
        super.onDetachedFromWindow()
    }
}
