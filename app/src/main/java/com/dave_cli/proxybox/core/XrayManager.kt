package com.dave_cli.proxybox.core

import android.content.Context
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

object XrayManager : ProxyEngine {

    private const val TAG = "XrayManager"
    private var coreController: CoreController? = null
    private val coreInitialized = AtomicBoolean(false)

    override val name: String = "Xray"
    override val isRunning: Boolean get() = coreController?.isRunning == true

    fun getVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun initCoreEnv(ctx: Context) {
        if (coreInitialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(ctx.applicationContext)
                extractGeoFiles(ctx)
                val assetsPath = ctx.getDir("assets", Context.MODE_PRIVATE).absolutePath
                Libv2ray.initCoreEnv(assetsPath, "")
                Log.i(TAG, "Xray core env initialized, version=${getVersion()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init core env", e)
                coreInitialized.set(false)
            }
        }
    }

    override fun start(configJson: String, service: android.net.VpnService): Boolean {
        return start(configJson, service, 0)
    }

    fun start(configJson: String, service: android.net.VpnService, tunFd: Int): Boolean {
        return try {
            stop()

            val ctx = service.applicationContext
            initCoreEnv(ctx)

            Log.i(TAG, "Starting xray, tunFd=$tunFd, config length=${configJson.length}")
            Log.d(TAG, "Config preview: ${configJson.take(500)}")

            val callback = object : CoreCallbackHandler {
                override fun startup(): Long {
                    Log.i(TAG, "xray startup callback invoked")
                    return 0
                }

                override fun shutdown(): Long {
                    Log.i(TAG, "xray shutdown callback invoked")
                    return 0
                }

                override fun onEmitStatus(status: Long, msg: String?): Long {
                    Log.i(TAG, "xray status [$status]: $msg")
                    return 0
                }
            }

            val controller = Libv2ray.newCoreController(callback)
            controller.startLoop(configJson, tunFd)

            if (!controller.isRunning) {
                Log.e(TAG, "Core controller NOT running after startLoop")
                return false
            }

            coreController = controller
            Log.i(TAG, "xray started successfully, isRunning=${controller.isRunning}")

            verifySocksProxy()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start xray", e)
            false
        }
    }

    private fun verifySocksProxy() {
        Thread {
            try {
                Thread.sleep(1000)
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", 10808), 2000)
                socket.close()
                Log.i(TAG, "SOCKS proxy on 10808 is LISTENING — OK")
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS proxy on 10808 NOT reachable: ${e.message}")
            }
        }.start()
    }

    override fun stop() {
        try {
            coreController?.let {
                if (it.isRunning) {
                    it.stopLoop()
                }
            }
            coreController = null
            Log.i(TAG, "xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping xray", e)
        }
    }

    fun queryStats(tag: String, link: String): Long {
        return try {
            coreController?.queryStats(tag, link) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun extractGeoFiles(ctx: Context) {
        val assetsDir = ctx.getDir("assets", Context.MODE_PRIVATE)
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val dest = File(assetsDir, name)
            if (dest.exists() && dest.length() < 1024) {
                Log.w(TAG, "$name appears corrupted (${dest.length()} bytes), removing")
                dest.delete()
            }
            if (!dest.exists()) {
                try {
                    ctx.assets.open(name).use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Extracted $name from assets (${dest.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract $name", e)
                }
            }
        }
    }
}
