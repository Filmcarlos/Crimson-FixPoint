package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GeoMath
import com.example.model.OrderGrade
import com.example.ui.components.CrimsonGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale

/**
 * Tab 4: FLOATING HUD & DIAGNOSTICS (โหมดหน้าปัดลอยทับหน้าจอ & กราฟสัญญาณดาวเทียม GNSS)
 */
@Composable
fun FloatingHudDiagnosticsTab(
    uiState: FixPointUiState,
    onOpenFullBikeMountHud: () -> Unit,
    onToggleFloatingHudStrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eval = uiState.currentOrderEvaluation

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Rider Floating HUD Preview Card + Bike Mount Fullscreen Trigger
        CrimsonGlassCard(borderColor = CrimsonNeon) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_tactical_hud_banner_1794580872742),
                    contentDescription = "Tactical HUD Radar Preview Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBlack.copy(alpha = 0.25f),
                                    ObsidianBlack.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "COMPACT RIDER FLOATING HUD PREVIEW",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonNeon
                    )
                    Text(
                        text = "โหมดหน้าปัดลอยกะทัดรัดสำหรับมองขณะวางโทรศัพท์บนที่ยึดมือถือมอเตอร์ไซค์",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Floating Pill Widget Preview (as requested: LOCKED, distance 0.12 km, dropoff <= 2.0 km, latest order grade)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianBlack)
                    .border(1.5.dp, CrimsonNeon, RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("floating_hud_widget_preview")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning)
                            )
                            Text(
                                text = if (uiState.isFixPointLocked) "STATUS: LOCKED" else "STATUS: STANDBY",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning
                            )
                        }

                        Text(
                            text = "${uiState.selectedPlatform.displayName} • ${uiState.selectedPlatform.gpsHz}Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanTelemetry
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HudPreviewStat(
                            label = "ระยะห่างจากหมุด",
                            value = "${GeoMath.formatKm(uiState.distanceToPinKm)} km",
                            valueColor = TextHudPrimary
                        )
                        HudPreviewStat(
                            label = "รัศมีคุมจุดส่ง",
                            value = "≤ ${GeoMath.format1Dec(uiState.maxDropoffRadiusKm.toDouble())} km",
                            valueColor = CrimsonNeon
                        )
                        HudPreviewStat(
                            label = "สถานะงานล่าสุด",
                            value = when (eval.grade) {
                                OrderGrade.S_RANK_LOOP -> "S-RANK วนหมุด"
                                OrderGrade.A_RANK_IN_ZONE -> "A-RANK ในโซน"
                                OrderGrade.REJECT_OUT_OF_ZONE -> "REJECT หลุดโซน"
                            },
                            valueColor = eval.grade.color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenFullBikeMountHud,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_full_bike_hud_button")
                ) {
                    Icon(imageVector = Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "เปิดหน้าปัดมอเตอร์ไซค์เต็มจอ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 2. GNSS SNR Matrix (กราฟแท่งแสดงความแรงสัญญาณดาวเทียม GPS, GLONASS, Galileo, BeiDou)
        CrimsonGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = EmeraldSRank
                    )
                    Column {
                        Text(
                            text = "GNSS SNR MATRIX (ความแรงสัญญาณดาวเทียม)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CrimsonNeon
                        )
                        Text(
                            text = "GPS • GLONASS • Galileo • BeiDou (หน่วย dB-Hz)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                            color = TextHudMuted
                        )
                    }
                }

                Text(
                    text = "FIX: ${uiState.satelliteCount} SATS",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSRank
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Vertical Bar Chart for Satellites
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                uiState.satellites.forEach { sat ->
                    val fraction = (sat.snrDbHz / 50f).coerceIn(0.15f, 1.0f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = "${sat.snrDbHz}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextHudPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(fraction * 0.72f)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            sat.color,
                                            sat.color.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sat.svId,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = sat.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🔴 GPS (USA)", style = MaterialTheme.typography.labelSmall, color = CrimsonNeon)
                Text("🟢 GLONASS", style = MaterialTheme.typography.labelSmall, color = EmeraldSRank)
                Text("🔵 Galileo", style = MaterialTheme.typography.labelSmall, color = CyanTelemetry)
                Text("🟡 BeiDou", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
            }
        }

        // 3. Drift & Short-Loop Order History Log Table
        CrimsonGlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CrimsonNeon
                )
                Text(
                    text = "DRIFT & ORDER FILTER LOG (ประวัติล็อคหมุดและคัดงาน)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.driftLogs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBlack.copy(alpha = 0.78f))
                            .border(
                                1.dp,
                                if (log.isPositive) EmeraldSRank.copy(alpha = 0.35f) else AmberWarning.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = log.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry
                                )
                                Text(
                                    text = "• ${log.pinName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextHudMuted
                                )
                            }
                            Text(
                                text = log.eventTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextHudPrimary
                            )
                            Text(
                                text = "ห่างหมุดรับ ${GeoMath.formatKm(log.pickupDistKm)} km | จุดส่ง ${GeoMath.formatKm(log.dropoffDistKm)} km",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHudSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (log.isPositive) EmeraldSRank.copy(alpha = 0.18f)
                                    else AmberWarning.copy(alpha = 0.18f)
                                )
                                .border(
                                    1.dp,
                                    if (log.isPositive) EmeraldSRank else AmberWarning,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = log.statusTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.isPositive) EmeraldSRank else AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudPreviewStat(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextHudMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
