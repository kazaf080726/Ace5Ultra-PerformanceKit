package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel

/**
 * Shown when root is unavailable or the module isn't installed. Guides the user
 * through granting root + flashing the module zip (built-in updater does both).
 */
@Composable
fun SetupScreen(vm: PerfViewModel, state: PerfViewModel.RootUi) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Almost there", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                when (state) {
                    is PerfViewModel.RootUi.NoRoot -> {
                        Text("Root not granted", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "PerfKit needs a root shell (SukiSU Ultra / BakaSU / KernelSU) to talk to the " +
                                "module. Grant root in your root manager, then retry. " +
                                "Without root you can still see basic device info from /proc.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is PerfViewModel.RootUi.MissingModule -> {
                        Text("Module not installed", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Root is working but ace5ultra_perfkit is not installed. Flash the module zip " +
                                "with your root manager. Use the Updater tab to download + hand it off.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text("Checking root…", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.refreshRoot() }) { Text("Retry") }
            }
        }
    }
}
