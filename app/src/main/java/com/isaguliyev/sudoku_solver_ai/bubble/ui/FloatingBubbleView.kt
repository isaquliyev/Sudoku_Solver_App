package com.isaguliyev.sudoku_solver_ai.bubble.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils
import com.isaguliyev.sudoku_solver_ai.scan.ScanPhase

class FloatingBubbleView(
    context: Context,
    sizePx: Int,
    theme: BubbleOverlayTheme
) : FrameLayout(context) {

    private val labelView: TextView
    private val progressView: ProgressBar

    init {
        tag = theme.primary
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.primary)
            setStroke(BubbleOverlayUtils.dpToPx(context, 2), theme.primaryStroke)
        }
        elevation = BubbleOverlayUtils.dpToPx(context, 6).toFloat()

        labelView = TextView(context).apply {
            text = "S"
            textSize = 22f
            setTextColor(theme.onPrimary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        addView(
            labelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        progressView = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(theme.onPrimary)
            visibility = GONE
        }
        addView(
            progressView,
            LayoutParams(
                (sizePx * 0.65f).toInt(),
                (sizePx * 0.65f).toInt(),
                Gravity.CENTER
            )
        )

        layoutParams = LayoutParams(sizePx, sizePx)
    }

    fun setProcessing(processing: Boolean, phase: ScanPhase? = null) {
        if (processing) {
            labelView.visibility = GONE
            progressView.visibility = VISIBLE
            contentDescription = when (phase) {
                ScanPhase.EXTRACTING -> "Extracting digits"
                ScanPhase.SOLVING -> "Solving puzzle"
                null -> "Processing sudoku"
            }
        } else {
            labelView.visibility = VISIBLE
            progressView.visibility = GONE
            contentDescription = "Sudoku bubble"
        }
    }
}
