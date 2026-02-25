package com.dave_cli.proxybox.core

import com.dave_cli.proxybox.data.db.ProfileEntity
import com.google.gson.Gson
import com.google.gson.JsonObject

object ConfigBuilder {

    private val gson = Gson()

    fun build(profile: ProfileEntity): String {
        val outbound = try {
            gson.fromJson(profile.configJson, JsonObject::class.java)
        } catch (e: Exception) {
            gson.fromJson("{\"protocol\":\"freedom\",\"tag\":\"proxy\"}", JsonObject::class.java)
        }

        if (!outbound.has("tag") || outbound.get("tag").asString != "proxy") {
            outbound.addProperty("tag", "proxy")
        }

        val config = JsonObject().apply {
            add("log", JsonObject().apply {
                addProperty("loglevel", "info")
            })

            add("inbounds", gson.toJsonTree(listOf(
                mapOf(
                    "tag" to "socks",
                    "port" to 10808,
                    "listen" to "127.0.0.1",
                    "protocol" to "socks",
                    "settings" to mapOf("udp" to true),
                    "sniffing" to mapOf(
                        "enabled" to true,
                        "destOverride" to listOf("http", "tls", "quic"),
                        "routeOnly" to false
                    )
                ),
                mapOf(
                    "tag" to "http",
                    "port" to 10809,
                    "listen" to "127.0.0.1",
                    "protocol" to "http",
                    "sniffing" to mapOf(
                        "enabled" to true,
                        "destOverride" to listOf("http", "tls"),
                        "routeOnly" to false
                    )
                )
            )))

            add("outbounds", gson.toJsonTree(listOf(
                outbound,
                mapOf(
                    "tag" to "direct",
                    "protocol" to "freedom",
                    "settings" to mapOf("domainStrategy" to "AsIs")
                ),
                mapOf(
                    "tag" to "block",
                    "protocol" to "blackhole"
                ),
                mapOf(
                    "tag" to "dns-out",
                    "protocol" to "dns"
                )
            )))

            add("dns", gson.toJsonTree(mapOf(
                "hosts" to mapOf(
                    "domain:googleapis.cn" to "googleapis.com"
                ),
                "servers" to listOf(
                    mapOf(
                        "address" to "https+local://1.1.1.1/dns-query",
                        "domains" to listOf<String>()
                    ),
                    "1.1.1.1",
                    "8.8.8.8",
                    "localhost"
                )
            )))

            add("routing", gson.toJsonTree(mapOf(
                "domainStrategy" to "IPIfNonMatch",
                "rules" to listOf(
                    mapOf(
                        "type" to "field",
                        "port" to "53",
                        "outboundTag" to "dns-out"
                    ),
                    mapOf(
                        "type" to "field",
                        "outboundTag" to "direct",
                        "ip" to listOf("geoip:private")
                    ),
                    mapOf(
                        "type" to "field",
                        "port" to "0-65535",
                        "outboundTag" to "proxy"
                    )
                )
            )))
        }

        return config.toString()
    }
}
