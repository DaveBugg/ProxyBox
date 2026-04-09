package com.dave_cli.proxybox.ui.main.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.data.db.RoutingRuleEntity
import com.dave_cli.proxybox.ui.main.IpCheckResult
import com.dave_cli.proxybox.ui.main.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Reusable text field ----

@Composable
fun ProxyBoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = C.TextPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(C.Primary),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(C.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, C.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = C.TextDim, fontSize = 15.sp)
                }
                inner()
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

// ---- Rename Dialog ----

@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename profile", color = C.TextPrimary) },
        text = {
            ProxyBoxTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Profile name"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val name = text.trim()
                if (name.isNotEmpty()) onConfirm(name)
            }) { Text("Save", color = C.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = C.TextSecondary) }
        },
        containerColor = C.SurfaceVariant,
    )
}

// ---- Delete Confirm ----

@Composable
fun DeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?", color = C.TextPrimary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = C.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = C.TextSecondary) }
        },
        containerColor = C.SurfaceVariant,
    )
}

// ---- Rules Dialog ----

private sealed class RulesPage {
    object List : RulesPage()
    object ImportUrl : RulesPage()
    data class NameRule(val json: String) : RulesPage()
}

@Composable
fun RulesDialog(
    rules: List<RoutingRuleEntity>,
    onSelectRule: (String?) -> Unit,
    onDeleteRule: (RoutingRuleEntity) -> Unit,
    onImportFromFile: () -> Unit,
    onAddRule: (name: String, json: String, callback: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf<RulesPage>(RulesPage.List) }
    var deleteTarget by remember { mutableStateOf<RoutingRuleEntity?>(null) }
    val context = LocalContext.current

    when (val p = page) {
        is RulesPage.ImportUrl -> {
            ImportUrlDialog(
                onImport = { json -> page = RulesPage.NameRule(json) },
                onDismiss = { page = RulesPage.List }
            )
        }
        is RulesPage.NameRule -> {
            NameRuleDialog(
                onConfirm = { name ->
                    onAddRule(name, p.json) { error ->
                        if (error == null) {
                            Toast.makeText(context, "Rule set \"$name\" added!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                    page = RulesPage.List
                },
                onDismiss = { page = RulesPage.List }
            )
        }
        is RulesPage.List -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Routing Rules", color = C.TextPrimary) },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // None option
                        val noneSelected = rules.none { it.isSelected }
                        Text(
                            text = if (noneSelected) "\u2713 None (no custom rules)" else "  None",
                            color = if (noneSelected) C.Green else C.TextPrimary,
                            fontWeight = if (noneSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRule(null) }
                                .padding(vertical = 12.dp)
                        )
                        Divider(color = C.Border)

                        rules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRule(rule.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (rule.isSelected) "\u2713 " else "  "}${rule.name} (${rule.ruleCount} rules)",
                                    color = if (rule.isSelected) C.Green else C.TextPrimary,
                                    fontWeight = if (rule.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { deleteTarget = rule }) {
                                    Text("\u2715", color = C.Red, fontSize = 14.sp)
                                }
                            }
                        }

                        if (rules.isEmpty()) {
                            Text(
                                "\nNo custom rules imported yet.\nFormat: v2rayN routing rules JSON\nUse File / URL below to import.",
                                color = C.TextSecondary, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            onDismiss()
                            onImportFromFile()
                        }) { Text("File", color = C.Blue) }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { page = RulesPage.ImportUrl }) {
                            Text("URL", color = C.Violet)
                        }
                    }
                },
                containerColor = C.SurfaceVariant,
            )
        }
    }

    // Delete confirmation for rule
    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${rule.name}\"?", color = C.TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRule(rule)
                    deleteTarget = null
                }) { Text("Delete", color = C.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = C.TextSecondary) }
            },
            containerColor = C.SurfaceVariant,
        )
    }
}

// ---- Import URL Dialog ----

@Composable
private fun ImportUrlDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Import Rules from URL", color = C.TextPrimary) },
        text = {
            Column {
                ProxyBoxTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    placeholder = "https://example.com/rules.json"
                )
                error?.let {
                    Text(it, color = C.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        val json = withContext(Dispatchers.IO) {
                            try {
                                java.net.URL(url.trim()).readText()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        isLoading = false
                        if (json != null) onImport(json) else error = "Failed to download"
                    }
                },
                enabled = url.isNotBlank() && !isLoading
            ) { Text(if (isLoading) "Loading..." else "Import", color = C.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = C.TextSecondary)
            }
        },
        containerColor = C.SurfaceVariant,
    )
}

// ---- Name Rule Dialog ----

@Composable
private fun NameRuleDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this rule set", color = C.TextPrimary) },
        text = {
            ProxyBoxTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Rule name"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.trim().ifEmpty { "Custom Rules" })
            }) { Text("Save", color = C.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = C.TextSecondary) }
        },
        containerColor = C.SurfaceVariant,
    )
}

// ---- IP Check Dialog ----

@Composable
fun IpCheckDialog(
    presetName: String,
    ipCheckResults: StateFlow<List<IpCheckResult>>,
    onDismiss: () -> Unit,
) {
    val results by ipCheckResults.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("IP Check \u2014 $presetName", color = C.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (results.isEmpty()) {
                    Text("Checking...", color = C.TextSecondary, fontSize = 14.sp)
                } else {
                    results.forEach { r ->
                        val icon = if (r.isRegional) "\uD83C\uDFE0" else "\uD83C\uDF10"
                        Text(
                            "$icon ${r.serviceName}",
                            color = C.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = r.ip ?: r.error ?: "\u2014",
                            color = if (r.ip != null) C.Green else C.TextDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        containerColor = C.SurfaceVariant,
    )
}
