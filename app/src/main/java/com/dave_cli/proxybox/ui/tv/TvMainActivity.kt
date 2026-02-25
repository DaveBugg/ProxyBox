package com.dave_cli.proxybox.ui.tv

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.databinding.ActivityTvMainBinding
import com.dave_cli.proxybox.ui.main.MainActivity
import com.dave_cli.proxybox.ui.main.MainViewModel
import com.dave_cli.proxybox.ui.server.LocalServerActivity
import kotlinx.coroutines.launch

class TvMainActivity : FragmentActivity() {

    private lateinit var binding: ActivityTvMainBinding
    private lateinit var viewModel: MainViewModel

    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
    }

    private lateinit var adapter: TvProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isTV()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        adapter = TvProfileAdapter(this) { profile ->
            viewModel.selectProfile(profile.id)
            if (CoreService.isActive) {
                reconnectVpn()
            }
        }

        binding.lvProfiles.adapter = adapter
        binding.lvProfiles.setOnItemClickListener { _, _, pos, _ ->
            adapter.getItem(pos)?.let { profile ->
                viewModel.selectProfile(profile.id)
                if (CoreService.isActive) {
                    reconnectVpn()
                }
            }
        }

        binding.btnConnect.setOnClickListener {
            if (CoreService.isActive) stopVpn() else requestVpnPermission()
        }

        binding.btnAddConfig.setOnClickListener {
            startActivity(Intent(this, LocalServerActivity::class.java))
        }

        binding.btnPingAll.setOnClickListener {
            viewModel.pingAllProfiles()
        }

        binding.btnTestConnection.setOnClickListener {
            binding.btnTestConnection.isEnabled = false
            binding.btnTestConnection.text = "Testing..."
            viewModel.testConnection { result ->
                binding.btnTestConnection.text = result
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.postDelayed({
                    binding.btnTestConnection.text = "Test Connection"
                }, 4000)
            }
        }

        lifecycleScope.launch {
            viewModel.isPinging.collect { pinging ->
                binding.btnPingAll.isEnabled = !pinging
                binding.btnPingAll.text = if (pinging) "Pinging..." else "Ping All"
            }
        }

        observeProfiles()
        observeVpnState()
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            viewModel.profiles.collect { profiles ->
                adapter.clear()
                adapter.addAll(profiles)
                adapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            CoreService.vpnState.collect { state ->
                when (state) {
                    VpnState.CONNECTED -> {
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = "Connected: ${CoreService.activeProfileName ?: ""}"
                        binding.tvStatus.setTextColor(0xFF4ADE80.toInt())
                    }
                    VpnState.CONNECTING -> {
                        binding.btnConnect.text = "Connecting..."
                        binding.btnConnect.isEnabled = false
                        binding.tvStatus.text = "Connecting..."
                        binding.tvStatus.setTextColor(0xFFAAAACC.toInt())
                    }
                    VpnState.DISCONNECTED -> {
                        binding.btnConnect.text = "Connect"
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = "Not connected"
                        binding.tvStatus.setTextColor(0xFF888888.toInt())
                    }
                    VpnState.ERROR -> {
                        binding.btnConnect.text = "Connect"
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = "Connection failed"
                        binding.tvStatus.setTextColor(0xFFF87171.toInt())
                    }
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermLauncher.launch(intent) else startVpn()
    }

    private fun startVpn() {
        lifecycleScope.launch {
            val selected = viewModel.profiles.value.firstOrNull { it.isSelected }
                ?: viewModel.profiles.value.firstOrNull()

            if (selected == null) {
                Toast.makeText(this@TvMainActivity, "Add a profile first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!selected.isSelected) {
                viewModel.selectProfile(selected.id)
            }
            val si = Intent(this@TvMainActivity, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            startForegroundService(si)
        }
    }

    private fun reconnectVpn() {
        stopVpn()
        binding.btnConnect.postDelayed({
            startVpn()
        }, 500)
    }

    private fun stopVpn() {
        val si = Intent(this, CoreService::class.java).apply { action = CoreService.ACTION_STOP }
        startService(si)
    }

    private fun isTV(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
