package com.simform.videoimageeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Original app colors (colors.xml on main): colorPrimary #2D647D,
 * colorAccent #46a0c6. The old theme was DayNight, so the buttons stay
 * teal in both light and dark mode.
 */
private val TealPrimary = Color(0xFF2D647D)
private val TealAccent = Color(0xFF46A0C6)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = TealAccent,
    onSecondary = Color.White,
    background = Color.White,
    surface = Color.White,
    onSurface = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = TealAccent,
    onSecondary = Color.White,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onSurface = Color.White
)

@Composable
fun VideoOperationsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
