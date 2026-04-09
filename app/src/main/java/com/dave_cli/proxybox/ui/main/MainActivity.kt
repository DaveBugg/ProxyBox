package com.dave_cli.proxybox.ui.main

import android.app.Activity
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.ui.add.AddProfileActivity
import com.dave_cli.proxybox.ui.main.theme.ProxyBoxTheme
import com.dave_cli.proxybox.ui.tv.TvMainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingWidgetConnect = false

    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
    }

    private var onRuleFileResult: ((String) -> Unit)? = null

    private val ruleFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (!json.isNullOrEmpty()) {
                    onRuleFileResult?.invoke(json)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        onRuleFileResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isTV()) {
            startActivity(Intent(this, TvMainActivity::class.java))
            finish()
            return
        }

        window.statusBarColor = 0xFF0F0F1A.toInt()
        window.navigationBarColor = 0xFF0F0F1A.toInt()

        handleWidgetIntent(intent)

        setContent {
            ProxyBoxTheme {
            val vpnState by CoreService.vpnState.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            if (showSettings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { showSettings = false },
                )
            } else {
                MainScreen(
                    viewModel = viewModel,
                    vpnState = vpnState,
                    onConnectToggle = {
                        if (CoreService.isActive) stopVpn() else requestVpnPermission()
                    },
                    onAddProfile = {
                        startActivity(Intent(this@MainActivity, AddProfileActivity::class.java))
                    },
                    onOpenSettings = { showSettings = true },
                    onPickRuleFile = {
                        onRuleFileResult = { json ->
                            showNameRuleDialogFromFile(json)
                        }
                        try {
                            ruleFileLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "No file manager available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReconnect = { reconnectIfActive() },
                )
            }
            } // ProxyBoxTheme
        }
    }

    private fun showNameRuleDialogFromFile(json: String) {
        // File picker returned JSON. We add it with a default name since the
        // Compose RulesDialog handles naming internally for URL imports.
        // For file imports, prompt the user via an old-style dialog.
        val input = android.widget.EditText(this).apply {
            hint = "Rule name"
            setTextColor(0xFFE0E0FF.toInt())
            setHintTextColor(0xFF555577.toInt())
            setBackgroundColor(0xFF2A2A4A.toInt())
            setPadding(32, 24, 32, 24)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 24, 48, 0)
            addView(input)
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Name this rule set")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Custom Rules" }
                viewModel.addRoutingRule(name, json) { error ->
                    if (error == null) {
                        Toast.makeText(this, "Rule set \"$name\" added!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        if (intent.action == com.dave_cli.proxybox.widget.VpnWidgetProvider.ACTION_CONNECT) {
            if (!CoreService.isActive) {
                pendingWidgetConnect = true
                lifecycleScope.launch {
                    withTimeoutOrNull(3000) {
                        viewModel.profiles.first { it.isNotEmpty() }
                    }
                    requestVpnPermission()
                }
            }
        }
    }

    private fun isTV(): Boolean {
        val uiMode = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
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
                Toast.makeText(this@MainActivity, "Add a profile first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!selected.isSelected) viewModel.selectProfile(selected.id)
            val si = Intent(this@MainActivity, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            startForegroundService(si)
            if (pendingWidgetConnect) {
                pendingWidgetConnect = false
                moveTaskToBack(true)
            }
        }
    }

    private fun stopVpn() {
        startService(Intent(this, CoreService::class.java).apply { action = CoreService.ACTION_STOP })
    }

    private fun reconnectIfActive() {
        if (CoreService.isActive) {
            stopVpn()
            lifecycleScope.launch {
                delay(500)
                startVpn()
            }
        }
    }
}
