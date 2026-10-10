package com.ace5ultra.perfkit.config

/**
 * Single place to change the GitHub repository the OTA updater points at.
 *
 * - release/update.json     : module manifest (module versionCode + bundled APK)
 * - release/app-update.json : app-only manifest (standalone APK self-update)
 */
object RepoConfig {
    const val OWNER = "kazaf080726"
    const val REPO = "Ace5Ultra-PerformanceKit"

    private const val BASE = "https://raw.githubusercontent.com/$OWNER/$REPO/main/release"

    const val UPDATE_JSON_URL = "$BASE/update.json"
    const val APP_UPDATE_JSON_URL = "$BASE/app-update.json"
}
