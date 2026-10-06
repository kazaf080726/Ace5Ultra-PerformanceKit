package com.ace5ultra.perfkit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ace5ultra.perfkit.config.RepoConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OTA updater. Fetches release/update.json, compares versionCode and either
 * hands the module zip to the root manager or offers the APK download.
 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val zipUrl: String,
    val changelog: String,
    val appVersion: String = "",
    val appVersionCode: Int = -1,
    val appUrl: String = "",
)

object Updater {

    fun fetchUpdateJson(timeoutMs: Int = 8000): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(RepoConfig.UPDATE_JSON_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            UpdateInfo(
                version = o.optString("version", ""),
                versionCode = o.optInt("versionCode", -1),
                zipUrl = o.optString("zipUrl", ""),
                changelog = o.optString("changelog", ""),
                appVersion = o.optString("appVersion", ""),
                appVersionCode = if (o.isNull("appVersionCode")) -1 else o.optInt("appVersionCode", -1),
                appUrl = o.optString("appUrl", ""),
            )
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** True when the remote versionCode is strictly greater than the installed module's. */
    fun moduleUpdateAvailable(localCode: Int, remote: UpdateInfo): Boolean =
        remote.versionCode > localCode && remote.zipUrl.isNotBlank()

    fun appUpdateAvailable(localCode: Int, remote: UpdateInfo): Boolean =
        remote.appVersionCode > localCode && remote.appUrl.isNotBlank()

    /** Download the zip into app cache and hand it to the root manager via VIEW intent. */
    fun downloadAndHandZip(context: Context, zipUrl: String, onResult: (File?, String?) -> Unit) {
        Thread {
            try {
                val out = File(context.cacheDir, "perfkit-update.zip")
                val conn = URL(zipUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.connect()
                conn.inputStream.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                conn.disconnect()
                onResult(out, null)
            } catch (t: Throwable) {
                onResult(null, t.message ?: "download failed")
            }
        }.start()
    }

    /** Open the downloaded zip with whatever root manager registered for it. */
    fun flashWithRootManager(context: Context, zip: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        } else {
            Uri.fromFile(zip)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Open the changelog in a browser. */
    fun openChangelog(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
