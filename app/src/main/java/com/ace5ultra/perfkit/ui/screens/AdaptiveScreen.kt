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
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.components.Fmt
import com.ace5ultra.perfkit.ui.glass.GlassPanel

@Composable
fun AdaptiveScreen(vm: PerfViewModel) {
    val status by vm.status.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val a = status?.adaptive

    // Keep refreshing while this screen is open so the daemon reason stays live.
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
        Text("Adaptive daemon", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Text("The daemon picks powersave / balanced / performance / game from live load and temperature.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        GlassPanel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Adaptive mode", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Current: ${if (a?.enabled == true) "ON" else "OFF"}",
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
                Text("Live daemon state", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                DV("reason", a?.reason?.ifBlank { "N/A" } ?: "N/A")
                DV("load", Fmt.percent(a?.load ?: Float.NaN))
                DV("temp", Fmt.temp(a?.tempC ?: Float.NaN))
                DV("active profile", status?.profile?.ifBlank { "N/A" } ?: "N/A")
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DV(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
    }
