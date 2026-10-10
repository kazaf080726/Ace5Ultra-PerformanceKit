package com.ace5ultra.perfkit.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable, null-safe mirror of the `perfctl status --json` contract (v1.0.1).
 *
 * Contract rule: fields the CLI cannot read are `-1` / empty / -1.0 and are NEVER
 * guessed. The UI renders those as "N/A". We keep the raw sentinel here so the UI
 * can label units and mark N/A exactly.
 */
data class DeviceInfo(
    val brand: String = "",
    val model: String = "",
    val device: String = "",
    val soc: String = "",
    val android: String = "",
    val kernel: String = "",
)

data class AdaptiveInfo(
    val enabled: Boolean = false,
    val reason: String = "",
    val load: Float = Float.NaN,
    val tempC: Float = Float.NaN,
)

data class CpuPolicy(
    val policy: Int = -1,
    val affectedCpus: List<Int> = emptyList(),
    val governor: String = "",
    val curFreqKhz: Long = -1L,
    val minFreqKhz: Long = -1L,
    val maxFreqKhz: Long = -1L,
    val availableGovernors: List<String> = emptyList(),
    val availableFreqs: List<Long> = emptyList(),
)

data class CpuCore(
    val cpu: Int = -1,
    val policy: Int = -1,
    val online: Boolean = false,
    val curFreqKhz: Long = -1L,
    val minFreqKhz: Long = -1L,
    val maxFreqKhz: Long = -1L,
    val util: Float = Float.NaN, // 0..100, NaN = N/A
)

data class TopProc(
    val pid: Int = -1,
    val name: String = "",
    val cpu: Float = Float.NaN,
)

data class CpuInfo(
    val numCores: Int = 0,
    val loadPercent: Float = Float.NaN,
    val topProcs: List<TopProc> = emptyList(),
    val policies: List<CpuPolicy> = emptyList(),
    val cores: List<CpuCore> = emptyList(),
)

data class MemoryInfo(
    val totalBytes: Long = -1L,
    val usedBytes: Long = -1L,
    val availableBytes: Long = -1L,
    val ramPercent: Float = Float.NaN,
    val swapTotalBytes: Long = -1L,
    val swapFreeBytes: Long = -1L,
    val swapCachedBytes: Long = -1L,
    val swapPercent: Float = Float.NaN,
    val zramTotalBytes: Long = -1L,
) {
    val swapUsedBytes: Long get() = if (swapTotalBytes >= 0 && swapFreeBytes >= 0) swapTotalBytes - swapFreeBytes else -1L
}

data class GpuInfo(
    val present: Boolean = false,
    val name: String = "",
    val driver: String = "",
    val curFreqKhz: Long = -1L,
    val minFreqKhz: Long = -1L,
    val maxFreqKhz: Long = -1L,
    val util: Float = Float.NaN,
    // v1.0.1 explicit MHz fields (preferred over khz)
    val curMhz: Long = -1L,
    val minMhz: Long = -1L,
    val maxMhz: Long = -1L,
    val loadPercent: Float = Float.NaN,
) {
    /** Effective current MHz: explicit field, else derive from khz. */
    val effCurMhz: Long get() = if (curMhz > 0) curMhz else if (curFreqKhz > 0) curFreqKhz / 1000 else -1L
    val effMinMhz: Long get() = if (minMhz > 0) minMhz else if (minFreqKhz > 0) minFreqKhz / 1000 else -1L
    val effMaxMhz: Long get() = if (maxMhz > 0) maxMhz else if (maxFreqKhz > 0) maxFreqKhz / 1000 else -1L
    val effLoad: Float get() = if (!loadPercent.isNaN()) loadPercent else util
}

data class ThermalZone(
    val type: String = "",
    val name: String = "",
    val tempC: Float = Float.NaN,
)

