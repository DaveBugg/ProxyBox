package com.dave_cli.proxybox.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.data.db.AppDatabase
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dave_cli.proxybox.widget.VpnWidgetProvider
import java.security.SecureRandom

class CoreService : VpnService() {

    enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    companion object {
        const val TAG = "CoreService"
        const val CHANNEL_ID = "proxybox_vpn"
        const val NOTIF_ID = 1

        const val ACTION_START = "com.dave_cli.proxybox.START"
        const val ACTION_STOP = "com.dave_cli.proxybox.STOP"

        private const val PRIVATE_VLAN4_CLIENT = "26.26.26.1"
        private const val PRIVATE_VLAN4_ROUTER = "26.26.26.2"
        private const val PRIVATE_VLAN6_CLIENT = "fd00::1"
        const val SOCKS_PORT = 39271
        private const val TUN_MTU = 9000

        private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        val isActive: Boolean get() = _vpnState.value == VpnState.CONNECTED

        var activeProfileName: String? = null
            private set

        // Per-session credentials for the local SOCKS inbound. Regenerated on every
        // connect and cleared on stop. Used to prevent other apps on the device from
        // freely using our local SOCKS proxy (see CVE-style issue in similar clients).
        @Volatile
        var socksUser: String? = null
            private set

        @Volatile
        var socksPass: String? = null
            private set

        @Volatile
        var connectionStartTime: Long = 0L
            private set

        private fun generateSocksCreds() {
            val rnd = SecureRandom()
            val bytes = ByteArray(16)
            rnd.nextBytes(bytes)
            val user = bytes.joinToString("") { "%02x".format(it) }
            rnd.nextBytes(bytes)
            val pass = bytes.joinToString("") { "%02x".format(it) }
            socksUser = user
            socksPass = pass
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var tunInterface: ParcelFileDescriptor? = null
    private val engine: ProxyEngine = XrayManager

    private val connectivity by lazy {
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopConnection()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildConnectingNotification())
                startConnection()
            }
        }
        return START_STICKY
    }

    private fun startConnection() {
        _vpnState.value = VpnState.CONNECTING
        scope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val profile = db.profileDao().getSelectedProfile()
                if (profile == null) {
                    Log.e(TAG, "No selected profile, stopping service")
                    _vpnState.value = VpnState.ERROR
                    stopSelf()
                    return@launch
                }

                activeProfileName = profile.name
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(profile))

                val tun = setupTunInterface()
                if (tun == null) {
                    Log.e(TAG, "Failed to create TUN interface")
                    _vpnState.value = VpnState.ERROR
                    stopSelf()
                    return@launch
                }
                tunInterface = tun

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        connectivity.unregisterNetworkCallback(defaultNetworkCallback)
                    } catch (_: Exception) { /* not registered yet */ }
                    try {
                        connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request default network callback", e)
                    }
                }

                val prefsBox = getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)
                val presetId = prefsBox.getString("active_preset", "global") ?: "global"
                val preset = RoutingPresets.findById(presetId)
                Log.i(TAG, "Using routing preset: ${preset.displayName}")

                generateSocksCreds()
                val selectedRule = db.routingRuleDao().getSelectedRule()
                val customRulesJson = selectedRule?.rulesJson
                if (selectedRule != null) {
                    Log.i(TAG, "Using custom routing rule: ${selectedRule.name} (${selectedRule.ruleCount} rules)")
                }
                val configJson = ConfigBuilder.build(profile, preset, socksUser, socksPass, customRulesJson)
                Log.d(TAG, "Starting engine with config length: ${configJson.length}")

                val tunFd = tun.fd
                val useTun2Socks = Tun2SocksManager.isAvailable()
                Log.i(TAG, "tun2socks available: $useTun2Socks")

                val coreTunFd = if (useTun2Socks) 0 else tunFd
                val ok = if (engine is XrayManager) {
                    engine.start(configJson, this@CoreService, coreTunFd)
                } else {
                    engine.start(configJson, this@CoreService)
                }
                if (!ok) {
                    Log.e(TAG, "Engine failed to start")
                    tunInterface?.close()
                    tunInterface = null
                    activeProfileName = null
                    _vpnState.value = VpnState.ERROR
                    VpnWidgetProvider.updateAll(applicationContext)
                    stopSelf()
                } else {
                    if (useTun2Socks) {
                        Tun2SocksManager.start(
                            applicationContext,
                            tunFd,
                            socksUser = socksUser,
                            socksPass = socksPass,
                        )
                    }

                    _vpnState.value = VpnState.CONNECTED
                    connectionStartTime = System.currentTimeMillis()
                    VpnWidgetProvider.updateAll(applicationContext)
                    Log.i(TAG, "CoreService started with profile: ${profile.name}")

                    val prefs = getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("auto_start", true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting connection", e)
                _vpnState.value = VpnState.ERROR
                stopSelf()
            }
        }
    }

    private fun setupTunInterface(): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setBlocking(false)
                .setSession("ProxyBox")
                .setMtu(TUN_MTU)
                .addAddress(PRIVATE_VLAN4_CLIENT, 30)
                .addRoute("0.0.0.0", 0)
                .addAddress(PRIVATE_VLAN6_CLIENT, 128)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            // Split tunneling
            val prefs = getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)
            val packages = prefs.getStringSet("split_tunnel_packages", emptySet()) ?: emptySet()
            val mode = prefs.getString("split_tunnel_mode", "BYPASS") ?: "BYPASS"

            if (packages.isNotEmpty() && mode == "ONLY" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (pkg in packages) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to allow app: $pkg", e)
                    }
                }
                Log.i(TAG, "Split tunnel ONLY mode: ${packages.size} apps")
            } else {
                builder.addDisallowedApplication(packageName)
                if (packages.isNotEmpty() && mode == "BYPASS") {
                    for (pkg in packages) {
                        try {
                            builder.addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to bypass app: $pkg", e)
                        }
                    }
                    Log.i(TAG, "Split tunnel BYPASS mode: ${packages.size} apps excluded")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish TUN", e)
            null
        }
    }

    private fun stopConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback", e)
            }
        }

        Tun2SocksManager.stop()
        engine.stop()
        tunInterface?.close()
        tunInterface = null
        activeProfileName = null
        connectionStartTime = 0L
        socksUser = null
        socksPass = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        _vpnState.value = VpnState.DISCONNECTED
        VpnWidgetProvider.updateAll(applicationContext)

        val prefs = getSharedPreferences("proxybox_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_start", false).apply()

        Log.i(TAG, "CoreService stopped")
    }

    override fun onRevoke() {
        stopConnection()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopConnection()
        job.cancel()
        super.onDestroy()
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun buildConnectingNotification(): Notification {
        createChannel()
        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_connecting))
            .setContentIntent(contentPi)
            .setOngoing(true)
            .build()
    }

    private fun buildNotification(profile: ProfileEntity): Notification {
        createChannel()

        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CoreService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(getString(R.string.notif_connected))
            .setContentText("${profile.name} (${profile.protocol.uppercase()})")
            .setContentIntent(contentPi)
            .addAction(R.drawable.ic_stop, getString(R.string.disconnect), stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
