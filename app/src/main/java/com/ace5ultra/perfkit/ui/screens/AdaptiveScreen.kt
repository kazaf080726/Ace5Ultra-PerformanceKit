package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.R
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.components.Fmt
import com.ace5ultra.perfkit.ui.glass.GlassPanel

@Composable
fun AdaptiveScreen(vm: PerfViewModel) {
    val status by vm.status.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val a = status?.adaptive

    LaunchedEffect(Unit) {
        while (true) { vm.pollOnce(); kotlinx.coroutines.delay(2000) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.adaptive_title), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.adaptive_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        GlassPanel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.adaptive_mode), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("${stringResource(R.string.active_profile)}: ${status?.profile?.ifBlank { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = a?.enabled == true, onCheckedChange = { on ->
                    vm.setAdaptive(on) { ok ->
                        msg = if (ok) if (on) "Adaptive ON" else "Adaptive OFF"
                        else "perfctl rejected"
                    }
                })
            }
        }

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.adaptive_live), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                DV(stringResource(R.string.adaptive_reason), a?.reason?.ifBlank { "N/A" } ?: "N/A")
                DV(stringResource(R.string.adaptive_load), Fmt.percent(a?.load ?: Float.NaN))
                DV(stringResource(R.string.adaptive_temp), Fmt.temp(a?.tempC ?: Float.NaN))
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DV(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
