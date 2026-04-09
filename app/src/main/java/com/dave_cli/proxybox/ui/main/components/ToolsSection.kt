package com.dave_cli.proxybox.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.core.RoutingPreset
import com.dave_cli.proxybox.core.RoutingPresets
import com.dave_cli.proxybox.ui.main.theme.C

@Composable
fun ToolsSection(
    activePreset: RoutingPreset,
    showPresetMenu: Boolean,
    onPresetMenuToggle: () -> Unit,
    onPresetSelected: (RoutingPreset) -> Unit,
    onPresetMenuDismiss: () -> Unit,
    onRules: () -> Unit,
    testResult: String?,
    isTesting: Boolean,
    onTest: () -> Unit,
    isPinging: Boolean,
    onPingAll: () -> Unit,
    onIpCheck: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        // ROUTING row
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("ROUTING")
            Spacer(Modifier.width(8.dp))
            Box {
                ToolChip(
                    text = "${presetShortName(activePreset)} \u25BE",
                    color = C.TextPrimary,
                    onClick = onPresetMenuToggle,
                )
                DropdownMenu(
                    expanded = showPresetMenu,
                    onDismissRequest = onPresetMenuDismiss,
                ) {
                    RoutingPresets.ALL.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.displayName, color = C.TextPrimary, fontSize = 14.sp) },
                            onClick = { onPresetSelected(preset) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            ToolChip("Rules", C.Green, onRules)
        }

        Spacer(Modifier.padding(top = 8.dp))

        // NETWORK row
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("NETWORK")
            Spacer(Modifier.width(8.dp))
            ToolChip(
                text = if (isTesting) "..." else (testResult ?: "Test"),
                color = C.Green,
                onClick = onTest,
                enabled = !isTesting
            )
            Spacer(Modifier.width(6.dp))
            ToolChip(
                text = if (isPinging) "..." else "Ping All",
                color = C.Violet,
                onClick = onPingAll,
                enabled = !isPinging
            )
            Spacer(Modifier.width(6.dp))
            ToolChip("IP Check", C.Amber, onIpCheck)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = C.Primary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.width(60.dp)
    )
}

@Composable
fun ToolChip(
    text: String,
    color: Color = C.TextPrimary,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(C.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun presetShortName(preset: RoutingPreset): String = when (preset.id) {
    "global" -> "\uD83C\uDF10 Global"
    "ru" -> "\uD83C\uDDF7\uD83C\uDDFA Russia"
    "ir" -> "\uD83C\uDDEE\uD83C\uDDF7 Iran"
    "cn" -> "\uD83C\uDDE8\uD83C\uDDF3 China"
    else -> preset.displayName.substringBefore(" \u2014")
}
