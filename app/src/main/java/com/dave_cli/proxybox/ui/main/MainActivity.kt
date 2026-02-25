package com.dave_cli.proxybox.ui.main

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.core.RoutingPresets
import com.dave_cli.proxybox.databinding.ActivityMainBinding
import com.dave_cli.proxybox.ui.add.AddProfileActivity
import com.dave_cli.proxybox.ui.server.LocalServerActivity
import com.dave_cli.proxybox.ui.tv.TvMainActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
    }

    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isTV()) {
            startActivity(Intent(this, TvMainActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupPresetSpinner()
        setupButtons()
        observeProfiles()
        observeVpnState()
    }

    private fun isTV(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter(
            onSelect = { profile -> viewModel.selectProfile(profile.id) },
            onDelete = { profile ->
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${profile.name}\"?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteProfile(profile) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter
    }

    private fun setupPresetSpinner() {
        val presets = RoutingPresets.ALL
        val spinnerAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item,
            presets.map { it.displayName }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(0xFFE0E0FF.toInt())
                    textSize = 13f
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(0xFFE0E0FF.toInt())
                    setBackgroundColor(0xFF1A1A2E.toInt())
                    setPadding(24, 20, 24, 20)
                }
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPreset.adapter = spinnerAdapter

        val activeIdx = presets.indexOfFirst { it.id == viewModel.activePreset.value.id }
        if (activeIdx >= 0) binding.spinnerPreset.setSelection(activeIdx, false)

        binding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val selected = presets[pos]
                if (selected.id != viewModel.activePreset.value.id) {
                    viewModel.setActivePreset(selected)
                    if (CoreService.isActive) {
                        Toast.makeText(
                            this@MainActivity,
                            "Reconnect VPN to apply preset",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            if (CoreService.isActive) {
                stopVpn()
            } else {
                requestVpnPermission()
            }
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddProfileActivity::class.java))
        }

        binding.btnLocalServer.setOnClickListener {
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
                    binding.btnTestConnection.text = "Test"
                }, 4000)
            }
        }

        binding.btnIpCheck.setOnClickListener {
            viewModel.checkIp()
            showIpCheckDialog()
        }

        lifecycleScope.launch {
            viewModel.isPinging.collect { pinging ->
                binding.btnPingAll.isEnabled = !pinging
                binding.btnPingAll.text = if (pinging) "Pinging..." else "Ping All"
            }
        }
    }

    private fun showIpCheckDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val tv = dialogView as TextView
        tv.text = "Checking..."
        tv.setTextColor(0xFFE0E0FF.toInt())
        tv.setPadding(48, 32, 48, 32)
        tv.textSize = 14f

        AlertDialog.Builder(this)
            .setTitle("IP Check — ${viewModel.activePreset.value.displayName}")
            .setView(tv)
            .setPositiveButton("Close", null)
            .show()

        lifecycleScope.launch {
            viewModel.ipCheckResults.collect { results ->
                if (results.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (r in results) {
                        val label = if (r.isRegional) "\uD83C\uDFE0 ${r.serviceName}" else "\uD83C\uDF10 ${r.serviceName}"
                        val value = r.ip ?: r.error ?: "—"
                        sb.appendLine("$label\n  $value\n")
                    }
                    tv.text = sb.toString().trim()
                }
            }
        }
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            viewModel.profiles.collect { profiles ->
                adapter.submitList(profiles)
                binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            CoreService.vpnState.collect { state ->
                updateUiForState(state)
            }
        }
    }

    private fun updateUiForState(state: VpnState) {
        when (state) {
            VpnState.CONNECTED -> {
                binding.btnConnect.text = "Disconnect"
                binding.btnConnect.isEnabled = true
                binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                binding.tvStatus.text = "Connected — ${CoreService.activeProfileName ?: ""}"
                binding.tvStatus.setTextColor(0xFF4ADE80.toInt())
            }
            VpnState.CONNECTING -> {
                binding.btnConnect.text = "Connecting..."
                binding.btnConnect.isEnabled = false
                binding.statusDot.setBackgroundResource(R.drawable.dot_grey)
                binding.tvStatus.text = "Connecting..."
                binding.tvStatus.setTextColor(0xFFAAAACC.toInt())
            }
            VpnState.DISCONNECTED -> {
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.statusDot.setBackgroundResource(R.drawable.dot_grey)
                binding.tvStatus.text = "Not connected"
                binding.tvStatus.setTextColor(0xFFAAAACC.toInt())
            }
            VpnState.ERROR -> {
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.statusDot.setBackgroundResource(R.drawable.dot_grey)
                binding.tvStatus.text = "Connection failed"
                binding.tvStatus.setTextColor(0xFFF87171.toInt())
                Toast.makeText(this, "VPN connection failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        lifecycleScope.launch {
            val selected = viewModel.profiles.value.firstOrNull { it.isSelected }
                ?: viewModel.profiles.value.firstOrNull()

            if (selected == null) {
                Toast.makeText(this@MainActivity, "Add a profile first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!selected.isSelected) {
                viewModel.selectProfile(selected.id)
            }
            val si = Intent(this@MainActivity, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            startForegroundService(si)
        }
    }

    private fun stopVpn() {
        val si = Intent(this, CoreService::class.java).apply { action = CoreService.ACTION_STOP }
        startService(si)
    }
}
