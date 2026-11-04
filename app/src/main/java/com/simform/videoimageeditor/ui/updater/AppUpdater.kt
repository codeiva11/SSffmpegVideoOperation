package com.simform.videoimageeditor.ui.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.simform.videoimageeditor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updater: on launch the app checks the latest GitHub release of
 * codeiva11/SSffmpegVideoOperation. Releases are tagged v2.0.0-<run number>
 * and the installed build carries its run number as versionCode, so a
 * newer tag means a newer build.
 *
 * Download is SHA-256 verified against the .sha256 sidecar asset that the
 * release workflow uploads next to the APK. Fail-closed: no checksum, or a
 * mismatch, means no install. Works on Wi-Fi and mobile data.
 */
private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/codeiva11/SSffmpegVideoOperation/releases/latest"

data class UpdateInfo(
    val tag: String,
    val runNumber: Int,
    val apkUrl: String,
    val shaUrl: String?
)

/** Returns the update when a newer release exists; null when up to date or on any error. */
suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val json = JSONObject(connection.inputStream.bufferedReader().readText())
        val tag = json.optString("tag_name", "")
        val runNumber = tag.removePrefix("v2.0.0-").toIntOrNull() ?: return@withContext null
        if (runNumber <= BuildConfig.VERSION_CODE) return@withContext null
        var apkUrl: String? = null
        var shaUrl: String? = null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when {
                asset.optString("name").endsWith(".apk") ->
                    apkUrl = asset.optString("browser_download_url")
                asset.optString("name").endsWith(".sha256") ->
                    shaUrl = asset.optString("browser_download_url")
            }
        }
        if (apkUrl.isNullOrEmpty()) return@withContext null
        UpdateInfo(tag, runNumber, apkUrl, shaUrl)
    } catch (_: Exception) {
        null
    }
}

/**
 * Downloads the APK (reporting 0..100 progress) and verifies its SHA-256
 * against the sidecar. Returns the verified file, or null on any failure.
 */
suspend fun downloadAndVerify(
    context: Context,
    info: UpdateInfo,
    onProgress: (Int) -> Unit
): File? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "app-release.apk")
        if (!downloadFile(info.apkUrl, apk, onProgress)) return@withContext null
        val expected = info.shaUrl
            ?.let { downloadText(it) }
            ?.trim()
            ?.split("\\s+".toRegex())
            ?.firstOrNull()
            .orEmpty()
        if (expected.isEmpty()) return@withContext null
        if (!sha256(apk).equals(expected, ignoreCase = true)) return@withContext null
        apk
    } catch (_: Exception) {
        null
    }
}

private fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit): Boolean {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
    val total = connection.contentLength
    connection.inputStream.use { input ->
        FileOutputStream(dest).use { output ->
            val buffer = ByteArray(64 * 1024)
            var downloaded = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
            }
        }
    }
    onProgress(100)
    return true
}

private fun downloadText(url: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.apply {
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
    return connection.inputStream.bufferedReader().readText()
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Hands the verified APK to the system installer. */
fun installApk(context: Context, apk: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
