package com.ace5ultra.perfkit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Scrolling multi-series history line chart drawn with raw Compose Canvas.
 * No chart library. Each entry in [series] is one line; colors cycle.
 * Values expected 0..100 (utilization). N/NaN entries are skipped per series.
 */
@Composable
fun HistoryLineChart(
    series: List<List<Float>>,
    modifier: Modifier = Modifier,
    valueRange: Float = 100f,
    lineColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF4CD964),
        Color(0xFF5AC8FA),
        Color(0xFFFFD60A),
        Color(0xFFFF9F0A),
        Color(0xFF0A84FF),
    ),
    maxPoints: Int = 60,
) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width
        val h = size.height
        // horizontal grid lines at 0/25/50/75/100%
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
            val y = h - h * f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        val n = series.maxOfOrNull { it.size } ?: 0
        if (n == 0) return@Canvas
        val window = if (n > maxPoints) n - maxPoints else 0
        series.forEachIndexed { idx, points ->
            val color = lineColors[idx % lineColors.size]
            val path = Path()
            var started = false
            points.forEachIndexed { i, v ->
                if (i < window) return@forEachIndexed
                if (v.isNaN()) return@forEachIndexed
                val x = w * (i - window) / (maxPoints - 1).coerceAtLeast(1)
                val y = h - (v.coerceIn(0f, valueRange) / valueRange) * h
                if (!started) {
                    path.moveTo(x, y); started = true
                } else {
                    path.lineTo(x, y)
                }
                // dot at the latest point
                if (i == points.lastIndex) {
                    drawCircle(color = color, radius = 3.5f, center = Offset(x, y))
                }
            }
            if (started) {
                drawPath(path, color, style = Stroke(width = 3f))
            }
        }
    }
}
