package com.ace5ultra.perfkit.data

import android.os.Build
import com.ace5ultra.perfkit.root.RootBridge
import org.json.JSONObject
import java.io.File

/**
 * Single entry point for talking to the root module and reading direct
 * world-readable /proc + /sys nodes. Defensive everywhere: every missing node
 * becomes a sentinel that the UI renders as N/A.
 */
class PerfRepository {

    companion object {
        const val MODULE_ID = "ace5ultra_perfkit"
        const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
        const val PERFCTL = "$MODULE_DIR/bin/perfctl"
    }

    sealed class RootState {
        data object Unknown : RootState()
        data object NoRoot : RootState()
        data class RootOk(val module: ModuleInfo) : RootState()
        data class Error(val message: String) : RootState()
    }

    // ---------- module detection ----------

    fun detectModule(): ModuleInfo {
        // module.prop is under /data/adb (root-only), so read it via the root shell.
        val prop = RootBridge.exec("cat $MODULE_DIR/module.prop 2>/dev/null").stdout
        val version = Regex("(?m)^version=(.*)$").find(prop)?.groupValues?.get(1)?.trim().orEmpty()
        val code = Regex("(?m)^versionCode=(\\d+)$").find(prop)?.groupValues?.get(1)?.toIntOrNull() ?: -1

        // Cross-check with perfctl itself.
        val v = perfctlVersion()
        return ModuleInfo(
            installed = version.isNotEmpty() || v != null,
            version = v?.first ?: version,
            versionCode = v?.second ?: code,
            contract = v?.third ?: -1,
        )
    }

    /** @return Triple(version, versionCode, contract) or null when unavailable. */
    private fun perfctlVersion(): Triple<String, Int, Int>? {
        val r = RootBridge.exec("$PERFCTL version")
        if (!r.ok || r.stdout.isBlank()) return null
        return try {
            val o = JSONObject(r.stdout)
            Triple(
                o.optString("version", ""),
                if (o.isNull("versionCode")) -1 else o.optInt("versionCode", -1),
                if (o.isNull("contract")) -1 else o.optInt("contract", -1),
            )
        } catch (t: Throwable) {
            null
        }
    }

    // ---------- perfctl commands ----------

    fun fetchStatus(): PerfStatus? {
        val r = RootBridge.exec("$PERFCTL status --json")
        if (!r.ok || r.stdout.isBlank()) return null
        return try {
            parsePerfStatus(r.stdout)
        } catch (t: Throwable) {
            null
        }
    }

    fun getProfile(): String? {
        val r = RootBridge.exec("$PERFCTL profile get")
        return try {
            val v = JSONObject(r.stdout).optString("profile", "")
            v.ifEmpty { null }
        } catch (t: Throwable) { null }
    }

    fun setProfile(name: String): Boolean {
        val r = RootBridge.exec("$PERFCTL profile set $name")
        return r.ok
    }

    fun adaptiveStatus(): Boolean? {
        val r = RootBridge.exec("$PERFCTL adaptive status")
        return try { JSONObject(r.stdout).optBoolean("enabled", false) } catch (t: Throwable) { null }
    }

    fun setAdaptive(on: Boolean): Boolean {
        val arg = if (on) "on" else "off"
        val r = RootBridge.exec("$PERFCTL adaptive $arg")
        return r.ok
    }

    fun listTuning(): List<TuningItem> {
        val r = RootBridge.exec("$PERFCTL tuning list")
        if (!r.ok) return emptyList()
        return try { parseTuningItems(r.stdout) } catch (t: Throwable) { emptyList() }
    }

    fun setTuning(key: String, value: String): Boolean {
        // perfctl refuses unsafe/out-of-range itself; quoting guards the shell.
        val r = RootBridge.exec("\"$PERFCTL\" tuning set \"$key\" \"$value\"")
        return r.ok
    }

    fun snapshot(): Boolean = RootBridge.exec("$PERFCTL snapshot").ok
    fun restore(): Boolean = RootBridge.exec("$PERFCTL restore").ok

    // ---------- direct world-readable fallback ----------

    /** Device build info (no root needed). */
    fun directDevice(): DeviceInfo = DeviceInfo(
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        soc = supportedSoc(),
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        kernel = System.getProperty("os.version").orEmpty(),
    )

    private fun supportedSoc(): String {
        val parts = mutableListOf<String>()
        Build.SOC_MODEL?.takeIf { it.isNotBlank() }?.let { parts += it }
        Build.HARDWARE?.takeIf { it.isNotBlank() && it != "goldfish" }?.let { parts += "hw=$it" }
        return parts.joinToString(" ")
    }

    fun directUptimeSeconds(): Long? = try {
        File("/proc/uptime").readText().trim().split("\\s+".toRegex()).firstOrNull()?.toFloat()?.toLong()
    } catch (t: Throwable) { null }

    /** Parses /proc/meminfo for the common fields. Returns bytes. */
    fun directMemory(): MemoryInfo? = try {
        val map = HashMap<String, Long>()
        File("/proc/meminfo").readLines().forEach { line ->
            val m = Regex("^(\\w+):\\s+(\\d+)\\s*kB").find(line) ?: return@forEach
            map[m.groupValues[1]] = m.groupValues[2].toLong() * 1024L
        }
        val total = map["MemTotal"] ?: return null
        val avail = map["MemAvailable"] ?: map["MemFree"] ?: return null
        val used = total - avail
        val swapTotal = map["SwapTotal"] ?: -1L
        val swapFree = map["SwapFree"] ?: -1L
        MemoryInfo(
            totalBytes = total,
            usedBytes = used,
            availableBytes = avail,
            ramPercent = if (total > 0) used * 100f / total else Float.NaN,
            swapTotalBytes = swapTotal,
            swapFreeBytes = swapFree,
            zramTotalBytes = -1L,
        )
    } catch (t: Throwable) { null }
}
