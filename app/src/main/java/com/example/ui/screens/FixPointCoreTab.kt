package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.ZoneMonitorStatus
import com.example.ui.components.CentralPowerCoreDial
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.InteractiveZoneMapCanvas
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.components.RainForecastHomeCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
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
    onTogglePower: () -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    onShareCoordinates: (Double, Double) -> Unit,
    onSaveLocation: (String) -> Unit,
    onMapTapPin: (Double, Double) -> Unit,
    onMuteZoneAlert: () -> Unit,
    onNavigateToZoneTab: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshRainForecast: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val hasCoordinates = telemetry.latitude != null && telemetry.longitude != null
    var showSaveDialog by remember { mutableStateOf(false) }
    var locationNameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showSaveDialog && hasCoordinates) {
            CrimsonPrecisionPanel(
                borderColor = CrimsonNeon,
                glowAlpha = 0.25f,
                modifier = Modifier.testTag("dialog_save_location")
            ) {
                Text(
                    text = "ปักหมุดตำแหน่งปัจจุบัน",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHudPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = GeoMath.formatCoordinatePair(
                        telemetry.latitude,
                        telemetry.longitude,
                        uiState.coordinateFormat
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = locationNameInput,
                    onValueChange = { locationNameInput = it },
                    label = { Text("ชื่อหมุดโซนรับงาน") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary,
                        focusedLabelColor = CrimsonNeon,
                        unfocusedLabelColor = TextHudMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_save_location_name")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChamferedActionButton(
                        label = "ยกเลิก",
                        icon = Icons.Default.Close,
                        onClick = { showSaveDialog = false },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_cancel_save_location"
                    )
                    ChamferedActionButton(
                        label = "บันทึกหมุด",
                        icon = Icons.Default.BookmarkAdd,
                        isPrimaryCrimson = true,
                        onClick = {
                            onSaveLocation(locationNameInput)
                            locationNameInput = ""
                            showSaveDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_confirm_save_location"
                    )
                }
            }
        }

        // Permission Denied Handling Panel
        if (!uiState.hasAnyLocationPermission || uiState.permissionDeniedMessageTh != null) {
            CrimsonPrecisionPanel(
                borderColor = AmberWarning,
                glowAlpha = 0.18f,
                modifier = Modifier.testTag("permission_warning_panel")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = uiState.permissionDeniedMessageTh ?: "ต้องการสิทธิ์การเข้าถึงตำแหน่งแม่นยำ",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChamferedActionButton(
                        label = "อนุญาตสิทธิ์ตำแหน่ง",
                        icon = Icons.Default.GpsFixed,
                        isPrimaryCrimson = true,
                        onClick = onRequestLocationPermission,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_request_location_permission"
                    )
                    ChamferedActionButton(
                        label = "ตั้งค่าระบบ",
                        icon = Icons.Default.Settings,
                        onClick = onOpenAppSettings,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_open_system_settings"
                    )
                }
            }
        }

        // 1. Work-Zone Status Card: Green when INSIDE_ZONE, Blinking Red when OUTSIDE_ZONE
        ZoneMonitorStatusCard(
            uiState = uiState,
            onMuteZoneAlert = onMuteZoneAlert,
            onNavigateToZoneTab = onNavigateToZoneTab
        )

        // 1b. Rain Forecast Card (Open-Meteo: "ไม่มีฝน" or "ฝนจะตกในอีก X นาที โอกาส Y%" or "ดึงพยากรณ์ไม่ได้")
        RainForecastHomeCard(
            rainForecast = uiState.rainForecast,
            onRefreshForecast = onRefreshRainForecast
        )

        // 2. Centerpiece Main Power Toggle Button (ปุ่มเปิด/ปิดขนาดใหญ่ตรงกลางหน้าหลัก)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            CentralPowerCoreDial(
                lockState = uiState.lockState,
                accuracyMeters = telemetry.accuracyMeters,
                onTogglePower = onTogglePower
            )
        }

        // 3. Primary Coordinate Readout Panel (พิกัดละติจูด / ลองจิจูด + ปุ่มคำสั่ง)
        CrimsonPrecisionPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "พิกัดตำแหน่งดาวเทียม",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudSecondary
                )
                Text(
                    text = if (telemetry.hasFix) "รับข้อมูลจริงจากเซ็นเซอร์" else "รอสัญญาณดาวเทียม",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (telemetry.hasFix) CrimsonNeon else TextHudMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CoordinateValueBox(
                    label = "ละติจูด (LATITUDE)",
                    value = GeoMath.formatCoordinate(
                        telemetry.latitude,
                        isLatitude = true,
                        format = uiState.coordinateFormat
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("readout_latitude")
                )
                CoordinateValueBox(
                    label = "ลองจิจูด (LONGITUDE)",
                    value = GeoMath.formatCoordinate(
                        telemetry.longitude,
                        isLatitude = false,
                        format = uiState.coordinateFormat
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("readout_longitude")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons (Copy, Share, Pin Current Location, Configure Zone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChamferedActionButton(
                    label = "คัดลอกพิกัด",
                    icon = Icons.Default.ContentCopy,
                    enabled = hasCoordinates,
                    onClick = {
                        val lat = telemetry.latitude
                        val lng = telemetry.longitude
                        if (lat != null && lng != null) {
                            onCopyCoordinates(lat, lng)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_copy_coordinates"
                )
                ChamferedActionButton(
                    label = "แชร์พิกัด",
                    icon = Icons.Default.Share,
                    enabled = hasCoordinates,
                    onClick = {
                        val lat = telemetry.latitude
                        val lng = telemetry.longitude
                        if (lat != null && lng != null) {
                            onShareCoordinates(lat, lng)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_share_coordinates"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChamferedActionButton(
                    label = "ตั้งค่ารัศมีโซน",
                    icon = Icons.Default.Radar,
                    onClick = onNavigateToZoneTab,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_open_maps"
                )
                ChamferedActionButton(
                    label = "บันทึกตำแหน่ง",
                    icon = Icons.Default.BookmarkAdd,
                    enabled = hasCoordinates,
                    isPrimaryCrimson = hasCoordinates,
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_save_location"
                )
            }
        }

        // 4. Real Sensor Telemetry Grid
        CrimsonPrecisionPanel {
            Text(
                text = "ข้อมูลพารามิเตอร์จากเซ็นเซอร์จริง",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SensorMetricTile(
                    icon = Icons.Default.GpsFixed,
                    title = "ความแม่นยำ",
                    value = GeoMath.formatDistanceMeters(telemetry.accuracyMeters, uiState.measurementUnit),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_accuracy")
                )
                SensorMetricTile(
                    icon = Icons.Default.Landscape,
                    title = "ความสูงจากระดับน้ำทะเล",
                    value = GeoMath.formatAltitude(telemetry.altitudeMeters, uiState.measurementUnit),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_altitude")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SensorMetricTile(
                    icon = Icons.Default.Speed,
                    title = "ความเร็ว",
                    value = GeoMath.formatSpeed(telemetry.speedMetersPerSec, uiState.measurementUnit),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_speed")
                )
                SensorMetricTile(
                    icon = Icons.Default.Explore,
                    title = "ทิศทาง",
                    value = GeoMath.cardinalDirectionTh(telemetry.effectiveDirectionDegrees),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_direction")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SensorMetricTile(
                    icon = Icons.Default.SatelliteAlt,
                    title = "ดาวเทียม (ใช้งาน/มองเห็น)",
                    value = "${telemetry.satellitesUsedInFix} / ${telemetry.satellitesVisible} ดวง",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_satellites")
                )
                SensorMetricTile(
                    icon = Icons.Default.SignalCellularAlt,
                    title = "คุณภาพสัญญาณ",
                    value = telemetry.signalQualityLabelTh,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_signal_quality")
                )
            }

            if (telemetry.satelliteBars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ความแรงสัญญาณดาวเทียมรายดวง (C/N0 dB-Hz)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextHudMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF09080C))
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    telemetry.satelliteBars.take(10).forEach { bar ->
                        val heightFraction = (bar.cn0DbHz / 50f).coerceIn(0.12f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .fillMaxHeight(heightFraction)
                                        .background(
                                            if (bar.usedInFix) {
                                                Brush.verticalGradient(listOf(CrimsonNeon, CrimsonRed))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0xFF52525B), Color(0xFF27272A)))
                                            }
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${bar.svid}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (bar.usedInFix) TextHudPrimary else TextHudMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(160.dp))
    }
}

/**
 * Prominent Work-Zone Status Card on Main Screen:
 * - INSIDE_ZONE: Emerald Green (#00E676) glowing border & status badge
 * - OUTSIDE_ZONE: Blinking Crimson Red (#F43F5E) alert panel with excess distance + Mute Alert button
 */
@Composable
private fun ZoneMonitorStatusCard(
    uiState: FixPointUiState,
    onMuteZoneAlert: () -> Unit,
    onNavigateToZoneTab: () -> Unit
) {
    val status = uiState.zoneStatus
    val activePin = uiState.activePin

    val blinkFraction: Float = if (status == ZoneMonitorStatus.OUTSIDE_ZONE) {
        val infiniteTransition = rememberInfiniteTransition(label = "zone_status_blink")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outside_zone_blink_alpha"
        )
        anim
    } else {
        1.0f
    }

    val panelBorderColor = when (status) {
        ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank
        ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon.copy(alpha = blinkFraction)
        ZoneMonitorStatus.WAITING_FOR_GPS -> AmberWarning.copy(alpha = 0.75f)
        ZoneMonitorStatus.NO_ACTIVE_PIN -> CrimsonBorderGlow
    }

    val statusAccentColor = when (status) {
        ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank
        ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
        ZoneMonitorStatus.WAITING_FOR_GPS -> AmberWarning
        ZoneMonitorStatus.NO_ACTIVE_PIN -> TextHudSecondary
    }

    CrimsonPrecisionPanel(
        borderColor = panelBorderColor,
        glowAlpha = when (status) {
            ZoneMonitorStatus.OUTSIDE_ZONE -> 0.35f * blinkFraction
            ZoneMonitorStatus.INSIDE_ZONE -> 0.18f
            else -> 0.12f
        },
        modifier = Modifier.testTag("zone_status_banner_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusAccentColor.copy(alpha = 0.18f * blinkFraction))
                        .border(1.5.dp, statusAccentColor.copy(alpha = blinkFraction), CircleShape)
                ) {
                    Icon(
                        imageVector = when (status) {
                            ZoneMonitorStatus.INSIDE_ZONE -> Icons.Default.CheckCircle
                            ZoneMonitorStatus.OUTSIDE_ZONE -> Icons.Default.WarningAmber
                            else -> Icons.Default.Place
                        },
                        contentDescription = null,
                        tint = statusAccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = status.titleTh,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = statusAccentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("zone_status_title_text")
                    )
                    Text(
                        text = if (activePin != null) {
                            "หมุดที่ใช้งาน: ${activePin.name} (รัศมี ${String.format(Locale.US, "%.1f", uiState.zoneRadiusKm)} กม.)"
                        } else {
                            status.detailTh
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(PrecisionButtonShape)
                    .background(statusAccentColor.copy(alpha = 0.16f * blinkFraction))
                    .border(1.dp, statusAccentColor, PrecisionButtonShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = when (status) {
                        ZoneMonitorStatus.INSIDE_ZONE -> "ในโซน"
                        ZoneMonitorStatus.OUTSIDE_ZONE -> "นอกโซน"
                        ZoneMonitorStatus.WAITING_FOR_GPS -> "รอ GPS"
                        ZoneMonitorStatus.NO_ACTIVE_PIN -> "ไม่มีหมุด"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = statusAccentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (activePin != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF09080C))
                    .border(1.dp, panelBorderColor.copy(alpha = 0.6f), PrecisionButtonShape)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ระยะห่างจากหมุดตอนนี้",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudMuted
                    )
                    Text(
                        text = GeoMath.formatZoneDistanceTh(uiState.currentDistanceFromPinMeters),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("home_distance_from_pin_text")
                    )
                }

                if (status == ZoneMonitorStatus.OUTSIDE_ZONE) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ระยะที่เกินโซน",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonNeon
                        )
                        Text(
                            text = "+${GeoMath.formatShortZoneDistanceTh(uiState.excessDistanceMeters)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                            color = CrimsonNeon,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("home_excess_distance_text")
                        )
                    }
                }
            }
        }

        if (status == ZoneMonitorStatus.OUTSIDE_ZONE) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChamferedActionButton(
                    label = if (uiState.isZoneAlertMuted) "ปิดเสียงเตือนแล้ว" else "ปิดเสียงเตือน",
                    icon = Icons.Default.NotificationsOff,
                    enabled = !uiState.isZoneAlertMuted,
                    isPrimaryCrimson = !uiState.isZoneAlertMuted,
                    onClick = onMuteZoneAlert,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_mute_zone_alert"
                )
                ChamferedActionButton(
                    label = "ปรับรัศมี/หมุด",
                    icon = Icons.Default.Place,
                    onClick = onNavigateToZoneTab,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_adjust_zone_from_alert"
                )
            }
        }
    }
}

@Composable
private fun CoordinateValueBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(PrecisionButtonShape)
            .background(ObsidianBlack)
            .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextHudMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextHudPrimary
        )
    }
}

@Composable
private fun SensorMetricTile(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0A090D))
            .border(1.dp, Color(0xFF281920), PrecisionButtonShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(PrecisionButtonShape)
                .background(Color(0xFF1A0E14))
                .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = CrimsonNeon,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                color = TextHudMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = TextHudPrimary
            )
        }
    }
}
