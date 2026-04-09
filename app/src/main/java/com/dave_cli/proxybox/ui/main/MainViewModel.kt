package com.dave_cli.proxybox.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dave_cli.proxybox.core.IpCheckService
import com.dave_cli.proxybox.core.RoutingPreset
import com.dave_cli.proxybox.core.RoutingPresets
import com.dave_cli.proxybox.core.UpdateChecker
import com.dave_cli.proxybox.core.UpdateResult
import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.data.db.RoutingRuleEntity
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
import com.dave_cli.proxybox.core.CoreService
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProfileRepository(AppDatabase.getInstance(app))
    private val prefs = app.getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)

    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repo.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routingRules: StateFlow<List<RoutingRuleEntity>> = repo.routingRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _activePreset = MutableStateFlow(loadActivePreset())
    val activePreset: StateFlow<RoutingPreset> = _activePreset.asStateFlow()

    private val _ipCheckResults = MutableStateFlow<List<IpCheckResult>>(emptyList())
    val ipCheckResults: StateFlow<List<IpCheckResult>> = _ipCheckResults.asStateFlow()

    private val _isCheckingIp = MutableStateFlow(false)
    val isCheckingIp: StateFlow<Boolean> = _isCheckingIp.asStateFlow()

    init {
        // The local SOCKS inbound is now password-protected. When we route our own
        // HTTP requests (IP check, test connection) through 127.0.0.1:39271, the JDK
        // SOCKS client asks this Authenticator for credentials.
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestingProtocol?.equals("SOCKS5", ignoreCase = true) != true) {
                    return null
                }
                val user = CoreService.socksUser ?: return null
                val pass = CoreService.socksPass ?: return null
                return PasswordAuthentication(user, pass.toCharArray())
            }
        })
    }

    fun selectProfile(id: String) = viewModelScope.launch { repo.selectProfile(id) }

    fun deleteProfile(profile: ProfileEntity) = viewModelScope.launch { repo.deleteProfile(profile) }

    fun renameProfile(id: String, newName: String) = viewModelScope.launch { repo.renameProfile(id, newName) }

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
                    val conn = url.openConnection(vpnProxy()) as HttpURLConnection
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

    // ─── Speed Test ────────────────────────────────────────────────────

    fun runSpeedTest(onResult: (Double?, String?) -> Unit) {
        viewModelScope.launch {
            val selected = profiles.value.firstOrNull { it.isSelected }
            if (selected == null) {
                onResult(null, "No profile selected")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                com.dave_cli.proxybox.core.SpeedTestEngine.run(selected)
            }
            onResult(result.downloadMbps, result.error)
        }
    }

    // ─── Routing Rules ─────────────────────────────────────────────────

    fun addRoutingRule(name: String, json: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = repo.addRoutingRule(name, json)
            onResult(error)
        }
    }

    fun deleteRoutingRule(rule: RoutingRuleEntity) = viewModelScope.launch {
        repo.deleteRoutingRule(rule)
    }

    fun selectRoutingRule(id: String?) = viewModelScope.launch {
        repo.selectRoutingRule(id)
    }

    // ─── Geo Update ────────────────────────────────────────────────────

    private val _isUpdatingGeo = MutableStateFlow(false)
    val isUpdatingGeo: StateFlow<Boolean> = _isUpdatingGeo.asStateFlow()

    private val _geoProgress = MutableStateFlow("")
    val geoProgress: StateFlow<String> = _geoProgress.asStateFlow()

    fun updateGeoFiles(onResult: (String) -> Unit) {
        if (_isUpdatingGeo.value) return
        _isUpdatingGeo.value = true
        _geoProgress.value = "Connecting..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val updated = com.dave_cli.proxybox.core.GeoFileManager.updateAll(
                        getApplication()
                    ) { fileName, bytesRead, totalBytes ->
                        val label = if (fileName == "geoip.dat") "GeoIP" else "GeoSite"
                        val text = when {
                            bytesRead == -1L -> "$label: verifying..."
                            totalBytes > 0 -> {
                                val pct = (bytesRead * 100 / totalBytes).toInt()
                                val mb = "%.1f".format(bytesRead / 1_048_576.0)
                                val totalMb = "%.1f".format(totalBytes / 1_048_576.0)
                                "$label: $mb / $totalMb MB ($pct%)"
                            }
                            else -> {
                                val mb = "%.1f".format(bytesRead / 1_048_576.0)
                                "$label: ${mb} MB"
                            }
                        }
                        _geoProgress.value = text
                    }
                    if (updated) "Geo files updated" else "Already up to date"
                } catch (e: Exception) {
                    "Update failed: ${e.message}"
                }
            }
            _geoProgress.value = ""
            _isUpdatingGeo.value = false
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

    private fun vpnProxy(): Proxy =
        if (CoreService.isActive)
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", CoreService.SOCKS_PORT))
        else
            Proxy.NO_PROXY

    private fun fetchIp(service: IpCheckService): IpCheckResult {
        val urls = listOf(service.url) + service.fallbackUrls
        var lastError: String = "No URLs"
        val proxy = if (!service.isRegional) vpnProxy() else Proxy.NO_PROXY

        for (urlStr in urls) {
            try {
                val conn = URL(urlStr).openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ProxyBox/1.0")
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code !in 200..399) {
                    conn.disconnect()
                    lastError = "HTTP $code ($urlStr)"
                    continue
                }
                val body = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()

                val ip = extractIp(body)
                if (ip != null) {
                    return IpCheckResult(service.name, ip, null, service.isRegional)
                }
                lastError = "Could not parse IP ($urlStr)"
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        return IpCheckResult(service.name, null, lastError, service.isRegional)
    }

    // ─── App Update ────────────────────────────────────────────────────

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    fun checkForUpdate(onResult: (UpdateResult) -> Unit) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            _isCheckingUpdate.value = false
            onResult(result)
        }
    }

    fun downloadAndInstallUpdate(context: Context, url: String, version: String) {
        UpdateChecker.downloadAndInstall(context, url, version)
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
