package com.dave_cli.proxybox.ui.tv

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
import android.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.core.RoutingPresets
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

        adapter = TvProfileAdapter(
            context = this,
            onClick = { profile ->
                viewModel.selectProfile(profile.id)
                if (CoreService.isActive) {
                    reconnectVpn()
                }
            },
            onLongClick = { profile, _ ->
                showDeleteDialog(profile.name, profile)
            }
        )

        binding.lvProfiles.apply {
            this.adapter = this@TvMainActivity.adapter
            itemsCanFocus = true
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
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

        setupPresetSpinner()
        setupIpCheck()

        lifecycleScope.launch {
            viewModel.isPinging.collect { pinging ->
                binding.btnPingAll.isEnabled = !pinging
                binding.btnPingAll.text = if (pinging) "Pinging..." else "Ping All"
            }
        }

        observeProfiles()
        observeVpnState()
    }

    private fun showDeleteDialog(name: String, profile: com.dave_cli.proxybox.data.db.ProfileEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Profile")
            .setMessage("Delete \"$name\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteProfile(profile)
                Toast.makeText(this, "Deleted: ${profile.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                    textSize = 14f
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(0xFFE0E0FF.toInt())
                    setBackgroundColor(0xFF2A2A4A.toInt())
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
                            this@TvMainActivity,
                            "Reconnect VPN to apply preset",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupIpCheck() {
        binding.btnIpCheck.setOnClickListener {
            viewModel.checkIp()
            showIpCheckDialog()
        }

        binding.btnUpdateGeo.setOnClickListener {
            binding.btnUpdateGeo.isEnabled = false
            viewModel.updateGeoFiles { result ->
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                binding.btnUpdateGeo.text = "Update Geo DB"
                binding.btnUpdateGeo.isEnabled = true
            }
        }

        lifecycleScope.launch {
            viewModel.geoProgress.collect { progress ->
                if (progress.isNotEmpty()) {
                    binding.btnUpdateGeo.text = progress
                }
            }
        }

        binding.btnUpdateApp.setOnClickListener {
            binding.btnUpdateApp.isEnabled = false
            binding.btnUpdateApp.text = "Checking..."
            viewModel.checkForUpdate { result ->
                binding.btnUpdateApp.text = "Update App"
                binding.btnUpdateApp.isEnabled = true
                if (result.hasUpdate && result.downloadUrl != null) {
                    showUpdateDialog(result.latestVersion, result.releaseNotes, result.downloadUrl)
                } else if (result.hasUpdate) {
                    Toast.makeText(this, "Update ${result.latestVersion} found but no APK attached", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "You're on the latest version (${result.latestVersion})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(version: String, notes: String?, downloadUrl: String) {
        val message = buildString {
            appendLine("New version: v$version")
            if (!notes.isNullOrBlank()) {
                appendLine()
                append(notes)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Download & Install") { _, _ ->
                viewModel.downloadAndInstallUpdate(this, downloadUrl, version)
                Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showIpCheckDialog() {
        val tv = TextView(this).apply {
            text = "Checking..."
            setTextColor(0xFFE0E0FF.toInt())
            setPadding(48, 32, 48, 32)
            textSize = 16f
        }

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
                        val label = if (r.isRegional) "Regional: ${r.serviceName}" else "Global: ${r.serviceName}"
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
                val focusedPos = binding.lvProfiles.selectedItemPosition
                adapter.clear()
                adapter.addAll(profiles)
                adapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE

                if (profiles.isNotEmpty() && focusedPos >= 0) {
                    val safePos = focusedPos.coerceAtMost(profiles.size - 1)
                    binding.lvProfiles.post {
                        binding.lvProfiles.setSelection(safePos)
                    }
                }
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
