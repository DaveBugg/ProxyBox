package com.dave_cli.proxybox.core

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.dave_cli.proxybox.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateResult(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String?,
    val releaseNotes: String?
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_URL =
        "https://api.github.com/repos/DaveBugg/ProxyBox/releases/latest"

    fun check(): UpdateResult {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ProxyBox/${BuildConfig.VERSION_NAME}")

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return UpdateResult(false, BuildConfig.VERSION_NAME, null, "HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val notes = json.optString("body", "").ifEmpty { null }

            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        return@let asset.optString("browser_download_url", null)
                    }
                }
                null
            }

            val hasUpdate = tagName.isNotEmpty() && isNewer(tagName, BuildConfig.VERSION_NAME)
            UpdateResult(hasUpdate, tagName, apkUrl, notes)
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateResult(false, BuildConfig.VERSION_NAME, null, "Error: ${e.message}")
        }
    }

    fun downloadAndInstall(context: Context, url: String, version: String) {
        val fileName = "ProxyBox-$version.apk"
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(downloadsDir, fileName)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ProxyBox $version")
            .setDescription("Downloading update...")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)
                installApk(ctx, target)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
