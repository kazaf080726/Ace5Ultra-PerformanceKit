package com.ace5ultra.perfkit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import com.ace5ultra.perfkit.ui.nav.Route

/** Floating, rounded, blurred bottom navigation bar. */
@Composable
fun GlassBottomBar(
    current: Route,
    onSelect: (Route) -> Unit,
) {
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp + navPad)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        GlassPanel(shape = RoundedCornerShape(28.dp), tintAlpha = 0.20f) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Route.entries.forEach { r ->
                    val selected = r == current
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    Column(
                        modifier = Modifier
                            .width(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(r) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = routeIcon(r),
                            contentDescription = r.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            r.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun routeIcon(r: Route): ImageVector = when (r) {
    Route.Dashboard -> Icons.Filled.BarChart
    Route.Profile -> Icons.Filled.Palette
    Route.Tuning -> Icons.Filled.SettingsEthernet
    Route.Adaptive -> Icons.Filled.AutoAwesome
    Route.Updater -> Icons.Filled.SystemUpdate
}
