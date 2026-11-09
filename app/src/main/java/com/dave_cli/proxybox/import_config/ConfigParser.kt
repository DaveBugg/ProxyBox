package com.dave_cli.proxybox.import_config

import android.net.Uri
import android.util.Base64
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID

object ConfigParser {

    private val gson = Gson()

    fun parse(input: String): ProfileEntity? {
        val trimmed = input.trim()
        return try {
            when {
                trimmed.startsWith("vless://")     -> parseVless(trimmed)
                trimmed.startsWith("vmess://")     -> parseVmess(trimmed)
                trimmed.startsWith("ss://")        -> parseShadowsocks(trimmed)
                trimmed.startsWith("trojan://")    -> parseTrojan(trimmed)
                trimmed.startsWith("hy2://") ||
                trimmed.startsWith("hysteria2://") -> parseHysteria2(trimmed)
                trimmed.startsWith("{")            -> parseRawJson(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─── VLESS ───────────────────────────────────────────────────────────────

    private fun parseVless(uri: String): ProfileEntity {
        val u = Uri.parse(uri)
        val uuid = u.userInfo ?: ""
        val host = u.host ?: ""
        val port = u.port.takeIf { it > 0 } ?: 443
        val name = Uri.decode(u.fragment ?: host)

        val security   = u.getQueryParameter("security") ?: "none"
        val pbk        = u.getQueryParameter("pbk") ?: ""
        val sid        = u.getQueryParameter("sid") ?: ""
        val fp         = u.getQueryParameter("fp") ?: "chrome"
        val sni        = u.getQueryParameter("sni") ?: host
        val network    = u.getQueryParameter("type") ?: "tcp"
        val headerType = u.getQueryParameter("headerType") ?: "none"
        val flow       = u.getQueryParameter("flow") ?: ""
        val path       = u.getQueryParameter("path") ?: "/"
        val wsHost     = u.getQueryParameter("host") ?: sni
        val serviceName = u.getQueryParameter("serviceName") ?: ""
        val alpn       = u.getQueryParameter("alpn") ?: ""
        val insecure   = (u.getQueryParameter("allowInsecure") ?: u.getQueryParameter("insecure") ?: "0") == "1"

        val streamSettings = buildStreamSettings(
            network, security, sni, pbk, sid, fp, headerType, path, wsHost, serviceName, alpn,
            allowInsecure = insecure,
            xhttpMode = u.getQueryParameter("mode") ?: "",
            xhttpExtra = u.getQueryParameter("extra") ?: ""
        )

        val userObj = JsonObject().apply {
            addProperty("id", uuid)
            addProperty("encryption", u.getQueryParameter("encryption") ?: "none")
            if (flow.isNotEmpty()) addProperty("flow", flow)
        }

        val outbound = JsonObject().apply {
            addProperty("protocol", "vless")
            addProperty("tag", "proxy")
            add("settings", JsonObject().apply {
                add("vnext", gson.toJsonTree(listOf(mapOf(
                    "address" to host,
                    "port" to port,
                    "users" to listOf(userObj)
                ))))
            })
            add("streamSettings", streamSettings)
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "vless",
            configJson = outbound.toString(),
            rawUri = uri
        )
    }

    // ─── VMESS ───────────────────────────────────────────────────────────────

    private fun parseVmess(uri: String): ProfileEntity {
        val encoded = uri.removePrefix("vmess://")
        val json = decodeBase64(encoded)
        val obj = gson.fromJson(json, JsonObject::class.java)

        val host     = obj.get("add")?.asString ?: ""
        val port     = obj.get("port")?.let { if (it.isJsonPrimitive) it.asString.toIntOrNull() ?: 443 else 443 } ?: 443
        val uuid     = obj.get("id")?.asString ?: ""
        val alterId  = obj.get("aid")?.let { if (it.isJsonPrimitive) it.asString.toIntOrNull() ?: 0 else 0 } ?: 0
        val network  = obj.get("net")?.asString ?: "tcp"
        val tls      = obj.get("tls")?.asString ?: ""
        val security = if (tls == "tls") "tls" else "none"
        val sni      = obj.get("sni")?.asString?.takeIf { it.isNotEmpty() } ?: host
        val name     = obj.get("ps")?.asString ?: host
        val path     = obj.get("path")?.asString ?: "/"
        val wsHost   = obj.get("host")?.asString ?: sni
        val fp       = obj.get("fp")?.asString ?: "chrome"
        val alpn     = obj.get("alpn")?.asString ?: ""

        val streamSettings = buildStreamSettings(
            network, security, sni, "", "", fp, "none", path, wsHost, "", alpn
        )

        val outbound = JsonObject().apply {
            addProperty("protocol", "vmess")
            addProperty("tag", "proxy")
            add("settings", gson.toJsonTree(mapOf(
                "vnext" to listOf(mapOf(
                    "address" to host,
                    "port" to port,
                    "users" to listOf(mapOf(
                        "id" to uuid,
                        "alterId" to alterId,
                        "security" to "auto"
                    ))
                ))
            )))
            add("streamSettings", streamSettings)
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "vmess",
            configJson = outbound.toString(),
            rawUri = uri
        )
    }

    // ─── SHADOWSOCKS ─────────────────────────────────────────────────────────

    private fun parseShadowsocks(uri: String): ProfileEntity {
        val withoutScheme = uri.removePrefix("ss://")
        val nameFragment = withoutScheme.substringAfter("#", "")
        val withoutFragment = withoutScheme.substringBefore("#")

        val method: String
        val password: String
        val host: String
        val port: Int

        if (withoutFragment.contains("@")) {
            val atIdx = withoutFragment.lastIndexOf("@")
            val credentials = withoutFragment.substring(0, atIdx)
            val hostPortPart = withoutFragment.substring(atIdx + 1).substringBefore("?")

            val decoded = try {
                decodeBase64(credentials)
            } catch (e: Exception) { credentials }

            val colonIdx = decoded.indexOf(":")
            method = if (colonIdx > 0) decoded.substring(0, colonIdx) else "aes-256-gcm"
            password = if (colonIdx > 0) decoded.substring(colonIdx + 1) else decoded

            host = hostPortPart.substringBeforeLast(":")
            port = hostPortPart.substringAfterLast(":").toIntOrNull() ?: 443
        } else {
            val decoded = decodeBase64(withoutFragment)
            val parts = decoded.split("@")
            val credentials = parts[0]
            val hostPortPart = parts.getOrElse(1) { "" }

            val colonIdx = credentials.indexOf(":")
            method = if (colonIdx > 0) credentials.substring(0, colonIdx) else "aes-256-gcm"
            password = if (colonIdx > 0) credentials.substring(colonIdx + 1) else credentials

            host = hostPortPart.substringBeforeLast(":")
            port = hostPortPart.substringAfterLast(":").toIntOrNull() ?: 443
        }

        val name = Uri.decode(nameFragment.ifEmpty { host })

        val outbound = JsonObject().apply {
            addProperty("protocol", "shadowsocks")
            addProperty("tag", "proxy")
            add("settings", gson.toJsonTree(mapOf(
                "servers" to listOf(mapOf(
                    "address" to host,
                    "port" to port,
                    "method" to method,
                    "password" to password
                ))
            )))
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "ss",
            configJson = outbound.toString(),
            rawUri = uri
        )
    }

    // ─── TROJAN ───────────────────────────────────────────────────────────────

    private fun parseTrojan(uri: String): ProfileEntity {
        val u = Uri.parse(uri)
        val password = u.userInfo ?: ""
        val host     = u.host ?: ""
        val port     = u.port.takeIf { it > 0 } ?: 443
        val sni      = u.getQueryParameter("sni") ?: host
        val name     = Uri.decode(u.fragment ?: host)
        val network  = u.getQueryParameter("type") ?: "tcp"
        val security = u.getQueryParameter("security") ?: "tls"
        val fp       = u.getQueryParameter("fp") ?: "chrome"
        val path     = u.getQueryParameter("path") ?: "/"
        val wsHost   = u.getQueryParameter("host") ?: sni
        val serviceName = u.getQueryParameter("serviceName") ?: ""
        val alpn     = u.getQueryParameter("alpn") ?: ""
        val authority = u.getQueryParameter("authority") ?: ""
        val mode     = u.getQueryParameter("mode") ?: ""
        val allowInsecure = (u.getQueryParameter("allowInsecure") ?: u.getQueryParameter("insecure") ?: "0") == "1"

        val streamSettings = buildStreamSettings(
            network, security, sni, "", "", fp, "none", path, wsHost, serviceName, alpn,
            authority = authority, grpcMode = mode, allowInsecure = allowInsecure
        )

        val outbound = JsonObject().apply {
            addProperty("protocol", "trojan")
            addProperty("tag", "proxy")
            add("settings", gson.toJsonTree(mapOf(
                "servers" to listOf(mapOf(
                    "address" to host,
                    "port" to port,
                    "password" to password
                ))
            )))
            add("streamSettings", streamSettings)
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "trojan",
            configJson = outbound.toString(),
            rawUri = uri
        )
    }

    // ─── HYSTERIA2 ────────────────────────────────────────────────────────────

    private fun parseHysteria2(uri: String): ProfileEntity {
        // Parsed by hand: hy2 links may carry multi-port specs ("host:443,5000-6000")
        // that android.net.Uri cannot handle.
        val withoutScheme = uri.substringAfter("://")
        val fragment = withoutScheme.substringAfter('#', "")
        val noFragment = withoutScheme.substringBefore('#')
        val query = noFragment.substringAfter('?', "")
        val authority = noFragment.substringBefore('?').substringBefore('/')
        val auth = Uri.decode(if (authority.contains('@')) authority.substringBeforeLast('@') else "")
        val hostPort = authority.substringAfterLast('@')

        val host: String
        val portSpec: String
        if (hostPort.startsWith("[")) {
            host = hostPort.substringAfter('[').substringBefore(']')
            portSpec = hostPort.substringAfter("]:", "")
        } else {
            host = hostPort.substringBeforeLast(':')
            portSpec = if (hostPort.contains(':')) hostPort.substringAfterLast(':') else ""
        }
        // First entry of "443,5000-6000"; for a pure range use its first port.
        // (Port hopping itself is not supported — the first port is used.)
        val port = portSpec.split(',').firstOrNull()
            ?.substringBefore('-')?.trim()?.toIntOrNull()
            ?: (query.let { Uri.parse("hy2://x/?$it").getQueryParameter("mport") }
                ?.split(',')?.firstOrNull()?.substringBefore('-')?.trim()?.toIntOrNull())
            ?: 443

        val q = Uri.parse("hy2://x/?$query")
        val sni = q.getQueryParameter("sni")?.takeIf { it.isNotEmpty() } ?: host
        val name = Uri.decode(fragment).ifEmpty { host }
        val insecure = q.getQueryParameter("insecure") == "1" ||
            q.getQueryParameter("allowInsecure") == "1"
        val pin = q.getQueryParameter("pinSHA256") ?: ""
        val obfs = q.getQueryParameter("obfs") ?: ""
        val obfsPassword = q.getQueryParameter("obfs-password") ?: ""
        val alpn = q.getQueryParameter("alpn")?.takeIf { it.isNotEmpty() } ?: "h3"

        val streamSettings = JsonObject().apply {
            addProperty("network", "hysteria")
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                addProperty("serverName", sni)
                add("alpn", gson.toJsonTree(alpn.split(",")))
                // allowInsecure was removed from Xray — OutboundNormalizer converts this
                // flag to a pinned certificate hash before the core starts.
                if (insecure) addProperty("allowInsecure", true)
                if (pin.isNotEmpty()) addProperty("pinnedPeerCertSha256", pin)
            })
            add("hysteriaSettings", JsonObject().apply {
                addProperty("version", 2)
                addProperty("auth", auth)
            })
            if (obfs == "salamander" && obfsPassword.isNotEmpty()) {
                add("finalmask", JsonObject().apply {
                    add("udp", gson.toJsonTree(listOf(mapOf(
                        "type" to "salamander",
                        "settings" to mapOf("password" to obfsPassword)
                    ))))
                })
            }
        }

        val outbound = JsonObject().apply {
            addProperty("protocol", "hysteria")
            addProperty("tag", "proxy")
            add("settings", gson.toJsonTree(mapOf(
                "version" to 2,
                "address" to host,
                "port" to port
            )))
            add("streamSettings", streamSettings)
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = "hy2",
            configJson = outbound.toString(),
            rawUri = uri
        )
    }

    // ─── RAW JSON ────────────────────────────────────────────────────────────

    private fun parseRawJson(json: String): ProfileEntity {
        val obj = gson.fromJson(json, JsonObject::class.java)

        val outbound: JsonObject
        val extraOutbounds = mutableListOf<JsonObject>()

        if (obj.has("outbounds")) {
            val arr = obj.getAsJsonArray("outbounds")
            val utilityProtocols = setOf("freedom", "blackhole", "dns")

            // Pick the main proxy outbound
            outbound = arr.firstOrNull {
                it.asJsonObject.get("tag")?.asString == "proxy"
            }?.asJsonObject
                ?: arr.firstOrNull {
                    it.asJsonObject.get("protocol")?.asString !in utilityProtocols
                }?.asJsonObject
                ?: arr.firstOrNull()?.asJsonObject
                ?: obj

            // Collect outbounds referenced by dialerProxy (e.g. frag-proxy for TLS
            // fragmentation). Without them xray-core would fail to resolve the reference.
            val deps = mutableSetOf<String>()
            collectDialerProxyDeps(outbound, deps)
            // Iteratively resolve transitive deps (a dialer proxy could itself
            // reference another one, though unusual)
            var frontier = deps.toSet()
            while (frontier.isNotEmpty()) {
                val next = mutableSetOf<String>()
                for (tag in frontier) {
                    val dep = arr.firstOrNull {
                        it.asJsonObject.get("tag")?.asString == tag
                    }?.asJsonObject
                    if (dep != null) {
                        extraOutbounds.add(dep)
                        collectDialerProxyDeps(dep, next)
                    }
                }
                next.removeAll(deps)
                deps.addAll(next)
                frontier = next
            }
        } else {
            outbound = obj
        }

        if (!outbound.has("tag")) {
            outbound.addProperty("tag", "proxy")
        }

        val protocol = outbound.get("protocol")?.asString ?: "raw"
        val remarks = obj.get("remarks")?.asString
            ?: obj.get("ps")?.asString
        val name = if (!remarks.isNullOrBlank()) remarks else "Import ($protocol)"

        // If there are dependent outbounds, store as JSON array [proxy, dep1, dep2, …]
        // Otherwise store as a single JSON object (backward compatible)
        val configJson = if (extraOutbounds.isNotEmpty()) {
            val all = com.google.gson.JsonArray()
            all.add(outbound)
            extraOutbounds.forEach { all.add(it) }
            all.toString()
        } else {
            outbound.toString()
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = protocol,
            configJson = configJson,
            rawUri = json.take(500)
        )
    }

    /**
     * Extracts outbound tag references that the given outbound depends on:
     * - `streamSettings.sockopt.dialerProxy` — modern xray chaining (e.g. frag-proxy, WARP)
     * - `proxySettings.tag` — legacy v2ray chaining (still generated by v2rayN for WARP chains)
     */
    private fun collectDialerProxyDeps(outbound: JsonObject, deps: MutableSet<String>) {
        // Modern: dialerProxy
        val dialerTag = outbound
            .getAsJsonObject("streamSettings")
            ?.getAsJsonObject("sockopt")
            ?.get("dialerProxy")?.asString
        if (!dialerTag.isNullOrEmpty()) {
            deps.add(dialerTag)
        }

        // Legacy: proxySettings.tag
        val proxyTag = outbound
            .getAsJsonObject("proxySettings")
            ?.get("tag")?.asString
        if (!proxyTag.isNullOrEmpty()) {
            deps.add(proxyTag)
        }
    }

    // ─── Stream Settings builder ─────────────────────────────────────────────

    private fun buildStreamSettings(
        network: String,
        security: String,
        sni: String,
        pbk: String,
        sid: String,
        fp: String,
        headerType: String,
        path: String = "/",
        wsHost: String = "",
        serviceName: String = "",
        alpn: String = "",
        authority: String = "",
        grpcMode: String = "",
        allowInsecure: Boolean = false,
        xhttpMode: String = "",
        xhttpExtra: String = ""
    ): JsonObject {
        // Xray >= 26.x removed the h2/http/h3/quic transports; XHTTP replaces them.
        val network = when (network) {
            "h2", "h3", "http", "quic", "splithttp" -> "xhttp"
            else -> network
        }
        return JsonObject().apply {
            addProperty("network", network)

            when (security) {
                "reality" -> {
                    addProperty("security", "reality")
                    add("realitySettings", JsonObject().apply {
                        addProperty("serverName", sni)
                        addProperty("fingerprint", fp)
                        addProperty("publicKey", pbk)
                        addProperty("shortId", sid)
                    })
                }
                "tls" -> {
                    addProperty("security", "tls")
                    add("tlsSettings", JsonObject().apply {
                        addProperty("serverName", sni)
                        if (fp.isNotEmpty()) addProperty("fingerprint", fp)
                        if (allowInsecure) addProperty("allowInsecure", true)
                        val effectiveAlpn = if (alpn.isNotEmpty()) alpn
                            else if (network == "grpc" || network == "xhttp") "h2"
                            else ""
                        if (effectiveAlpn.isNotEmpty()) {
                            add("alpn", gson.toJsonTree(effectiveAlpn.split(",")))
                        }
                    })
                }
                else -> addProperty("security", "none")
            }

            when (network) {
                "ws" -> {
                    add("wsSettings", JsonObject().apply {
                        addProperty("path", path)
                        if (wsHost.isNotEmpty()) {
                            add("headers", JsonObject().apply {
                                addProperty("Host", wsHost)
                            })
                        }
                    })
                }
                "grpc" -> {
                    add("grpcSettings", JsonObject().apply {
                        addProperty("serviceName", serviceName)
                        addProperty("multiMode", grpcMode == "multi")
                        if (authority.isNotEmpty()) addProperty("authority", authority)
                    })
                }
                "httpupgrade" -> {
                    add("httpupgradeSettings", JsonObject().apply {
                        addProperty("path", path)
                        if (wsHost.isNotEmpty()) addProperty("host", wsHost)
                    })
                }
                "xhttp" -> {
                    add("xhttpSettings", JsonObject().apply {
                        addProperty("path", path)
                        if (wsHost.isNotEmpty()) addProperty("host", wsHost)
                        if (xhttpMode.isNotEmpty()) addProperty("mode", xhttpMode)
                        if (xhttpExtra.isNotEmpty()) {
                            try {
                                add("extra", gson.fromJson(xhttpExtra, JsonObject::class.java))
                            } catch (_: Exception) { /* ignore malformed extra */ }
                        }
                    })
                }
                "tcp" -> {
                    if (headerType == "http") {
                        add("tcpSettings", JsonObject().apply {
                            add("header", JsonObject().apply {
                                addProperty("type", "http")
                                add("request", JsonObject().apply {
                                    addProperty("path", path)
                                    if (wsHost.isNotEmpty()) {
                                        add("headers", JsonObject().apply {
                                            add("Host", gson.toJsonTree(listOf(wsHost)))
                                        })
                                    }
                                })
                            })
                        })
                    }
                }
                "kcp", "mkcp" -> {
                    add("kcpSettings", JsonObject())
                    // kcpSettings.header is ignored by recent Xray; header camouflage
                    // now lives in finalmask (mkcp-legacy).
                    if (headerType.isNotEmpty() && headerType != "none") {
                        add("finalmask", JsonObject().apply {
                            add("udp", gson.toJsonTree(listOf(mapOf(
                                "type" to "mkcp-legacy",
                                "settings" to mapOf("header" to headerType)
                            ))))
                        })
                    }
                }
            }
        }
    }

    private fun decodeBase64(input: String): String {
        val padded = when (input.length % 4) {
            2 -> "$input=="
            3 -> "$input="
            else -> input
        }
        return try {
            String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
        } catch (e: Exception) {
            String(Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP))
        }
    }
}
