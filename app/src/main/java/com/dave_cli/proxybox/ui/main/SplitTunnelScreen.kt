package com.dave_cli.proxybox.ui.main

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dave_cli.proxybox.R
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.ui.main.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Drawable?,
)

@Composable
fun SplitTunnelScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onReconnect: () -> Unit = {},
) {
    val context = LocalContext.current
    val splitMode by viewModel.splitTunnelMode.collectAsState()
    val selectedPackages by viewModel.splitTunnelPackages.collectAsState()

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var hideSystem by remember { mutableStateOf(true) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
        isLoading = false
    }

    val filtered = remember(apps, searchQuery, hideSystem, selectedPackages) {
        val query = searchQuery.lowercase()
        apps.filter { app ->
            if (hideSystem && app.isSystem) return@filter false
            if (query.isNotEmpty()) {
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            } else true
        }.sortedWith(
            compareByDescending<AppItem> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase() }
        )
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
                .padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2190",
                fontSize = 22.sp,
                color = C.TextPrimary,
                modifier = Modifier
                    .clickable {
                        if (dirty && CoreService.isActive) onReconnect()
                        onBack()
                    }
                    .padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.split_tunneling), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (selectedPackages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.Primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${selectedPackages.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            ModeChip(
                label = stringResource(R.string.bypass_selected),
                selected = splitMode == SplitTunnelMode.BYPASS,
                onClick = {
                    viewModel.setSplitTunnelMode(SplitTunnelMode.BYPASS)
                    if (CoreService.isActive) onReconnect()
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ModeChip(
                label = stringResource(R.string.only_selected),
                selected = splitMode == SplitTunnelMode.ONLY,
                onClick = {
                    viewModel.setSplitTunnelMode(SplitTunnelMode.ONLY)
                    if (CoreService.isActive) onReconnect()
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Mode description
        Text(
            text = when (splitMode) {
                SplitTunnelMode.BYPASS -> stringResource(R.string.bypass_description)
                SplitTunnelMode.ONLY -> stringResource(R.string.only_description)
            },
            color = C.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        // Search + hide system
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_apps), color = C.TextDim) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = C.Surface,
                unfocusedContainerColor = C.Surface,
                focusedTextColor = C.TextPrimary,
                unfocusedTextColor = C.TextPrimary,
                cursorColor = C.Primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hideSystem = !hideSystem }
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = hideSystem,
                onCheckedChange = { hideSystem = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.Primary,
                    checkedTrackColor = C.SurfaceVariant,
                    uncheckedThumbColor = C.TextDim,
                    uncheckedTrackColor = C.Surface,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.hide_system_apps), color = C.TextSecondary, fontSize = 14.sp)
        }

        // App list
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.Primary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val isSelected = app.packageName in selectedPackages
                    AppRow(
                        app = app,
                        isSelected = isSelected,
                        onToggle = {
                            viewModel.toggleSplitTunnelApp(app.packageName)
                            dirty = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.Primary else C.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else C.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        val bitmap = remember(app.packageName) {
            app.icon?.toBitmap(48, 48)?.asImageBitmap()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(app.label.take(1), color = C.TextPrimary, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                color = if (isSelected) C.TextPrimary else C.TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                app.packageName,
                color = C.TextDim,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }

        Switch(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Primary,
                checkedTrackColor = C.SurfaceVariant,
                uncheckedThumbColor = C.TextDim,
                uncheckedTrackColor = C.Surface,
            ),
        )
    }
}

private fun loadInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val ownPkg = context.packageName
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.packageName != ownPkg }
        .map { info ->
            AppItem(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                icon = try { info.loadIcon(pm) } catch (_: Exception) { null },
            )
        }
        .sortedBy { it.label.lowercase() }
}