data class BatteryInfo(
    val level: Int = -1,
    val temperatureC: Float = Float.NaN,
    val currentNowMa: Long = Long.MIN_VALUE,
    val status: String = "",
    // v1.0.1 rich battery object
    val powerWatts: Float = Float.NaN,
    val voltageVolts: Float = Float.NaN,
    val currentUa: Long = Long.MIN_VALUE,
)

data class MountInfo(
    val mode: String = "",
    val provider: String = "",
    val worldReadableTopology: Boolean = false,
)

data class PerfStatus(
    val version: String = "",
    val versionCode: Int = -1,
    val contract: Int = -1,
    val device: DeviceInfo = DeviceInfo(),
    val profile: String = "",
    val adaptive: AdaptiveInfo = AdaptiveInfo(),
    val cpu: CpuInfo = CpuInfo(),
    val memory: MemoryInfo = MemoryInfo(),
    val gpu: GpuInfo = GpuInfo(),
    val thermal: List<ThermalZone> = emptyList(),
    val battery: BatteryInfo = BatteryInfo(),
    val mount: MountInfo = MountInfo(),
    val uptimeSeconds: Long = -1L,
)

data class TuningItem(
    val key: String,
    val value: String,
    val min: Double,
    val max: Double,
    val unit: String,
    val safe: Boolean,
)

data class ModuleInfo(
    val installed: Boolean,
    val version: String = "",
    val versionCode: Int = -1,
    val contract: Int = -1,
)

object JsonExt

private fun JSONObject.optStr(key: String): String = if (isNull(key)) "" else optString(key, "")
private fun JSONObject.optLong2(key: String): Long = if (isNull(key)) -1L else optLong(key, -1L)
private fun JSONObject.optInt2(key: String): Int = if (isNull(key)) -1 else optInt(key, -1)
private fun JSONObject.optFloat2(key: String): Float =
    if (isNull(key)) Float.NaN else optDouble(key, Double.NaN).toFloat()

private fun JSONArray.toStringList(): List<String> = (0 until length()).map { optString(it) }
private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { optInt(it) }
private fun JSONArray.toLongList(): List<Long> = (0 until length()).map { optLong(it) }

