package com.vitbon.kkm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VitbonPrimary,
    onPrimary = Color.White,
    primaryContainer = VitbonPrimaryVariant,
    secondary = VitbonSecondary,
    background = DarkBackground,
    surface = DarkSurface
)

private val LightColorScheme = lightColorScheme(
    primary = VitbonPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    secondary = VitbonSecondary,
    background = LightBackground,
    surface = LightSurface
)

@Composable
fun VitbonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as Activity
        activity.window.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
