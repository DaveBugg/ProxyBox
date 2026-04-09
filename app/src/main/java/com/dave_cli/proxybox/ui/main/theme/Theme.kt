package com.dave_cli.proxybox.ui.main.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Background = Color(0xFF0F0F1A)
    val Surface = Color(0xFF1A1A2E)
    val SurfaceVariant = Color(0xFF252540)
    val Primary = Color(0xFF7C6FFF)
    val TextPrimary = Color(0xFFE0E0FF)
    val TextSecondary = Color(0xFF888899)
    val TextDim = Color(0xFF555577)
    val Green = Color(0xFF4ADE80)
    val GreenDark = Color(0xFF3A7A50)
    val Red = Color(0xFFF87171)
    val Yellow = Color(0xFFFACC15)
    val Blue = Color(0xFF60A5FA)
    val Amber = Color(0xFFF59E0B)
    val Pink = Color(0xFFF472B6)
    val Violet = Color(0xFFA78BFA)
    val Border = Color(0xFF2A2A4A)
    val Divider = Color(0xFF1A1A30)
}

private val DarkScheme = darkColorScheme(
    primary = C.Primary,
    onPrimary = Color.White,
    surface = C.SurfaceVariant,
    onSurface = C.TextPrimary,
    surfaceVariant = C.Surface,
    onSurfaceVariant = C.TextPrimary,
    background = C.Background,
    onBackground = C.TextPrimary,
    outline = C.Border,
)

@Composable
fun ProxyBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
