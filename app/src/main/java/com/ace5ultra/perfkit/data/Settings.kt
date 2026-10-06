package com.ace5ultra.perfkit.data

import android.content.Context
import android.content.SharedPreferences

/** Small preferences store: dashboard refresh interval + last known profile. */
class Settings(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("perfkit_prefs", Context.MODE_PRIVATE)

    /** Refresh interval in seconds. One of 3, 4, 5. */
    var refreshSeconds: Int
        get() {
            val v = sp.getInt(KEY_REFRESH, 4)
            return if (v in 3..5) v else 4
        }
        set(value) = sp.edit().putInt(KEY_REFRESH, value.coerceIn(3, 5)).apply()

    var lastProfile: String
        get() = sp.getString(KEY_PROFILE, "balanced") ?: "balanced"
        set(value) = sp.edit().putString(KEY_PROFILE, value).apply()

    companion object {
        private const val KEY_REFRESH = "refresh_seconds"
        private const val KEY_PROFILE = "last_profile"
        val REFRESH_CHOICES = listOf(3, 4, 5)
    }
}
