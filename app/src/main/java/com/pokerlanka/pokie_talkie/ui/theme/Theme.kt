package com.pokerlanka.pokie_talkie.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pokerlanka.pokie_talkie.data.local.AppThemeMode

private val PokieDarkColorScheme = darkColorScheme(
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

private val PokieLightColorScheme = lightColorScheme(
    primary = PokieEmerald,
    onPrimary = PokieLightSurface,
    primaryContainer = PokieEmeraldDark,
    onPrimaryContainer = PokieLightSurface,
    secondary = PokieGreenAccent,
    onSecondary = PokieLightSurface,
    background = PokieLightBg,
    onBackground = PokieLightTextPrimary,
    surface = PokieLightSurface,
    onSurface = PokieLightTextPrimary,
    surfaceVariant = PokieLightInput,
    onSurfaceVariant = PokieLightTextSecondary,
    error = PokieCallRed
)

@Composable
fun PokieTalkieTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) PokieDarkColorScheme else PokieLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = (if (isDark) PokieDarkSurface else PokieLightSurface).toArgb()
                window.navigationBarColor = (if (isDark) PokieDarkBg else PokieLightBg).toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

