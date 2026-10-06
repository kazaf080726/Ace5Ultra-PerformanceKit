package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ace5ultra.perfkit.data.CpuCore
import com.ace5ultra.perfkit.data.PerfStatus
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.components.Fmt
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import com.ace5ultra.perfkit.ui.components.HistoryLineChart
import com.ace5ultra.perfkit.ui.theme.GoodGreen
import com.ace5ultra.perfkit.ui.theme.HotRed
import com.ace5ultra.perfkit.ui.theme.WarnAmber

@Composable
fun DashboardScreen(vm: PerfViewModel, modifier: Modifier = Modifier) {
    val status by vm.status.collectAsState()
    val history by vm.history.collectAsState()
    val refreshChoices = com.ace5ultra.perfkit.data.Settings.REFRESH_CHOICES

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 48.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeaderCard(status = status, currentRefresh = vm.settings.refreshSeconds,
            onRefreshPick = { vm.settings.refreshSeconds = it; vm.pollOnce() }) }
        item { RefreshRow(choices = refreshChoices, current = vm.settings.refreshSeconds,
            onPick = { vm.settings.refreshSeconds = it; vm.pollOnce() }) }
        item { CpuCard(status = status, history = history) }
        item { MemoryCard(status = status) }
        item { GpuCard(status = status) }
        item { ThermalCard(status = status) }
        item { BatteryCard(status = status) }
    }
}

@Composable
private fun HeaderCard(status: PerfStatus?, currentRefresh: Int, onRefreshPick: (Int) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("PerfKit", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            val d = status?.device
            Text("SoC: ${Fmt.naIf(d?.soc ?: "")}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Model: ${Fmt.naIf(d?.model ?: "")}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Kernel: ${Fmt.naIf(d?.kernel ?: "")}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                ProfileBadge(status?.profile ?: "")
            }
            Spacer(Modifier.height(4.dp))
            Text("Uptime: ${Fmt.uptime(status?.uptimeSeconds ?: -1L)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Refresh: ${currentRefresh}s", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileBadge(profile: String) {
    val (bg, fg) = when (profile.lowercase()) {
        "performance", "game" -> HotRed to androidx.compose.ui.graphics.Color.White
        "powersave" -> GoodGreen to androidx.compose.ui.graphics.Color.White
        "adaptive" -> MaterialTheme.colorScheme.primary to androidx.compose.ui.graphics.Color.White
        else -> WarnAmber to androidx.compose.ui.graphics.Color.Black
    }
    Box(
        Modifier
            .androidx_background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(profile.ifBlank { "N/A" }, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RefreshRow(choices: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { s ->
            FilterChip(selected = s == current, onClick = { onPick(s) },
                label = { Text("${s}s") })
        }
    }
}

@Composable
private fun CpuCard(status: PerfStatus?, history: Map<Int, java.util.ArrayDeque<Float>>) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("CPU · ${status?.cpu?.numCores ?: -1} cores")
            val policies = status?.cpu?.policies.orEmpty()
            if (policies.isEmpty()) {
                Muted("No cpufreq policies exposed")
            }
            policies.forEach { p ->
                Spacer(Modifier.height(8.dp))
                Text("Policy ${p.policy} · cores ${p.affectedCpus.joinToString(",")}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Governor: ${Fmt.naIf(p.governor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Freq: ${Fmt.freqKhz(p.curFreqKhz)}  (min ${Fmt.freqKhz(p.minFreqKhz)} / max ${Fmt.freqKhz(p.maxFreqKhz)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // history chart for cores in this policy
                val series = p.affectedCpus.mapNotNull { c ->
                    history[c]?.let { java.util.ArrayList(it) }
                }
                if (series.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HistoryLineChart(series = series)
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.affectedCpus.forEachIndexed { i, c ->
                            LegendDot("c$c", color = chartColor(i))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            status?.cpu?.cores?.let { cores ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cores.forEach { CoreRow(it) }
                }
            }
        }
    }
}

@Composable
private fun CoreRow(c: CpuCore) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("cpu${c.cpu} ${if (c.online) "●" else "○"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (c.online) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${Fmt.freqKhz(c.curFreqKhz)} · util ${Fmt.percent(c.util)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoryCard(status: PerfStatus?) {
    val m = status?.memory
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Memory")
            KV("Total", Fmt.bytes(m?.totalBytes ?: -1L))
            KV("Used", Fmt.bytes(m?.usedBytes ?: -1L))
            KV("Available", Fmt.bytes(m?.availableBytes ?: -1L))
            KV("RAM used", Fmt.percent(m?.ramPercent ?: Float.NaN))
            KV("Swap", "${Fmt.bytes(m?.swapUsedBytes ?: -1L)} / ${Fmt.bytes(m?.swapTotalBytes ?: -1L)}")
            KV("ZRAM", Fmt.bytes(m?.zramTotalBytes ?: -1L))
        }
    }
}

@Composable
private fun GpuCard(status: PerfStatus?) {
    val g = status?.gpu
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("GPU")
            if (g == null || !g.present) {
                Muted("GPU telemetry not exposed by this device")
            } else {
                KV("Name", Fmt.naIf(g.name))
                KV("Freq", "${Fmt.freqKhz(g.curFreqKhz)}  (min ${Fmt.freqKhz(g.minFreqKhz)} / max ${Fmt.freqKhz(g.maxFreqKhz)})")
                KV("Load", Fmt.percent(g.util))
            }
        }
    }
}

@Composable
private fun ThermalCard(status: PerfStatus?) {
    val list = status?.thermal.orEmpty()
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Thermal")
            if (list.isEmpty()) Muted("No thermal zones reported")
            else list.forEach { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.name.ifBlank { t.type.ifBlank { "zone" } },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.temp(t.tempC), style = MaterialTheme.typography.bodySmall,
                        color = tempColor(t.tempC))
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(status: PerfStatus?) {
    val b = status?.battery
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Battery")
            KV("Level", if (b == null || b.level < 0) "N/A" else "${b.level}%")
            KV("Temperature", Fmt.temp(b?.temperatureC ?: Float.NaN))
            KV("Current", Fmt.ma(b?.currentNowMa ?: Long.MIN_VALUE))
            KV("Status", Fmt.naIf(b?.status ?: ""))
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)

@Composable private fun KV(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
    }

@Composable private fun Muted(t: String) =
    Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).androidx_background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun chartColor(i: Int): androidx.compose.ui.graphics.Color = when (i % 6) {
    0 -> androidx.compose.ui.graphics.Color(0xFFEB0029)
    1 -> androidx.compose.ui.graphics.Color(0xFF4CD964)
    2 -> androidx.compose.ui.graphics.Color(0xFF5AC8FA)
    3 -> androidx.compose.ui.graphics.Color(0xFFFFD60A)
    4 -> androidx.compose.ui.graphics.Color(0xFFFF9F0A)
    else -> androidx.compose.ui.graphics.Color(0xFF0A84FF)
}

@Composable
private fun tempColor(t: Float): androidx.compose.ui.graphics.Color = when {
    t.isNaN() -> MaterialTheme.colorScheme.onSurfaceVariant
    t >= 60f -> HotRed
    t >= 45f -> WarnAmber
    else -> GoodGreen
}

/** tiny alias so call sites stay short */
private fun Modifier.androidx_background(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.background(color = color, shape = shape)
