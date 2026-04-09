package com.dave_cli.proxybox.core

import com.dave_cli.proxybox.data.db.ProfileEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object ConfigBuilder {

    private val gson = Gson()

    fun build(
        profile: ProfileEntity,
        preset: RoutingPreset? = null,
        socksUser: String? = null,
        socksPass: String? = null,
        customRulesJson: String? = null,
    ): String {
        val activePreset = preset ?: RoutingPresets.findById("global")

        // configJson may be a single outbound object {"protocol":...}
        // or an array of outbounds [proxy, frag-proxy, ...] when dialerProxy deps exist
        val (outbound, extraOutbounds) = parseProfileOutbounds(profile.configJson)

        if (!outbound.has("tag") || outbound.get("tag").asString != "proxy") {
            outbound.addProperty("tag", "proxy")
        }

        val socksSettings: Map<String, Any> =
            if (!socksUser.isNullOrEmpty() && !socksPass.isNullOrEmpty()) {
                mapOf(
                    "auth" to "password",
                    "accounts" to listOf(mapOf("user" to socksUser, "pass" to socksPass)),
                    "udp" to true
                )
            } else {
                mapOf("udp" to true)
            }

        val config = JsonObject().apply {
            add("log", JsonObject().apply {
                addProperty("loglevel", "info")
            })

            add("inbounds", gson.toJsonTree(listOf(
                mapOf(
                    "tag" to "socks",
                    "port" to CoreService.SOCKS_PORT,
                    "listen" to "127.0.0.1",
                    "protocol" to "socks",
                    "settings" to socksSettings,
                    "sniffing" to mapOf(
                        "enabled" to true,
                        "destOverride" to listOf("http", "tls", "quic"),
                        "routeOnly" to false
                    )
                )
            )))

            // Build outbounds: proxy + any dependent outbounds (e.g. frag-proxy)
            // + our standard utility outbounds (direct, block, dns-out)
            val outboundsArray = JsonArray().apply {
                add(outbound)
                extraOutbounds.forEach { add(it) }
                add(gson.toJsonTree(mapOf(
                    "tag" to "direct",
                    "protocol" to "freedom",
                    "settings" to mapOf("domainStrategy" to "AsIs")
                )))
                add(gson.toJsonTree(mapOf(
                    "tag" to "block",
                    "protocol" to "blackhole",
                    "settings" to mapOf("response" to mapOf("type" to "http"))
                )))
                add(gson.toJsonTree(mapOf(
                    "tag" to "dns-out",
                    "protocol" to "dns"
                )))
            }
            add("outbounds", outboundsArray)

            add("dns", buildDns(activePreset))
            add("routing", buildRouting(activePreset, customRulesJson))
        }

        return config.toString()
    }

    private fun buildDns(preset: RoutingPreset): com.google.gson.JsonElement {
        val servers = mutableListOf<Any>()

        // Regional DNS for direct domains (resolves locally, not through proxy)
        if (preset.regionDns != null && preset.regionDnsDomains.isNotEmpty()) {
            servers.add(mapOf(
                "address" to preset.regionDns,
                "domains" to preset.regionDnsDomains
            ))
        }

        // Primary DoH via Cloudflare (resolves through proxy for non-regional domains)
        servers.add("https://1.1.1.1/dns-query")
        // Fallback plain DNS
        servers.add("8.8.8.8")
        servers.add("localhost")

        return gson.toJsonTree(mapOf(
            "hosts" to mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            ),
            "servers" to servers,
            "queryStrategy" to "UseIPv4"
        ))
    }

    private fun buildRouting(preset: RoutingPreset, customRulesJson: String? = null): com.google.gson.JsonElement {
        val rules = mutableListOf<Map<String, Any>>()

        rules.add(mapOf(
            "type" to "field",
            "port" to "53",
            "outboundTag" to "dns-out"
        ))

        rules.add(mapOf(
            "type" to "field",
            "outboundTag" to "direct",
            "ip" to listOf("geoip:private")
        ))

        // Custom routing rules (from imported v2rayN JSON)
        if (!customRulesJson.isNullOrEmpty()) {
            try {
                val customArray = gson.fromJson(customRulesJson, com.google.gson.JsonArray::class.java)
                for (i in 0 until customArray.size()) {
                    val rule = customArray[i].asJsonObject
                    val map = mutableMapOf<String, Any>()
                    for (entry in rule.entrySet()) {
                        val v = entry.value
                        when {
                            v.isJsonPrimitive -> map[entry.key] = v.asString
                            v.isJsonArray -> map[entry.key] = v.asJsonArray.map { it.asString }
                            else -> map[entry.key] = v
                        }
                    }
                    rules.add(map)
                }
            } catch (e: Exception) {
                android.util.Log.w("ConfigBuilder", "Failed to parse custom rules, skipping", e)
            }
        }

        if (preset.directDomains.isNotEmpty()) {
            rules.add(mapOf(
                "type" to "field",
                "outboundTag" to "direct",
                "domain" to preset.directDomains
            ))
        }

        if (preset.directIps.isNotEmpty()) {
            rules.add(mapOf(
                "type" to "field",
                "outboundTag" to "direct",
                "ip" to preset.directIps
            ))
        }

        if (preset.blockDomains.isNotEmpty()) {
            rules.add(mapOf(
                "type" to "field",
                "outboundTag" to "block",
                "domain" to preset.blockDomains
            ))
        }

        rules.add(mapOf(
            "type" to "field",
            "outboundTag" to "block",
            "network" to "udp",
            "port" to "443"
        ))

        rules.add(mapOf(
            "type" to "field",
            "port" to "0-65535",
            "outboundTag" to "proxy"
        ))

        return gson.toJsonTree(mapOf(
            "domainStrategy" to "IPIfNonMatch",
            "rules" to rules
        ))
    }

    /**
     * Parses configJson which is either:
     * - a single JSON object `{...}` (one outbound, legacy/common case)
     * - a JSON array `[{proxy}, {frag-proxy}, ...]` (proxy + dependent outbounds)
     *
     * Returns the main proxy outbound and a list of extra dependent outbounds.
     */
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
                val obj = gson.fromJson(trimmed, JsonObject::class.java)
                obj to emptyList()
            }
        } catch (e: Exception) {
            val fallback = JsonObject().apply { addProperty("protocol", "freedom"); addProperty("tag", "proxy") }
            fallback to emptyList()
        }
    }
}
