package com.dave_cli.proxybox.core

import android.content.Context

data class GeoProfile(
    val id: String,
    val geoipUrl: String,
    val geoipSha256Url: String,
    val geositeUrl: String,
    val geositeSha256Url: String,
) {
    companion object {
        private const val PREFS_NAME = "proxybox_prefs"
        private const val KEY_GEO_PROFILE = "geo_profile"

        val V2FLY = GeoProfile(
            id = "v2fly",
            geoipUrl = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat",
            geoipSha256Url = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat.sha256sum",
            geositeUrl = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat",
            geositeSha256Url = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat.sha256sum",
        )

        val LOYALSOLDIER = GeoProfile(
            id = "loyalsoldier",
            geoipUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            geoipSha256Url = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat.sha256sum",
            geositeUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
            geositeSha256Url = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat.sha256sum",
        )

        val RUNETFREEDOM = GeoProfile(
            id = "runetfreedom",
            geoipUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat",
            geoipSha256Url = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat.sha256sum",
            geositeUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat",
            geositeSha256Url = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat.sha256sum",
        )

        val ALL = listOf(LOYALSOLDIER, V2FLY, RUNETFREEDOM)

        fun findById(id: String): GeoProfile =
            ALL.firstOrNull { it.id == id } ?: LOYALSOLDIER

        fun getSaved(context: Context): GeoProfile {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_GEO_PROFILE, LOYALSOLDIER.id) ?: LOYALSOLDIER.id
            return findById(id)
        }

        fun save(context: Context, profile: GeoProfile) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_GEO_PROFILE, profile.id).apply()
        }
    }
}
