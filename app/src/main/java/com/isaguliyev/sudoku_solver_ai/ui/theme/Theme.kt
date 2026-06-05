package com.isaguliyev.sudoku_solver_ai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary              = Indigo40,
    onPrimary            = androidx.compose.ui.graphics.Color.White,
    primaryContainer     = Indigo90,
    onPrimaryContainer   = Indigo10,

    secondary            = Blue40,
    onSecondary          = androidx.compose.ui.graphics.Color.White,
    secondaryContainer   = Blue90,
    onSecondaryContainer = Indigo10,

    tertiary             = Teal40,
    onTertiary           = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer    = Teal90,
    onTertiaryContainer  = Indigo10,

    error                = Red40,
    onError              = androidx.compose.ui.graphics.Color.White,

    background           = Neutral99,
    onBackground         = Indigo10,
    surface              = Neutral99,
    onSurface            = Indigo10,
    surfaceVariant       = NeutralVariant90,
    onSurfaceVariant     = androidx.compose.ui.graphics.Color(0xFF454658),
    outline              = androidx.compose.ui.graphics.Color(0xFF767689),
)

private val DarkColorScheme = darkColorScheme(
    primary              = Indigo80,
    onPrimary            = Indigo20,
    primaryContainer     = Indigo30,
    onPrimaryContainer   = Indigo90,

    secondary            = Blue80,
    onSecondary          = androidx.compose.ui.graphics.Color(0xFF003254),
    secondaryContainer   = androidx.compose.ui.graphics.Color(0xFF00497A),
    onSecondaryContainer = Blue90,

    tertiary             = Teal80,
    onTertiary           = androidx.compose.ui.graphics.Color(0xFF003732),
    tertiaryContainer    = androidx.compose.ui.graphics.Color(0xFF004F49),
    onTertiaryContainer  = Teal90,

    error                = Red80,
    onError              = androidx.compose.ui.graphics.Color(0xFF690005),

    background           = Neutral10,
    onBackground         = androidx.compose.ui.graphics.Color(0xFFE3E1F5),
    surface              = Neutral17,
    onSurface            = androidx.compose.ui.graphics.Color(0xFFE3E1F5),
    surfaceVariant       = Neutral22,
    onSurfaceVariant     = androidx.compose.ui.graphics.Color(0xFFC5C4DA),
    outline              = androidx.compose.ui.graphics.Color(0xFF8F8FA3),
)

@Composable
fun Sudoku_solver_aiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
