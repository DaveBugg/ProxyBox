package com.dave_cli.proxybox.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

typealias ProgressCallback = (fileName: String, bytesRead: Long, totalBytes: Long) -> Unit

object GeoFileManager {

    private const val TAG = "GeoFileManager"
    private const val PREFS_NAME = "geo_update_prefs"

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (CoreService.isActive) {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", CoreService.SOCKS_PORT))
            builder.proxy(proxy)

            val user = CoreService.socksUser
            val pass = CoreService.socksPass
            if (user != null && pass != null) {
                builder.proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(user, pass))
                        .build()
                }
            }
        }

        return builder.build()
    }

    suspend fun updateAll(
        context: Context,
        onProgress: ProgressCallback? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val assetsDir = context.getDir("assets", Context.MODE_PRIVATE)
        val geoProfile = GeoProfile.getSaved(context)
        val client = buildClient()

        Log.i(TAG, "Updating geo files using profile: ${geoProfile.id}" +
                if (CoreService.isActive) " (via VPN proxy)" else " (direct)")

        val geoipUpdated = downloadIfNew(
            client, assetsDir, geoProfile.geoipUrl, geoProfile.geoipSha256Url,
            "geoip.dat", prefs, onProgress
        )
        val geositeUpdated = downloadIfNew(
            client, assetsDir, geoProfile.geositeUrl, geoProfile.geositeSha256Url,
            "geosite.dat", prefs, onProgress
        )

        if (geoipUpdated || geositeUpdated) {
            Log.i(TAG, "Geo files updated (geoip=$geoipUpdated, geosite=$geositeUpdated)")
            prefs.edit().putLong("last_update_time", System.currentTimeMillis()).apply()
        }

        geoipUpdated || geositeUpdated
    }

    fun getLastUpdateTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("last_update_time", 0L)
    }

    fun clearEtags(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove("etag_geoip.dat")
            .remove("etag_geosite.dat")
            .apply()
    }

    private fun downloadIfNew(
        client: OkHttpClient,
        destDir: File,
        url: String,
        sha256Url: String,
        targetFileName: String,
        prefs: SharedPreferences,
        onProgress: ProgressCallback? = null
    ): Boolean {
        val etagKey = "etag_$targetFileName"
        val savedEtag = prefs.getString(etagKey, null)

        val requestBuilder = Request.Builder().url(url)
        if (!savedEtag.isNullOrEmpty()) {
            requestBuilder.addHeader("If-None-Match", savedEtag)
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 304) {
            Log.d(TAG, "$targetFileName: not modified (304)")
            response.close()
            return false
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "$targetFileName: HTTP ${response.code}")
            response.close()
            return false
        }

        val contentLength = response.body?.contentLength() ?: -1L
        val tmpFile = File(destDir, "$targetFileName.tmp")
        try {
            response.body?.byteStream()?.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress?.invoke(targetFileName, totalRead, contentLength)
                    }
                }
            } ?: run {
                response.close()
                return false
            }

            if (tmpFile.length() < 1024) {
                Log.w(TAG, "$targetFileName: downloaded file too small (${tmpFile.length()} bytes)")
                tmpFile.delete()
                return false
            }

            onProgress?.invoke(targetFileName, -1, -1) // signal: verifying

            val expectedHash = fetchSha256Hash(client, sha256Url)
            if (expectedHash != null && !verifySha256(tmpFile, expectedHash)) {
                Log.w(TAG, "$targetFileName: SHA256 verification failed")
                tmpFile.delete()
                return false
            }

            val destFile = File(destDir, targetFileName)
            destFile.delete()
            if (!tmpFile.renameTo(destFile)) {
                Log.e(TAG, "$targetFileName: failed to rename tmp to target")
                tmpFile.delete()
                return false
            }

            val newEtag = response.header("ETag")
            if (newEtag != null) {
                prefs.edit().putString(etagKey, newEtag).apply()
            }

            Log.i(TAG, "$targetFileName: updated (${destFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$targetFileName: download failed", e)
            tmpFile.delete()
            return false
        } finally {
            response.close()
        }
    }

    private fun fetchSha256Hash(client: OkHttpClient, url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: return null
            body.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch SHA256: ${e.message}")
            null
        }
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            actualHash == expectedHash
        } catch (e: Exception) {
            Log.e(TAG, "SHA256 verification error", e)
            false
        }
    }
}
