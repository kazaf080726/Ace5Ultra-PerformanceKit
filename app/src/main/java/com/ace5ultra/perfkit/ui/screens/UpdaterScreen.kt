package com.ace5ultra.perfkit.ui.screens

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ace5ultra.perfkit.BuildConfig
import com.ace5ultra.perfkit.R
import com.ace5ultra.perfkit.data.Updater
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun UpdaterScreen(vm: PerfViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf<Updater.Channel?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val localModuleCode = (vm.root.value as? PerfViewModel.RootUi.Ready)?.module?.versionCode ?: -1
    val localAppCode = BuildConfig.VERSION_CODE
    val sDownloading = stringResource(R.string.updater_downloading)
    val sHanding = stringResource(R.string.updater_handing)

    suspend fun check() {
        channel = null
        channel = withContext(Dispatchers.IO) { Updater.decide(localModuleCode, localAppCode) }
    }

    LaunchedEffect(Unit) { check() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.updater_title), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.updater_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                when (val c = channel) {
                    null -> RowLoading()
                    Updater.Channel.Unavailable ->
                        Text(stringResource(R.string.updater_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Updater.Channel.UpToDate -> {
                        Text(stringResource(R.string.updater_up_to_date),
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("${stringResource(R.string.updater_installed)}: module $localModuleCode · app $localAppCode",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is Updater.Channel.ModuleApp -> {
                        Text(stringResource(R.string.updater_module_update),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.updater_channel_module),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("module: $localModuleCode → ${c.info.versionCode} (v${c.info.version})",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("app:    $localAppCode → ${c.info.versionCode} (bundled in zip)",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            busy = true; note = sDownloading
                            Updater.downloadFile(context, c.info.zipUrl, "perfkit-update.zip") { f, err ->
                                busy = false
                                if (f != null) {
                                    note = sHanding
                                    Updater.flashWithRootManager(context, f)
                                } else note = "download failed: $err"
                            }
                        }, enabled = !busy) { Text(stringResource(R.string.updater_flash_module)) }
                    }
                    is Updater.Channel.AppOnly -> {
                        Text(stringResource(R.string.updater_app_update),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.updater_channel_app),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("app: $localAppCode → ${c.info.versionCode} (v${c.info.version})",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            busy = true; note = sDownloading
                            Updater.downloadFile(context, c.info.apkUrl, "PerfKit-update.apk") { f, err ->
                                busy = false
                                if (f != null) {
                                    note = sHanding
                                    Updater.installApk(context, f)
                                } else note = "download failed: $err"
                            }
                        }, enabled = !busy) { Text(stringResource(R.string.updater_install_apk)) }
                    }
                }
                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { scope.launch { check() } }) {
                    Text(stringResource(R.string.updater_recheck))
                }
            }
        }

        LanguageCard()
    }
}

@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "" }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            val options = listOf(
                "" to R.string.lang_system,
                "en" to R.string.lang_english,
                "zh-CN" to R.string.lang_chinese_cn,
                "zh-TW" to R.string.lang_chinese_tw,
            )
            options.forEach { (tag, labelRes) ->
                val selected = current == tag
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { applyLocale(context, tag) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(labelRes),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                    if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun applyLocale(context: Context, tag: String) {
    val list = if (tag.isBlank())
        androidx.core.os.LocaleListCompat.getEmptyLocaleList()
    else
        androidx.core.os.LocaleListCompat.forLanguageTags(tag)
    AppCompatDelegate.setApplicationLocales(list)
}

@Composable
private fun RowLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(stringResource(R.string.updater_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
