package com.dave_cli.proxybox.import_config

import android.util.Base64
import com.dave_cli.proxybox.data.db.ProfileEntity
import java.util.UUID

/**
 * Fetches a subscription URL (base64 or plain list) and returns parsed profiles.
 * Subscription format: base64-encoded list of proxy URLs, one per line.
 */
object SubscriptionParser {

    /**
     * Parse raw subscription content (string already fetched from URL).
     * Tries base64 decode first; falls back to treating as plain text.
     */
    fun parse(content: String, subscriptionId: String): List<ProfileEntity> {
        val lines = decode(content)
        return lines
            .mapNotNull { line ->
                val profile = ConfigParser.parse(line)
                profile?.copy(
                    id = UUID.randomUUID().toString(),
                    subscriptionId = subscriptionId
                )
            }
    }

    private fun decode(content: String): List<String> {
        val trimmed = content.trim()
        // Try base64 decode
        return try {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT or Base64.NO_WRAP))
            decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            // Plain text — split by newlines
            trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
