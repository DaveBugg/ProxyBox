package com.dave_cli.proxybox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,          // UUID
    val name: String,
    val protocol: String,                // vless, vmess, ss, trojan, hy2, raw
    val configJson: String,              // xray outbound JSON (built from URL)
    val rawUri: String,                  // original URL/string user pasted
    val subscriptionId: String? = null,  // set if imported from subscription
    val isSelected: Boolean = false,     // currently active profile
    val latencyMs: Long = -1L,           // last ping result
    val createdAt: Long = System.currentTimeMillis()
)
