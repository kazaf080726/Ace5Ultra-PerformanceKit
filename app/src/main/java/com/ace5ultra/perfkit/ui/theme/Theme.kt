package com.ace5ultra.perfkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val White = Color.White

private val DarkColors = darkColorScheme(
    primary = OnePlusRed,
    onPrimary = White,
    primaryContainer = OnePlusRedDim,
    onPrimaryContainer = White,
    secondary = Color(0xFFE8E8ED),
    background = DarkBase,
    onBackground = OnSurfaceLight,
    surface = DarkSurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFF202228),
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF3A3D45),
    error = HotRed,
)

private val LightColors = lightColorScheme(
    primary = OnePlusRed,
    onPrimary = White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = OnePlusRedDim,
    secondary = Color(0xFF33363B),
    background = Color(0xFFF5F6F8),
    onBackground = OnSurfaceDark,
    surface = Color(0xFFFFFFFF),
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFFE7E9EE),
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Color(0xFFC5C9D1),
    error = HotRed,
)

@Composable
fun PerfKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
