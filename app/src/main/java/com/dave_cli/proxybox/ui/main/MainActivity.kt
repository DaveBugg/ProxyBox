package com.dave_cli.proxybox.ui.main

import android.app.UiModeManager
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.dave_cli.proxybox.data.db.RoutingRuleEntity
import com.dave_cli.proxybox.databinding.ActivityMainBinding
import com.dave_cli.proxybox.ui.add.AddProfileActivity
import com.dave_cli.proxybox.ui.server.LocalServerActivity
import com.dave_cli.proxybox.ui.tv.TvMainActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val RC_PICK_RULE_FILE = 9001
    }

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

        binding.btnRules.setOnClickListener {
            showRulesDialog()
        }

        binding.btnUpdateGeo.setOnClickListener {
            binding.btnUpdateGeo.isEnabled = false
            viewModel.updateGeoFiles { result ->
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                binding.btnUpdateGeo.text = "Update Geo"
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

        lifecycleScope.launch {
            viewModel.isPinging.collect { pinging ->
                binding.btnPingAll.isEnabled = !pinging
                binding.btnPingAll.text = if (pinging) "Pinging..." else "Ping All"
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

    private fun showRulesDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(listContainer)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Routing Rules")
            .setView(ScrollView(this).apply { addView(container) })
            .setNeutralButton("Import from File") { _, _ -> pickRuleFile() }
            .setNegativeButton("Import from URL") { _, _ -> showImportRuleFromUrlDialog() }
            .setPositiveButton("Close", null)
            .create()

        fun refreshList() {
            listContainer.removeAllViews()
            val rules = viewModel.routingRules.value

            // "None" option
            val noneView = TextView(this@MainActivity).apply {
                text = if (rules.none { it.isSelected }) "  None (no custom rules)" else "  None"
                textSize = 14f
                setTextColor(if (rules.none { it.isSelected }) 0xFF4ADE80.toInt() else 0xFFE0E0FF.toInt())
                if (rules.none { it.isSelected }) setTypeface(null, Typeface.BOLD)
                setPadding(0, 20, 0, 20)
                setOnClickListener {
                    viewModel.selectRoutingRule(null)
                    Toast.makeText(this@MainActivity, "Custom rules disabled", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            listContainer.addView(noneView)

            // Divider
            listContainer.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0xFF2A2A4A.toInt())
            })

            for (rule in rules) {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 16, 0, 16)
                }

                val label = TextView(this@MainActivity).apply {
                    text = "${if (rule.isSelected) "  " else "  "}${rule.name} (${rule.ruleCount} rules)"
                    textSize = 14f
                    setTextColor(if (rule.isSelected) 0xFF4ADE80.toInt() else 0xFFE0E0FF.toInt())
                    if (rule.isSelected) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        viewModel.selectRoutingRule(rule.id)
                        Toast.makeText(this@MainActivity, "Activated: ${rule.name}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }

                val deleteBtn = TextView(this@MainActivity).apply {
                    text = "  X  "
                    textSize = 14f
                    setTextColor(0xFFF87171.toInt())
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete \"${rule.name}\"?")
                            .setPositiveButton("Delete") { _, _ ->
                                viewModel.deleteRoutingRule(rule)
                                Toast.makeText(this@MainActivity, "Deleted", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(300)
                                    showRulesDialog()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

                row.addView(label)
                row.addView(deleteBtn)
                listContainer.addView(row)
            }

            if (rules.isEmpty()) {
                listContainer.addView(TextView(this@MainActivity).apply {
                    text = "\nNo custom rules imported yet.\nFormat: v2rayN routing rules JSON"
                    textSize = 13f
                    setTextColor(0xFF555577.toInt())
                })
            }
        }

        refreshList()
        dialog.show()

        // Style dialog buttons
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(0xFF60A5FA.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFA78BFA.toInt())
    }

    @Suppress("DEPRECATION")
    private fun pickRuleFile() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, RC_PICK_RULE_FILE)
        } catch (e: Exception) {
            Toast.makeText(this, "No file manager available", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        android.util.Log.d("MainActivity", "onActivityResult: reqCode=$requestCode resultCode=$resultCode data=$data uri=${data?.data}")
        if (requestCode == RC_PICK_RULE_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                Toast.makeText(this, "File picker returned no data", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (json.isNullOrEmpty()) {
                    Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
                    return
                }
                showNameRuleDialog(json)
            } catch (e: Exception) {
                Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImportRuleFromUrlDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(48, 32, 48, 32)
        }
        val input = EditText(this).apply {
            hint = "https://example.com/rules.json"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
            setBackgroundColor(0xFF2A2A4A.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 15f
        }
        container.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Import Rules from URL")
            .setView(container)
            .setPositiveButton("Import") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            java.net.URL(url).readText()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (json == null) {
                        Toast.makeText(this@MainActivity, "Failed to download", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    showNameRuleDialog(json)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNameRuleDialog(json: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(48, 32, 48, 32)
        }
        val input = EditText(this).apply {
            hint = "Rule name"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
            setBackgroundColor(0xFF2A2A4A.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 15f
        }
        container.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Name this rule set")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Custom Rules" }
                viewModel.addRoutingRule(name, json) { error ->
                    if (error == null) {
                        Toast.makeText(this, "Rule set \"$name\" added!", Toast.LENGTH_SHORT).show()
                        // Delay to let Room Flow propagate the update to StateFlow
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(300)
                            showRulesDialog()
                        }
                    } else {
                        Toast.makeText(this, "Invalid: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
