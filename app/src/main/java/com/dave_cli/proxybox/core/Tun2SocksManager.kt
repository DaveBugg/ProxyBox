package com.dave_cli.proxybox.core

import android.content.Context
import android.util.Log
import hev.htproxy.TProxyService
import java.io.File

object Tun2SocksManager {

    private const val TAG = "Tun2Socks"

    private var nativeLoaded = false
    private var started = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            nativeLoaded = true
            Log.i(TAG, "hev-socks5-tunnel loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "hev-socks5-tunnel NOT available: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = nativeLoaded

    fun start(
        context: Context,
        tunFd: Int,
        socksPort: Int = CoreService.SOCKS_PORT,
        socksUser: String? = null,
        socksPass: String? = null,
    ) {
        if (!nativeLoaded) {
            Log.e(TAG, "Cannot start: native library not loaded")
            return
        }
        if (started) {
            Log.w(TAG, "tun2socks already running")
            return
        }

        val configFile = File(context.cacheDir, "tun2socks.yml")
        val authLines =
            if (!socksUser.isNullOrEmpty() && !socksPass.isNullOrEmpty()) {
                "\n              username: '${yamlEscape(socksUser)}'" +
                "\n              password: '${yamlEscape(socksPass)}'"
            } else ""
        val config = """
            misc:
              task-stack-size: 81920
              log-level: info
            tunnel:
              mtu: 9000
            socks5:
              port: $socksPort
              address: '127.0.0.1'
              udp: 'udp'$authLines
        """.trimIndent()

        configFile.writeText(config)
        Log.i(TAG, "Starting tun2socks, tunFd=$tunFd, socksPort=$socksPort")
        Log.d(TAG, "Config:\n$config")

        try {
            TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
            started = true
            Log.i(TAG, "tun2socks started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
        }
    }

    fun stop() {
        if (!started) return
        try {
            TProxyService.TProxyStopService()
            started = false
            Log.i(TAG, "tun2socks stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tun2socks", e)
        }
    }

    private fun yamlEscape(s: String): String =
        s.replace("\\", "\\\\").replace("'", "''")

    fun getStats(): Pair<Long, Long> {
        if (!nativeLoaded) return Pair(0L, 0L)
        return try {
            val stats = TProxyService.TProxyGetStats()
            if (stats.size >= 2) Pair(stats[0], stats[1]) else Pair(0L, 0L)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }
}
