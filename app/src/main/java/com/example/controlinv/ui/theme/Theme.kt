package com.example.controlinv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AceroBlueLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A2533),
    onPrimaryContainer = Color(0xFFE6F7FF),
    secondary = AmberAccentLight,
    onSecondary = Color.Black,
    tertiary = AceroBlue,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFE5E7EB),
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AceroBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Slate900,
    secondary = AmberAccent,
    onSecondary = Slate900,
    tertiary = Slate700,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = Slate900,
    surface = SurfaceLight,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Slate700,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun ControlInvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
