package com.dave_cli.proxybox.data.repository

import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.data.db.SubscriptionEntity
import com.dave_cli.proxybox.import_config.ConfigParser
import com.dave_cli.proxybox.import_config.SubscriptionParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProfileRepository(db: AppDatabase) {

    private val profileDao = db.profileDao()
    private val subscriptionDao = db.subscriptionDao()
    private val gson = Gson()

    val profiles: Flow<List<ProfileEntity>> = profileDao.getAllProfiles()
    val subscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    suspend fun addProfileFromString(input: String): Boolean = withContext(Dispatchers.IO) {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) {
            var added = 0
            for (line in lines) {
                val profile = ConfigParser.parse(line) ?: continue
                profileDao.insertOrReplace(profile)
                added++
            }
            return@withContext added > 0
        }
        val profile = ConfigParser.parse(input) ?: return@withContext false
        profileDao.insertOrReplace(profile)
        true
    }

    suspend fun deleteProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        profileDao.delete(profile)
    }

    suspend fun selectProfile(id: String) = withContext(Dispatchers.IO) {
        profileDao.clearSelection()
        profileDao.selectProfile(id)
    }

    suspend fun getSelectedProfile() = withContext(Dispatchers.IO) {
        profileDao.getSelectedProfile()
    }

    suspend fun pingProfile(profile: ProfileEntity): Long = withContext(Dispatchers.IO) {
        try {
            val outbound = gson.fromJson(profile.configJson, JsonObject::class.java)
            val (host, port) = extractHostPort(outbound)
            if (host.isEmpty()) return@withContext -1L

            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5000)
            }
            val latency = System.currentTimeMillis() - start
            profileDao.updateLatency(profile.id, latency)
            latency
        } catch (e: Exception) {
            profileDao.updateLatency(profile.id, -1L)
            -1L
        }
    }

    private fun extractHostPort(outbound: JsonObject): Pair<String, Int> {
        val settings = outbound.getAsJsonObject("settings") ?: return "" to 443
        val vnext = settings.getAsJsonArray("vnext")
        if (vnext != null && vnext.size() > 0) {
            val server = vnext[0].asJsonObject
            return (server.get("address")?.asString ?: "") to (server.get("port")?.asInt ?: 443)
        }
        val servers = settings.getAsJsonArray("servers")
        if (servers != null && servers.size() > 0) {
            val server = servers[0].asJsonObject
            return (server.get("address")?.asString ?: "") to (server.get("port")?.asInt ?: 443)
        }
        return "" to 443
    }

    suspend fun addSubscription(name: String, url: String): Boolean = withContext(Dispatchers.IO) {
        val sub = SubscriptionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url
        )
        subscriptionDao.insertOrReplace(sub)
        refreshSubscription(sub.id, url)
    }

    suspend fun refreshSubscription(subId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val content = client.newCall(request).execute().body?.string() ?: return@withContext false

            val profiles = SubscriptionParser.parse(content, subId)
            profileDao.deleteBySubscription(subId)
            profileDao.insertAll(profiles)
            subscriptionDao.updateMeta(subId, System.currentTimeMillis(), profiles.size)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteSubscription(sub: SubscriptionEntity) = withContext(Dispatchers.IO) {
        profileDao.deleteBySubscription(sub.id)
        subscriptionDao.delete(sub)
    }
}
