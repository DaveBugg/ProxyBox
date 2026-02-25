package com.dave_cli.proxybox.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dave_cli.proxybox.core.IpCheckService
import com.dave_cli.proxybox.core.RoutingPreset
import com.dave_cli.proxybox.core.RoutingPresets
import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.data.db.SubscriptionEntity
import com.dave_cli.proxybox.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProfileRepository(AppDatabase.getInstance(app))
    private val prefs = app.getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)

    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repo.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _activePreset = MutableStateFlow(loadActivePreset())
    val activePreset: StateFlow<RoutingPreset> = _activePreset.asStateFlow()

    private val _ipCheckResults = MutableStateFlow<List<IpCheckResult>>(emptyList())
    val ipCheckResults: StateFlow<List<IpCheckResult>> = _ipCheckResults.asStateFlow()

    private val _isCheckingIp = MutableStateFlow(false)
    val isCheckingIp: StateFlow<Boolean> = _isCheckingIp.asStateFlow()

    fun selectProfile(id: String) = viewModelScope.launch { repo.selectProfile(id) }

    fun deleteProfile(profile: ProfileEntity) = viewModelScope.launch { repo.deleteProfile(profile) }

    fun addProfileFromString(input: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.addProfileFromString(input)
            onResult(ok)
        }
    }

    fun pingProfile(profile: ProfileEntity, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val latency = repo.pingProfile(profile)
            onResult(latency)
        }
    }

    fun pingAllProfiles() {
        viewModelScope.launch {
            _isPinging.value = true
            profiles.value.forEach { profile ->
                repo.pingProfile(profile)
            }
            _isPinging.value = false
        }
    }

    fun addSubscription(name: String, url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.addSubscription(name, url)
            onResult(ok)
        }
    }

    fun refreshSubscription(subId: String, url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.refreshSubscription(subId, url)
            onResult(ok)
        }
    }

    fun deleteSubscription(sub: SubscriptionEntity) = viewModelScope.launch {
        repo.deleteSubscription(sub)
    }

    fun testConnection(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val start = System.currentTimeMillis()
                    val url = URL("https://www.google.com/generate_204")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = true
                    val code = conn.responseCode
                    val elapsed = System.currentTimeMillis() - start
                    conn.disconnect()
                    if (code in 200..399) "OK — ${elapsed}ms" else "HTTP $code — ${elapsed}ms"
                } catch (e: Exception) {
                    "FAIL — ${e.message}"
                }
            }
            onResult(result)
        }
    }

    // ─── Routing Presets ─────────────────────────────────────────────────

    private fun loadActivePreset(): RoutingPreset {
        val id = prefs.getString("active_preset", "global") ?: "global"
        return RoutingPresets.findById(id)
    }

    fun setActivePreset(preset: RoutingPreset) {
        prefs.edit().putString("active_preset", preset.id).apply()
        _activePreset.value = preset
    }

    // ─── IP Check ────────────────────────────────────────────────────────

    fun checkIp() {
        val services = _activePreset.value.ipCheckServices
        if (services.isEmpty()) return

        _isCheckingIp.value = true
        _ipCheckResults.value = services.map {
            IpCheckResult(it.name, null, "Checking...", it.isRegional)
        }

        viewModelScope.launch {
            val results = services.map { service ->
                withContext(Dispatchers.IO) { fetchIp(service) }
            }
            _ipCheckResults.value = results
            _isCheckingIp.value = false
        }
    }

    private fun fetchIp(service: IpCheckService): IpCheckResult {
        return try {
            val url = URL(service.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "ProxyBox/1.0")
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code !in 200..399) {
                conn.disconnect()
                return IpCheckResult(service.name, null, "HTTP $code", service.isRegional)
            }
            val body = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()

            val ip = extractIp(body)
            if (ip != null) {
                IpCheckResult(service.name, ip, null, service.isRegional)
            } else {
                IpCheckResult(service.name, null, "Could not parse IP", service.isRegional)
            }
        } catch (e: Exception) {
            IpCheckResult(service.name, null, e.message ?: "Error", service.isRegional)
        }
    }

    private val ipRegex = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""")

    private fun extractIp(body: String): String? {
        val plainIp = body.lines().firstOrNull()?.trim()
        if (plainIp != null && ipRegex.matches(plainIp)) return plainIp
        return ipRegex.find(body)?.groupValues?.get(1)
    }
}

data class IpCheckResult(
    val serviceName: String,
    val ip: String?,
    val error: String?,
    val isRegional: Boolean
)
