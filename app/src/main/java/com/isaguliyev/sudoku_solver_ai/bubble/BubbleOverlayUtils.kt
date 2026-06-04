package com.isaguliyev.sudoku_solver_ai.bubble

import android.content.Context
import kotlin.math.pow

object BubbleOverlayUtils {

    fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun lerpColor(start: Int, end: Int, t: Float): Int {
        val a = smoothstep(t)
        val sa = (start shr 24) and 0xFF
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val ea = (end shr 24) and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        return (
            ((sa + (ea - sa) * a).toInt() shl 24) or
                ((sr + (er - sr) * a).toInt() shl 16) or
                ((sg + (eg - sg) * a).toInt() shl 8) or
                (sb + (eb - sb) * a).toInt()
            )
    }
}
