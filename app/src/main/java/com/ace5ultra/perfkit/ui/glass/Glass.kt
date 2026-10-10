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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass helpers. On Android 12+ (API 31) real GPU backdrop blur is
 * available via [Modifier.blur] (RenderEffect.createBlurEffect). Below API 31
 * we fall back to a translucent frosted tint + border; the panels still read as
 * glass, just without backdrop sampling.
 */
object Glass {
    val BlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Composable
    fun BackdropBrush(dark: Boolean): Brush {
        val base = Color(0xFFEEF1F5)
        val cyan = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        val cool = Color(0xFFDDE8F2)
        return Brush.radialGradient(
            colors = listOf(cyan, cool, base),
            center = Offset(600f, 200f),
            radius = 1400f,
        )
    }
}

@Composable
fun GlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (Glass.BlurSupported) Modifier.blur(140.dp) else Modifier)
            .background(Glass.BackdropBrush(dark)),
    )
}

/**
 * A frosted glass panel: near-opaque white card with soft shadow, hairline
 * border, and a faint cyan tint. On API 31+ the scrim layers over the blurred
 * [GlassBackdrop] behind it. Radius defaults to 24dp.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tintAlpha: Float = 0.82f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, clip = false, spotColor = Color(0x220B1020))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = tintAlpha + 0.08f),
                        Color.White.copy(alpha = tintAlpha),
                    ),
                ),
            )
            .border(
                width = Dp.Hairline,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.9f), Color(0xFFE6ECF3).copy(alpha = 0.6f)),
                ),
                shape = shape,
            ),
    ) { content() }
}

fun Modifier.accentGlow(color: Color, radius: Dp = 240.dp): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.16f), Color.Transparent),
            center = center,
            radius = radius.toPx() * 1.4f,
        ),
    )
}
