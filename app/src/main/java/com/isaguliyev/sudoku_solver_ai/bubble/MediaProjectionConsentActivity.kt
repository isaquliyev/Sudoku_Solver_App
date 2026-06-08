package com.isaguliyev.sudoku_solver_ai.bubble

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Transparent trampoline to request MediaProjection consent from a visible activity.
 * Started by [FloatingBubbleService] when the user taps Scan without an active projection.
 */
class MediaProjectionConsentActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIGGER_CAPTURE = "trigger_capture"

        fun requestConsent(context: Context, triggerCapture: Boolean = false) {
            context.startActivity(
                Intent(context, MediaProjectionConsentActivity::class.java).apply {
                    putExtra(EXTRA_TRIGGER_CAPTURE, triggerCapture)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private val mpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FloatingBubbleService::class.java).apply {
                    putExtra(FloatingBubbleService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(FloatingBubbleService.EXTRA_RESULT_DATA, result.data)
                }
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mpLauncher.launch(mpManager.createScreenCaptureIntent())
    }
}
