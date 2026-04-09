package com.dave_cli.proxybox.ui.main.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.data.db.ProfileEntity
import com.dave_cli.proxybox.ui.main.theme.C

@Composable
fun ProfileList(
    profiles: List<ProfileEntity>,
    menuOpenId: String?,
    onSelect: (String) -> Unit,
    onMenuToggle: (String) -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: (ProfileEntity) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            ProfileCard(
                profile = profile,
                isMenuOpen = menuOpenId == profile.id,
                onSelect = { onSelect(profile.id) },
                onMenuToggle = { onMenuToggle(profile.id) },
                onMenuDismiss = onMenuDismiss,
                onRename = { onRename(profile) },
                onCopyConfig = {
                    onMenuDismiss()
                    val text = profile.rawUri.ifBlank { profile.configJson }
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("config", text))
                    Toast.makeText(context, "Config copied", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    onMenuDismiss()
                    val text = profile.rawUri.ifBlank { profile.configJson }
                    val intent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }, "Share config"
                    )
                    context.startActivity(intent)
                },
                onDelete = { onDelete(profile) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    isMenuOpen: Boolean,
    onSelect: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onCopyConfig: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val selected = profile.isSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF1A1A38) else C.Surface)
            .then(
                if (selected)
                    Modifier.border(2.dp, C.Primary, RoundedCornerShape(14.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onMenuToggle,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Radio
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) C.Primary else Color(0xFF444444), CircleShape)
                .then(if (selected) Modifier.background(C.Primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = C.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.padding(top = 3.dp)) {
                Text(
                    text = profile.protocol.uppercase(),
                    color = C.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (profile.latencyMs > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${profile.latencyMs}ms",
                        color = when {
                            profile.latencyMs < 200 -> C.Green
                            profile.latencyMs < 500 -> C.Yellow
                            else -> C.Red
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Menu button
        Box {
            Text(
                text = "\u22EE",
                color = Color(0xFF555555),
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = onMenuToggle)
                    .padding(8.dp)
            )
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = onMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text("\u270F\uFE0F  Rename", color = C.TextPrimary) },
                    onClick = { onMenuDismiss(); onRename() },
                )
                DropdownMenuItem(
                    text = { Text("\uD83D\uDCCB  Copy config", color = C.TextPrimary) },
                    onClick = onCopyConfig,
                )
                DropdownMenuItem(
                    text = { Text("\uD83D\uDCE4  Share", color = C.TextPrimary) },
                    onClick = onShare,
                )
                Divider(color = C.Border)
                DropdownMenuItem(
                    text = { Text("\uD83D\uDDD1  Delete", color = C.Red) },
                    onClick = { onMenuDismiss(); onDelete() },
                )
            }
        }
    }
}
