package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel

private val PROFILES = listOf(
    "powersave" to "Battery saver. Caps max freq, prefers efficient cores, lower boost.",
    "balanced" to "Stock-ish governor/freq, boost on demand. Default.",
    "performance" to "Raise freq floor, boost enabled. Never disables thermal safety.",
    "game" to "Performance + sustained GPU floor, foreground-aware.",
    "adaptive" to "Daemon picks among the four using load, temp and foreground app.",
)

@Composable
fun ProfileScreen(vm: PerfViewModel) {
    val status by vm.status.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Profile", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Active: ${status?.profile?.ifBlank { "N/A" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        PROFILES.forEach { (name, desc) ->
            val selected = status?.profile == name
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground)
                        if (selected) Text("ACTIVE", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        busy = true; msg = null
                        vm.setProfile(name) { ok ->
                            busy = false
                            msg = if (ok) "Applied $name" else "Rejected by perfctl"
                        }
                    }, enabled = !busy) { Text("Apply") }
                }
            }
        }

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Safety", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Perfctl snapshots every original node before changing it. Use Restore to write them back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.snapshot { msg = if (it) "Snapshot saved" else "Snapshot failed" } }) {
                        Text("Snapshot")
                    }
                    OutlinedButton(onClick = { vm.restore { msg = if (it) "Restored" else "Restore failed" } }) {
                        Text("Restore")
                    }
                }
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
