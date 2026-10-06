package com.ace5ultra.perfkit.ui.components

import java.util.Locale
import kotlin.math.roundToLong

/** Formatting + N/A helpers. Contract: -1 / empty / NaN -> "N/A". */
object Fmt {
    private const val NA = "N/A"

    fun naIf(v: Long): String = if (v < 0) NA else v.toString()
    fun naIf(v: Float): String = if (v.isNaN()) NA else v.toString()
    fun naIf(v: String): String = v.ifBlank { NA }

    /** kHz -> human GHz/MHz with units. */
    fun freqKhz(vKhz: Long): String {
        if (vKhz < 0) return NA
        val mhz = vKhz / 1000.0
        return if (mhz >= 1000.0) String.format(Locale.US, "%.2f GHz", mhz / 1000.0)
        else String.format(Locale.US, "%.0f MHz", mhz)
    }

    fun bytes(v: Long): String {
        if (v < 0) return NA
        val gb = v / (1024.0 * 1024.0 * 1024.0)
        val mb = v / (1024.0 * 1024.0)
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            else -> String.format(Locale.US, "%.0f MB", mb)
        }
    }

    fun percent(v: Float): String {
        if (v.isNaN()) return NA
        return String.format(Locale.US, "%.0f%%", v)
    }

    fun temp(v: Float): String {
        if (v.isNaN()) return NA
        return String.format(Locale.US, "%.1f °C", v)
    }

    fun uptime(sec: Long): String {
        if (sec < 0) return NA
        val d = sec / 86400
        val h = (sec % 86400) / 3600
        val m = (sec % 3600) / 60
        return when {
            d > 0 -> "%dd %02dh %02dm".format(d, h, m)
            h > 0 -> "%dh %02dm".format(h, m)
            else -> "%dm %02ds".format(m, sec % 60)
        }
    }

    fun ma(v: Long): String =
        if (v == Long.MIN_VALUE) NA else String.format(Locale.US, "%+d mA", v)
}
