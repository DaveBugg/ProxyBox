package com.dave_cli.proxybox.core

import android.util.Log
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

object SpeedTestEngine {

    private const val TAG = "SpeedTestEngine"
    private const val TEST_PORT = 39272
    private val gson = Gson()

    private const val WARMUP_MS = 2000L   // discard first 2s (TCP slow-start)
    private const val MEASURE_MS = 8000L  // measure for 8s after warmup
    private const val MAX_MS = WARMUP_MS + MEASURE_MS

    private val TEST_URLS = listOf(
        "http://speed.cloudflare.com/__down?bytes=200000000",   // 200MB
        "http://proof.ovh.net/files/100Mb.dat",                 // 100MB
        "http://speedtest.tele2.net/100MB.zip",                 // 100MB
    )

    data class Result(val downloadMbps: Double?, val error: String?)

    /**
     * Runs a speed test for the given profile by spawning a temporary xray
     * instance on a separate SOCKS port (no auth, localhost only).
     * All traffic goes through the node, regardless of main VPN routing.
     *
     * Must be called from a background thread (Dispatchers.IO).
     */
    @Synchronized
    fun run(profile: ProfileEntity): Result {
        var controller: CoreController? = null

        try {
            // 1. Build minimal config: SOCKS inbound on TEST_PORT (no auth), profile outbounds, global routing
            val config = buildTestConfig(profile)
            Log.d(TAG, "Speed test config: $config")

            // 2. Create and start temporary controller (no TUN)
            val callback = object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(status: Long, msg: String?): Long {
                    Log.d(TAG, "test xray status [$status]: $msg")
                    return 0
                }
            }
            controller = Libv2ray.newCoreController(callback)
            controller.startLoop(config, 0) // tunFd=0 → no TUN

            if (!controller.isRunning) {
                return Result(null, "Xray test instance failed to start")
            }

            // 3. Wait for SOCKS to become available
            if (!waitForPort(TEST_PORT, timeoutMs = 3000)) {
                return Result(null, "Test proxy not reachable")
            }

            // 4. Download through the test proxy (no auth needed — localhost only)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TEST_PORT))
            val mbps = downloadTest(proxy)
                ?: return Result(null, "All test URLs failed")

            return Result(mbps, null)
        } catch (e: Exception) {
            Log.e(TAG, "Speed test failed", e)
            return Result(null, e.message ?: "Unknown error")
        } finally {
            // 5. Cleanup — stop controller and wait for port to be released
            try {
                controller?.stopLoop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping test controller", e)
            }
            // Give OS time to release the port before next test
            Thread.sleep(300)
        }
    }

    private fun buildTestConfig(profile: ProfileEntity): String {
        // Parse profile outbounds (same logic as ConfigBuilder)
        val (outbound, extraOutbounds) = parseProfileOutbounds(profile.configJson)

        if (!outbound.has("tag") || outbound.get("tag").asString != "proxy") {
            outbound.addProperty("tag", "proxy")
        }

        val config = JsonObject().apply {
            add("log", JsonObject().apply {
                addProperty("loglevel", "warning")
            })

            // SOCKS inbound on test port — NO AUTH (localhost only, short-lived)
            add("inbounds", gson.toJsonTree(listOf(
                mapOf(
                    "tag" to "socks-test",
                    "port" to TEST_PORT,
                    "listen" to "127.0.0.1",
                    "protocol" to "socks",
                    "settings" to mapOf(
                        "auth" to "noauth",
                        "udp" to true
                    )
                )
            )))

            // Outbounds: proxy + deps + direct + dns-out (no block, no bypass)
            val outboundsArray = JsonArray().apply {
                add(outbound)
                extraOutbounds.forEach { add(it) }
                add(gson.toJsonTree(mapOf(
                    "tag" to "direct",
                    "protocol" to "freedom",
                    "settings" to mapOf("domainStrategy" to "AsIs")
                )))
                add(gson.toJsonTree(mapOf(
                    "tag" to "dns-out",
                    "protocol" to "dns"
                )))
            }
            add("outbounds", outboundsArray)

            // DNS — simple, no regional
            add("dns", gson.toJsonTree(mapOf(
                "servers" to listOf("https://1.1.1.1/dns-query", "8.8.8.8"),
                "queryStrategy" to "UseIPv4"
            )))

            // Routing — everything through proxy (pure node test)
            add("routing", gson.toJsonTree(mapOf(
                "domainStrategy" to "IPIfNonMatch",
                "rules" to listOf(
                    mapOf("type" to "field", "port" to "53", "outboundTag" to "dns-out"),
                    mapOf("type" to "field", "port" to "0-65535", "outboundTag" to "proxy")
                )
            )))
        }

        return config.toString()
    }

    private fun parseProfileOutbounds(configJson: String): Pair<JsonObject, List<JsonObject>> {
        val trimmed = configJson.trim()
        return try {
            if (trimmed.startsWith("[")) {
                val arr = gson.fromJson(trimmed, JsonArray::class.java)
                val main = arr.firstOrNull()?.asJsonObject
                    ?: JsonObject().apply { addProperty("protocol", "freedom"); addProperty("tag", "proxy") }
                val extras = (1 until arr.size()).map { arr[it].asJsonObject }
                main to extras
            } else {
                gson.fromJson(trimmed, JsonObject::class.java) to emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse profile outbounds, falling back to DIRECT! json=$trimmed", e)
            JsonObject().apply { addProperty("protocol", "freedom"); addProperty("tag", "proxy") } to emptyList()
        }
    }

    private fun downloadTest(proxy: Proxy): Double? {
        for (url in TEST_URLS) {
            try {
                val conn = URL(url).openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 ProxyBox/SpeedTest")
                conn.setRequestProperty("Cache-Control", "no-cache, no-store")
                conn.setRequestProperty("Connection", "close")
                conn.instanceFollowRedirects = true

                val start = System.currentTimeMillis()
                val stream = conn.inputStream
                val buf = ByteArray(65536)
                var totalAll = 0L      // all bytes (including warmup)
                var measureBytes = 0L  // bytes during measurement phase only
                var measureStart = 0L  // timestamp when measurement phase begins

                while (true) {
                    val n = stream.read(buf)
                    if (n == -1) break
                    totalAll += n

                    val elapsed = System.currentTimeMillis() - start

                    // After warmup period, start counting measured bytes
                    if (elapsed >= WARMUP_MS) {
                        if (measureStart == 0L) {
                            measureStart = System.currentTimeMillis()
                        }
                        measureBytes += n
                    }

                    if (elapsed >= MAX_MS) break
                }
                stream.close()
                conn.disconnect()

                val totalElapsed = (System.currentTimeMillis() - start) / 1000.0

                // If we had enough measurement time, use measured phase
                if (measureStart > 0L && measureBytes > 50000) {
                    val measureSec = (System.currentTimeMillis() - measureStart) / 1000.0
                    if (measureSec > 0.5) {
                        val mbps = (measureBytes * 8.0) / (measureSec * 1_000_000.0)
                        Log.i(TAG, "Speed test OK: %.1f Mbps (measured %d bytes in %.1fs, warmup discarded, total %.1fs) via %s"
                            .format(mbps, measureBytes, measureSec, totalElapsed, url))
                        return mbps
                    }
                }

                // Fallback: use all bytes if warmup phase didn't complete
                if (totalElapsed > 0.5 && totalAll > 50000) {
                    val mbps = (totalAll * 8.0) / (totalElapsed * 1_000_000.0)
                    Log.i(TAG, "Speed test OK (no warmup split): %.1f Mbps (%d bytes in %.1fs) via %s"
                        .format(mbps, totalAll, totalElapsed, url))
                    return mbps
                }
            } catch (e: Exception) {
                Log.w(TAG, "Test URL failed: $url — ${e.message}")
                continue
            }
        }
        return null
    }

    private fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 500)
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(200)
            }
        }
        return false
    }
}
