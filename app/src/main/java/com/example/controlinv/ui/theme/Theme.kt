package com.example.controlinv.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val DarkColorScheme = darkColorScheme(
    primary = AceroBlueLight,
    onPrimary = Slate900,
    primaryContainer = Slate700,
    onPrimaryContainer = Slate100,
    secondary = AmberAccentLight,
    onSecondary = Slate900,
    tertiary = AceroBlue,
    onTertiary = Slate100,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate800,
    onSurface = Slate100,
    surfaceVariant = SurfaceCardDark,
    onSurfaceVariant = Slate300,
    error = ErrorRed,
    onError = Slate100
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
    background = Slate100,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = SurfaceCard,
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
