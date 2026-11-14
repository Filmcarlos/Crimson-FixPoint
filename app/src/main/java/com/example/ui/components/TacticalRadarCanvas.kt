package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GpsLockState
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Centerpiece Power Core Dial & Radar Scanner on the Main Screen:
 * - OFF (ปิดการทำงาน): Dark charcoal-steel core (#181820), static precision ring.
 * - SEARCHING (กำลังค้นหาสัญญาณ): Pulsing crimson alert + 360-degree rotating radar scan sweep.
 * - LOCKED (ล็อกสัญญาณแล้ว): Radiant crimson core with rhythmic heartbeat glow wave.
 */
@Composable
fun CentralPowerCoreDial(
    lockState: GpsLockState,
    accuracyMeters: Float?,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarRotation: Float
    val pulseFraction: Float
    if (lockState != GpsLockState.OFF) {
        val infiniteTransition = rememberInfiniteTransition(label = "central_power_core_transition")
        val animRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_rotation"
        )
        val animPulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (lockState == GpsLockState.SEARCHING) 700 else 1600,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "core_pulse"
        )
        radarRotation = animRotation
        pulseFraction = animPulse
    } else {
        radarRotation = 0f
        pulseFraction = 0f
    }

    val corePrimaryColor by animateColorAsState(
        targetValue = when (lockState) {
            GpsLockState.OFF -> Color(0xFF272732)
            GpsLockState.SEARCHING -> Color(0xFFDC2626)
            GpsLockState.LOCKED -> CrimsonRed
        },
        animationSpec = tween(400),
        label = "core_primary_color"
    )

    val ringAccentColor by animateColorAsState(
        targetValue = when (lockState) {
            GpsLockState.OFF -> Color(0xFF3F3F4E)
            GpsLockState.SEARCHING -> CrimsonNeon
            GpsLockState.LOCKED -> Color(0xFFFF2E55)
        },
        animationSpec = tween(400),
        label = "ring_accent_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(236.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = CrimsonNeon),
                role = Role.Button,
                onClick = onTogglePower
            )
            .semantics {
                contentDescription = "ปุ่มหลักเปิดปิดการล็อกสัญญาณ สถานะปัจจุบัน ${lockState.labelTh}"
            }
            .testTag("main_power_toggle_button")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val maxR = size.minDimension / 2f

            // 1. Ambient soft radial glow behind the dial
            val glowAlpha = when (lockState) {
                GpsLockState.OFF -> 0.05f
                GpsLockState.SEARCHING -> 0.18f + (pulseFraction * 0.28f)
                GpsLockState.LOCKED -> 0.34f + (pulseFraction * 0.16f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corePrimaryColor.copy(alpha = glowAlpha),
                        DeepCrimsonGlow.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // 2. Outer Octagonal Bezel Frame
            val octRadius = maxR * 0.92f
            val octPath = Path().apply {
                for (i in 0 until 8) {
                    val angleRad = Math.toRadians((i * 45.0) + 22.5)
                    val x = cx + (octRadius * cos(angleRad)).toFloat()
                    val y = cy + (octRadius * sin(angleRad)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = octPath,
                color = ringAccentColor.copy(alpha = if (lockState == GpsLockState.OFF) 0.35f else 0.72f),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 3. Precision Degree Ticks around Perimeter
            val tickOuterR = maxR * 0.84f
            val tickInnerR = maxR * 0.78f
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 45 == 0
                val startR = if (isMajor) tickInnerR - 3.dp.toPx() else tickInnerR
                drawLine(
                    color = if (isMajor) ringAccentColor.copy(alpha = 0.8f) else ringAccentColor.copy(alpha = 0.32f),
                    start = Offset(cx + (startR * cos(rad)).toFloat(), cy + (startR * sin(rad)).toFloat()),
                    end = Offset(cx + (tickOuterR * cos(rad)).toFloat(), cy + (tickOuterR * sin(rad)).toFloat()),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // 4. State-Specific Ring Animation
            val radarR = maxR * 0.74f
            when (lockState) {
                GpsLockState.OFF -> {
                    drawCircle(
                        color = Color(0xFF2E2E3A),
                        radius = radarR,
                        center = center,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )
                }

                GpsLockState.SEARCHING -> {
                    // Rotating 360° Radar Sweep Cone + Blinking Crimson Ring
                    rotate(degrees = radarRotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                0.92f to CrimsonRed.copy(alpha = 0.28f),
                                1.0f to CrimsonNeon.copy(alpha = 0.75f),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = true,
                            topLeft = Offset(cx - radarR, cy - radarR),
                            size = Size(radarR * 2f, radarR * 2f)
                        )
                        drawLine(
                            color = CrimsonNeon,
                            start = center,
                            end = Offset(cx + radarR, cy),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(
                        color = CrimsonNeon.copy(alpha = 0.35f + pulseFraction * 0.65f),
                        radius = radarR,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                GpsLockState.LOCKED -> {
                    // Steady Radiant Crimson Ring + Rhythmic Expanding Energy Pulse
                    val pulseRingR = radarR * (0.86f + pulseFraction * 0.18f)
                    drawCircle(
                        color = CrimsonNeon.copy(alpha = (1f - pulseFraction) * 0.65f),
                        radius = pulseRingR,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = CrimsonNeon,
                        radius = radarR,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 5. Inner Core Disc
            val innerDiscR = maxR * 0.56f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = when (lockState) {
                        GpsLockState.OFF -> listOf(Color(0xFF1E1E26), Color(0xFF101015), ObsidianBlack)
                        GpsLockState.SEARCHING -> listOf(
                            CrimsonRed.copy(alpha = 0.35f + pulseFraction * 0.45f),
                            DeepCrimsonGlow.copy(alpha = 0.5f),
                            Color(0xFF0E080B)
                        )
                        GpsLockState.LOCKED -> listOf(
                            CrimsonRed.copy(alpha = 0.88f),
                            DeepCrimsonGlow.copy(alpha = 0.92f),
                            Color(0xFF1A060B)
                        )
                    },
                    center = center,
                    radius = innerDiscR
                ),
                radius = innerDiscR,
                center = center
            )
            drawCircle(
                color = ringAccentColor,
                radius = innerDiscR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center Icon & Formal Thai Status Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (lockState) {
                    GpsLockState.OFF -> Icons.Default.PowerSettingsNew
                    GpsLockState.SEARCHING -> Icons.Default.GpsNotFixed
                    GpsLockState.LOCKED -> Icons.Default.GpsFixed
                },
                contentDescription = null,
                tint = when (lockState) {
                    GpsLockState.OFF -> TextHudMuted
                    GpsLockState.SEARCHING -> CrimsonNeon
                    GpsLockState.LOCKED -> Color.White
                },
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = lockState.labelTh,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (lockState == GpsLockState.OFF) TextHudMuted else TextHudPrimary
            )

            if (lockState == GpsLockState.LOCKED && accuracyMeters != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "±%.1f ม.", accuracyMeters),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
