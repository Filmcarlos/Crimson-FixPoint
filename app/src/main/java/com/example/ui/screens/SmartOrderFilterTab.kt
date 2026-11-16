package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.example.model.CoordinateFormat
import com.example.model.MeasurementUnit
import com.example.model.RainLeadTimeOption
import com.example.model.UpdateFrequency
import com.example.model.ZoneAlertTone
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.components.RainAlertSettingsPanel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import kotlin.math.roundToInt

/**
 * Screen 4: การตั้งค่า (Settings Screen: Out-of-Zone Alert & Buffer, Rain Alert Settings, Permissions & Battery Exemption, Units, Frequency, Keep Screen On)
 */
@Composable
fun SmartOrderFilterTab(
    uiState: FixPointUiState,
    onToggleAlertVibration: (Boolean) -> Unit,
    onToggleAlertSound: (Boolean) -> Unit,
    onSelectAlertTone: (ZoneAlertTone) -> Unit,
    onSetAlertVolumePercent: (Int) -> Unit,
    onSetGpsBufferMeters: (Int) -> Unit,
    onSelectMeasurementUnit: (MeasurementUnit) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onSelectUpdateFrequency: (UpdateFrequency) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onRequestForegroundPermissions: () -> Unit,
    onRequestBackgroundLocationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onToggleRainAlert: (Boolean) -> Unit = {},
    onSelectRainLeadTime: (RainLeadTimeOption) -> Unit = {},
    onSetRainProbabilityThreshold: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Out-of-Zone Alert & GPS Buffer Configuration (ตั้งค่าแจ้งเตือนออกนอกโซนและค่าเผื่อ GPS)
        CrimsonPrecisionPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "การแจ้งเตือนเมื่อออกนอกโซนรับงาน",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                    color = TextHudPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggle Vibration
            PrecisionToggleRow(
                icon = Icons.Default.Vibration,
                title = "การสั่นเตือนเมื่อออกนอกโซน",
                subtitle = if (uiState.alertVibrationEnabled) "สถานะ: เปิดการสั่นเตือน" else "สถานะ: ปิดการสั่นเตือน",
                enabled = uiState.alertVibrationEnabled,
                onToggle = { onToggleAlertVibration(!uiState.alertVibrationEnabled) },
                testTag = "setting_zone_vibrate_toggle"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Toggle Sound
            PrecisionToggleRow(
                icon = Icons.Default.VolumeUp,
                title = "เสียงเตือนเมื่อออกนอกโซน (ซ้ำทุก 10 วินาที)",
                subtitle = if (uiState.alertSoundEnabled) "สถานะ: เปิดเสียงเตือน" else "สถานะ: ปิดเสียงเตือน",
                enabled = uiState.alertSoundEnabled,
                onToggle = { onToggleAlertSound(!uiState.alertSoundEnabled) },
                testTag = "setting_zone_sound_toggle"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Select Alert Tone
            Text(
                text = "รูปแบบเสียงเตือน",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneAlertTone.entries.forEach { tone ->
                    PrecisionOptionRow(
                        title = tone.titleTh,
                        subtitle = tone.subtitleTh,
                        selected = uiState.alertTone == tone,
                        onClick = { onSelectAlertTone(tone) },
                        testTag = "setting_tone_${tone.name.lowercase()}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Alert Volume Slider (0 - 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ระดับความดังเสียงเตือน",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.alertVolumePercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("setting_alert_volume_label")
                )
            }
            Slider(
                value = uiState.alertVolumePercent.toFloat(),
                onValueChange = { onSetAlertVolumePercent(it.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = Color(0xFF271B22)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_alert_volume")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(25, 50, 75, 100).forEach { vol ->
                    val selected = uiState.alertVolumePercent == vol
                    QuickStepChip(
                        label = "$vol%",
                        selected = selected,
                        onClick = { onSetAlertVolumePercent(vol) },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_vol_$vol"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GPS Buffer (0 - 100 meters)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = CrimsonNeon,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "ค่าเผื่อสัญญาณ GPS (Buffer)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${uiState.gpsBufferMeters} เมตร",
                    style = MaterialTheme.typography.titleMedium,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("setting_gps_buffer_label")
                )
            }
            Slider(
                value = uiState.gpsBufferMeters.toFloat(),
                onValueChange = { onSetGpsBufferMeters(it.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = Color(0xFF271B22)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_gps_buffer")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 15, 30, 50, 100).forEach { buf ->
                    val selected = uiState.gpsBufferMeters == buf
                    QuickStepChip(
                        label = "$buf ม.",
                        selected = selected,
                        onClick = { onSetGpsBufferMeters(buf) },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_buffer_$buf"
                    )
                }
            }
        }

        // 2. System Permissions & Battery Optimization Exemption
        CrimsonPrecisionPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "สิทธิ์ระบบและการทำงานเบื้องหลัง",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                    color = TextHudPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PermissionStatusRow(
                label = "สิทธิ์ตำแหน่งแม่นยำ (GPS)",
                granted = uiState.hasFineLocationPermission,
                grantedText = "อนุญาตแล้ว"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                label = "สิทธิ์ตำแหน่งเบื้องหลัง (ตลอดเวลา)",
                granted = uiState.hasBackgroundLocationPermission,
                grantedText = "อนุญาตแล้ว"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                label = "สิทธิ์การแจ้งเตือนสถานะ",
                granted = uiState.hasNotificationPermission,
                grantedText = "อนุญาตแล้ว"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                label = "ข้ามการประหยัดแบตเตอรี่",
                granted = uiState.isIgnoringBatteryOptimizations,
                grantedText = "ยกเว้นแล้ว"
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChamferedActionButton(
                    label = "ขอสิทธิ์ตำแหน่ง/แจ้งเตือน",
                    icon = Icons.Default.Security,
                    isPrimaryCrimson = !uiState.hasAnyLocationPermission || !uiState.hasBackgroundLocationPermission,
                    onClick = {
                        if (!uiState.hasAnyLocationPermission) {
                            onRequestForegroundPermissions()
                        } else if (!uiState.hasBackgroundLocationPermission) {
                            onRequestBackgroundLocationPermission()
                        } else {
                            onRequestForegroundPermissions()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_settings_request_permission"
                )
                ChamferedActionButton(
                    label = "ข้ามประหยัดแบต",
                    icon = Icons.Default.BatteryChargingFull,
                    isPrimaryCrimson = !uiState.isIgnoringBatteryOptimizations,
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_settings_request_battery_exemption"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ChamferedActionButton(
                label = "เปิดหน้าการตั้งค่าแอปของระบบ",
                icon = Icons.Default.Settings,
                onClick = onOpenSystemSettings,
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_settings_open_system"
            )
        }

        // 3. Measurement Unit Selection (เลือกหน่วยวัด)
        CrimsonPrecisionPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "หน่วยวัดความเร็วและระยะทาง",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                    color = TextHudPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasurementUnit.entries.forEach { unit ->
                    val selected = uiState.measurementUnit == unit
                    PrecisionOptionRow(
                        title = unit.titleTh,
                        subtitle = "ระยะทาง: ${unit.distanceUnitTh} • ความเร็ว: ${unit.speedUnitTh}",
                        selected = selected,
                        onClick = { onSelectMeasurementUnit(unit) },
                        testTag = "setting_unit_${unit.name.lowercase()}"
                    )
                }
            }
        }

        // 4. Update Frequency Selection (ความถี่การอัปเดตสัญญาณ)
        CrimsonPrecisionPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "ความถี่การอัปเดตสัญญาณดาวเทียม",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                    color = TextHudPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateFrequency.entries.forEach { freq ->
                    val selected = uiState.updateFrequency == freq
                    PrecisionOptionRow(
                        title = freq.titleTh,
                        subtitle = freq.detailTh,
                        selected = selected,
                        onClick = { onSelectUpdateFrequency(freq) },
                        testTag = "setting_freq_${freq.name.lowercase()}"
                    )
                }
            }
        }

        // 5. Coordinate Format Selection (รูปแบบการแสดงพิกัด)
        CrimsonPrecisionPanel {
            Text(
                text = "รูปแบบการแสดงผลพิกัด",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                color = TextHudPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoordinateFormat.entries.forEach { format ->
                    val selected = uiState.coordinateFormat == format
                    PrecisionOptionRow(
                        title = format.titleTh,
                        subtitle = null,
                        selected = selected,
                        onClick = { onSelectCoordinateFormat(format) },
                        testTag = "setting_coord_${format.name.lowercase()}"
                    )
                }
            }
        }

        // 6. Keep Screen On Toggle (เปิดหน้าจอค้างไว้เสมอ)
        CrimsonPrecisionPanel {
            PrecisionToggleRow(
                icon = Icons.Default.ScreenLockPortrait,
                title = "เปิดหน้าจอค้างไว้เสมอ",
                subtitle = if (uiState.keepScreenOn) "สถานะ: เปิดใช้งาน" else "สถานะ: ปิดใช้งาน",
                enabled = uiState.keepScreenOn,
                onToggle = { onToggleKeepScreenOn(!uiState.keepScreenOn) },
                testTag = "setting_keep_screen_on_toggle"
            )
        }

        // 7. Rain Forecast Alert Settings (ตั้งค่าการแจ้งเตือนฝนใกล้ตก)
        RainAlertSettingsPanel(
            rainForecast = uiState.rainForecast,
            onToggleRainAlert = onToggleRainAlert,
            onSelectLeadTime = onSelectRainLeadTime,
            onSetProbabilityThreshold = onSetRainProbabilityThreshold
        )

        Spacer(modifier = Modifier.height(160.dp))
    }
}

@Composable
private fun PrecisionToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0A090E))
            .border(
                width = 1.dp,
                color = if (enabled) CrimsonNeon else CrimsonBorderGlow,
                shape = PrecisionButtonShape
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) CrimsonNeon else TextHudMuted,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = if (enabled) CrimsonNeon else TextHudMuted
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(PrecisionButtonShape)
                .background(if (enabled) CrimsonRed else Color(0xFF1E1E26))
                .border(
                    1.dp,
                    if (enabled) CrimsonNeon else Color(0xFF3F3F46),
                    PrecisionButtonShape
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (enabled) "เปิด" else "ปิด",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else TextHudMuted
            )
        }
    }
}

@Composable
private fun QuickStepChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(38.dp)
            .clip(PrecisionButtonShape)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF120F15), Color(0xFF0A090D)))
                }
            )
            .border(
                1.dp,
                if (selected) CrimsonNeon else CrimsonBorderGlow,
                PrecisionButtonShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = if (selected) Color.White else TextHudSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PrecisionOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(PrecisionButtonShape)
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(CrimsonRed.copy(alpha = 0.26f), DeepCrimsonGlow.copy(alpha = 0.14f))
                    )
                } else {
                    Brush.linearGradient(listOf(Color(0xFF0B0A0E), Color(0xFF09080B)))
                }
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CrimsonNeon else Color(0xFF261B21),
                shape = PrecisionButtonShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) TextHudPrimary else TextHudSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    color = TextHudMuted
                )
            }
        }

        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(PrecisionButtonShape)
                    .background(CrimsonRed)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "เลือกอยู่",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    grantedText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0A090D))
            .border(1.dp, Color(0xFF241A1F), PrecisionButtonShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextHudSecondary
        )
        Text(
            text = if (granted) grantedText else "ยังไม่อนุญาต",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) EmeraldSRank else AmberWarning,
            fontWeight = FontWeight.Bold
        )
    }
}
