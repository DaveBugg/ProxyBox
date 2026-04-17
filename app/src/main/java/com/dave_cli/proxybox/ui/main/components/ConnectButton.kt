package com.dave_cli.proxybox.ui.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dave_cli.proxybox.core.CoreService
import com.dave_cli.proxybox.core.CoreService.VpnState
import com.dave_cli.proxybox.ui.main.theme.C
import kotlinx.coroutines.delay

@Composable
fun ConnectSection(
    vpnState: VpnState,
    onToggle: () -> Unit,
    onSpeedTest: ((Double?, String?) -> Unit) -> Unit,
) {
    val isConnected = vpnState == VpnState.CONNECTED
    val isConnecting = vpnState == VpnState.CONNECTING

    val iconColor by animateColorAsState(
        when {
            isConnected -> C.Green
            isConnecting -> C.Primary
            else -> Color(0xFF555555)
        },
        label = "icon"
    )
    val borderColor by animateColorAsState(
        when {
            isConnected -> C.Green
            isConnecting -> C.Primary
            else -> Color(0xFF333333)
        },
        label = "border"
    )
    val glowAlpha by animateFloatAsState(
        if (isConnected) 0.12f else 0f,
        animationSpec = tween(600),
        label = "glow"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Power button
        Box(
            modifier = Modifier
                .size(130.dp)
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFF4ADE80),
                            radius = size.minDimension / 2 + 14.dp.toPx(),
                            alpha = glowAlpha
                        )
                    }
                }
                .clip(CircleShape)
                .border(2.5.dp, borderColor, CircleShape)
                .background(
                    if (isConnected)
                        Brush.radialGradient(listOf(Color(0xFF0F2A1A), Color(0xFF0A1A10)))
                    else
                        Brush.radialGradient(listOf(Color(0xFF1E1E3A), Color(0xFF12122A)))
                )
                .clickable(enabled = !isConnecting) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            PowerIcon(color = iconColor, modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(20.dp))

        // Status row: left column (status + duration) — right column (speed test)
        if (isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: status + profile + duration
                Column {
                    Text(
                        "Connected",
                        color = C.Green,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = CoreService.activeProfileName ?: "",
                        color = C.GreenDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    ConnectionDuration()
                }
                // Right: speed test
                SpeedTestChip(onSpeedTest = onSpeedTest)
            }
        } else {
            // Centered status when not connected
            Text(
                text = when (vpnState) {
                    VpnState.CONNECTING -> "Connecting..."
                    VpnState.ERROR -> "Connection failed"
                    else -> "Not connected"
                },
                color = when (vpnState) {
                    VpnState.ERROR -> C.Red
                    else -> Color(0xFF666666)
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (!isConnecting && vpnState != VpnState.ERROR) {
                Text(
                    "Tap to connect", color = Color(0xFF444444), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Speed test centered when disconnected
            SpeedTestChip(onSpeedTest = onSpeedTest)
        }
    }
}

@Composable
private fun PowerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 3.5.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 - strokeW

        // Arc — gap at top (30° each side of 270°)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )
        // Vertical stem
        val stemTop = cy - r - strokeW * 0.3f
        val stemBottom = cy - r * 0.05f
        drawLine(
            color = color,
            start = Offset(cx, stemBottom),
            end = Offset(cx, stemTop),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ConnectionDuration() {
    val startTime = CoreService.connectionStartTime
    if (startTime <= 0L) return

    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(startTime) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60),
            color = C.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        Text("duration", color = Color(0xFF555555), fontSize = 11.sp,
            modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun SpeedTestChip(
    onSpeedTest: ((Double?, String?) -> Unit) -> Unit,
) {
    var speedMbps by remember { mutableStateOf<Double?>(null) }
    var speedError by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val chipModifier = Modifier
        .padding(top = 10.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(C.Surface)

    if (isTesting) {
        Box(modifier = chipModifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Testing...", color = C.TextSecondary, fontSize = 13.sp)
        }
    } else if (speedMbps != null) {
        Row(
            modifier = chipModifier
                .clickable {
                    isTesting = true
                    onSpeedTest { mbps, err ->
                        speedMbps = mbps
                        speedError = err
                        isTesting = false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2193 %.1f Mbps".format(speedMbps),
                color = C.Green,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text("\u21BB", color = C.TextSecondary, fontSize = 16.sp)
        }
    } else if (speedError != null) {
        Box(
            modifier = chipModifier
                .clickable {
                    isTesting = true
                    speedError = null
                    onSpeedTest { mbps, err ->
                        speedMbps = mbps
                        speedError = err
                        isTesting = false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Failed \u21BB", color = C.Red, fontSize = 13.sp)
        }
    } else {
        Box(
            modifier = chipModifier
                .clickable {
                    isTesting = true
                    onSpeedTest { mbps, err ->
                        speedMbps = mbps
                        speedError = err
                        isTesting = false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("\u26A1 Speed Test", color = C.Amber, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}
