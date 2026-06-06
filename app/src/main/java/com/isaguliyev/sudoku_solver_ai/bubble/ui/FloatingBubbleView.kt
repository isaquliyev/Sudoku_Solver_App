package com.isaguliyev.sudoku_solver_ai.bubble.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils

class FloatingBubbleView(
    context: Context,
    sizePx: Int,
    theme: BubbleOverlayTheme
) : FrameLayout(context) {

    init {
        tag = theme.primary
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.primary)
            setStroke(BubbleOverlayUtils.dpToPx(context, 2), theme.primaryStroke)
        }
        elevation = BubbleOverlayUtils.dpToPx(context, 6).toFloat()

        addView(
            TextView(context).apply {
                text = "S"
                textSize = 22f
                setTextColor(theme.onPrimary)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        layoutParams = LayoutParams(sizePx, sizePx)
    }
}
