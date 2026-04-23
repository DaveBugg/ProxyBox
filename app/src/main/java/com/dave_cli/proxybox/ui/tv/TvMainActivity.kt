package com.dave_cli.proxybox.ui.tv

import android.app.UiModeManager
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.content.Context
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.LocaleHelper
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.core.RoutingPresets
import com.dave_cli.proxybox.databinding.ActivityTvMainBinding
import com.dave_cli.proxybox.ui.main.MainActivity
import com.dave_cli.proxybox.ui.main.MainViewModel
import com.dave_cli.proxybox.ui.main.SplitTunnelMode
import com.dave_cli.proxybox.ui.server.LocalServerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvMainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

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
            binding.btnTestConnection.text = getString(R.string.testing)
            viewModel.testConnection { result ->
                binding.btnTestConnection.text = result
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.postDelayed({
                    binding.btnTestConnection.text = getString(R.string.test_connection)
                }, 4000)
            }
        }

        setupPresetSpinner()
        setupIpCheck()

        binding.btnSpeedTest.setOnClickListener {
            binding.btnSpeedTest.isEnabled = false
            binding.btnSpeedTest.text = getString(R.string.testing)
            viewModel.runSpeedTest { mbps, error ->
                if (mbps != null) {
                    binding.btnSpeedTest.text = getString(R.string.speed_result_mbps, mbps)
                } else {
                    binding.btnSpeedTest.text = error ?: getString(R.string.speed_failed)
                }
                binding.btnSpeedTest.isEnabled = true
                binding.btnSpeedTest.postDelayed({
                    binding.btnSpeedTest.text = getString(R.string.speed_test)
                }, 5000)
            }
        }

        binding.btnRules.setOnClickListener {
            showRulesDialog()
        }

        binding.btnSplitTunnel.setOnClickListener {
            showSplitTunnelDialog()
        }

        updateLanguageButton()
        binding.btnLanguage.setOnClickListener {
            val current = LocaleHelper.getSavedLanguage(this)
            val next = when (current) {
                "" -> "en"
                "en" -> "ru"
                else -> ""
            }
            LocaleHelper.saveLanguage(this, next)
            recreate()
        }

        lifecycleScope.launch {
            viewModel.isPinging.collect { pinging ->
                binding.btnPingAll.isEnabled = !pinging
                binding.btnPingAll.text = if (pinging) getString(R.string.pinging) else getString(R.string.ping_all)
            }
        }

        observeProfiles()
        observeVpnState()
    }

    private fun showDeleteDialog(name: String, profile: com.dave_cli.proxybox.data.db.ProfileEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_profile))
            .setMessage(getString(R.string.delete_confirm, name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteProfile(profile)
                Toast.makeText(this, getString(R.string.deleted_toast, profile.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
                            getString(R.string.reconnect_preset_toast),
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
                binding.btnUpdateGeo.text = getString(R.string.update_geo)
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
            binding.btnUpdateApp.text = getString(R.string.checking)
            viewModel.checkForUpdate { result ->
                binding.btnUpdateApp.text = getString(R.string.update_app)
                binding.btnUpdateApp.isEnabled = true
                if (result.hasUpdate && result.downloadUrl != null) {
                    showUpdateDialog(result.latestVersion, result.releaseNotes, result.downloadUrl)
                } else if (result.hasUpdate) {
                    Toast.makeText(this, getString(R.string.update_found_no_apk, result.latestVersion), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.on_latest_version, result.latestVersion), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(version: String, notes: String?, downloadUrl: String) {
        val message = buildString {
            appendLine(getString(R.string.new_version, version))
            if (!notes.isNullOrBlank()) {
                appendLine()
                append(notes)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available))
            .setMessage(message)
            .setPositiveButton(getString(R.string.download_install)) { _, _ ->
                viewModel.downloadAndInstallUpdate(this, downloadUrl, version)
                Toast.makeText(this, getString(R.string.downloading_update), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.later), null)
            .show()
    }

    private fun showIpCheckDialog() {
        val tv = TextView(this).apply {
            text = getString(R.string.checking)
            setTextColor(0xFFE0E0FF.toInt())
            setPadding(48, 32, 48, 32)
            textSize = 16f
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ip_check_title, viewModel.activePreset.value.displayName))
            .setView(tv)
            .setPositiveButton(getString(R.string.close), null)
            .show()

        lifecycleScope.launch {
            viewModel.ipCheckResults.collect { results ->
                if (results.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (r in results) {
                        val label = if (r.isRegional) getString(R.string.ip_regional, r.serviceName) else getString(R.string.ip_global, r.serviceName)
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
            .setTitle(getString(R.string.routing_rules))
            .setView(ScrollView(this).apply { addView(container) })
            .setNeutralButton(getString(R.string.add_via_phone)) { _, _ ->
                startActivity(Intent(this, LocalServerActivity::class.java))
            }
            .setPositiveButton(getString(R.string.close), null)
            .create()

        fun refreshList() {
            listContainer.removeAllViews()
            val rules = viewModel.routingRules.value

            val noneView = TextView(this@TvMainActivity).apply {
                text = if (rules.none { it.isSelected }) "  ${getString(R.string.none_no_custom_rules)}" else "  ${getString(R.string.none)}"
                textSize = 16f
                setTextColor(if (rules.none { it.isSelected }) 0xFF4ADE80.toInt() else 0xFFE0E0FF.toInt())
                if (rules.none { it.isSelected }) setTypeface(null, Typeface.BOLD)
                setPadding(0, 24, 0, 24)
                isFocusable = true
                setOnClickListener {
                    viewModel.selectRoutingRule(null)
                    Toast.makeText(this@TvMainActivity, getString(R.string.custom_rules_disabled), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    reconnectIfActive()
                }
            }
            listContainer.addView(noneView)

            listContainer.addView(View(this@TvMainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0xFF2A2A4A.toInt())
            })

            for (rule in rules) {
                val row = LinearLayout(this@TvMainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 20, 0, 20)
                }

                val label = TextView(this@TvMainActivity).apply {
                    text = "${if (rule.isSelected) "  " else "  "}${rule.name} (${rule.ruleCount} rules)"
                    textSize = 16f
                    setTextColor(if (rule.isSelected) 0xFF4ADE80.toInt() else 0xFFE0E0FF.toInt())
                    if (rule.isSelected) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    isFocusable = true
                    setOnClickListener {
                        viewModel.selectRoutingRule(rule.id)
                        Toast.makeText(this@TvMainActivity, getString(R.string.rule_activated, rule.name), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        reconnectIfActive()
                    }
                }

                val deleteBtn = TextView(this@TvMainActivity).apply {
                    text = "  ${getString(R.string.delete)}  "
                    textSize = 14f
                    setTextColor(0xFFF87171.toInt())
                    isFocusable = true
                    setOnClickListener {
                        AlertDialog.Builder(this@TvMainActivity)
                            .setTitle(getString(R.string.delete_confirm, rule.name))
                            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                viewModel.deleteRoutingRule(rule)
                                Toast.makeText(this@TvMainActivity, getString(R.string.delete), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show()
                    }
                }

                row.addView(label)
                row.addView(deleteBtn)
                listContainer.addView(row)
            }

            if (rules.isEmpty()) {
                listContainer.addView(TextView(this@TvMainActivity).apply {
                    text = getString(R.string.no_rules_tv)
                    textSize = 14f
                    setTextColor(0xFF888899.toInt())
                })
            }
        }

        refreshList()
        dialog.show()
    }

    private fun showSplitTunnelDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Mode toggle
        val modeLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF7C6FFF.toInt())
        }
        val modeBtn = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFE0E0FF.toInt())
            setPadding(0, 16, 0, 16)
            isFocusable = true
        }

        fun updateModeViews() {
            val mode = viewModel.splitTunnelMode.value
            modeLabel.text = getString(R.string.mode_label)
            modeBtn.text = when (mode) {
                SplitTunnelMode.BYPASS -> "▸ ${getString(R.string.bypass_mode_btn)}"
                SplitTunnelMode.ONLY -> "▸ ${getString(R.string.only_mode_btn)}"
            }
        }
        updateModeViews()

        container.addView(modeLabel)
        container.addView(modeBtn)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 8; bottomMargin = 8 }
            setBackgroundColor(0xFF2A2A4A.toInt())
        })

        val loadingText = TextView(this).apply {
            text = getString(R.string.loading_apps)
            textSize = 14f
            setTextColor(0xFF888899.toInt())
        }
        container.addView(loadingText)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(listContainer)

        var needsReconnect = false

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.split_tunneling))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.close), null)
            .setOnDismissListener {
                if (needsReconnect) reconnectIfActive()
            }
            .create()

        modeBtn.setOnClickListener {
            val newMode = when (viewModel.splitTunnelMode.value) {
                SplitTunnelMode.BYPASS -> SplitTunnelMode.ONLY
                SplitTunnelMode.ONLY -> SplitTunnelMode.BYPASS
            }
            viewModel.setSplitTunnelMode(newMode)
            updateModeViews()
            needsReconnect = true
        }

        // Load apps in background
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val ownPkg = packageName
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.packageName != ownPkg }
                    .map { info ->
                        val label = info.loadLabel(pm).toString()
                        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        Triple(info.packageName, label, isSystem)
                    }
                    .filter { !it.third }
                    .sortedWith(
                        compareByDescending<Triple<String, String, Boolean>> {
                            it.first in viewModel.splitTunnelPackages.value
                        }.thenBy { it.second.lowercase() }
                    )
            }

            loadingText.visibility = View.GONE
            for ((pkg, label, _) in apps) {
                val row = LinearLayout(this@TvMainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 12, 0, 12)
                    isFocusable = true
                }

                val cb = CheckBox(this@TvMainActivity).apply {
                    isChecked = pkg in viewModel.splitTunnelPackages.value
                    isFocusable = false
                }

                val textView = TextView(this@TvMainActivity).apply {
                    text = "$label\n$pkg"
                    textSize = 14f
                    setTextColor(0xFFE0E0FF.toInt())
                    setPadding(16, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.setOnClickListener {
                    viewModel.toggleSplitTunnelApp(pkg)
                    cb.isChecked = pkg in viewModel.splitTunnelPackages.value
                    needsReconnect = true
                }

                row.addView(cb)
                row.addView(textView)
                listContainer.addView(row)
            }
        }

        dialog.show()
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
                        binding.btnConnect.text = getString(R.string.disconnect)
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = "${getString(R.string.connected)}: ${CoreService.activeProfileName ?: ""}"
                        binding.tvStatus.setTextColor(0xFF4ADE80.toInt())
                    }
                    VpnState.CONNECTING -> {
                        binding.btnConnect.text = getString(R.string.connecting)
                        binding.btnConnect.isEnabled = false
                        binding.tvStatus.text = getString(R.string.connecting)
                        binding.tvStatus.setTextColor(0xFFAAAACC.toInt())
                    }
                    VpnState.DISCONNECTED -> {
                        binding.btnConnect.text = getString(R.string.connect)
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = getString(R.string.not_connected)
                        binding.tvStatus.setTextColor(0xFF888888.toInt())
                    }
                    VpnState.ERROR -> {
                        binding.btnConnect.text = getString(R.string.connect)
                        binding.btnConnect.isEnabled = true
                        binding.tvStatus.text = getString(R.string.connection_failed)
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
                Toast.makeText(this@TvMainActivity, getString(R.string.add_profile_first), Toast.LENGTH_SHORT).show()
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

    private fun reconnectIfActive() {
        if (CoreService.isActive) {
            stopVpn()
            lifecycleScope.launch {
                kotlinx.coroutines.delay(500)
                startVpn()
            }
        }
    }

    private fun updateLanguageButton() {
        val lang = LocaleHelper.getSavedLanguage(this)
        binding.btnLanguage.text = "${getString(R.string.language)}: ${LocaleHelper.getDisplayName(lang)}"
    }

    private fun isTV(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
