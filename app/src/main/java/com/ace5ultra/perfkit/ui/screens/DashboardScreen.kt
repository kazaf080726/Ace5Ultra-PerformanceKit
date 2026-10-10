package com.ace5ultra.perfkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ace5ultra.perfkit.R
import com.ace5ultra.perfkit.data.CpuCore
import com.ace5ultra.perfkit.data.PerfStatus
import com.ace5ultra.perfkit.data.TopProc
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.components.Fmt
import com.ace5ultra.perfkit.ui.components.RingChart
import com.ace5ultra.perfkit.ui.glass.GlassPanel
import com.ace5ultra.perfkit.ui.theme.AccentBlue
import com.ace5ultra.perfkit.ui.theme.AccentCyan
import com.ace5ultra.perfkit.ui.theme.GoodGreen
import com.ace5ultra.perfkit.ui.theme.HotRed
import com.ace5ultra.perfkit.ui.theme.RingTrack
import com.ace5ultra.perfkit.ui.theme.WarnAmber

@Composable
fun DashboardScreen(vm: PerfViewModel, modifier: Modifier = Modifier) {
    val status by vm.status.collectAsState()
    val refreshChoices = com.ace5ultra.perfkit.data.Settings.REFRESH_CHOICES

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 40.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                ProfileBadge(status?.profile ?: "")
            }
        }
        item {
            RefreshRow(choices = refreshChoices, current = vm.settings.refreshSeconds,
                onPick = { vm.settings.refreshSeconds = it; vm.pollOnce() })
        }
        item { MemoryCard(status) }
        item { GpuCard(status) }
        item { CpuCard(status) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { BatteryCard(status) }
                Box(Modifier.weight(1f)) { DeviceCard(status) }
            }
        }
    }
}

@Composable
private fun RefreshRow(choices: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { s ->
            FilterChip(selected = s == current, onClick = { onPick(s) },
                label = { Text("$s s") })
        }
    }
}

@Composable
private fun MemoryCard(status: PerfStatus?) {
    val m = status?.memory
    val ramPct = m?.ramPercent ?: Float.NaN
    val swapPct = m?.swapPercent ?: Float.NaN
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RingChart(fraction = if (ramPct.isNaN()) 0f else ramPct / 100f, size = 118.dp,
                progressColor = AccentBlue) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.percent(ramPct), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.memory_title), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.fillMaxWidth()) {
                BarLine(label = stringResource(R.string.memory_ram), pct = ramPct,
                    value = "${Fmt.bytes(m?.usedBytes ?: -1L)} / ${Fmt.bytes(m?.totalBytes ?: -1L)}")
                Spacer(Modifier.height(10.dp))
                BarLine(label = stringResource(R.string.memory_swap), pct = swapPct,
                    value = "${Fmt.bytes(m?.swapUsedBytes ?: -1L)} / ${Fmt.bytes(m?.swapTotalBytes ?: -1L)}")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stringResource(R.string.memory_used)} ${Fmt.percent(ramPct)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("SwapCached ${Fmt.bytes(m?.swapCachedBytes ?: -1L)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BarLine(label: String, pct: Float, value: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            .background(RingTrack)) {
            if (!pct.isNaN()) Box(
                Modifier.fillMaxWidth(pct.coerceIn(0f, 100f) / 100f).height(8.dp)
                    .clip(RoundedCornerShape(50)).background(AccentCyan)
            )
        }
    }
}

@Composable
private fun GpuCard(status: PerfStatus?) {
    val g = status?.gpu
    val load = g?.effLoad ?: Float.NaN
    val f = if (!load.isNaN()) load / 100f else {
        val cur = g?.effCurMhz ?: -1L; val max = g?.effMaxMhz ?: -1L
        if (cur > 0 && max > 0) cur.toFloat() / max else Float.NaN
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RingChart(fraction = if (f.isNaN()) 0f else f, size = 118.dp, progressColor = AccentBlue) {
                Text("GPU", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                val mhz = g?.effCurMhz ?: -1L
                Text(if (mhz > 0) "$mhz MHz" else "N/A",
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${stringResource(R.string.gpu_load)} ${Fmt.percent(load)}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(Fmt.naIf(g?.name ?: ""), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!g?.driver.isNullOrBlank())
                    Text(Fmt.naIf(g?.driver ?: ""), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                val mn = g?.effMinMhz ?: -1L; val mx = g?.effMaxMhz ?: -1L
                Text(if (mn > 0 && mx > 0) "$mn ~ $mx MHz" else "N/A",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CpuCard(status: PerfStatus?) {
    val cpu = status?.cpu
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cpu_top_procs), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    cpu?.topProcs.orEmpty().take(5).forEach { TopProcRow(it) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CPU", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status?.device?.soc ?: "N/A", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("${stringResource(R.string.cpu_load)} ${Fmt.percent(cpu?.loadPercent ?: Float.NaN)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            val cores = cpu?.cores.orEmpty()
            val rows = (cores.size + 3) / 4
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 4) {
                            val idx = r * 4 + c
                            if (idx < cores.size) Box(Modifier.weight(1f)) { CoreCell(cores[idx]) }
                            else Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProcRow(p: TopProc) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Fmt.naIf(p.name), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(Fmt.percent(p.cpu), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoreCell(c: CpuCore) {
    val mhz = if (c.curFreqKhz > 0) c.curFreqKhz / 1000 else -1L
    val mn = if (c.minFreqKhz > 0) c.minFreqKhz / 1000 else -1L
    val mx = if (c.maxFreqKhz > 0) c.maxFreqKhz / 1000 else -1L
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Fmt.percent(c.util), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        // mini utilization bar
        Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3)).background(RingTrack)) {
            if (!c.util.isNaN() && c.online) Box(
                Modifier.fillMaxWidth((c.util.coerceIn(0f, 100f)) / 100f).height(5.dp)
                    .clip(RoundedCornerShape(3)).background(AccentBlue)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(if (mhz > 0) "$mhz" else "N/A", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = if (c.online) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (mn > 0 && mx > 0) "$mn~$mx" else "N/A", fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryCard(status: PerfStatus?) {
    val b = status?.battery
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.battery_title), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val w = b?.powerWatts ?: Float.NaN
            Text(if (!w.isNaN()) "${"%.2f".format(w)} W" else "N/A",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(if (b != null && b.level >= 0) "${b.level}%" else "N/A",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val v = b?.voltageVolts ?: Float.NaN
            Text(if (!v.isNaN()) "${"%.2f".format(v)} V" else "N/A",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.temp(b?.temperatureC ?: Float.NaN), fontSize = 12.sp,
                color = tempColor(b?.temperatureC ?: Float.NaN))
        }
    }
}

@Composable
private fun DeviceCard(status: PerfStatus?) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.device_title), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.naIf(status?.device?.android ?: ""), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text("${stringResource(R.string.uptime)} ${Fmt.uptime(status?.uptimeSeconds ?: -1L)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileBadge(profile: String) {
    val p = profile.lowercase()
    val (bg, fg) = when (p) {
        "performance", "game" -> HotRed to Color.White
        "powersave" -> GoodGreen to Color.White
        "system" -> WarnAmber to Color.Black
        else -> AccentBlue to Color.White
    }
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(profile.ifBlank { "N/A" }, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun tempColor(t: Float): Color = when {
    t.isNaN() -> MaterialTheme.colorScheme.onSurfaceVariant
    t >= 60f -> HotRed
    t >= 45f -> WarnAmber
    else -> GoodGreen
}
