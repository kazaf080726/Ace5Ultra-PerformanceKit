package com.ace5ultra.perfkit.config

/**
 * Single place to change the GitHub repository the OTA updater points at.
 *
 * update.json lives at:
 *   https://raw.githubusercontent.com/OWNER/REPO/main/release/update.json
 * module zip is downloaded from the release URL written inside update.json.
 */
object RepoConfig {
    const val OWNER = "kazaf080726"
    const val REPO = "Ace5Ultra-PerformanceKit"

    const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/$OWNER/$REPO/main/release/update.json"
}
