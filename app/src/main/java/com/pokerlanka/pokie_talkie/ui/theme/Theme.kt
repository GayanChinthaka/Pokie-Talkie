package com.pokerlanka.pokie_talkie.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PokieColorScheme = darkColorScheme(
    primary = PokieEmerald,
    onPrimary = PokieDarkBg,
    primaryContainer = PokieEmeraldDark,
    onPrimaryContainer = PokieTextPrimary,
    secondary = PokieGreenAccent,
    onSecondary = PokieDarkBg,
    background = PokieDarkBg,
    onBackground = PokieTextPrimary,
    surface = PokieDarkSurface,
    onSurface = PokieTextPrimary,
    surfaceVariant = PokieDarkCard,
    onSurfaceVariant = PokieTextSecondary,
    error = PokieCallRed
)

@Composable
fun PokieTalkieTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = PokieColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = PokieDarkSurface.toArgb()
                window.navigationBarColor = PokieDarkBg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
