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
        if (isConnected) 0.15f else 0f,
        animationSpec = tween(600),
        label = "glow"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Power button
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFF4ADE80),
                            radius = size.minDimension / 2 + 20.dp.toPx(),
                            alpha = glowAlpha
                        )
                    }
                }
                .clip(CircleShape)
                .border(3.dp, borderColor, CircleShape)
                .background(
                    if (isConnected)
                        Brush.radialGradient(listOf(Color(0xFF0F2A1A), Color(0xFF0A1A10)))
                    else
                        Brush.radialGradient(listOf(Color(0xFF1E1E3A), Color(0xFF12122A)))
                )
                .clickable(enabled = !isConnecting) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            PowerIcon(color = iconColor, modifier = Modifier.size(52.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Status text
        Text(
            text = when (vpnState) {
                VpnState.CONNECTED -> "Connected"
                VpnState.CONNECTING -> "Connecting..."
                VpnState.ERROR -> "Connection failed"
                else -> "Not connected"
            },
            color = when (vpnState) {
                VpnState.CONNECTED -> C.Green
                VpnState.ERROR -> C.Red
                else -> Color(0xFF666666)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        if (isConnected) {
            Text(
                text = CoreService.activeProfileName ?: "",
                color = C.GreenDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            ConnectionDuration()
        } else if (!isConnecting && vpnState != VpnState.ERROR) {
            Text("Tap to connect", color = Color(0xFF444444), fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }

        // Speed test — always visible (uses its own xray instance, independent of VPN)
        SpeedTestChip(onSpeedTest = onSpeedTest)
    }
}

@Composable
private fun PowerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 4.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 - strokeW

        // Arc (circle with gap at top)
        drawArc(
            color = color,
            startAngle = -240f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )
        // Vertical line
        drawLine(
            color = color,
            start = Offset(cx, cy - r * 0.15f),
            end = Offset(cx, cy - r - strokeW * 0.5f),
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

    Column(
        modifier = Modifier.padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60),
            color = C.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        Text("duration", color = Color(0xFF555555), fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp))
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
