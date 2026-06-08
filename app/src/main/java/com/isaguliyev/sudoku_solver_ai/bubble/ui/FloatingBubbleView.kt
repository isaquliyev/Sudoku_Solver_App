package com.isaguliyev.sudoku_solver_ai.bubble.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import com.isaguliyev.sudoku_solver_ai.R
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils
import com.isaguliyev.sudoku_solver_ai.scan.ScanPhase

class FloatingBubbleView(
    context: Context,
    sizePx: Int,
    theme: BubbleOverlayTheme
) : FrameLayout(context) {

    private val iconView: ImageView
    private val progressView: ProgressBar
    private val idleBackground: GradientDrawable
    private val processingBackground: GradientDrawable

    init {
        tag = theme.primary

        idleBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(BubbleOverlayUtils.dpToPx(context, 2), theme.primaryStroke)
        }
        processingBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.primary)
        }
        background = idleBackground

        elevation = BubbleOverlayUtils.dpToPx(context, 6).toFloat()
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        iconView = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Sudoku bubble"
        }
        addView(
            iconView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        progressView = ProgressBar(context, null, android.R.attr.progressBarStyle).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(theme.onPrimary)
            background = null
            visibility = GONE
        }
        addView(
            progressView,
            LayoutParams(
                (sizePx * 0.7f).toInt(),
                (sizePx * 0.7f).toInt(),
                Gravity.CENTER
            )
        )

        layoutParams = LayoutParams(sizePx, sizePx)
    }

    fun setProcessing(processing: Boolean, phase: ScanPhase? = null) {
        if (processing) {
            background = processingBackground
            iconView.visibility = GONE
            progressView.visibility = VISIBLE
            contentDescription = when (phase) {
                ScanPhase.EXTRACTING -> "Extracting digits"
                ScanPhase.SOLVING -> "Solving puzzle"
                null -> "Processing sudoku"
            }
        } else {
            background = idleBackground
            iconView.visibility = VISIBLE
            progressView.visibility = GONE
            contentDescription = "Sudoku bubble"
        }
    }
}
