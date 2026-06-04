package com.isaguliyev.sudoku_solver_ai.bubble.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils

class FloatingBubbleView(
    context: Context,
    sizePx: Int
) : FrameLayout(context) {

    companion object {
        const val DEFAULT_COLOR = 0xFF6650A4.toInt()
    }

    init {
        tag = DEFAULT_COLOR
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(DEFAULT_COLOR)
            setStroke(BubbleOverlayUtils.dpToPx(context, 2), 0x556650A4.toInt())
        }
        elevation = BubbleOverlayUtils.dpToPx(context, 6).toFloat()

        addView(
            TextView(context).apply {
                text = "S"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        layoutParams = LayoutParams(sizePx, sizePx)
    }
}
