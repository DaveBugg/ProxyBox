package com.dave_cli.proxybox.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.BuildConfig
import com.dave_cli.proxybox.core.UpdateResult
import com.dave_cli.proxybox.ui.main.theme.C

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isUpdatingGeo by viewModel.isUpdatingGeo.collectAsState()
    val geoProgress by viewModel.geoProgress.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2190",
                fontSize = 22.sp,
                color = C.TextPrimary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.TextPrimary)
        }

        // UPDATES
        GroupLabel("UPDATES")

        SettingsItem(
            icon = "\u2193",
            iconColor = C.Pink,
            title = "Update App",
            subtitle = if (isCheckingUpdate) "Checking..."
            else "v${BuildConfig.VERSION_NAME} \u2014 tap to check",
            onClick = {
                viewModel.checkForUpdate { result ->
                    if (result.hasUpdate && result.downloadUrl != null) {
                        updateResult = result
                    } else if (result.hasUpdate) {
                        Toast.makeText(context, "Update ${result.latestVersion} found but no APK attached", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "You're on the latest version (${result.latestVersion})", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        SettingsItem(
            icon = "\uD83C\uDF0D",
            iconColor = C.Blue,
            title = "Update Geo Databases",
            subtitle = if (isUpdatingGeo && geoProgress.isNotEmpty()) geoProgress
            else "geoip.dat \u00B7 geosite.dat",
            onClick = {
                viewModel.updateGeoFiles { result ->
                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ABOUT
        GroupLabel("ABOUT")

        SettingsItem(
            icon = "\u25C6",
            iconColor = C.Primary,
            title = "ProxyBox",
            subtitle = "v${BuildConfig.VERSION_NAME} \u00B7 Open-source VPN client",
            showArrow = false,
        )

        SettingsItem(
            icon = "\u2B21",
            iconColor = C.TextPrimary,
            title = "GitHub",
            subtitle = "github.com/DaveBugg/ProxyBox",
            subtitleColor = C.Primary,
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DaveBugg/ProxyBox"))
                )
            }
        )

        SettingsItem(
            icon = "\u26A1",
            iconColor = C.Green,
            title = "Xray Core",
            subtitle = "Powered by XTLS/Xray-core",
            showArrow = false,
        )

        SettingsItem(
            icon = "\uD83D\uDCDC",
            iconColor = C.Amber,
            title = "License",
            subtitle = "GPLv3",
            showArrow = false,
        )
    }

    // Update dialog
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text("Update Available", color = C.TextPrimary) },
            text = {
                Column {
                    Text("New version: v${result.latestVersion}", color = C.TextPrimary, fontSize = 14.sp)
                    if (!result.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(result.releaseNotes, color = C.TextSecondary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.downloadAndInstallUpdate(context, result.downloadUrl!!, result.latestVersion)
                    Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
                    updateResult = null
                }) { Text("Download & Install", color = C.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) { Text("Later", color = C.TextSecondary) }
            },
            containerColor = C.SurfaceVariant,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = C.Primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color = C.TextDim,
    showArrow: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A3A)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 18.sp, color = iconColor)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = C.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = subtitleColor, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (showArrow && onClick != null) {
            Text("\u203A", color = Color(0xFF444444), fontSize = 20.sp)
        }
    }
}
