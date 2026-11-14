package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.HotzonePin
import com.example.model.IncomingOrderCard
import com.example.model.MerchantPoint
import com.example.model.OrderEvaluation
import com.example.model.OrderGrade
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Signature Animation 1 & 2:
 * - Dual-Ring Tactical Radar & Pin Lock Animation (360-deg sweep, Inner Pickup Ring, Outer Drop-off Ring,
 *   Merchants, Order Pickup/Drop-off vectors, and central Crimson Anchor Pin).
 * - Crimson Shockwave & Crosshair Lock-On ripple when ENGAGE FIXPOINT LOCK is pressed.
 */
@Composable
fun TacticalRadarCanvas(
    activePin: HotzonePin,
    riderLat: Double,
    riderLng: Double,
    maxPickupRadiusKm: Float,
    maxDropoffRadiusKm: Float,
    isLocked: Boolean,
    shockwaveTriggerCount: Int,
    merchants: List<MerchantPoint>,
    orderEvaluation: OrderEvaluation?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_infinite")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLocked) 3200 else 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val crosshairPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crosshair_pulse"
    )

    // Shockwave ripple triggered on ENGAGE FIXPOINT LOCK or Pin change
    var shockwaveTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shockwaveTriggerCount) {
        shockwaveTarget = 0f
        kotlinx.coroutines.delay(20)
        shockwaveTarget = 1f
    }
    val shockwaveProgress by animateFloatAsState(
        targetValue = shockwaveTarget,
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "shockwave_ripple"
    )

    // Smoothly animate ring radii when user drags sliders
    val animatedPickupKm by animateFloatAsState(
        targetValue = maxPickupRadiusKm,
        animationSpec = tween(durationMillis = 220),
        label = "pickup_radius_anim"
    )
    val animatedDropoffKm by animateFloatAsState(
        targetValue = maxDropoffRadiusKm,
        animationSpec = tween(durationMillis = 220),
        label = "dropoff_radius_anim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(295.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1F0910),
                        CarbonDark,
                        ObsidianBlack
                    )
                )
            )
            .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(18.dp))
            .semantics {
                contentDescription = "เรดาร์ยุทธวิธีแสดงหมุดหลัก วงแหวนจุดรับและวงแหวนจุดส่ง"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val maxVisualRadius = min(size.width, size.height) * 0.44f

            // Dynamic scale so both rings fit clearly on the radar while responding to slider changes
            val radarScaleKm = (animatedDropoffKm * 1.25f).coerceIn(2.2f, 9.5f)
            val kmToPx = maxVisualRadius / radarScaleKm

            val pickupRadiusPx = (animatedPickupKm * kmToPx).coerceIn(maxVisualRadius * 0.14f, maxVisualRadius * 0.85f)
            val dropoffRadiusPx = (animatedDropoffKm * kmToPx).coerceIn(pickupRadiusPx + 14f, maxVisualRadius * 0.96f)

            // 1. Tactical background grid & range rings
            for (step in 1..4) {
                val r = maxVisualRadius * (step / 4f)
                drawCircle(
                    color = CrimsonRed.copy(alpha = 0.12f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            // Crosshair axes
            drawLine(
                color = CrimsonRed.copy(alpha = 0.18f),
                start = Offset(cx - maxVisualRadius * 1.08f, cy),
                end = Offset(cx + maxVisualRadius * 1.08f, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = CrimsonRed.copy(alpha = 0.18f),
                start = Offset(cx, cy - maxVisualRadius * 1.08f),
                end = Offset(cx, cy + maxVisualRadius * 1.08f),
                strokeWidth = 1.dp.toPx()
            )

            // 2. Outer Drop-off Ring (Dashed Neon Crimson / Emerald Loop Guard)
            drawCircle(
                color = CrimsonNeon.copy(alpha = 0.10f),
                radius = dropoffRadiusPx,
                center = center
            )
            drawCircle(
                color = CrimsonNeon.copy(alpha = 0.85f),
                radius = dropoffRadiusPx,
                center = center,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                )
            )

            // 3. Inner Pickup Ring (Solid Glowing Crimson Red)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonRed.copy(alpha = 0.26f),
                        DeepCrimsonGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = pickupRadiusPx
                ),
                radius = pickupRadiusPx,
                center = center
            )
            drawCircle(
                color = CrimsonRed,
                radius = pickupRadiusPx,
                center = center,
                style = Stroke(width = 2.6.dp.toPx())
            )

            // 4. 360-Degree Rotating Radar Sweep Beam
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        0.95f to CrimsonRed.copy(alpha = 0.18f),
                        1.0f to CrimsonNeon.copy(alpha = 0.48f),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = Offset(cx - maxVisualRadius, cy - maxVisualRadius),
                    size = Size(maxVisualRadius * 2f, maxVisualRadius * 2f)
                )
                drawLine(
                    color = CrimsonNeon.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(cx + maxVisualRadius, cy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Merchant Cluster Dots around the Pin
            merchants.forEach { m ->
                val dLatKm = (m.lat - activePin.lat) * 111.0
                val dLngKm = (m.lng - activePin.lng) * 108.0
                val mx = (cx + (dLngKm * kmToPx).toFloat()).coerceIn(cx - maxVisualRadius, cx + maxVisualRadius)
                val my = (cy - (dLatKm * kmToPx).toFloat()).coerceIn(cy - maxVisualRadius, cy + maxVisualRadius)

                drawCircle(
                    color = CyanTelemetry.copy(alpha = 0.28f),
                    radius = 7.dp.toPx(),
                    center = Offset(mx, my)
                )
                drawCircle(
                    color = CyanTelemetry,
                    radius = 3.5.dp.toPx(),
                    center = Offset(mx, my)
                )
            }

            // 6. Active Order Pickup -> Drop-off -> Loop-Back Vector on Radar
            if (orderEvaluation != null) {
                val ord = orderEvaluation.order
                val pLatKm = (ord.pickupLat - activePin.lat) * 111.0
                val pLngKm = (ord.pickupLng - activePin.lng) * 108.0
                val dLatKm = (ord.dropoffLat - activePin.lat) * 111.0
                val dLngKm = (ord.dropoffLng - activePin.lng) * 108.0

                val px = (cx + (pLngKm * kmToPx).toFloat()).coerceIn(cx - maxVisualRadius * 0.95f, cx + maxVisualRadius * 0.95f)
                val py = (cy - (pLatKm * kmToPx).toFloat()).coerceIn(cy - maxVisualRadius * 0.95f, cy + maxVisualRadius * 0.95f)
                val dx = (cx + (dLngKm * kmToPx).toFloat()).coerceIn(cx - maxVisualRadius * 0.98f, cx + maxVisualRadius * 0.98f)
                val dy = (cy - (dLatKm * kmToPx).toFloat()).coerceIn(cy - maxVisualRadius * 0.98f, cy + maxVisualRadius * 0.98f)

                val pickupPt = Offset(px, py)
                val dropoffPt = Offset(dx, dy)
                val vectorColor = when (orderEvaluation.grade) {
                    OrderGrade.S_RANK_LOOP -> EmeraldSRank
                    OrderGrade.A_RANK_IN_ZONE -> CrimsonNeon
                    OrderGrade.REJECT_OUT_OF_ZONE -> AmberWarning
                }

                // Line from Pin to Pickup
                drawLine(
                    color = EmeraldSRank.copy(alpha = 0.75f),
                    start = center,
                    end = pickupPt,
                    strokeWidth = 2.dp.toPx()
                )
                // Line from Pickup to Dropoff
                drawLine(
                    color = vectorColor,
                    start = pickupPt,
                    end = dropoffPt,
                    strokeWidth = 2.8.dp.toPx()
                )
                // Dashed Loop-Back Line from Dropoff back to Pin
                drawLine(
                    color = vectorColor.copy(alpha = 0.65f),
                    start = dropoffPt,
                    end = center,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Pickup Marker (Green Square/Circle)
                drawCircle(color = EmeraldSRank.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = pickupPt)
                drawCircle(color = EmeraldSRank, radius = 4.5.dp.toPx(), center = pickupPt)

                // Dropoff Marker (Grade-colored Ring + Core)
                drawCircle(color = vectorColor.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = dropoffPt)
                drawCircle(color = vectorColor, radius = 5.5.dp.toPx(), center = dropoffPt)
            }

            // 7. Crimson Shockwave Ripple when ENGAGE FIXPOINT LOCK is triggered
            if (shockwaveProgress in 0.01f..0.99f) {
                val rippleR1 = maxVisualRadius * shockwaveProgress
                val rippleR2 = maxVisualRadius * (shockwaveProgress * 0.7f)
                val alpha = (1f - shockwaveProgress).coerceIn(0f, 1f)
                drawCircle(
                    color = CrimsonNeon.copy(alpha = alpha * 0.85f),
                    radius = rippleR1,
                    center = center,
                    style = Stroke(width = 3.5.dp.toPx())
                )
                drawCircle(
                    color = CrimsonRed.copy(alpha = alpha * 0.55f),
                    radius = rippleR2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 8. Central Crimson Anchor Pin & Rotating Lock-On Crosshair
            val bracketRadius = 18.dp.toPx() * (if (isLocked) 1f else crosshairPulse)
            rotate(degrees = if (isLocked) 45f else sweepAngle * 0.5f, pivot = center) {
                drawCircle(
                    color = if (isLocked) CrimsonNeon else AmberWarning,
                    radius = bracketRadius,
                    center = center,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }

            // Central Anchor Diamond / Pin
            val pinPath = Path().apply {
                moveTo(cx, cy - 10.dp.toPx())
                lineTo(cx + 8.dp.toPx(), cy)
                lineTo(cx, cy + 10.dp.toPx())
                lineTo(cx - 8.dp.toPx(), cy)
                close()
            }
            drawPath(path = pinPath, color = CrimsonRed)
            drawCircle(color = Color.White, radius = 2.8.dp.toPx(), center = center)
        }

        // Top-Left & Top-Right HUD Overlay Badges on Radar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack.copy(alpha = 0.78f))
                    .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "ANCHOR: ${activePin.zoneTag}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.4f°N, %.4f°E", riderLat, riderLng),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudPrimary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack.copy(alpha = 0.78f))
                    .border(
                        1.dp,
                        if (isLocked) EmeraldSRank.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLocked) EmeraldSRank else AmberWarning)
                    )
                    Text(
                        text = if (isLocked) "FIXPOINT LOCKED" else "DRIFT STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLocked) EmeraldSRank else AmberWarning,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "รับ ≤ ${GeoMath.format1Dec(maxPickupRadiusKm.toDouble())}km | ส่ง ≤ ${GeoMath.format1Dec(maxDropoffRadiusKm.toDouble())}km",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }
        }

        // Bottom Legend Strip inside Radar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ObsidianBlack.copy(alpha = 0.82f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarLegendDot(color = CrimsonRed, label = "วงแหวนรับ (${GeoMath.format1Dec(maxPickupRadiusKm.toDouble())} กม.)")
            RadarLegendDot(color = CrimsonNeon, label = "วงแหวนส่ง (${GeoMath.format1Dec(maxDropoffRadiusKm.toDouble())} กม.)")
            RadarLegendDot(color = CyanTelemetry, label = "ดงร้าน (${merchants.size})")
            RadarLegendDot(color = EmeraldSRank, label = "จุดรับ-ส่งงาน")
        }
    }
}

@Composable
private fun RadarLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextHudPrimary
        )
    }
}
