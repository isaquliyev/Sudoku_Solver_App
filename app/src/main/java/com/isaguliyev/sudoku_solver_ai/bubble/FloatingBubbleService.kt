package com.isaguliyev.sudoku_solver_ai.bubble

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.isaguliyev.sudoku_solver_ai.MainActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var dismissZoneView: View? = null
    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMenuVisible = false
    private var isInDismissZone = false

    // Touch tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TRIGGER_CAPTURE = "trigger_capture"
        const val ACTION_STOP = "com.isaguliyev.sudoku_solver_ai.STOP_BUBBLE"

        var isRunning = false
            private set

        /** Set before [startForegroundService] so UI/lifecycle reads running state immediately. */
        fun markRunning() {
            isRunning = true
        }

        private const val BUBBLE_SIZE_DP = 60
        private const val DRAG_THRESHOLD_DP = 8
        private const val DISMISS_ZONE_BOTTOM_INSET_DP = 100
        private const val DISMISS_ZONE_RADIUS_DP = 120
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithCurrentType()

        applyMediaProjectionFromIntent(intent)

        if (intent?.getBooleanExtra(EXTRA_TRIGGER_CAPTURE, false) == true) {
            mainHandler.post { beginCaptureAfterProjectionReady() }
        }

        if (bubbleView == null) {
            setupBubble()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        hideMenu()
        hideDismissZone()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            bubbleView = null
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun startForegroundWithCurrentType() {
        val notification = BubbleNotificationHelper.createNotification(this)
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

    // ── Bubble setup ─────────────────────────────────────────────────────────

    private fun setupBubble() {
        val size = dpToPx(BUBBLE_SIZE_DP)
        val view = createBubbleView(size)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 400
        }
        bubbleParams = params

        windowManager.addView(view, params)
        setupTouchListener(view, params)
    }

    private fun createBubbleView(size: Int): View {
        return FrameLayout(this).apply {
            tag = 0xFF6650A4.toInt()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF6650A4.toInt())
                setStroke(dpToPx(2), 0x556650A4.toInt())
            }
            elevation = dpToPx(6).toFloat()

            val label = TextView(this@FloatingBubbleService).apply {
                text = "S"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun setupTouchListener(view: View, params: WindowManager.LayoutParams) {
        val dragThreshold = dpToPx(DRAG_THRESHOLD_DP)

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(dx) > dragThreshold || abs(dy) > dragThreshold)) {
                        isDragging = true
                        if (isMenuVisible) hideMenu()
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                        updateDismissZoneFeedback(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && isInDismissZone) {
                        hideDismissZone()
                        stopSelf()
                    } else {
                        if (!isDragging) toggleMenu(params)
                        resetDismissZoneFeedback(view)
                        hideDismissZone()
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    // ── Swipe-to-dismiss zone ─────────────────────────────────────────────────

    private fun isBubbleInDismissZone(params: WindowManager.LayoutParams): Boolean {
        val size = dpToPx(BUBBLE_SIZE_DP)
        val metrics = resources.displayMetrics
        val bubbleCenterX = params.x + size / 2f
        val bubbleCenterY = params.y + size / 2f
        val zoneCenterX = metrics.widthPixels / 2f
        val zoneCenterY = metrics.heightPixels - dpToPx(DISMISS_ZONE_BOTTOM_INSET_DP)
        val radius = dpToPx(DISMISS_ZONE_RADIUS_DP).toFloat()
        return hypot(
            (bubbleCenterX - zoneCenterX).toDouble(),
            (bubbleCenterY - zoneCenterY).toDouble()
        ) <= radius
    }

    private fun updateDismissZoneFeedback(view: View, params: WindowManager.LayoutParams) {
        val inZone = isBubbleInDismissZone(params)
        if (inZone != isInDismissZone) {
            isInDismissZone = inZone
            if (inZone) showDismissZone() else hideDismissZone()
        }
        if (inZone) {
            view.scaleX = 0.75f
            view.scaleY = 0.75f
            (view.background as? GradientDrawable)?.setColor(0xFFE53935.toInt())
        } else {
            resetDismissZoneFeedback(view)
        }
    }

    private fun resetDismissZoneFeedback(view: View) {
        isInDismissZone = false
        view.scaleX = 1f
        view.scaleY = 1f
        val defaultColor = view.tag as? Int ?: 0xFF6650A4.toInt()
        (view.background as? GradientDrawable)?.setColor(defaultColor)
    }

    private fun showDismissZone() {
        if (dismissZoneView != null) return
        val metrics = resources.displayMetrics
        val zoneSize = dpToPx(72)
        val zone = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x44E53935)
                setStroke(dpToPx(2), 0xAAE53935.toInt())
            }
            alpha = 0.9f
        }
        dismissZoneView = zone
        val zoneParams = WindowManager.LayoutParams(
            zoneSize, zoneSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(DISMISS_ZONE_BOTTOM_INSET_DP) - zoneSize / 2
        }
        try {
            windowManager.addView(zone, zoneParams)
        } catch (_: Exception) {
            dismissZoneView = null
        }
    }

    private fun hideDismissZone() {
        dismissZoneView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            dismissZoneView = null
        }
    }

    // ── Expanded menu ─────────────────────────────────────────────────────────

    private fun toggleMenu(bubbleLayoutParams: WindowManager.LayoutParams) {
        if (isMenuVisible) hideMenu() else showMenu(bubbleLayoutParams)
    }

    private fun showMenu(bubbleLayoutParams: WindowManager.LayoutParams) {
        if (menuView != null) return

        val menu = createMenuView()
        menuView = menu

        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleSize = dpToPx(BUBBLE_SIZE_DP)
        val menuEstimatedWidth = dpToPx(160)
        val menuX = if (bubbleLayoutParams.x + bubbleSize + menuEstimatedWidth > screenWidth) {
            (bubbleLayoutParams.x - menuEstimatedWidth - dpToPx(8)).coerceAtLeast(0)
        } else {
            bubbleLayoutParams.x + bubbleSize + dpToPx(8)
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
            y = bubbleLayoutParams.y + dpToPx(4)
        }

        windowManager.addView(menu, menuParams)
        isMenuVisible = true
    }

    private fun hideMenu() {
        menuView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            menuView = null
        }
        isMenuVisible = false
    }

    private fun createMenuView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dpToPx(32).toFloat()
                setStroke(dpToPx(1), 0xFFDDDDDD.toInt())
            }
            elevation = dpToPx(8).toFloat()

            val hPad = dpToPx(20)
            val vPad = dpToPx(14)
            setPadding(hPad, vPad, hPad, vPad)

            val label = TextView(this@FloatingBubbleService).apply {
                text = "Scan Sudoku"
                textSize = 15f
                setTextColor(0xFF1C1B1F.toInt())
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(label)

            setOnClickListener {
                hideMenu()
                initiateScreenCapture()
            }
        }
    }

    // ── Screenshot capture ────────────────────────────────────────────────────

    private fun initiateScreenCapture() {
        if (mediaProjection == null) {
            MediaProjectionConsentActivity.requestConsent(this, triggerCapture = true)
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
            MediaProjectionConsentActivity.requestConsent(this, triggerCapture = true)
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
                saveBitmapAndLaunch(cropped)

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

    private fun saveBitmapAndLaunch(bitmap: Bitmap) {
        Thread {
            try {
                val file = File(cacheDir, "sudoku_scan.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                bitmap.recycle()

                mainHandler.post {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_SCREENSHOT
                            putExtra(MainActivity.EXTRA_SCREENSHOT_PATH, file.absolutePath)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Failed to save scan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
