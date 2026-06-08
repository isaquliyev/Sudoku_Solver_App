package com.isaguliyev.sudoku_solver_ai.bubble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import com.isaguliyev.sudoku_solver_ai.bubble.dismiss.BubbleDismissController
import com.isaguliyev.sudoku_solver_ai.bubble.dismiss.DismissParticleBurst
import com.isaguliyev.sudoku_solver_ai.bubble.dismiss.DismissTargetView
import com.isaguliyev.sudoku_solver_ai.bubble.dismiss.DismissZoneMetrics
import com.isaguliyev.sudoku_solver_ai.bubble.touch.BubbleTouchHandler
import com.isaguliyev.sudoku_solver_ai.bubble.ui.FloatingBubbleView
import com.isaguliyev.sudoku_solver_ai.bubble.ui.RadialActionMenu
import com.isaguliyev.sudoku_solver_ai.scan.ScanPhase
import com.isaguliyev.sudoku_solver_ai.scan.ScanResult
import com.isaguliyev.sudoku_solver_ai.scan.SudokuScanProcessor
import com.isaguliyev.sudoku_solver_ai.scan.toDigitIntArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: FloatingBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: RadialActionMenu? = null
    private val ghostViews = mutableListOf<FrameLayout>()
    private var dismissController: BubbleDismissController? = null
    private var touchHandler: BubbleTouchHandler? = null
    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMenuVisible = false
    private var dismissTargetView: DismissTargetView? = null
    private var implodeAnimator: AnimatorSet? = null
    private var particleBurst: DismissParticleBurst? = null
    private var isStopping = false
    private var isInRemovalZone = false
    private var bubbleScaleAnimator: ValueAnimator? = null
    private var bubbleEnterAnimator: AnimatorSet? = null
    private var menuAnimator: AnimatorSet? = null
    private lateinit var overlayTheme: BubbleOverlayTheme
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanProcessor: SudokuScanProcessor? = null
    private var isProcessing = false

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TRIGGER_CAPTURE = "trigger_capture"
        const val ACTION_STOP = "com.isaguliyev.sudoku_solver_ai.STOP_BUBBLE"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        var isRunning: Boolean = false
            private set

        fun markRunning() = setRunning(true)

        private fun setRunning(running: Boolean) {
            isRunning = running
            _isRunningFlow.value = running
        }

        private const val BUBBLE_SIZE_DP = 60
        private const val DRAG_THRESHOLD_DP = 8
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setRunning(true)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayTheme = BubbleOverlayTheme.forContext(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            mainHandler.post { requestStopWithAnimation() }
            return START_NOT_STICKY
        }

        startForegroundWithCurrentType()
        applyMediaProjectionFromIntent(intent)
        overlayTheme = BubbleOverlayTheme.fromIntent(this, intent)

        if (bubbleView == null) {
            setupBubble()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cancelAllAnimations()
        super.onDestroy()
        setRunning(false)
        hideMenu(immediate = true)
        hideDismissTarget()
        removeGhostTrails()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            bubbleView = null
        }
        particleBurst?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            particleBurst = null
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun cancelAllAnimations() {
        implodeAnimator?.cancel()
        implodeAnimator = null
        bubbleEnterAnimator?.cancel()
        bubbleEnterAnimator = null
        menuAnimator?.cancel()
        menuAnimator = null
        particleBurst?.cancelBurst()
        bubbleScaleAnimator?.cancel()
        bubbleScaleAnimator = null
        dismissTargetView?.snapRemovalState(false)
        isStopping = false
    }

    private fun requestStopWithAnimation() {
        if (isStopping) return
        if (bubbleView == null) {
            stopSelf()
            return
        }
        playImplodeAndStop()
    }

    private fun startForegroundWithCurrentType() {
        val notification = BubbleNotificationHelper.createNotification(this)
        startForegroundWithNotification(notification)
    }

    private fun startForegroundWithNotification(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (mediaProjection != null) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            if (serviceType != 0) {
                startForeground(
                    BubbleNotificationHelper.NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } else {
                startForeground(BubbleNotificationHelper.NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(BubbleNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun applyMediaProjectionFromIntent(intent: Intent?) {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            mediaProjection?.stop()
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
            startForegroundWithCurrentType()
        }
    }

    private fun setupBubble() {
        val metrics = resources.displayMetrics
        val bubbleSize = BubbleOverlayUtils.dpToPx(this, BUBBLE_SIZE_DP)
        val view = FloatingBubbleView(this, bubbleSize, overlayTheme)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 400
        }
        bubbleParams = params

        dismissController = BubbleDismissController(
            context = this,
            bubbleSizePx = bubbleSize,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )

        touchHandler = BubbleTouchHandler(
            dragThresholdPx = BubbleOverlayUtils.dpToPx(this, DRAG_THRESHOLD_DP),
            dismissController = dismissController!!,
            onDragStart = {
                hideMenu(immediate = true)
                isInRemovalZone = false
                showGhostTrails()
                showDismissTarget()
            },
            onDragMove = { p ->
                try { windowManager.updateViewLayout(view, p) } catch (_: Exception) {}
                updateGhostPositions()
                updateDismissZoneFeedback(view, p)
            },
            onDragEnd = { result, _ ->
                hideDismissTarget()
                removeGhostTrails()
                resetBubbleDismissFeedback(view)
                dismissController?.reset()
                when (result) {
                    is BubbleDismissController.DragEndResult.Dismiss -> playImplodeAndStop()
                    is BubbleDismissController.DragEndResult.Dock -> {
                        val (dx, dy) = result.position
                        bubbleParams?.let { bp -> animateDock(view, bp, dx, dy) }
                    }
                    is BubbleDismissController.DragEndResult.Tap -> Unit
                }
            },
            onTap = {
                if (!isProcessing) {
                    bubbleParams?.let { toggleMenu(it) }
                }
            }
        )
        touchHandler!!.attach(view, params)

        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        windowManager.addView(view, params)
        playBubbleEnterAnimation(view)
    }

    private fun playBubbleEnterAnimation(view: View) {
        bubbleEnterAnimator?.cancel()
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        bubbleEnterAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun showGhostTrails() {
        if (ghostViews.isNotEmpty()) return
        val bp = bubbleParams ?: return
        val size = BubbleOverlayUtils.dpToPx(this, BUBBLE_SIZE_DP)
        repeat(2) {
            val ghost = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(overlayTheme.primaryGhost)
                }
                alpha = 0.35f
            }
            val gParams = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bp.x
                y = bp.y
            }
            try {
                windowManager.addView(ghost, gParams)
                ghostViews.add(ghost)
            } catch (_: Exception) {
                return
            }
        }
        updateGhostPositions()
    }

    private fun updateGhostPositions() {
        val bp = bubbleParams ?: return
        ghostViews.forEachIndexed { index, ghost ->
            val offset = (index + 1) * BubbleOverlayUtils.dpToPx(this, 6)
            val lp = ghost.layoutParams as? WindowManager.LayoutParams ?: return@forEachIndexed
            lp.x = bp.x - offset
            lp.y = bp.y - offset / 2
            try { windowManager.updateViewLayout(ghost, lp) } catch (_: Exception) {}
        }
    }

    private fun removeGhostTrails() {
        ghostViews.forEach { ghost ->
            try { windowManager.removeView(ghost) } catch (_: Exception) {}
        }
        ghostViews.clear()
    }

    private fun showDismissTarget() {
        if (dismissTargetView != null) return
        val zoneSize = BubbleOverlayUtils.dpToPx(this, DismissZoneMetrics.TARGET_SIZE_DP)
        val target = DismissTargetView(this)
        dismissTargetView = target
        val zoneParams = WindowManager.LayoutParams(
            zoneSize, zoneSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = BubbleOverlayUtils.dpToPx(this@FloatingBubbleService, DismissZoneMetrics.BOTTOM_INSET_DP) - zoneSize / 2
        }
        try {
            windowManager.addView(target, zoneParams)
        } catch (_: Exception) {
            dismissTargetView = null
        }
    }

    private fun hideDismissTarget() {
        dismissTargetView?.snapRemovalState(false)
        dismissTargetView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        dismissTargetView = null
        isInRemovalZone = false
    }

    private fun updateDismissZoneFeedback(view: FloatingBubbleView, params: WindowManager.LayoutParams) {
        val inZone = dismissController?.isInDismissZone(params) == true
        if (inZone == isInRemovalZone) return
        isInRemovalZone = inZone
        dismissTargetView?.animateRemovalState(inZone)
        animateBubbleRemovalScale(view, inZone)
    }

    private fun animateBubbleRemovalScale(view: FloatingBubbleView, active: Boolean) {
        bubbleScaleAnimator?.cancel()
        val start = view.scaleX
        val end = if (active) 0.85f else 1f
        bubbleScaleAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 250L
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                view.scaleX = s
                view.scaleY = s
            }
            start()
        }
    }

    private fun resetBubbleDismissFeedback(view: FloatingBubbleView) {
        bubbleScaleAnimator?.cancel()
        bubbleScaleAnimator = null
        isInRemovalZone = false
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun animateDock(view: FloatingBubbleView, params: WindowManager.LayoutParams, targetX: Int, targetY: Int) {
        val startX = params.x
        val startY = params.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                params.x = (startX + (targetX - startX) * t).toInt()
                params.y = (startY + (targetY - startY) * t).toInt()
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun bubbleCenterOnScreen(view: View, params: WindowManager.LayoutParams): Pair<Float, Float> {
        val width = if (view.width > 0) view.width else params.width
        val height = if (view.height > 0) view.height else params.height
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return (location[0] + width / 2f) to (location[1] + height / 2f)
    }

    private fun playImplodeAndStop() {
        if (isStopping) return
        isStopping = true
        hideMenu(immediate = true)
        hideDismissTarget()
        removeGhostTrails()
        val view = bubbleView ?: run {
            stopSelf()
            return
        }
        val params = bubbleParams ?: run {
            stopSelf()
            return
        }
        resetBubbleDismissFeedback(view)

        val width = if (view.width > 0) view.width else params.width
        val height = if (view.height > 0) view.height else params.height
        view.pivotX = width / 2f
        view.pivotY = height / 2f

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 0f)
        val rotation = ObjectAnimator.ofFloat(view, View.ROTATION, view.rotation, view.rotation + 720f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)

        implodeAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, rotation, alpha)
            duration = 420L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val (cx, cy) = bubbleCenterOnScreen(view, params)
                    showParticleBurst(cx, cy)
                }
            })
            start()
        }
    }

    private fun showParticleBurst(centerX: Float, centerY: Float) {
        val burst = DismissParticleBurst(this, centerX, centerY, overlayTheme.primary) {
            mainHandler.post {
                particleBurst?.let {
                    try { windowManager.removeView(it) } catch (_: Exception) {}
                }
                particleBurst = null
                stopSelf()
            }
        }
        particleBurst = burst
        val size = BubbleOverlayUtils.dpToPx(this, 200)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - size / 2).toInt()
            y = (centerY - size / 2).toInt()
        }
        try {
            windowManager.addView(burst, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun toggleMenu(bubbleLayoutParams: WindowManager.LayoutParams) {
        if (isMenuVisible) hideMenu() else showMenu(bubbleLayoutParams)
    }

    private fun showMenu(bubbleLayoutParams: WindowManager.LayoutParams) {
        menuView?.let { existing ->
            menuAnimator?.cancel()
            menuAnimator = null
            try { windowManager.removeView(existing) } catch (_: Exception) {}
            menuView = null
        }

        val menu = RadialActionMenu(context = this, theme = overlayTheme) {
            hideMenu()
            initiateScreenCapture()
        }
        menuView = menu

        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleSize = BubbleOverlayUtils.dpToPx(this, BUBBLE_SIZE_DP)
        val menuEstimatedWidth = BubbleOverlayUtils.dpToPx(this, 160)
        val menuOnLeft = bubbleLayoutParams.x + bubbleSize + menuEstimatedWidth > screenWidth
        val menuX = if (menuOnLeft) {
            (bubbleLayoutParams.x - menuEstimatedWidth - BubbleOverlayUtils.dpToPx(this, 8)).coerceAtLeast(0)
        } else {
            bubbleLayoutParams.x + bubbleSize + BubbleOverlayUtils.dpToPx(this, 8)
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX
            y = bubbleLayoutParams.y + BubbleOverlayUtils.dpToPx(this@FloatingBubbleService, 4)
        }

        menu.scaleX = 0.85f
        menu.scaleY = 0.85f
        menu.alpha = 0f
        windowManager.addView(menu, menuParams)
        isMenuVisible = true

        menu.post {
            menu.pivotX = if (menuOnLeft) menu.width.toFloat() else 0f
            menu.pivotY = menu.height / 2f
            playMenuEnterAnimation(menu)
        }
    }

    private fun playMenuEnterAnimation(menu: View) {
        menuAnimator?.cancel()
        val scaleX = ObjectAnimator.ofFloat(menu, View.SCALE_X, 0.85f, 1f)
        val scaleY = ObjectAnimator.ofFloat(menu, View.SCALE_Y, 0.85f, 1f)
        val alpha = ObjectAnimator.ofFloat(menu, View.ALPHA, 0f, 1f)
        menuAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 220L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun hideMenu(immediate: Boolean = false) {
        val menu = menuView ?: run {
            isMenuVisible = false
            return
        }
        if (immediate) {
            menuAnimator?.cancel()
            menuAnimator = null
            try { windowManager.removeView(menu) } catch (_: Exception) {}
            menuView = null
            isMenuVisible = false
            return
        }
        menuAnimator?.cancel()
        isMenuVisible = false
        val scaleX = ObjectAnimator.ofFloat(menu, View.SCALE_X, menu.scaleX, 0.9f)
        val scaleY = ObjectAnimator.ofFloat(menu, View.SCALE_Y, menu.scaleY, 0.9f)
        val alpha = ObjectAnimator.ofFloat(menu, View.ALPHA, menu.alpha, 0f)
        menuAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 150L
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (menuView === menu) {
                        try { windowManager.removeView(menu) } catch (_: Exception) {}
                        menuView = null
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (menuView === menu) {
                        try { windowManager.removeView(menu) } catch (_: Exception) {}
                        menuView = null
                    }
                }
            })
            start()
        }
    }

    private fun initiateScreenCapture() {
        if (isProcessing) {
            Toast.makeText(this, "Already processing a scan", Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaProjection == null) {
            MediaProjectionConsentActivity.requestConsent(this, triggerCapture = false)
            Toast.makeText(
                this,
                "Allow screen capture, then tap Scan again",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        beginCaptureAfterProjectionReady()
    }

    private fun beginCaptureAfterProjectionReady() {
        if (mediaProjection == null) {
            Toast.makeText(this, "Screen capture permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val bp = bubbleParams
        val bv = bubbleView
        if (bv != null && bp != null) {
            bp.alpha = 0f
            try { windowManager.updateViewLayout(bv, bp) } catch (_: Exception) {}
        }

        mainHandler.postDelayed({ performCapture() }, 300)
    }

    private fun performCapture() {
        val mp = mediaProjection
        if (mp == null) {
            restoreBubbleVisibility()
            MediaProjectionConsentActivity.requestConsent(this, triggerCapture = false)
            Toast.makeText(
                this,
                "Allow screen capture, then tap Scan again",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image == null) return@setOnImageAvailableListener

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride

                val full = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buffer)
                image.close()

                val cropped = if (bitmapWidth > width) {
                    Bitmap.createBitmap(full, 0, 0, width, height).also { full.recycle() }
                } else full

                virtualDisplay?.release()
                imageReader.close()

                restoreBubbleVisibility()
                processScanInBackground(cropped)

            } catch (e: Exception) {
                try { image.close() } catch (_: Exception) {}
                virtualDisplay?.release()
                imageReader.close()
                restoreBubbleVisibility()
                invalidateMediaProjection()
                Toast.makeText(
                    this,
                    "Capture failed — tap Scan again to re-allow screen capture",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, mainHandler)

        try {
            virtualDisplay = mp.createVirtualDisplay(
                "SudokuScan",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )
        } catch (e: Exception) {
            imageReader.close()
            restoreBubbleVisibility()
            invalidateMediaProjection()
            Toast.makeText(
                this,
                "Screen capture expired — tap Scan again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun invalidateMediaProjection() {
        mediaProjection?.stop()
        mediaProjection = null
        startForegroundWithCurrentType()
    }

    private fun restoreBubbleVisibility() {
        val bp = bubbleParams
        val bv = bubbleView
        if (bv != null && bp != null) {
            bp.alpha = 1f
            try { windowManager.updateViewLayout(bv, bp) } catch (_: Exception) {}
        }
    }

    private fun processScanInBackground(bitmap: Bitmap) {
        isProcessing = true
        updateProcessingUi(ScanPhase.EXTRACTING)

        val processor = scanProcessor ?: SudokuScanProcessor(this).also { scanProcessor = it }

        serviceScope.launch {
            try {
                val result = processor.process(bitmap) { phase ->
                    mainHandler.post { updateProcessingUi(phase) }
                }
                bitmap.recycle()

                when (result) {
                    is ScanResult.Success -> {
                        BubbleNotificationHelper.showSuccessNotification(
                            context = this@FloatingBubbleService,
                            solvedDigits = result.solvedDigits,
                            extractedDigits = result.extractedDigits.toDigitIntArray(),
                            screenshotPath = result.screenshotPath
                        )
                    }
                    is ScanResult.Failure -> {
                        BubbleNotificationHelper.showFailureNotification(
                            context = this@FloatingBubbleService,
                            extractedDigits = result.extractedDigits.toDigitIntArray(),
                            screenshotPath = result.screenshotPath
                        )
                    }
                }
            } catch (e: Exception) {
                bitmap.recycle()
                Toast.makeText(
                    this@FloatingBubbleService,
                    "Scan failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isProcessing = false
                mainHandler.post {
                    bubbleView?.setProcessing(false)
                    startForegroundWithCurrentType()
                }
            }
        }
    }

    private fun updateProcessingUi(phase: ScanPhase) {
        bubbleView?.setProcessing(true, phase)
        val notification = BubbleNotificationHelper.createProgressNotification(this, phase)
        startForegroundWithNotification(notification)
    }
}
