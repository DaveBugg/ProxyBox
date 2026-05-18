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
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.BuildConfig
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.GeoProfile
import com.dave_cli.proxybox.core.LocaleHelper
import com.dave_cli.proxybox.core.UpdateResult
import com.dave_cli.proxybox.ui.main.theme.C

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSplitTunnel: () -> Unit = {},
    onLanguageChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val isUpdatingGeo by viewModel.isUpdatingGeo.collectAsState()
    val geoProgress by viewModel.geoProgress.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val currentGeoProfile by viewModel.geoProfile.collectAsState()
    val subUserAgent by viewModel.subUserAgent.collectAsState()

    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var showGeoProfileDialog by remember { mutableStateOf(false) }
    var showUaDialog by remember { mutableStateOf(false) }

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
            Text(stringResource(R.string.settings), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.TextPrimary)
        }

        // VPN
        GroupLabel(stringResource(R.string.group_vpn))

        SettingsItem(
            icon = "\uD83D\uDD00",
            iconColor = C.Violet,
            title = stringResource(R.string.split_tunneling),
            subtitle = stringResource(R.string.split_tunneling_subtitle),
            onClick = onOpenSplitTunnel,
        )

        SettingsItem(
            icon = "\uD83C\uDD94",
            iconColor = C.Blue,
            title = stringResource(R.string.sub_user_agent),
            subtitle = if (subUserAgent.isBlank()) stringResource(R.string.sub_ua_default) else subUserAgent,
            onClick = { showUaDialog = true },
        )

        // LANGUAGE
        val currentLang = LocaleHelper.getSavedLanguage(context)
        val langLabel = LocaleHelper.getDisplayName(currentLang)
        SettingsItem(
            icon = "\uD83C\uDF10",
            iconColor = C.Amber,
            title = stringResource(R.string.language),
            subtitle = langLabel,
            onClick = {
                val next = when (currentLang) {
                    "" -> "en"
                    "en" -> "ru"
                    else -> ""
                }
                LocaleHelper.saveLanguage(context, next)
                onLanguageChanged()
            },
        )

        // UPDATES
        GroupLabel(stringResource(R.string.group_updates))

        SettingsItem(
            icon = "\u2193",
            iconColor = C.Pink,
            title = stringResource(R.string.update_app),
            subtitle = if (isCheckingUpdate) stringResource(R.string.update_app_checking)
            else stringResource(R.string.update_app_subtitle, BuildConfig.VERSION_NAME),
            onClick = {
                viewModel.checkForUpdate { result ->
                    if (result.hasUpdate && result.downloadUrl != null) {
                        updateResult = result
                    } else if (result.hasUpdate) {
                        Toast.makeText(context, context.getString(R.string.update_found_no_apk, result.latestVersion), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.on_latest_version, result.latestVersion), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        SettingsItem(
            icon = "\uD83D\uDDFA",
            iconColor = C.Violet,
            title = stringResource(R.string.geo_profile),
            subtitle = geoProfileDisplayName(currentGeoProfile),
            onClick = { showGeoProfileDialog = true }
        )

        SettingsItem(
            icon = "\uD83C\uDF0D",
            iconColor = C.Blue,
            title = stringResource(R.string.update_geo),
            subtitle = if (isUpdatingGeo && geoProgress.isNotEmpty()) geoProgress
            else stringResource(R.string.update_geo_subtitle),
            onClick = {
                viewModel.updateGeoFiles { result ->
                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ABOUT
        GroupLabel(stringResource(R.string.group_about))

        SettingsItem(
            icon = "\u25C6",
            iconColor = C.Primary,
            title = "ProxyBox",
            subtitle = stringResource(R.string.about_subtitle, BuildConfig.VERSION_NAME),
            showArrow = false,
        )

        SettingsItem(
            icon = "\u2B21",
            iconColor = C.TextPrimary,
            title = stringResource(R.string.github),
            subtitle = stringResource(R.string.github_url),
            subtitleColor = C.Primary,
            onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DaveBugg/ProxyBox"))
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "github.com/DaveBugg/ProxyBox", Toast.LENGTH_LONG).show()
                }
            }
        )

        SettingsItem(
            icon = "\u26A1",
            iconColor = C.Green,
            title = stringResource(R.string.xray_core),
            subtitle = stringResource(R.string.xray_subtitle),
            showArrow = false,
        )

        SettingsItem(
            icon = "\uD83D\uDCDC",
            iconColor = C.Amber,
            title = stringResource(R.string.license),
            subtitle = stringResource(R.string.license_value),
            showArrow = false,
        )
    }

    // Geo profile dialog
    if (showGeoProfileDialog) {
        GeoProfileDialog(
            currentProfile = currentGeoProfile,
            onSelect = { profile ->
                viewModel.setGeoProfile(profile)
                showGeoProfileDialog = false
                Toast.makeText(
                    context,
                    context.getString(R.string.geo_profile_switched, geoProfileDisplayName(profile)),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showGeoProfileDialog = false }
        )
    }

    // User-Agent dialog
    if (showUaDialog) {
        UserAgentDialog(
            currentUa = subUserAgent,
            onSelect = { ua ->
                viewModel.setSubUserAgent(ua)
                showUaDialog = false
            },
            onDismiss = { showUaDialog = false }
        )
    }

    // Update dialog
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(stringResource(R.string.update_available), color = C.TextPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.new_version, result.latestVersion), color = C.TextPrimary, fontSize = 14.sp)
                    if (!result.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(result.releaseNotes, color = C.TextSecondary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.downloadAndInstallUpdate(context, result.downloadUrl!!, result.latestVersion)
                    Toast.makeText(context, context.getString(R.string.downloading_update), Toast.LENGTH_SHORT).show()
                    updateResult = null
                }) { Text(stringResource(R.string.download_install), color = C.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) { Text(stringResource(R.string.later), color = C.TextSecondary) }
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

private fun geoProfileDisplayName(profile: GeoProfile): String = when (profile.id) {
    "loyalsoldier" -> "Loyalsoldier"
    "v2fly" -> "v2fly upstream"
    "runetfreedom" -> "runetfreedom (RU-focused)"
    else -> profile.id
}

@Composable
private fun GeoProfileDialog(
    currentProfile: GeoProfile,
    onSelect: (GeoProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geo_profile), color = C.TextPrimary) },
        text = {
            Column {
                Text(
                    stringResource(R.string.geo_profile_hint),
                    color = C.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                GeoProfile.ALL.forEach { profile ->
                    val isActive = profile.id == currentProfile.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) C.Primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(profile) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                geoProfileDisplayName(profile),
                                color = if (isActive) C.Primary else C.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                geoProfileDescription(profile),
                                color = C.TextDim,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (isActive) {
                            Text("✓", color = C.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = C.TextSecondary)
            }
        },
        containerColor = C.SurfaceVariant,
    )
}

@Composable
private fun geoProfileDescription(profile: GeoProfile): String = when (profile.id) {
    "loyalsoldier" -> stringResource(R.string.geo_desc_loyalsoldier)
    "v2fly" -> stringResource(R.string.geo_desc_v2fly)
    "runetfreedom" -> stringResource(R.string.geo_desc_runetfreedom)
    else -> ""
}

private val UA_PRESETS = listOf(
    "" to "Default (none)",
    "Happ/3.20.4/Android" to "Happ Android",
    "v2rayNG/1.8.29" to "v2rayNG",
    "ClashForAndroid/2.5.12" to "Clash for Android",
)

@Composable
private fun UserAgentDialog(
    currentUa: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customUa by remember { mutableStateOf(currentUa) }
    var isCustom by remember {
        mutableStateOf(UA_PRESETS.none { it.first == currentUa })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sub_user_agent), color = C.TextPrimary) },
        text = {
            Column {
                Text(
                    stringResource(R.string.sub_ua_hint),
                    color = C.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                UA_PRESETS.forEach { (ua, label) ->
                    val isActive = !isCustom && ua == currentUa
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) C.Primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                isCustom = false
                                onSelect(ua)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                color = if (isActive) C.Primary else C.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (ua.isNotBlank()) {
                                Text(ua, color = C.TextDim, fontSize = 11.sp)
                            }
                        }
                        if (isActive) {
                            Text("\u2713", color = C.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.sub_ua_custom),
                    color = C.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value = customUa,
                    onValueChange = { customUa = it; isCustom = true },
                    singleLine = true,
                    placeholder = { Text("MyApp/1.0", color = C.TextDim, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.TextPrimary,
                        unfocusedTextColor = C.TextPrimary,
                        focusedBorderColor = C.Primary,
                        unfocusedBorderColor = C.TextDim.copy(alpha = 0.3f),
                        cursorColor = C.Primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (isCustom) {
                TextButton(onClick = { onSelect(customUa.trim()) }) {
                    Text(stringResource(R.string.save), color = C.Primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = C.TextSecondary)
            }
        },
        containerColor = C.SurfaceVariant,
    )
}
