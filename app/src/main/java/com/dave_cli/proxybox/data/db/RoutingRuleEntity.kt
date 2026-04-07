package com.dave_cli.proxybox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rulesJson: String,       // v2rayN routing rules JSON array
    val ruleCount: Int = 0,
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
