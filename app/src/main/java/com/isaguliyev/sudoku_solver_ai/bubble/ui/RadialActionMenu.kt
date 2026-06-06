package com.isaguliyev.sudoku_solver_ai.bubble.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils

/** Single-action overlay menu: Scan Sudoku. */
class RadialActionMenu(
    context: Context,
    theme: BubbleOverlayTheme,
    onScan: () -> Unit
) : FrameLayout(context) {

    init {
        val hPad = BubbleOverlayUtils.dpToPx(context, 20)
        val vPad = BubbleOverlayUtils.dpToPx(context, 14)
        setPadding(hPad, vPad, hPad, vPad)

        background = GradientDrawable().apply {
            setColor(theme.surface)
            cornerRadius = BubbleOverlayUtils.dpToPx(context, 32).toFloat()
            setStroke(BubbleOverlayUtils.dpToPx(context, 1), theme.outline)
        }
        elevation = BubbleOverlayUtils.dpToPx(context, 8).toFloat()

        addView(
            TextView(context).apply {
                text = "Scan Sudoku"
                textSize = 15f
                setTextColor(theme.onSurface)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setOnClickListener { onScan() }
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }
}
