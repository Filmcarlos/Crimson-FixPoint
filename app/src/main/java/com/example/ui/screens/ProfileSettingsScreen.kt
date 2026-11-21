package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CoordinateFormat
import com.example.model.MeasurementUnit
import com.example.model.PrivacyPreferences
import com.example.model.RiderProfileConfig
import com.example.model.UpdateFrequency
import com.example.model.VehicleType
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun ProfileSettingsScreen(
    uiState: FixPointUiState,
    onUpdateRiderProfile: (RiderProfileConfig) -> Unit,
    onUpdateFuelSettings: (Float, Float, Float) -> Unit,
    onMarkRefueledFullTank: () -> Unit,
    onUpdatePrivacy: ((PrivacyPreferences) -> PrivacyPreferences) -> Unit,
    onClearOcrHistory: () -> Unit,
    onToggleAlertVibration: (Boolean) -> Unit,
    onSetGpsBufferMeters: (Int) -> Unit,
    onSelectMeasurementUnit: (MeasurementUnit) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onSelectUpdateFrequency: (UpdateFrequency) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onRequestForegroundPermissions: () -> Unit,
    onRequestBackgroundPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.riderProfile
    val fuel = uiState.fuelState

    var riderName by remember(profile.riderName) { mutableStateOf(profile.riderName) }
    var vehicleModel by remember(profile.vehicleModel) { mutableStateOf(profile.vehicleModel) }
    var vehicleType by remember(profile.vehicleType) { mutableStateOf(profile.vehicleType) }
    var fuelPrice by remember(profile.fuelPricePerLiterBaht) { mutableStateOf(profile.fuelPricePerLiterBaht.toString()) }
    var maintPerKm by remember(profile.maintenanceBahtPerKm) { mutableStateOf(profile.maintenanceBahtPerKm.toString()) }
    var minPerKm by remember(profile.minAcceptableBahtPerKm) { mutableStateOf(profile.minAcceptableBahtPerKm.toString()) }
    var minPerHr by remember(profile.minAcceptableBahtPerHour) { mutableStateOf(profile.minAcceptableBahtPerHour.toString()) }

    var tankLiters by remember(fuel.tankCapacityLiters) { mutableStateOf(fuel.tankCapacityLiters.toString()) }
    var kmPerLiter by remember(fuel.consumptionKmPerLiter) { mutableStateOf(fuel.consumptionKmPerLiter.toString()) }
    var warnLiters by remember(fuel.lowFuelWarningLiters) { mutableStateOf(fuel.lowFuelWarningLiters.toString()) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("profile_settings_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Rider Profile Hero Card
            item {
                CrimsonHeroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed.copy(alpha = 0.25f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "โปรไฟล์ไรเดอร์",
                                tint = CrimsonNeon,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.riderName,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${profile.vehicleType.labelTh} • ${profile.vehicleModel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(text = "OFFLINE 100%", accentColor = EmeraldSRank)
                    }
                }
            }

            // 2. Rider & AI Profitability Criteria Settings
            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "โปรไฟล์ไรเดอร์ & เกณฑ์คำนวณ AI",
                        subtitle = "ใช้คำนวณ Smart Order Score และต้นทุนจริงต่อกิโลเมตร"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = riderName,
                        onValueChange = { riderName = it },
                        label = { Text("ชื่อไรเดอร์") },
                        singleLine = true,
                        colors = crimsonFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_profile_rider_name")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vehicleModel,
                        onValueChange = { vehicleModel = it },
                        label = { Text("รุ่นรถ (เช่น Honda Wave 125i)") },
                        singleLine = true,
                        colors = crimsonFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VehicleType.entries.forEach { vt ->
                            FilterChip(
                                selected = vehicleType == vt,
                                onClick = { vehicleType = vt },
                                label = { Text(vt.labelTh) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = fuelPrice,
                            onValueChange = { fuelPrice = it },
                            label = { Text("ราคาน้ำมัน (฿/ลิตร)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maintPerKm,
                            onValueChange = { maintPerKm = it },
                            label = { Text("ค่าเสื่อม (฿/กม.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = minPerKm,
                            onValueChange = { minPerKm = it },
                            label = { Text("ขั้นต่ำที่รับ (฿/กม.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minPerHr,
                            onValueChange = { minPerHr = it },
                            label = { Text("ขั้นต่ำที่รับ (฿/ชม.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PremiumPrimaryButton(
                        text = "บันทึกโปรไฟล์ & เกณฑ์ AI",
                        icon = Icons.Default.TwoWheeler,
                        onClick = {
                            onUpdateRiderProfile(
                                profile.copy(
                                    riderName = riderName,
                                    vehicleModel = vehicleModel,
                                    vehicleType = vehicleType,
                                    fuelPricePerLiterBaht = fuelPrice.toFloatOrNull() ?: profile.fuelPricePerLiterBaht,
                                    maintenanceBahtPerKm = maintPerKm.toFloatOrNull() ?: profile.maintenanceBahtPerKm,
                                    minAcceptableBahtPerKm = minPerKm.toFloatOrNull() ?: profile.minAcceptableBahtPerKm,
                                    minAcceptableBahtPerHour = minPerHr.toFloatOrNull() ?: profile.minAcceptableBahtPerHour
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "btn_save_rider_profile"
                    )
                }
            }

            // 3. Fuel Tank & Consumption Settings
            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "ตั้งค่าถังน้ำมันและอัตราสิ้นเปลือง",
                        subtitle = String.format(
                            Locale.US,
                            "คงเหลือ ~%.2f ลิตร (วิ่งได้ ~%.0f กม.)",
                            fuel.remainingLiters,
                            fuel.estimatedRemainingRangeKm
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tankLiters,
                            onValueChange = { tankLiters = it },
                            label = { Text("ความจุ (ล.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = kmPerLiter,
                            onValueChange = { kmPerLiter = it },
                            label = { Text("กม./ลิตร") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = warnLiters,
                            onValueChange = { warnLiters = it },
                            label = { Text("เตือน (ล.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PremiumPrimaryButton(
                            text = "บันทึกค่าน้ำมัน",
                            onClick = {
                                onUpdateFuelSettings(
                                    tankLiters.toFloatOrNull() ?: fuel.tankCapacityLiters,
                                    kmPerLiter.toFloatOrNull() ?: fuel.consumptionKmPerLiter,
                                    warnLiters.toFloatOrNull() ?: fuel.lowFuelWarningLiters
                                )
                            },
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_save_fuel_settings"
                        )
                        PremiumSecondaryButton(
                            text = "เติมเต็มถังแล้ว",
                            icon = Icons.Default.LocalGasStation,
                            accentColor = EmeraldSRank,
                            onClick = onMarkRefueledFullTank,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_refuel_full_tank"
                        )
                    }
                }
            }

            // 4. Local Backup & Restore (Zero-Cloud Offline JSON)
            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "สำรองและกู้คืนข้อมูลในเครื่อง (Offline)",
                        subtitle = "ส่งออก/นำเข้าไฟล์ JSON ในอุปกรณ์ของคุณเอง ไม่มีระบบคลาวด์"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PremiumPrimaryButton(
                            text = "สำรองไฟล์ JSON",
                            icon = Icons.Default.Backup,
                            onClick = onExportLocalBackup,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_export_local_backup"
                        )
                        PremiumSecondaryButton(
                            text = "กู้คืนจากไฟล์",
                            icon = Icons.Default.Restore,
                            accentColor = CyanTelemetry,
                            onClick = onImportLocalBackup,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_import_local_backup"
                        )
                    }
                }
            }

            // 5. Privacy & Local OCR Controls
            item {
                val privacy = uiState.privacyPreferences
                PremiumCard {
                    SectionHeaderRow(
                        title = "ความเป็นส่วนตัว & OCR ในเครื่อง",
                        subtitle = "ไม่มีบัญชีผู้ใช้ ไม่มีคลาวด์ ไม่มีค่าสมัครสมาชิก"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSwitchRow(
                        title = "ลบภาพชั่วคราวทันทีหลังอ่าน OCR",
                        subtitle = "ป้องกันการเก็บภาพหน้าจอค้างในหน่วยความจำ",
                        checked = privacy.autoDeleteScreenshots,
                        onCheckedChange = { v -> onUpdatePrivacy { it.copy(autoDeleteScreenshots = v) } }
                    )
                    SettingsSwitchRow(
                        title = "บันทึกข้อความ OCR ดิบในประวัติ",
                        subtitle = "ปิดไว้เพื่อความเป็นส่วนตัวสูงสุด",
                        checked = privacy.storeRawOcrText,
                        onCheckedChange = { v -> onUpdatePrivacy { it.copy(storeRawOcrText = v) } }
                    )
                    SettingsSwitchRow(
                        title = "แจ้งเตือนระดับน้ำมันใกล้หมด",
                        subtitle = "เตือนเมื่อระยะคงเหลือต่ำกว่าเกณฑ์ที่ตั้งไว้",
                        checked = privacy.lowFuelAlertEnabled,
                        onCheckedChange = { v -> onUpdatePrivacy { it.copy(lowFuelAlertEnabled = v) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PremiumSecondaryButton(
                        text = "ล้างประวัติออเดอร์ OCR ทั้งหมด (${uiState.analyzedOrders.size})",
                        icon = Icons.Default.DeleteSweep,
                        accentColor = CrimsonNeon,
                        enabled = uiState.analyzedOrders.isNotEmpty(),
                        onClick = onClearOcrHistory,
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "btn_clear_ocr_history"
                    )
                }
            }

            // 6. GPS, Zone Alerts & Display Preferences
            item {
                PremiumCard {
                    SectionHeaderRow(title = "การตั้งค่า GPS และโซนรับงาน")
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSwitchRow(
                        title = "สั่นแจ้งเตือนเมื่อออกนอกโซน",
                        subtitle = "สั่นเมื่อเลยรัศมีหมุดหลักที่ตั้งไว้",
                        checked = uiState.alertVibrationEnabled,
                        onCheckedChange = onToggleAlertVibration
                    )
                    SettingsSwitchRow(
                        title = "เปิดหน้าจอค้างไว้เสมอ",
                        subtitle = "ป้องกันหน้าจอดับระหว่างวิ่งงาน",
                        checked = uiState.keepScreenOn,
                        onCheckedChange = onToggleKeepScreenOn
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ระยะเผื่อสัญญาณ GPS (Buffer)", style = MaterialTheme.typography.bodyMedium, color = TextHudPrimary)
                        Text(
                            text = "${uiState.gpsBufferMeters} ม.",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = CrimsonNeon
                        )
                    }
                    Slider(
                        value = uiState.gpsBufferMeters.toFloat(),
                        onValueChange = { onSetGpsBufferMeters(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonNeon, activeTrackColor = CrimsonRed)
                    )

                    Text("ความถี่อัปเดต GPS", style = MaterialTheme.typography.labelMedium, color = TextHudSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UpdateFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = uiState.updateFrequency == freq,
                                onClick = { onSelectUpdateFrequency(freq) },
                                label = { Text(freq.titleTh, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("หน่วยวัดและรูปแบบพิกัด", style = MaterialTheme.typography.labelMedium, color = TextHudSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MeasurementUnit.entries.forEach { u ->
                            FilterChip(
                                selected = uiState.measurementUnit == u,
                                onClick = { onSelectMeasurementUnit(u) },
                                label = { Text(u.titleTh, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CoordinateFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = uiState.coordinateFormat == fmt,
                                onClick = { onSelectCoordinateFormat(fmt) },
                                label = { Text(fmt.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // 7. Permissions Center
            item {
                PremiumCard {
                    SectionHeaderRow(title = "สิทธิ์ระบบที่จำเป็น")
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRowItem(
                        title = "ตำแหน่งแม่นยำ (GPS)",
                        granted = uiState.hasFineLocationPermission,
                        actionLabel = "ขอสิทธิ์",
                        onAction = onRequestForegroundPermissions
                    )
                    PermissionRowItem(
                        title = "ตำแหน่งเบื้องหลัง (ตรวจโซนขณะปิดจอ)",
                        granted = uiState.hasBackgroundLocationPermission,
                        actionLabel = "ตั้งค่า",
                        onAction = onRequestBackgroundPermission
                    )
                    PermissionRowItem(
                        title = "ข้ามการประหยัดแบตเตอรี่",
                        granted = uiState.isIgnoringBatteryOptimizations,
                        actionLabel = "อนุญาต",
                        onAction = onRequestBatteryExemption
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PremiumSecondaryButton(
                        text = "เปิดหน้าตั้งค่าแอปของ Android",
                        icon = Icons.Default.Settings,
                        accentColor = TextHudSecondary,
                        onClick = onOpenSystemSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 8. App Information
            item {
                PremiumCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CrimsonNeon)
                        Column {
                            Text(
                                text = "Crimson FixPoint 2.0 (v2.0.0)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Rider Assistant แบบออฟไลน์ 100% • ฐานข้อมูล Room ในเครื่อง • แผนที่ © OpenStreetMap contributors",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextHudPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextHudMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CrimsonRed,
                uncheckedThumbColor = TextHudMuted,
                uncheckedTrackColor = SurfaceLevel2
            )
        )
    }
}

@Composable
private fun PermissionRowItem(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (granted) EmeraldSRank else AmberWarning,
                modifier = Modifier.size(18.dp)
            )
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextHudPrimary)
        }
        if (granted) {
            StatusPillBadge(text = "อนุญาตแล้ว", accentColor = EmeraldSRank)
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CrimsonRed.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, CrimsonNeon),
                modifier = Modifier.clickable(onClick = onAction)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
