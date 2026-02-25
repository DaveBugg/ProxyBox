package com.dave_cli.proxybox.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repo.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

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
}
