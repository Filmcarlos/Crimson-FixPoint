package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.ui.components.TacticalRadarCanvas
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

/**
 * High-contrast Fullscreen Motorcycle Mount HUD Mode for delivery riders on the road.
 */
@Composable
fun BikeMountFullscreenHud(
    uiState: FixPointUiState,
    onCloseHud: () -> Unit,
    onSimulateNextOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCloseHud)

    val eval = uiState.currentOrderEvaluation

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBlack, Color(0xFF14070B), ObsidianBlack)
                )
            )
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top HUD Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CarbonDark)
                .border(1.5.dp, CrimsonNeon, RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning)
                    )
                    Text(
                        text = if (uiState.isFixPointLocked) "BIKE HUD • LOCKED" else "BIKE HUD • STANDBY",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning
                    )
                }
                Text(
                    text = "หมุด: ${uiState.activePin.zoneTag} (${uiState.selectedPlatform.displayName})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHudSecondary
                )
            }

            Button(
                onClick = onCloseHud,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("close_bike_mount_hud_button")
            ) {
                Icon(imageVector = Icons.Default.CloseFullscreen, contentDescription = "ปิดโหมดเต็มจอ", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ย่อกลับ", fontWeight = FontWeight.Bold)
            }
        }

        // Giant Telemetry Readouts for Glanceability on Motorcycle Handlebar Mount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonDark)
                    .border(1.5.dp, CrimsonBorderGlow, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ระยะห่างหมุดหลัก", style = MaterialTheme.typography.labelMedium, color = TextHudMuted)
                Text(
                    text = "${GeoMath.formatKm(uiState.distanceToPinKm)} km",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
                    color = EmeraldSRank,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(${uiState.distanceToPinMeters} เมตร • ±${uiState.accuracyMeters}m)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonDark)
                    .border(1.5.dp, CyanTelemetry.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = Icons.Default.CompassCalibration, contentDescription = null, tint = CyanTelemetry, modifier = Modifier.size(16.dp))
                    Text("ทิศตีรถกลับหมุด", style = MaterialTheme.typography.labelMedium, color = CyanTelemetry)
                }
                Text(
                    text = "${uiState.loopBackBearingDeg.toInt()}°",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.loopBackDirectionText} (~${uiState.estReturnMinutes} นาที)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary
                )
            }
        }

        // Radar in Center
        TacticalRadarCanvas(
            activePin = uiState.activePin,
            riderLat = uiState.riderLat,
            riderLng = uiState.riderLng,
            maxPickupRadiusKm = uiState.maxPickupRadiusKm,
            maxDropoffRadiusKm = uiState.maxDropoffRadiusKm,
            isLocked = uiState.isFixPointLocked,
            shockwaveTriggerCount = uiState.shockwaveTriggerCount,
            merchants = uiState.clusterMerchants,
            orderEvaluation = eval
        )

        // Instant Order Verdict Banner at Bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(eval.grade.color.copy(alpha = 0.18f))
                .border(2.dp, eval.grade.color, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = eval.grade.badgeTitle,
                style = MaterialTheme.typography.titleLarge,
                color = eval.grade.color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${eval.order.merchantName} -> ${eval.order.dropoffName}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextHudPrimary,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "รับห่าง ${GeoMath.formatKm(eval.pickupToPinKm)} km | ส่งห่าง ${GeoMath.formatKm(eval.dropoffToPinKm)} km | ฿${eval.order.payoutBaht}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextHudSecondary
                )
                Button(
                    onClick = onSimulateNextOrder,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("สแกนงานถัดไป")
                }
            }
        }
    }
}
