package com.isaguliyev.sudoku_solver_ai.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.isaguliyev.sudoku_solver_ai.MainActivity
import com.isaguliyev.sudoku_solver_ai.R
import com.isaguliyev.sudoku_solver_ai.scan.ScanPhase
import com.isaguliyev.sudoku_solver_ai.solver.SudokuSolver

object BubbleNotificationHelper {

    const val CHANNEL_ID = "sudoku_bubble_channel"
    const val RESULTS_CHANNEL_ID = "sudoku_scan_results_alert"
    const val NOTIFICATION_ID = 1001
    const val SUCCESS_NOTIFICATION_ID = 1002
    const val FAILURE_NOTIFICATION_ID = 1003

    private val SMALL_ICON = R.drawable.ic_stat_notify

    private fun launcherLargeIcon(context: Context): Bitmap? =
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

    private fun NotificationCompat.Builder.withAppIcons(context: Context): NotificationCompat.Builder =
        setSmallIcon(SMALL_ICON).setLargeIcon(launcherLargeIcon(context))

    fun createNotification(context: Context): Notification {
        createBubbleChannel(context)

        val openPendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            context, 1,
            Intent(context, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sudoku Solver")
            .setContentText("Bubble active — tap it to scan a puzzle")
            .withAppIcons(context)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    fun createProgressNotification(context: Context, phase: ScanPhase): Notification {
        createBubbleChannel(context)

        val text = when (phase) {
            ScanPhase.EXTRACTING -> "Extracting digits…"
            ScanPhase.SOLVING -> "Solving puzzle…"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Processing Sudoku")
            .setContentText(text)
            .withAppIcons(context)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .setSilent(true)
            .build()
    }

    fun showSuccessNotification(
        context: Context,
        solvedDigits: List<Int>,
        extractedDigits: IntArray,
        screenshotPath: String
    ) {
        createResultsChannel(context)

        val boardText = SudokuSolver.formatBoardLines(solvedDigits)
        val pendingIntent = scanResultPendingIntent(
            context = context,
            requestCode = SUCCESS_NOTIFICATION_ID,
            screenshotPath = screenshotPath,
            extractedDigits = extractedDigits,
            solvedDigits = solvedDigits.toIntArray()
        )

        val notification = NotificationCompat.Builder(context, RESULTS_CHANNEL_ID)
            .setContentTitle("Sudoku Solved")
            .setContentText(boardText.lines().firstOrNull() ?: "Tap to view in app")
            .setStyle(NotificationCompat.BigTextStyle().bigText(boardText))
            .withAppIcons(context)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(false)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(SUCCESS_NOTIFICATION_ID, notification)
    }

    fun showFailureNotification(
        context: Context,
        extractedDigits: IntArray,
        screenshotPath: String
    ) {
        createResultsChannel(context)

        val pendingIntent = scanResultPendingIntent(
            context = context,
            requestCode = FAILURE_NOTIFICATION_ID,
            screenshotPath = screenshotPath,
            extractedDigits = extractedDigits,
            solvedDigits = null
        )

        val notification = NotificationCompat.Builder(context, RESULTS_CHANNEL_ID)
            .setContentTitle("Could not solve Sudoku")
            .setContentText("Tap to open the app and edit the board")
            .withAppIcons(context)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(false)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(FAILURE_NOTIFICATION_ID, notification)
    }

    private fun scanResultPendingIntent(
        context: Context,
        requestCode: Int,
        screenshotPath: String,
        extractedDigits: IntArray,
        solvedDigits: IntArray?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SCAN_RESULT
            putExtra(MainActivity.EXTRA_SCREENSHOT_PATH, screenshotPath)
            putExtra(MainActivity.EXTRA_EXTRACTED_DIGITS, extractedDigits)
            putExtra(MainActivity.EXTRA_FROM_BACKGROUND_SCAN, true)
            if (solvedDigits != null) {
                putExtra(MainActivity.EXTRA_SOLVED_DIGITS, solvedDigits)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createBubbleChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sudoku Bubble",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for the floating bubble service"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun createResultsChannel(context: Context) {
        val channel = NotificationChannel(
            RESULTS_CHANNEL_ID,
            "Scan Results",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sudoku scan and solve results"
            enableVibration(true)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
