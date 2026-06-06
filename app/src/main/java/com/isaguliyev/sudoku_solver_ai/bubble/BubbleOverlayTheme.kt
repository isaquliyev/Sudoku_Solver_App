package com.isaguliyev.sudoku_solver_ai.bubble

import android.content.Context
import android.content.Intent
import android.content.res.Configuration

data class BubbleOverlayTheme(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val outline: Int,
    val error: Int
) {
    val primaryStroke: Int get() = withAlpha(primary, 0.33f)
    val primaryGhost: Int get() = withAlpha(primary, 0.27f)

    fun putExtras(intent: Intent) {
        intent.putExtra(EXTRA_PRIMARY, primary)
        intent.putExtra(EXTRA_ON_PRIMARY, onPrimary)
        intent.putExtra(EXTRA_SURFACE, surface)
        intent.putExtra(EXTRA_ON_SURFACE, onSurface)
        intent.putExtra(EXTRA_OUTLINE, outline)
        intent.putExtra(EXTRA_ERROR, error)
    }

    companion object {
        const val EXTRA_PRIMARY = "bubble_theme_primary"
        const val EXTRA_ON_PRIMARY = "bubble_theme_on_primary"
        const val EXTRA_SURFACE = "bubble_theme_surface"
        const val EXTRA_ON_SURFACE = "bubble_theme_on_surface"
        const val EXTRA_OUTLINE = "bubble_theme_outline"
        const val EXTRA_ERROR = "bubble_theme_error"

        fun fromIntent(context: Context, intent: Intent?): BubbleOverlayTheme {
            if (intent != null && intent.hasExtra(EXTRA_PRIMARY)) {
                return BubbleOverlayTheme(
                    primary = intent.getIntExtra(EXTRA_PRIMARY, 0),
                    onPrimary = intent.getIntExtra(EXTRA_ON_PRIMARY, 0),
                    surface = intent.getIntExtra(EXTRA_SURFACE, 0),
                    onSurface = intent.getIntExtra(EXTRA_ON_SURFACE, 0),
                    outline = intent.getIntExtra(EXTRA_OUTLINE, 0),
                    error = intent.getIntExtra(EXTRA_ERROR, 0)
                )
            }
            return forContext(context)
        }

        fun forContext(context: Context): BubbleOverlayTheme {
            val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return if (isDark) darkFallback() else lightFallback()
        }

        private fun lightFallback() = BubbleOverlayTheme(
            primary = 0xFF1A3FD0.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            surface = 0xFFFDFBFF.toInt(),
            onSurface = 0xFF000E3C.toInt(),
            outline = 0xFF767689.toInt(),
            error = 0xFFBA1A1A.toInt()
        )

        private fun darkFallback() = BubbleOverlayTheme(
            primary = 0xFFB8C4FF.toInt(),
            onPrimary = 0xFF001C7A.toInt(),
            surface = 0xFF252839.toInt(),
            onSurface = 0xFFE3E1F5.toInt(),
            outline = 0xFF8F8FA3.toInt(),
            error = 0xFFFFB4AB.toInt()
        )

        fun withAlpha(color: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
