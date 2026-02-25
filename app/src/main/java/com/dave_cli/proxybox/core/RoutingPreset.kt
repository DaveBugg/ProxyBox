package com.dave_cli.proxybox.core

data class RoutingPreset(
    val id: String,
    val displayName: String,
    val directDomains: List<String> = emptyList(),
    val directIps: List<String> = emptyList(),
    val blockDomains: List<String> = emptyList(),
    val regionDns: String? = null,
    val regionDnsDomains: List<String> = emptyList(),
    val ipCheckServices: List<IpCheckService> = emptyList()
)

data class IpCheckService(
    val name: String,
    val url: String,
    val isRegional: Boolean = false
)

object RoutingPresets {

    private val GLOBAL_IP_CHECK = IpCheckService(
        name = "Global (ipify.org)",
        url = "https://api.ipify.org?format=text"
    )

    val ALL: List<RoutingPreset> = listOf(
        RoutingPreset(
            id = "global",
            displayName = "Global — proxy all",
            ipCheckServices = listOf(GLOBAL_IP_CHECK)
        ),
        RoutingPreset(
            id = "ru",
            displayName = "Russia — bypass RU",
            directDomains = listOf("geosite:category-ru"),
            directIps = listOf("geoip:ru"),
            regionDns = "77.88.8.8",
            regionDnsDomains = listOf("geosite:category-ru"),
            ipCheckServices = listOf(
                GLOBAL_IP_CHECK,
                IpCheckService("2ip.ru", "https://2ip.ru/", isRegional = true)
            )
        ),
        RoutingPreset(
            id = "ir",
            displayName = "Iran — bypass IR",
            directDomains = listOf("geosite:category-ir"),
            directIps = listOf("geoip:ir"),
            regionDns = "78.157.42.100",
            regionDnsDomains = listOf("geosite:category-ir"),
            ipCheckServices = listOf(
                GLOBAL_IP_CHECK,
                IpCheckService("ipmanchie.ir", "https://my.ipmanchie.ir", isRegional = true)
            )
        ),
        RoutingPreset(
            id = "cn",
            displayName = "China — bypass CN",
            directDomains = listOf("geosite:cn"),
            directIps = listOf("geoip:cn"),
            regionDns = "119.29.29.29",
            regionDnsDomains = listOf("geosite:cn"),
            ipCheckServices = listOf(
                GLOBAL_IP_CHECK,
                IpCheckService("ipip.net", "https://myip.ipip.net/", isRegional = true)
            )
        )
    )

    fun findById(id: String): RoutingPreset =
        ALL.firstOrNull { it.id == id } ?: ALL.first()
}
