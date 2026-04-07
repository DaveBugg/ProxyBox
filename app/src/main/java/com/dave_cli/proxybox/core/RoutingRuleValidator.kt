package com.dave_cli.proxybox.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException

object RoutingRuleValidator {

    private val gson = Gson()
    private val validOutboundTags = setOf("proxy", "direct", "block")

    data class ValidationResult(
        val valid: Boolean,
        val rulesJson: String = "[]",
        val ruleCount: Int = 0,
        val error: String? = null
    )

    /**
     * Validates v2rayN routing rules JSON.
     * Expected format: JSON array of objects with { type, outboundTag, domain/ip, ... }
     */
    fun validate(json: String): ValidationResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult(false, error = "Empty input")
        }

        val array: JsonArray = try {
            gson.fromJson(trimmed, JsonArray::class.java)
                ?: return ValidationResult(false, error = "Not a JSON array")
        } catch (e: JsonSyntaxException) {
            return ValidationResult(false, error = "Invalid JSON: ${e.message}")
        }

        if (array.size() == 0) {
            return ValidationResult(false, error = "Empty rules array")
        }

        val cleaned = JsonArray()
        for (i in 0 until array.size()) {
            val element = array[i]
            if (!element.isJsonObject) {
                return ValidationResult(false, error = "Rule #${i + 1} is not an object")
            }
            val rule = element.asJsonObject

            if (!rule.has("type") || rule.get("type").asString != "field") {
                return ValidationResult(false, error = "Rule #${i + 1}: missing or invalid 'type' (must be 'field')")
            }

            if (!rule.has("outboundTag")) {
                return ValidationResult(false, error = "Rule #${i + 1}: missing 'outboundTag'")
            }

            // Skip disabled rules
            if (rule.has("enabled") && !rule.get("enabled").asBoolean) {
                continue
            }

            val tag = rule.get("outboundTag").asString
            if (tag !in validOutboundTags) {
                return ValidationResult(false, error = "Rule #${i + 1}: invalid outboundTag '$tag' (must be proxy/direct/block)")
            }

            val hasDomain = rule.has("domain") && rule.getAsJsonArray("domain")?.size()?.let { it > 0 } == true
            val hasIp = rule.has("ip") && rule.getAsJsonArray("ip")?.size()?.let { it > 0 } == true
            val hasPort = rule.has("port")

            if (!hasDomain && !hasIp && !hasPort) {
                return ValidationResult(false, error = "Rule #${i + 1}: must have 'domain', 'ip', or 'port'")
            }

            // Keep only meaningful fields
            val clean = JsonObject().apply {
                addProperty("type", "field")
                addProperty("outboundTag", tag)
                if (hasDomain) add("domain", rule.getAsJsonArray("domain"))
                if (hasIp) add("ip", rule.getAsJsonArray("ip"))
                if (hasPort) add("port", rule.get("port"))
                if (rule.has("network")) add("network", rule.get("network"))
            }
            cleaned.add(clean)
        }

        return ValidationResult(
            valid = true,
            rulesJson = gson.toJson(cleaned),
            ruleCount = cleaned.size()
        )
    }
}
