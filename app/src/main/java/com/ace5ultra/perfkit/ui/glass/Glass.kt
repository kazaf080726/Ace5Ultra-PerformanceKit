package com.ace5ultra.perfkit.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass helpers.
 *
 * On Android 12+ (API 31) real GPU blur is available through [Modifier.blur]
 * (which uses RenderEffect.createBlurEffect under the hood). Below API 31 blur
 * is unsupported in Compose, so we fall back to a translucent frosted tint and
 * border — the panels still read as glass, just without backdrop sampling.
 */
object Glass {
    val BlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Soft neutral + red ambient blobs used as the app-wide blurred backdrop. */
    @Composable
    fun BackdropBrush(dark: Boolean): Brush {
        val base = if (dark) Color(0xFF0E0F12) else Color(0xFFF2F3F6)
        val red = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.55f else 0.45f)
        val cool = if (dark) Color(0xFF223047) else Color(0xFFD9E2F2)
        return Brush.radialGradient(
            colors = listOf(red, cool, base),
            center = Offset(600f, 300f),
            radius = 1200f,
        )
    }
}

/** Full-screen ambient blurred backdrop. */
@Composable
fun GlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (Glass.BlurSupported) Modifier.blur(140.dp) else Modifier)
            .background(Glass.BackdropBrush(dark)),
    )
}

/**
 * A frosted glass panel. Translucent white scrim + hairline border. On API 31+
 * the scrim is layered over the already-blurred [GlassBackdrop] behind it.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tintAlpha: Float = 0.14f,
    content: @Composable () -> Unit,
) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = if (onDark) Color.White else Color.White
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = tintAlpha + 0.05f),
                        tint.copy(alpha = tintAlpha),
                    ),
                ),
            )
            .border(
                width = Dp.Hairline,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)),
                ),
                shape = shape,
            ),
    ) { content() }
}

private fun Color.luminance(): Double =
    (0.299 * red + 0.587 * green + 0.114 * blue).toDouble()

/** Debug/metric helper: paint a subtle radial glow behind accent chips. */
fun Modifier.accentGlow(color: Color, radius: Dp = 240.dp): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
            center = center,
            radius = radius.toPx() * 1.4f,
        ),
    )
}
