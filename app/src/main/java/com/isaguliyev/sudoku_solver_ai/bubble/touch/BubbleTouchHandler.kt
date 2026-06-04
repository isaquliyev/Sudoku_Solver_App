package com.isaguliyev.sudoku_solver_ai.bubble.touch

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import com.isaguliyev.sudoku_solver_ai.bubble.dismiss.BubbleDismissController
import kotlin.math.abs

class BubbleTouchHandler(
    private val dragThresholdPx: Int,
    private val dismissController: BubbleDismissController,
    private val onDragStart: () -> Unit,
    private val onDragMove: (WindowManager.LayoutParams) -> Unit,
    private val onDragEnd: (BubbleDismissController.DragEndResult, Boolean) -> Unit,
    private val onTap: () -> Unit
) {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    fun attach(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx)) {
                        isDragging = true
                        onDragStart()
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        onDragMove(params)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    val result = if (isDragging) {
                        dismissController.endDrag(params, wasDragging = true, velocityX = vx, velocityY = vy)
                    } else {
                        BubbleDismissController.DragEndResult.Tap
                    }
                    val wasDrag = isDragging
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (result is BubbleDismissController.DragEndResult.Tap && !wasDrag) {
                        onTap()
                    } else {
                        onDragEnd(result, wasDrag)
                    }
                    true
                }

                else -> false
            }
        }
    }
}
