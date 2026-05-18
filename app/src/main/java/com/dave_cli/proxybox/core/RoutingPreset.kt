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
    val fallbackUrls: List<String> = emptyList(),
    val isRegional: Boolean = false
)

object RoutingPresets {

    private val GLOBAL_IP_CHECK = IpCheckService(
        name = "Global (ipify.org)",
        url = "https://api.ipify.org?format=text"
    )

    private val RU_IP_CHECK = IpCheckService(
        name = "RU regional",
        url = "https://internet-lab.ru/ip",
        fallbackUrls = listOf(
            "https://2ip.ru/",
            "https://2ip.me/"
        ),
        isRegional = true
    )

    private val IR_IP_CHECK = IpCheckService("ipmanchie.ir", "https://my.ipmanchie.ir", isRegional = true)
    private val CN_IP_CHECK = IpCheckService("ipip.net", "https://myip.ipip.net/", isRegional = true)

    // Base presets (work with any geo profile)
    private val BASE = listOf(
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
            ipCheckServices = listOf(GLOBAL_IP_CHECK, RU_IP_CHECK)
        ),
        RoutingPreset(
            id = "ir",
            displayName = "Iran — bypass IR",
            directDomains = listOf("geosite:category-ir"),
            directIps = listOf("geoip:ir"),
            regionDns = "78.157.42.100",
            regionDnsDomains = listOf("geosite:category-ir"),
            ipCheckServices = listOf(GLOBAL_IP_CHECK, IR_IP_CHECK)
        ),
        RoutingPreset(
            id = "cn",
            displayName = "China — bypass CN",
            directDomains = listOf("geosite:cn"),
            directIps = listOf("geoip:cn"),
            regionDns = "119.29.29.29",
            regionDnsDomains = listOf("geosite:cn"),
            ipCheckServices = listOf(GLOBAL_IP_CHECK, CN_IP_CHECK)
        )
    )

    // Enhanced RU preset for Loyalsoldier
    // category-ru already includes gov-ru, bank-ru, mts-ru, yandex, mailru-group etc.
    private val RU_LOYALSOLDIER = BASE.first { it.id == "ru" }.copy(
        blockDomains = listOf("geosite:category-ads-all"),
    )

    // Enhanced CN preset for Loyalsoldier (enriched cn list + apple/google CN domains)
    private val CN_LOYALSOLDIER = BASE.first { it.id == "cn" }.copy(
        directDomains = listOf(
            "geosite:cn",
            "geosite:apple-cn",
            "geosite:google-cn",
        ),
        blockDomains = listOf("geosite:category-ads-all"),
    )

    // Enhanced RU preset for runetfreedom (has ru-available-only-inside)
    // category-ru already includes all banks, operators, yandex, mailru-group etc.
    // ru-available-only-inside = domains accessible only from inside Russia
    private val RU_RUNETFREEDOM = BASE.first { it.id == "ru" }.copy(
        directDomains = listOf(
            "geosite:category-ru",
            "geosite:ru-available-only-inside",
        ),
    )

    val ALL: List<RoutingPreset> = BASE

    fun getPresetsForGeoProfile(geoProfileId: String): List<RoutingPreset> {
        return when (geoProfileId) {
            "loyalsoldier" -> BASE.map { preset ->
                when (preset.id) {
                    "ru" -> RU_LOYALSOLDIER
                    "cn" -> CN_LOYALSOLDIER
                    else -> preset
                }
            }
            "runetfreedom" -> BASE.map { preset ->
                when (preset.id) {
                    "ru" -> RU_RUNETFREEDOM
                    else -> preset
                }
            }
            else -> BASE
        }
    }

    fun findById(id: String, geoProfileId: String? = null): RoutingPreset {
        val presets = if (geoProfileId != null) getPresetsForGeoProfile(geoProfileId) else BASE
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }
}
