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

/** Module manifest (release/update.json). */
data class ModuleUpdateInfo(
    val version: String,
    val versionCode: Int,
    val zipUrl: String,
    val changelog: String,
)

/** App-only manifest (release/app-update.json). */
data class AppUpdateInfo(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
)

object Updater {

    private fun httpGet(url: String, timeoutMs: Int = 8000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun fetchModuleManifest(): ModuleUpdateInfo? {
        val body = httpGet(RepoConfig.UPDATE_JSON_URL) ?: return null
        return try {
            val o = JSONObject(body)
            ModuleUpdateInfo(
                version = o.optString("version", ""),
                versionCode = if (o.isNull("versionCode")) -1 else o.optInt("versionCode", -1),
                zipUrl = o.optString("zipUrl", ""),
                changelog = o.optString("changelog", ""),
            )
        } catch (t: Throwable) { null }
    }

    fun fetchAppManifest(): AppUpdateInfo? {
        val body = httpGet(RepoConfig.APP_UPDATE_JSON_URL) ?: return null
        return try {
            val o = JSONObject(body)
            AppUpdateInfo(
                version = o.optString("version", ""),
                versionCode = if (o.isNull("versionCode")) -1 else o.optInt("versionCode", -1),
                apkUrl = o.optString("apkUrl", ""),
                changelog = o.optString("changelog", ""),
            )
        } catch (t: Throwable) { null }
    }

    sealed class Channel {
        /** Module remote code > installed module: flash zip only (carries bundled APK). */
        data class ModuleApp(val info: ModuleUpdateInfo) : Channel()
        /** No module update, but app-only remote code > installed app: self-update APK. */
        data class AppOnly(val info: AppUpdateInfo) : Channel()
        data object UpToDate : Channel()
        data object Unavailable : Channel()
    }

    fun decide(localModuleCode: Int, localAppCode: Int): Channel {
        val m = fetchModuleManifest() ?: return Channel.Unavailable
        if (m.versionCode > localModuleCode && m.zipUrl.isNotBlank()) return Channel.ModuleApp(m)
        val a = fetchAppManifest()
        if (a != null && a.versionCode > localAppCode && a.apkUrl.isNotBlank()) return Channel.AppOnly(a)
        return Channel.UpToDate
    }

    fun downloadFile(context: Context, url: String, outName: String, onResult: (File?, String?) -> Unit) {
        Thread {
            try {
                val out = File(context.cacheDir, outName)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 60000
                conn.connect()
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                onResult(out, null)
            } catch (t: Throwable) {
                onResult(null, t.message ?: "download failed")
            }
        }.start()
    }

    fun flashWithRootManager(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openChangelog(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
