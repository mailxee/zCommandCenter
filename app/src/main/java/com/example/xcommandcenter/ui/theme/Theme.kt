package com.example.xcommandcenter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app icon's colors: an orange accent on cool charcoal greys.
private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF331200),
    inversePrimary = Color(0xFFFFB68C),
    secondary = Color(0xFF5B5F6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3D4),
    onSecondaryContainer = Color(0xFF3A1A06),
    tertiary = Color(0xFF3F6181),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001D32),
    background = Color(0xFFF9F9FC),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF9F9FC),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E2EA),
    onSurfaceVariant = Color(0xFF45474F),
    surfaceTint = Color(0xFF9C4500),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F4),
    outline = Color(0xFF75777F),
    outlineVariant = Color(0xFFC5C6CE),
    surfaceBright = Color(0xFFF9F9FC),
    surfaceDim = Color(0xFFD9D9DD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F7),
    surfaceContainer = Color(0xFFEDEDF1),
    surfaceContainerHigh = Color(0xFFE8E8EC),
    surfaceContainerHighest = Color(0xFFE2E2E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFF773300),
    onPrimaryContainer = Color(0xFFFFDBC9),
    inversePrimary = Color(0xFF9C4500),
    secondary = Color(0xFFC3C6D3),
    onSecondary = Color(0xFF2C303A),
    secondaryContainer = Color(0xFF4A3529),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFA8CAEE),
    onTertiary = Color(0xFF0B3350),
    tertiaryContainer = Color(0xFF274A68),
    onTertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFF121419),
    onBackground = Color(0xFFE3E2E7),
    surface = Color(0xFF121419),
    onSurface = Color(0xFFE3E2E7),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFFFB68C),
    inverseSurface = Color(0xFFE3E2E7),
    inverseOnSurface = Color(0xFF2F3036),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF44464F),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121419),
    surfaceContainerLowest = Color(0xFF0D0F13),
    surfaceContainerLow = Color(0xFF1A1C21),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34353B),
)

/** Material 3 theme in the app icon's colors, light or dark with the system. */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
