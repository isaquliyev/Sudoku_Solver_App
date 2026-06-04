package com.isaguliyev.sudoku_solver_ai.bubble.dismiss

import android.content.Context
import android.view.WindowManager
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayUtils
import kotlin.math.hypot

/**
 * Bottom dismiss zone, optional fling dismiss, and edge dock for the floating bubble.
 */
class BubbleDismissController(
    context: Context,
    private val bubbleSizePx: Int,
    private val screenWidth: Int,
    screenHeight: Int
) {

    private val dismissZoneBottomInsetPx =
        BubbleOverlayUtils.dpToPx(context, DismissZoneMetrics.BOTTOM_INSET_DP)
    private val dismissZoneRadiusPx =
        BubbleOverlayUtils.dpToPx(context, DismissZoneMetrics.HIT_RADIUS_DP).toFloat()
    private val edgeMarginPx = BubbleOverlayUtils.dpToPx(context, 16)
    private val edgeFlingProximityPx = BubbleOverlayUtils.dpToPx(context, 48)
    private val screenHeightPx = screenHeight

    private val zoneCenterX = screenWidth / 2f
    private val zoneCenterY = screenHeightPx - dismissZoneBottomInsetPx.toFloat()

    fun isInDismissZone(bubbleParams: WindowManager.LayoutParams): Boolean {
        val cx = bubbleParams.x + bubbleSizePx / 2f
        val cy = bubbleParams.y + bubbleSizePx / 2f
        return hypot(
            (cx - zoneCenterX).toDouble(),
            (cy - zoneCenterY).toDouble()
        ) <= dismissZoneRadiusPx
    }

    fun endDrag(
        bubbleParams: WindowManager.LayoutParams,
        wasDragging: Boolean,
        velocityX: Float = 0f,
        velocityY: Float = 0f
    ): DragEndResult {
        if (!wasDragging) return DragEndResult.Tap

        val speed = hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()
        val cx = bubbleParams.x + bubbleSizePx / 2f
        val cy = bubbleParams.y + bubbleSizePx / 2f
        val inLowerScreen = cy > screenHeightPx * 0.65f
        val nearEdge = cx < edgeFlingProximityPx ||
            cx > screenWidth - edgeFlingProximityPx ||
            cy < edgeFlingProximityPx ||
            cy > screenHeightPx - edgeFlingProximityPx

        if ((velocityY > 2500f && inLowerScreen) || (speed > 4000f && nearEdge)) {
            return DragEndResult.Dismiss
        }

        if (isInDismissZone(bubbleParams)) {
            return DragEndResult.Dismiss
        }

        return DragEndResult.Dock(computeDockPosition(bubbleParams))
    }

    fun reset() = Unit

    private fun computeDockPosition(params: WindowManager.LayoutParams): Pair<Int, Int> {
        val cx = params.x + bubbleSizePx / 2f
        val cy = params.y + bubbleSizePx / 2f
        val toLeft = cx
        val toRight = screenWidth - cx
        val toTop = cy
        val min = minOf(toLeft, toRight, toTop)
        val x = when {
            min == toLeft -> edgeMarginPx
            min == toRight -> screenWidth - bubbleSizePx - edgeMarginPx
            else -> params.x
        }
        val y = if (min == toTop) edgeMarginPx else params.y
        return x.coerceIn(edgeMarginPx, screenWidth - bubbleSizePx - edgeMarginPx) to
            y.coerceIn(edgeMarginPx, screenHeightPx - bubbleSizePx - edgeMarginPx)
    }

    sealed class DragEndResult {
        data object Tap : DragEndResult()
        data object Dismiss : DragEndResult()
        data class Dock(val position: Pair<Int, Int>) : DragEndResult()
    }
}
