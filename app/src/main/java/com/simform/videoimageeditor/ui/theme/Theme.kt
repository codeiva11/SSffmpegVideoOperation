package com.simform.videoimageeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    secondary = Color(0xFF535F70),
    background = Color(0xFFFBFCFE),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCAFF),
    onPrimary = Color(0xFF003352),
    secondary = Color(0xFFBBC7DB),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6)
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
