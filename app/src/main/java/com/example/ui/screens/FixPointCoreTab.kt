package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.RiderPlatform
import com.example.model.TacticalLockMode
import com.example.ui.components.CrimsonGlassCard
import com.example.ui.components.TacticalLeafletMapView
import com.example.ui.components.TacticalRadarCanvas
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
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun FixPointCoreTab(
    uiState: FixPointUiState,
    onSelectPlatform: (RiderPlatform) -> Unit,
    onUpdatePickupRadius: (Float) -> Unit,
    onUpdateDropoffRadius: (Float) -> Unit,
    onSelectLockMode: (TacticalLockMode) -> Unit,
    onToggleEngageLock: () -> Unit,
    onMapPinClicked: (Double, Double) -> Unit,
    onRequestGpsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Allow switching between Interactive Leaflet CartoDB Dark Matter Map, 360 Radar HUD, or Dual View
    var visualizerMode by remember { mutableStateOf("MAP_AND_RADAR") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Platform Mode Selector
        CrimsonGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLATFORM MODE SELECTOR (โหมดค่ายแอปวิ่งงาน)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon
                )
                Text(
                    text = "${uiState.selectedPlatform.gpsHz}Hz Keep-Alive",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSRank
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiderPlatform.entries.forEach { platform ->
                    val isSelected = uiState.selectedPlatform == platform
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) {
                                    platform.accentColor.copy(alpha = 0.22f)
                                } else {
                                    ObsidianBlack.copy(alpha = 0.65f)
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) platform.accentColor else CrimsonBorderGlow,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPlatform(platform) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("platform_chip_${platform.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(platform.accentColor)
                            )
                            Text(
                                text = platform.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                                color = if (isSelected) TextHudPrimary else TextHudSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚡ โปรไฟล์ ${uiState.selectedPlatform.displayName}: ${uiState.selectedPlatform.algorithmNote}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextHudSecondary
            )
        }

        // 2. Display Mode Switcher: CartoDB Dark Matter Map + Dual-Ring Tactical Radar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VisualizerToggleButton(
                title = "แผนที่จริง + เรดาร์",
                icon = Icons.Default.Map,
                selected = visualizerMode == "MAP_AND_RADAR",
                onClick = { visualizerMode = "MAP_AND_RADAR" },
                modifier = Modifier.weight(1f),
                testTag = "mode_map_and_radar"
            )
            VisualizerToggleButton(
                title = "CartoDB Map ล้วน",
                icon = Icons.Default.Map,
                selected = visualizerMode == "MAP_ONLY",
                onClick = { visualizerMode = "MAP_ONLY" },
                modifier = Modifier.weight(1f),
                testTag = "mode_map_only"
            )
            VisualizerToggleButton(
                title = "360° Radar ล้วน",
                icon = Icons.Default.Radar,
                selected = visualizerMode == "RADAR_ONLY",
                onClick = { visualizerMode = "RADAR_ONLY" },
                modifier = Modifier.weight(1f),
                testTag = "mode_radar_only"
            )
        }

        if (visualizerMode == "MAP_AND_RADAR" || visualizerMode == "MAP_ONLY") {
            TacticalLeafletMapView(
                activePin = uiState.activePin,
                maxPickupRadiusKm = uiState.maxPickupRadiusKm,
                maxDropoffRadiusKm = uiState.maxDropoffRadiusKm,
                isLocked = uiState.isFixPointLocked,
                merchants = uiState.clusterMerchants,
                goldenCentroidLat = uiState.goldenCentroidLat,
                goldenCentroidLng = uiState.goldenCentroidLng,
                orderEvaluation = uiState.currentOrderEvaluation,
                onMapPinClicked = onMapPinClicked,
                testTagName = "core_leaflet_map"
            )
        }

        if (visualizerMode == "MAP_AND_RADAR" || visualizerMode == "RADAR_ONLY") {
            TacticalRadarCanvas(
                activePin = uiState.activePin,
                riderLat = uiState.riderLat,
                riderLng = uiState.riderLng,
                maxPickupRadiusKm = uiState.maxPickupRadiusKm,
                maxDropoffRadiusKm = uiState.maxDropoffRadiusKm,
                isLocked = uiState.isFixPointLocked,
                shockwaveTriggerCount = uiState.shockwaveTriggerCount,
                merchants = uiState.clusterMerchants,
                orderEvaluation = uiState.currentOrderEvaluation
            )
        }

        Button(
            onClick = onToggleEngageLock,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isFixPointLocked) CrimsonRed else AmberWarning,
                contentColor = if (uiState.isFixPointLocked) Color.White else ObsidianBlack
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(
                    width = 1.5.dp,
                    color = if (uiState.isFixPointLocked) CrimsonNeon else AmberWarning,
                    shape = RoundedCornerShape(14.dp)
                )
                .testTag("engage_fixpoint_lock_button")
        ) {
            Icon(
                imageVector = if (uiState.isFixPointLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (uiState.isFixPointLocked) {
                    "ENGAGE FIXPOINT LOCK • ตรึงพิกัดหมุดหลักทำงานอยู่"
                } else {
                    "ENGAGE FIXPOINT LOCK • กดเพื่อล็อคสัญญาณเข้าหมุด"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // 3. Dual-Radius Sliders (ตั้งค่ารัศมีรับใกล้หมุด & ส่งใกล้หมุด)
        CrimsonGlassCard {
            Text(
                text = "DUAL-RADIUS LOOP GUARD (ตั้งค่ารัศมีรับใกล้-ส่งใกล้หมุด)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonNeon
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Slider 1: Max Pickup Distance from Pin (0.1 km - 3.0 km)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. Max Pickup Distance from Pin",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "วงกลมสีแดงทึบบนแผนที่: รัศมีรับงานใกล้หมุดหลัก",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonRed.copy(alpha = 0.2f))
                        .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${GeoMath.formatKm(uiState.maxPickupRadiusKm.toDouble())} กม.",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonNeon
                    )
                }
            }

            Slider(
                value = uiState.maxPickupRadiusKm,
                onValueChange = onUpdatePickupRadius,
                valueRange = 0.1f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pickup_radius_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.1 กม. (ติดหมุด)", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                Text("แนะนำ: 0.8 กม.", style = MaterialTheme.typography.labelSmall, color = EmeraldSRank)
                Text("3.0 กม.", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Slider 2: Max Drop-off Distance from Pin (0.5 km - 8.0 km)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2. Max Drop-off Distance from Pin",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "วงกลมเส้นประเรืองแสง: รัศมีจุดส่งไม่เกินหมุดเดิม",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldSRank.copy(alpha = 0.18f))
                        .border(1.dp, EmeraldSRank, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${GeoMath.format1Dec(uiState.maxDropoffRadiusKm.toDouble())} กม.",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldSRank
                    )
                }
            }

            Slider(
                value = uiState.maxDropoffRadiusKm,
                onValueChange = onUpdateDropoffRadius,
                valueRange = 0.5f..8.0f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldSRank,
                    activeTrackColor = EmeraldSRank,
                    inactiveTrackColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dropoff_radius_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.5 กม. (วนสั้นจัด)", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                Text("ค่าเริ่มต้น: 2.0 กม.", style = MaterialTheme.typography.labelSmall, color = EmeraldSRank)
                Text("8.0 กม.", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
            }
        }

        // 4. 3 Tactical GPS Lock Modes (3 โหมดตรึงสัญญาณ)
        CrimsonGlassCard {
            Text(
                text = "TACTICAL GPS LOCK MODES (3 โหมดตรึงสัญญาณ)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonNeon
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TacticalLockMode.entries.forEach { mode ->
                    val selected = uiState.lockMode == mode
                    val icon = when (mode) {
                        TacticalLockMode.PIN_CENTER_MICRO -> Icons.Default.GpsFixed
                        TacticalLockMode.MERCHANT_CENTROID -> Icons.Default.Hub
                        TacticalLockMode.AUTO_LOOP_COMPASS -> Icons.Default.Explore
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) DeepCrimsonGlow.copy(alpha = 0.36f)
                                else ObsidianBlack.copy(alpha = 0.65f)
                            )
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) CrimsonNeon else CrimsonBorderGlow,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectLockMode(mode) }
                            .padding(12.dp)
                            .testTag("lock_mode_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) CrimsonRed else CarbonDark)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) Color.White else TextHudMuted
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.titleEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) TextHudPrimary else TextHudSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Kalman ${(mode.kalmanGain * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) EmeraldSRank else TextHudMuted
                                )
                            }
                            Text(
                                text = mode.titleTh,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = CrimsonNeon
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextHudMuted
                            )
                        }
                    }
                }
            }

            // Auto Loop-Back Compass Live Banner
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .border(1.dp, CyanTelemetry.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = null,
                        tint = CyanTelemetry,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AUTO LOOP-BACK COMPASS: ทิศกลับหมุด ${uiState.loopBackDirectionText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanTelemetry
                        )
                        Text(
                            text = "ระยะห่างหมุด ${uiState.distanceToPinMeters} เมตร • เวลาตีรถกลับประมาณ ${uiState.estReturnMinutes} นาที",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextHudPrimary
                        )
                    }
                }
                Text(
                    text = "${uiState.loopBackBearingDeg.toInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSRank
                )
            }
        }

        // 5. Live Telemetry Cards
        CrimsonGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE GNSS TELEMETRY (สัญญาณพิกัดสด)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (uiState.hasLocationPermission) EmeraldSRank.copy(alpha = 0.18f)
                            else CrimsonRed.copy(alpha = 0.22f)
                        )
                        .border(
                            1.dp,
                            if (uiState.hasLocationPermission) EmeraldSRank else CrimsonNeon,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onRequestGpsPermission() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("sync_hardware_gps_button")
                ) {
                    Text(
                        text = if (uiState.hasLocationPermission) "HARDWARE GPS: ACTIVE" else "เชื่อมต่อ GPS เครื่องจริง",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.hasLocationPermission) EmeraldSRank else TextHudPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricTile(
                    icon = Icons.Default.MyLocation,
                    label = "พิกัดล็อคปัจจุบัน",
                    value = String.format(Locale.US, "%.4f, %.4f", uiState.riderLat, uiState.riderLng),
                    subValue = "Kalman Anti-Drift ลดแกว่ง ${uiState.kalmanDriftSuppressedMeters}m",
                    valueColor = TextHudPrimary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    icon = Icons.Default.GpsFixed,
                    label = "ห่างหมุดหลัก (Dist to Pin)",
                    value = "${GeoMath.formatKm(uiState.distanceToPinKm)} km (${uiState.distanceToPinMeters}m)",
                    subValue = if (uiState.distanceToPinKm <= uiState.maxPickupRadiusKm) "อยู่ในโซนรับใกล้หมุด" else "อยู่นอกรัศมีรับ",
                    valueColor = if (uiState.distanceToPinKm <= uiState.maxPickupRadiusKm) EmeraldSRank else AmberWarning,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricTile(
                    icon = Icons.Default.Bolt,
                    label = "ความแม่นยำ (Accuracy)",
                    value = "±${String.format(Locale.US, "%.1f", uiState.accuracyMeters)} เมตร",
                    subValue = "โหมด ${uiState.lockMode.titleEn}",
                    valueColor = CyanTelemetry,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    icon = Icons.Default.SatelliteAlt,
                    label = "ดาวเทียม & ความเสถียร",
                    value = "${uiState.satelliteCount} ดวง • ${uiState.signalStabilityPercent}%",
                    subValue = "GPS + GLONASS + Galileo + BeiDou",
                    valueColor = EmeraldSRank,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VisualizerToggleButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CrimsonRed.copy(alpha = 0.25f) else CarbonDark)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CrimsonNeon else CrimsonBorderGlow,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) CrimsonNeon else TextHudMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = if (selected) TextHudPrimary else TextHudSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TelemetryMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBlack.copy(alpha = 0.78f))
            .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CrimsonNeon,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextHudMuted
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextHudSecondary
        )
    }
}
