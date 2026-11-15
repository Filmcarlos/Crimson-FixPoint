package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.model.ZoneRadiusPreset
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState

/**
 * Chamfered Octagonal Control Panel for Target Pin Lock, Delivery Zone Radius (300m / 500m / 1km / 2km),
 * Real-Time Distance & Bearing Readout, Out-of-Zone Alert Toggle, and One-Tap Return Navigation.
 */
@Composable
fun TargetPinZonePanel(
    uiState: FixPointUiState,
    onPinCurrentLocation: () -> Unit,
    onNavigateToTargetPin: (Double, Double, String) -> Unit,
    onClearTargetPin: () -> Unit,
    onSelectZoneRadius: (ZoneRadiusPreset) -> Unit,
    onToggleOutOfZoneAlert: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetPin = uiState.targetPin
    val proximity = uiState.targetProximity
    val hasGpsFix = uiState.telemetry.latitude != null && uiState.telemetry.longitude != null

    val panelBorderColor = when {
        proximity != null && !proximity.isInsideZone -> AmberWarning
        proximity != null && proximity.isInsideZone -> CrimsonNeon
        targetPin != null -> CrimsonNeon.copy(alpha = 0.7f)
        else -> CrimsonBorderGlow
    }

    CrimsonPrecisionPanel(
        borderColor = panelBorderColor,
        glowAlpha = if (targetPin != null) 0.24f else 0.10f,
        modifier = modifier.testTag("target_pin_zone_panel")
    ) {
        // 1. Header & Zone Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "โซนหมุดเป้าหมายรับ-ส่งอาหาร",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (targetPin != null) {
                            "หมุดปัจจุบัน: ${targetPin.name}"
                        } else {
                            "ยังไม่ได้กำหนดหมุดเป้าหมาย"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = if (targetPin != null) CrimsonNeon else TextHudMuted,
                        modifier = Modifier.testTag("target_pin_name")
                    )
                }
            }

            val badgeText = when {
                proximity != null -> proximity.zoneBadgeTh
                targetPin != null -> "ล็อกหมุดแล้ว"
                else -> "ว่าง"
            }
            val badgeColor = when {
                proximity != null && !proximity.isInsideZone -> AmberWarning
                proximity != null && proximity.isInsideZone -> EmeraldSRank
                targetPin != null -> CrimsonNeon
                else -> TextHudMuted
            }

            Box(
                modifier = Modifier
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF120B0F))
                    .border(1.dp, badgeColor, PrecisionButtonShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("target_zone_status_badge")
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Real-Time Distance & Bearing to Target Pin
        if (targetPin != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF09080C))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "พิกัดหมุด: ${
                            GeoMath.formatCoordinatePair(
                                targetPin.latitude,
                                targetPin.longitude,
                                uiState.coordinateFormat
                            )
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextHudSecondary,
                        modifier = Modifier.testTag("target_pin_coordinates")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PrecisionButtonShape)
                                .background(ObsidianBlack)
                                .border(1.dp, Color(0xFF2A1820), PrecisionButtonShape)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("target_pin_distance")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = null,
                                    tint = CrimsonNeon,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "ระยะห่างจากหมุด",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextHudMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = GeoMath.formatTargetDistance(
                                    proximity?.distanceMeters,
                                    uiState.measurementUnit
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (proximity != null && !proximity.isInsideZone) {
                                    AmberWarning
                                } else {
                                    TextHudPrimary
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PrecisionButtonShape)
                                .background(ObsidianBlack)
                                .border(1.dp, Color(0xFF2A1820), PrecisionButtonShape)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("target_pin_bearing")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = CrimsonNeon,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "ทิศทางไปหมุด",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextHudMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = GeoMath.cardinalDirectionTh(proximity?.bearingToPinDegrees),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextHudPrimary
                            )
                        }
                    }

                    // Zone Proximity Progress Bar & Status Line
                    if (proximity != null) {
                        val statusDetail = if (proximity.isInsideZone) {
                            "อยู่ในรัศมีโซนรับงาน (${uiState.zoneRadiusPreset.shortLabelTh})"
                        } else {
                            val overFormatted = GeoMath.formatTargetDistance(
                                proximity.overflowMeters,
                                uiState.measurementUnit
                            )
                            "ออกนอกรัศมีโซนรับงาน เกินระยะ $overFormatted"
                        }

                        Text(
                            text = statusDetail,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (proximity.isInsideZone) EmeraldSRank else AmberWarning,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("target_zone_detail_text")
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(PrecisionButtonShape)
                                .background(Color(0xFF1A1418))
                        ) {
                            val barFill = if (proximity.isInsideZone) {
                                proximity.proximityRatio.coerceIn(0.15f, 1f)
                            } else {
                                1f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barFill)
                                    .fillMaxHeight()
                                    .background(
                                        if (proximity.isInsideZone) {
                                            Brush.horizontalGradient(listOf(CrimsonRed, EmeraldSRank))
                                        } else {
                                            Brush.horizontalGradient(listOf(CrimsonRed, AmberWarning))
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Zone Radius Preset Selector (300m, 500m, 1km, 2km)
        Text(
            text = "รัศมีโซนรับงานรอบหมุดเป้าหมาย",
            style = MaterialTheme.typography.labelMedium,
            color = TextHudSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZoneRadiusPreset.entries.forEach { preset ->
                val selected = uiState.zoneRadiusPreset == preset
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(PrecisionButtonShape)
                        .background(
                            if (selected) {
                                Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF111016), Color(0xFF0B0A0E)))
                            }
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) CrimsonNeon else CrimsonBorderGlow,
                            shape = PrecisionButtonShape
                        )
                        .clickable { onSelectZoneRadius(preset) }
                        .padding(vertical = 10.dp)
                        .testTag("zone_radius_${preset.name.lowercase()}")
                ) {
                    Text(
                        text = preset.shortLabelTh,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = if (selected) Color.White else TextHudSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Out-of-Zone Alert Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(PrecisionButtonShape)
                .background(Color(0xFF0A090E))
                .border(
                    width = 1.dp,
                    color = if (uiState.outOfZoneAlertEnabled) CrimsonNeon.copy(alpha = 0.7f) else CrimsonBorderGlow,
                    shape = PrecisionButtonShape
                )
                .clickable { onToggleOutOfZoneAlert(!uiState.outOfZoneAlertEnabled) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("toggle_out_of_zone_alert"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (uiState.outOfZoneAlertEnabled) {
                        Icons.Default.NotificationsActive
                    } else {
                        Icons.Default.NotificationsOff
                    },
                    contentDescription = null,
                    tint = if (uiState.outOfZoneAlertEnabled) CrimsonNeon else TextHudMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "แจ้งเตือนเมื่อออกนอกรัศมีหมุด",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = if (uiState.outOfZoneAlertEnabled) "เปิด" else "ปิด",
                style = MaterialTheme.typography.labelLarge,
                color = if (uiState.outOfZoneAlertEnabled) CrimsonNeon else TextHudMuted,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Target Pin & Return Navigation Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChamferedActionButton(
                label = "ปักหมุดจุดจอดปัจจุบัน",
                icon = Icons.Default.MyLocation,
                enabled = hasGpsFix,
                isPrimaryCrimson = targetPin == null && hasGpsFix,
                onClick = onPinCurrentLocation,
                modifier = Modifier.weight(1f),
                testTag = "btn_set_current_target_pin"
            )

            if (targetPin != null) {
                ChamferedActionButton(
                    label = "ยกเลิกหมุด",
                    icon = Icons.Default.Close,
                    onClick = onClearTargetPin,
                    modifier = Modifier.weight(0.65f),
                    testTag = "btn_clear_target_pin"
                )
            }
        }

        if (targetPin != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ChamferedActionButton(
                label = "นำทางกลับหมุดเป้าหมาย",
                icon = Icons.Default.Navigation,
                isPrimaryCrimson = true,
                onClick = {
                    onNavigateToTargetPin(targetPin.latitude, targetPin.longitude, targetPin.name)
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_navigate_to_target_pin"
            )
        }
    }
}
