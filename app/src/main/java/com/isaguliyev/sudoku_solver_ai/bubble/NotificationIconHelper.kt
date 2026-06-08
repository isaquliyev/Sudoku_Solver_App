package com.isaguliyev.sudoku_solver_ai.bubble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap

object NotificationIconHelper {

    fun launcherNotificationBitmap(context: Context, sizePx: Int = 256): Bitmap {
        val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
        return when (drawable) {
            is AdaptiveIconDrawable -> renderAdaptiveIcon(drawable, sizePx)
            is BitmapDrawable -> {
                val bitmap = drawable.bitmap
                if (bitmap != null && bitmap.width == sizePx && bitmap.height == sizePx) {
                    bitmap
                } else {
                    drawable.toBitmap(sizePx, sizePx)
                }
            }
            else -> drawable.toBitmap(sizePx, sizePx)
        }
    }

    private fun renderAdaptiveIcon(icon: AdaptiveIconDrawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        return bitmap
    }
}
