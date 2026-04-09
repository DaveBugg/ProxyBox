package com.dave_cli.proxybox.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.ui.main.components.ConnectSection
import com.dave_cli.proxybox.ui.main.components.DeleteConfirmDialog
import com.dave_cli.proxybox.ui.main.components.IpCheckDialog
import com.dave_cli.proxybox.ui.main.components.ProfileList
import com.dave_cli.proxybox.ui.main.components.RenameDialog
import com.dave_cli.proxybox.ui.main.components.RulesDialog
import com.dave_cli.proxybox.ui.main.components.ToolsSection
import com.dave_cli.proxybox.ui.main.theme.C
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    vpnState: VpnState,
    onConnectToggle: () -> Unit,
    onAddProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickRuleFile: () -> Unit,
    onReconnect: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val routingRules by viewModel.routingRules.collectAsState()
    val context = LocalContext.current

    // Dialog state
    var showRulesDialog by remember { mutableStateOf(false) }
    var showIpCheckDialog by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ProfileEntity?>(null) }
    var menuOpenProfileId by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    // Auto-clear test result
    LaunchedEffect(testResult) {
        if (testResult != null) { delay(4000); testResult = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ProxyBox",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = C.TextPrimary,
                letterSpacing = (-0.5).sp,
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(C.Surface, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2699", fontSize = 16.sp, color = C.Primary)
            }
        }

        // Connect button
        ConnectSection(
            vpnState = vpnState,
            onToggle = onConnectToggle,
            onSpeedTest = { callback -> viewModel.runSpeedTest(callback) },
        )

        // Tools
        ToolsSection(
            activePreset = activePreset,
            showPresetMenu = showPresetMenu,
            onPresetMenuToggle = { showPresetMenu = !showPresetMenu },
            onPresetSelected = { preset ->
                viewModel.setActivePreset(preset)
                showPresetMenu = false
                if (CoreService.isActive) {
                    Toast.makeText(context, "Reconnect VPN to apply preset", Toast.LENGTH_SHORT).show()
                }
            },
            onPresetMenuDismiss = { showPresetMenu = false },
            onRules = { showRulesDialog = true },
            testResult = testResult,
            isTesting = isTesting,
            onTest = {
                isTesting = true
                viewModel.testConnection { result -> testResult = result; isTesting = false }
            },
            isPinging = isPinging,
            onPingAll = { viewModel.pingAllProfiles() },
            onIpCheck = { viewModel.checkIp(); showIpCheckDialog = true },
        )

        // Divider
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(1.dp)
                .background(C.Divider)
        )

        // Profiles label
        Text(
            "PROFILES",
            color = C.Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
        )

        // Profiles list or empty
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No profiles yet.\nTap + to add a config.",
                    color = C.TextDim, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            ProfileList(
                profiles = profiles,
                menuOpenId = menuOpenProfileId,
                onSelect = { id ->
                    viewModel.selectProfile(id)
                    if (CoreService.isActive) onReconnect()
                },
                onMenuToggle = { id ->
                    menuOpenProfileId = if (menuOpenProfileId == id) null else id
                },
                onMenuDismiss = { menuOpenProfileId = null },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                modifier = Modifier.weight(1f),
            )
        }

        // Bottom bar
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = onAddProfile,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Primary),
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.width(6.dp))
                Text("Add Profile", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ---- Dialogs ----

    renameTarget?.let { p ->
        RenameDialog(
            currentName = p.name,
            onConfirm = { viewModel.renameProfile(p.id, it); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { p ->
        DeleteConfirmDialog(
            name = p.name,
            onConfirm = { viewModel.deleteProfile(p); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showRulesDialog) {
        RulesDialog(
            rules = routingRules,
            onSelectRule = { id ->
                viewModel.selectRoutingRule(id)
                showRulesDialog = false
                onReconnect()
            },
            onDeleteRule = { viewModel.deleteRoutingRule(it) },
            onImportFromFile = { showRulesDialog = false; onPickRuleFile() },
            onAddRule = { name, json, cb -> viewModel.addRoutingRule(name, json, cb) },
            onDismiss = { showRulesDialog = false },
        )
    }

    if (showIpCheckDialog) {
        IpCheckDialog(
            presetName = activePreset.displayName,
            ipCheckResults = viewModel.ipCheckResults,
            onDismiss = { showIpCheckDialog = false }
        )
    }
}
