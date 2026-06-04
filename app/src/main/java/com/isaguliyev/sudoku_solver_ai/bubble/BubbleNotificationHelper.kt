package com.isaguliyev.sudoku_solver_ai.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.isaguliyev.sudoku_solver_ai.MainActivity

object BubbleNotificationHelper {

    const val CHANNEL_ID = "sudoku_bubble_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotification(context: Context): Notification {
        createChannel(context)

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
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createChannel(context: Context) {
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
}
