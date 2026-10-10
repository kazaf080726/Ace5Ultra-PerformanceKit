package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ace5ultra.perfkit.R
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import com.ace5ultra.perfkit.ui.theme.AccentBlue

private data class ProfileDef(val key: String, val titleRes: Int, val descRes: Int, val badge: String? = null)

@Composable
fun ProfileScreen(vm: PerfViewModel) {
    val status by vm.status.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    val profiles = listOf(
        ProfileDef("powersave", R.string.profile_powersave, R.string.profile_powersave_desc),
        ProfileDef("balanced", R.string.profile_balanced, R.string.profile_balanced_desc),
        ProfileDef("performance", R.string.profile_performance, R.string.profile_performance_desc),
        ProfileDef("game", R.string.profile_game, R.string.profile_game_desc, stringResource(R.string.profile_game_extreme)),
        ProfileDef("system", R.string.profile_system, R.string.profile_system_desc),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text("${stringResource(R.string.active_profile)}: ${status?.profile?.ifBlank { "N/A" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        profiles.forEach { def ->
            val selected = status?.profile == def.key
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(def.titleRes), style = MaterialTheme.typography.titleMedium,
                                color = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold)
                            if (def.badge != null) {
                                Spacer(Modifier.height(0.dp)); Spacer(Modifier.padding(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(50)).background(AccentBlue)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text(def.badge, color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                        if (selected) Text(stringResource(R.string.active),
                            style = MaterialTheme.typography.labelSmall, color = AccentBlue,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(def.descRes), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        busy = true; msg = null
                        vm.setProfile(def.key) { ok ->
                            busy = false
                            msg = if (ok) "Applied ${def.key}" else "Rejected by perfctl"
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.apply)) }
                }
            }
        }

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.safety_title), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.safety_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.snapshot { msg = if (it) "Snapshot saved" else "Snapshot failed" } }) {
                        Text(stringResource(R.string.snapshot))
                    }
                    OutlinedButton(onClick = { vm.restore { msg = if (it) "Restored" else "Restore failed" } }) {
                        Text(stringResource(R.string.restore))
                    }
                }
            }
        }
        msg?.let { Text(it, color = AccentBlue) }
    }
}
