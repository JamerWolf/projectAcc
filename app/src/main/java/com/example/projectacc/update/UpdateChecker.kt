package com.example.projectacc.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val tagName: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "JamerWolf"
    private const val REPO_NAME = "projectAcc"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * Checks GitHub for the latest release.
     * Returns UpdateInfo if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "projectAcc-updater")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode != 200) {
                    Log.w(TAG, "GitHub API error: ${conn.responseCode}")
                    return@withContext null
                }

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val tagName = json.optString("tag_name", "")
                val version = tagName.removePrefix("v").trim()
                val body = json.optString("body", "Sin descripción")

                // Find APK asset
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                if (version.isEmpty() || apkUrl == null) {
                    Log.w(TAG, "No valid release found (version=$version, apk=$apkUrl)")
                    return@withContext null
                }

                Log.d(TAG, "Latest release: v$version (current: v$currentVersion)")

                if (isNewerVersion(version, currentVersion)) {
                    UpdateInfo(
                        version = version,
                        changelog = body,
                        downloadUrl = apkUrl,
                        tagName = tagName
                    )
                } else {
                    Log.d(TAG, "App is up to date")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update: ${e.message}")
                null
            }
        }
    }

    /**
     * Compares two semver strings. Returns true if `latest` is newer than `current`.
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0..maxOf(latestParts.size, currentParts.size) - 1) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Downloads the APK using DownloadManager and returns the download ID.
     */
    fun downloadApk(context: Context, url: String, tagName: String): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "projectAcc-$tagName.apk"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription("projectAcc $tagName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    /**
     * Returns an intent to install an APK using a FileProvider.
     */
    fun getInstallIntent(context: Context, apkPath: String): Intent {
        val file = java.io.File(apkPath)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