fun parsePerfStatus(raw: String): PerfStatus {
    val obj = JSONObject(raw)
    val d = obj.optJSONObject("device") ?: JSONObject()
    val a = obj.optJSONObject("adaptive") ?: JSONObject()
    val mem = obj.optJSONObject("memory") ?: JSONObject()
    val gpu = obj.optJSONObject("gpu") ?: JSONObject()
    val bat = obj.optJSONObject("battery") ?: JSONObject()
    val cpu = obj.optJSONObject("cpu") ?: JSONObject()
    val mount = obj.optJSONObject("mount") ?: JSONObject()

    val policies = cpu.optJSONArray("policies")?.let { arr ->
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            CpuPolicy(
                policy = p.optInt("policy", -1),
                affectedCpus = p.optJSONArray("affectedCpus")?.toIntList() ?: emptyList(),
                governor = p.optStr("governor"),
                curFreqKhz = p.optLong2("curFreq"),
                minFreqKhz = p.optLong2("minFreq"),
                maxFreqKhz = p.optLong2("maxFreq"),
                availableGovernors = p.optJSONArray("availableGovernors")?.toStringList() ?: emptyList(),
                availableFreqs = p.optJSONArray("availableFreqs")?.toLongList() ?: emptyList(),
            )
        }
    } ?: emptyList()

    val cores = cpu.optJSONArray("cores")?.let { arr ->
        (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            CpuCore(
                cpu = c.optInt("cpu", -1),
                policy = c.optInt("policy", -1),
                online = c.optInt("online", 0) == 1,
                curFreqKhz = c.optLong2("curFreq"),
                minFreqKhz = c.optLong2("minFreq"),
                maxFreqKhz = c.optLong2("maxFreq"),
                util = c.optFloat2("util"),
            )
        }
    } ?: emptyList()

    val topProcs = cpu.optJSONArray("topProcs")?.let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TopProc(pid = o.optInt("pid", -1), name = o.optStr("name"), cpu = o.optFloat2("cpu"))
        }
    } ?: emptyList()

    val thermals = obj.optJSONArray("thermal")?.let { arr ->
        (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            ThermalZone(type = t.optStr("type"), name = t.optStr("name"), tempC = t.optFloat2("tempC"))
        }
    } ?: emptyList()

    return PerfStatus(
        version = obj.optStr("version"),
        versionCode = obj.optInt2("versionCode"),
        contract = obj.optInt2("contract"),
        device = DeviceInfo(
            brand = d.optStr("brand"),
            model = d.optStr("model"),
            device = d.optStr("device"),
            soc = d.optStr("soc"),
            android = d.optStr("android"),
            kernel = d.optStr("kernel"),
        ),
        profile = obj.optStr("profile"),
        adaptive = AdaptiveInfo(
            enabled = a.optBoolean("enabled", false),
            reason = a.optStr("reason"),
            load = a.optFloat2("load"),
            tempC = a.optFloat2("tempC"),
        ),
        cpu = CpuInfo(
            numCores = cpu.optInt("numCores", 0),
            loadPercent = cpu.optFloat2("loadPercent"),
            topProcs = topProcs,
            policies = policies,
            cores = cores,
        ),
        memory = MemoryInfo(
            totalBytes = mem.optLong2("total"),
            usedBytes = mem.optLong2("used"),
            availableBytes = mem.optLong2("available"),
            ramPercent = mem.optFloat2("ramPercent"),
            swapTotalBytes = mem.optLong2("swapTotal"),
            swapFreeBytes = mem.optLong2("swapFree"),
            swapCachedBytes = mem.optLong2("swapCachedBytes"),
            swapPercent = mem.optFloat2("swapPercent"),
            zramTotalBytes = mem.optLong2("zramTotal"),
        ),
        gpu = GpuInfo(
            present = gpu.optBoolean("present", false),
            name = gpu.optStr("name"),
            driver = gpu.optStr("driver"),
            curFreqKhz = gpu.optLong2("curFreq"),
            minFreqKhz = gpu.optLong2("minFreq"),
            maxFreqKhz = gpu.optLong2("maxFreq"),
            util = gpu.optFloat2("util"),
            curMhz = gpu.optLong2("curMhz"),
            minMhz = gpu.optLong2("minMhz"),
            maxMhz = gpu.optLong2("maxMhz"),
            loadPercent = gpu.optFloat2("loadPercent"),
        ),
        thermal = thermals,
        battery = BatteryInfo(
            level = bat.optInt2("level").let { if (it == -1) bat.optInt2("percent") else it },
            temperatureC = bat.optFloat2("tempC").let { if (it.isNaN()) bat.optFloat2("temperatureC") else it },
            currentNowMa = if (bat.isNull("currentNowMa")) Long.MIN_VALUE else bat.optLong("currentNowMa", Long.MIN_VALUE),
            status = bat.optStr("status"),
            powerWatts = bat.optFloat2("powerWatts"),
            voltageVolts = bat.optFloat2("voltageVolts"),
            currentUa = if (bat.isNull("currentUa")) Long.MIN_VALUE else bat.optLong("currentUa", Long.MIN_VALUE),
        ),
        mount = MountInfo(
            mode = mount.optStr("mode"),
            provider = mount.optStr("provider"),
            worldReadableTopology = mount.optBoolean("worldReadableTopology", false),
        ),
        uptimeSeconds = obj.optLong2("uptimeSeconds"),
    )
}

fun parseTuningItems(raw: String): List<TuningItem> {
    val arr = JSONObject(raw).optJSONArray("items") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        TuningItem(
            key = o.optString("key", ""),
            value = o.optString("value", ""),
            min = if (o.isNull("min")) Double.NaN else o.optDouble("min", Double.NaN),
            max = if (o.isNull("max")) Double.NaN else o.optDouble("max", Double.NaN),
            unit = o.optStr("unit"),
            safe = o.optBoolean("safe", false),
        )
    }
}
