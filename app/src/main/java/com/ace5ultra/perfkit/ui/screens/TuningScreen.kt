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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.data.TuningItem
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel

@Composable
fun TuningScreen(vm: PerfViewModel) {
    val items by vm.tuning.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.reloadTuning() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Manual tuning", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Only keys perfctl marks safe + in-range are editable. Out-of-range or unsafe writes are refused by perfctl.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (items.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("No tunable keys discovered for this device.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items.forEach { item -> TuningRow(item, onApply = { v ->
            vm.setTuning(item.key, v) { ok ->
                msg = if (ok) "Applied ${item.key}=$v" else "Rejected by perfctl"
            }
        }) }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun TuningRow(item: TuningItem, onApply: (String) -> Unit) {
    val numeric = !item.min.isNaN() && !item.max.isNaN() && item.min < item.max
    var slider by remember(item.key) {
        mutableFloatStateOf(item.value.toFloatOrNull()?.coerceIn(item.min.toFloat(), item.max.toFloat()) ?: item.min.toFloat())
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.key, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text(if (item.safe) "safe" else "read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.safe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("current=${item.value} ${item.unit}".trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (numeric && item.safe) {
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    valueRange = item.min.toFloat()..item.max.toFloat(),
                    onValueChangeFinished = {
                        val v = "%.0f".format(slider)
                        onApply(v)
                    },
                )
                Text("${"%.0f".format(slider)} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
