package com.ace5ultra.perfkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Scene-style light scheme with a neon cyan accent. We force light so the
// frosted cards read on the light gray backdrop; dark mode still adapts via
// the glass overlay (see Glass.kt).
private val AppColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F1FF),
    onPrimaryContainer = Color(0xFF0B4E73),
    secondary = OnSurface,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = OnSurface,
    surface = CardSurface,
    onSurface = OnSurface,
    surfaceVariant = Color(0xFFE7EBF1),
    onSurfaceVariant = OnSurfaceVariant,
    outline = Color(0xFFC8CFDA),
    error = HotRed,
)

@Composable
fun PerfKitTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}
