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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
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
import com.example.model.CoordinateFormat
import com.example.model.MeasurementUnit
import com.example.model.UpdateFrequency
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.PrecisionButtonShape
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

/**
 * Screen 3: การตั้งค่า (Settings Screen: Measurement Unit, Coordinate Format, Update Frequency, Keep Screen On, Permissions)
 */
@Composable
fun SmartOrderFilterTab(
    uiState: FixPointUiState,
    onSelectMeasurementUnit: (MeasurementUnit) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onSelectUpdateFrequency: (UpdateFrequency) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onRequestForegroundPermissions: () -> Unit,
    onRequestBackgroundLocationPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Measurement Unit Selection (เลือกหน่วยวัด)
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

        // 2. Update Frequency Selection (ความถี่การอัปเดตสัญญาณ)
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

        // 3. Coordinate Format Selection (รูปแบบการแสดงพิกัด)
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

        // 4. Keep Screen On Toggle (เปิดหน้าจอค้างไว้เสมอ)
        CrimsonPrecisionPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF0A090E))
                    .border(
                        width = 1.dp,
                        color = if (uiState.keepScreenOn) CrimsonNeon else CrimsonBorderGlow,
                        shape = PrecisionButtonShape
                    )
                    .clickable { onToggleKeepScreenOn(!uiState.keepScreenOn) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("setting_keep_screen_on_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenLockPortrait,
                        contentDescription = null,
                        tint = if (uiState.keepScreenOn) CrimsonNeon else TextHudMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "เปิดหน้าจอค้างไว้เสมอ",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.keepScreenOn) "สถานะ: เปิดใช้งาน" else "สถานะ: ปิดใช้งาน",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = if (uiState.keepScreenOn) CrimsonNeon else TextHudMuted
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(PrecisionButtonShape)
                        .background(
                            if (uiState.keepScreenOn) CrimsonRed else Color(0xFF1E1E26)
                        )
                        .border(
                            1.dp,
                            if (uiState.keepScreenOn) CrimsonNeon else Color(0xFF3F3F46),
                            PrecisionButtonShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (uiState.keepScreenOn) "เปิด" else "ปิด",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.keepScreenOn) Color.White else TextHudMuted
                    )
                }
            }
        }

        // 5. System Permissions Status & Management (สิทธิ์ที่จำเป็นของระบบ)
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
                    text = "สถานะสิทธิ์การเข้าถึงระบบ",
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
                label = "สิทธิ์ตำแหน่งเบื้องหลัง",
                granted = uiState.hasBackgroundLocationPermission,
                grantedText = "อนุญาตแล้ว"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                label = "สิทธิ์การแจ้งเตือนสถานะ",
                granted = uiState.hasNotificationPermission,
                grantedText = "อนุญาตแล้ว"
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChamferedActionButton(
                    label = "ขอสิทธิ์ตำแหน่ง",
                    icon = Icons.Default.Security,
                    isPrimaryCrimson = !uiState.hasAnyLocationPermission,
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
                    label = "ตั้งค่าระบบ",
                    icon = Icons.Default.Settings,
                    onClick = onOpenSystemSettings,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_settings_open_system"
                )
            }
        }
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
