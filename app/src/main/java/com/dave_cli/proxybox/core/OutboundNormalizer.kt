package com.dave_cli.proxybox.core

import android.util.Log
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import libv2ray.Libv2ray

/**
 * Brings stored / imported outbounds in line with the syntax of the current Xray core.
 *
 * Profiles live in Room for a long time, so configs created for older cores must keep
 * working after a core update. Changes handled here (Xray 26.x):
 *  - `network` h2 / http / h3 / quic were removed  → converted to `xhttp`
 *  - `tlsSettings.allowInsecure` was removed       → replaced by `pinnedPeerCertSha256`
 *    (the certificate hash is fetched from the server, see [Libv2ray.fetchTlsCertSha256])
 *  - the old hysteria2 stub (protocol `hysteria2`, tcp transport) → real `hysteria` outbound
 *
 * [prepare] may perform network I/O (certificate fetch) and must be called from a
 * background thread, **before** the VPN tunnel is established.
 */
object OutboundNormalizer {

    private const val TAG = "OutboundNormalizer"
    private val gson = Gson()

    /** Returns [profile] with a configJson that the current core accepts. */
    fun prepare(profile: ProfileEntity): ProfileEntity {
        return try {
            val trimmed = profile.configJson.trim()
            val result: JsonElement = if (trimmed.startsWith("[")) {
                val arr = gson.fromJson(trimmed, JsonArray::class.java)
                JsonArray().also { out ->
                    arr.forEach { out.add(normalize(it.asJsonObject)) }
                }
            } else {
                normalize(gson.fromJson(trimmed, JsonObject::class.java))
            }
            profile.copy(configJson = result.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not normalize profile config, using it as is", e)
            profile
        }
    }

    fun normalize(outbound: JsonObject): JsonObject {
        migrateLegacyHysteria(outbound)
        migrateProxySettings(outbound)

        val stream = outbound.getAsJsonObject("streamSettings") ?: return outbound
        migrateTransport(stream)
        migrateInsecureTls(outbound, stream)
        return outbound
    }

    /** `proxySettings.tag` (legacy chaining) was removed → `sockopt.dialerProxy`. */
    private fun migrateProxySettings(outbound: JsonObject) {
        val tag = outbound.getAsJsonObject("proxySettings")?.get("tag")?.asString
        if (!outbound.has("proxySettings")) return
        outbound.remove("proxySettings")
        if (tag.isNullOrEmpty()) return
        val stream = outbound.getAsJsonObject("streamSettings")
            ?: JsonObject().also { outbound.add("streamSettings", it) }
        val sockopt = stream.getAsJsonObject("sockopt")
            ?: JsonObject().also { stream.add("sockopt", it) }
        if (!sockopt.has("dialerProxy")) sockopt.addProperty("dialerProxy", tag)
    }

    // ── transports ───────────────────────────────────────────────────────────

    private fun migrateTransport(stream: JsonObject) {
        val key = if (stream.has("network")) "network" else if (stream.has("method")) "method" else return
        val net = stream.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: return

        if (net == "h2" || net == "h3" || net == "http" || net == "quic") {
            stream.addProperty(key, "xhttp")
            val xhttp = JsonObject()
            val http = stream.getAsJsonObject("httpSettings")
            if (http != null) {
                http.get("path")?.let { xhttp.add("path", it) }
                val host = http.get("host")
                if (host != null && host.isJsonArray && host.asJsonArray.size() > 0) {
                    xhttp.add("host", host.asJsonArray[0])
                } else if (host != null && host.isJsonPrimitive) {
                    xhttp.add("host", host)
                }
            }
            stream.remove("httpSettings")
            stream.remove("quicSettings")
            stream.add("xhttpSettings", xhttp)
        }
    }

    // ── allowInsecure → pinnedPeerCertSha256 ─────────────────────────────────

    private fun migrateInsecureTls(outbound: JsonObject, stream: JsonObject) {
        val tls = stream.getAsJsonObject("tlsSettings") ?: return
        val insecure = tls.get("allowInsecure")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        if (!tls.has("allowInsecure")) return
        tls.remove("allowInsecure")
        if (!insecure) return
        if (tls.has("pinnedPeerCertSha256")) return

        val (address, port) = findServer(outbound) ?: run {
            Log.w(TAG, "allowInsecure requested but server address not found")
            return
        }
        val serverName = tls.get("serverName")?.asString?.takeIf { it.isNotEmpty() } ?: address
        val quic = stream.get("network")?.asString == "hysteria" ||
            stream.get("method")?.asString == "hysteria"

        val hash = fetchCertSha256(address, port, serverName, quic)
        if (hash != null) {
            tls.addProperty("pinnedPeerCertSha256", hash)
            Log.i(TAG, "allowInsecure replaced by pinned certificate for $address:$port")
        } else {
            Log.w(TAG, "Could not fetch certificate of $address:$port — connecting with normal verification")
        }
    }

    private fun fetchCertSha256(address: String, port: Int, serverName: String, quic: Boolean): String? {
        return try {
            val request = gson.toJson(mapOf(
                "address" to address,
                "port" to port,
                "serverName" to serverName,
                "timeoutMs" to 5000
            ))
            val response = if (quic) Libv2ray.fetchQuicCertSha256(request)
                           else Libv2ray.fetchTlsCertSha256(request)
            val obj = gson.fromJson(response, JsonObject::class.java)
            val sha = obj.get("sha256")?.asString.orEmpty()
            if (sha.isEmpty()) {
                Log.w(TAG, "Cert fetch failed: ${obj.get("error")?.asString}")
                null
            } else sha
        } catch (e: Throwable) {
            Log.w(TAG, "Cert fetch error", e)
            null
        }
    }

    private fun findServer(outbound: JsonObject): Pair<String, Int>? {
        val settings = outbound.getAsJsonObject("settings") ?: return null
        val entry: JsonObject = listOf("vnext", "servers")
            .firstNotNullOfOrNull { k ->
                settings.getAsJsonArray(k)?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            } ?: settings
        val address = entry.get("address")?.asString ?: return null
        val port = entry.get("port")?.asInt ?: 443
        return address to port
    }

    // ── old hysteria2 stub ───────────────────────────────────────────────────

    private fun migrateLegacyHysteria(outbound: JsonObject) {
        if (outbound.get("protocol")?.asString != "hysteria2") return
        val server = outbound.getAsJsonObject("settings")
            ?.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject ?: return
        val oldTls = outbound.getAsJsonObject("streamSettings")?.getAsJsonObject("tlsSettings")

        outbound.addProperty("protocol", "hysteria")
        outbound.add("settings", JsonObject().apply {
            addProperty("version", 2)
            add("address", server.get("address"))
            add("port", server.get("port"))
        })
        outbound.add("streamSettings", JsonObject().apply {
            addProperty("network", "hysteria")
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                oldTls?.get("serverName")?.let { add("serverName", it) }
                add("alpn", gson.toJsonTree(listOf("h3")))
                if (oldTls?.has("allowInsecure") == true) addProperty("allowInsecure", true)
            })
            add("hysteriaSettings", JsonObject().apply {
                addProperty("version", 2)
                addProperty("auth", server.get("password")?.asString.orEmpty())
            })
        })
    }
}
