package com.ace5ultra.perfkit.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.data.Updater
import com.ace5ultra.perfkit.data.UpdateInfo
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UpdaterScreen(vm: PerfViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }

    val localCode = (vm.root.value as? PerfViewModel.RootUi.Ready)?.module?.versionCode ?: -1
    val localAppCode = localAppVersionCode(context)

    suspend fun check() {
        checking = true
        info = withContext(Dispatchers.IO) { Updater.fetchUpdateJson() }
        checking = false
    }

    LaunchedEffect(Unit) { check() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Updater", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Compares module + APK versionCode against release/update.json.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (checking) {
                    RowLoading()
                } else {
                    val u = info
                    if (u == null) {
                        Text("Could not fetch update.json. Check network or repo owner constant.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Remote: v${u.version} (code ${u.versionCode})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("Installed module code: $localCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val moduleAvail = Updater.moduleUpdateAvailable(localCode, u)
                        val appAvail = Updater.appUpdateAvailable(localAppCode, u)

                        if (moduleAvail) {
                            Button(
                                onClick = {
                                    busy = true; note = "Downloading zip…"
                                    Updater.downloadAndHandZip(context, u.zipUrl) { file, err ->
                                        busy = false
                                        if (file != null) {
                                            note = "Handing ${file.name} to root manager"
                                            Updater.flashWithRootManager(context, file)
                                        } else note = "Download failed: $err"
                                    }
                                },
                                enabled = !busy,
                            ) { Text("Flash module update") }
                        } else {
                            Text("Module up to date.",
                                color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(Modifier.height(8.dp))
                        if (appAvail) {
                            OutlinedButton(onClick = { Updater.openChangelog(context, u.appUrl) }) {
                                Text("Open newer APK download")
                            }
                        } else {
                            Text("App up to date (local code $localAppCode).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { Updater.openChangelog(context, u.changelog) }) {
                            Text("View changelog")
                        }
                    }
                }
            }
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = { scope.launch { check() } }) { Text("Re-check") }
    }
}

private fun localAppVersionCode(ctx: Context): Int = try {
    val pm = ctx.packageManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, 0).versionCode
    }
} catch (t: Throwable) { -1 }

@Composable
private fun RowLoading() {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text("Checking GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
