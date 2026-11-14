package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.window.Dialog
import com.example.model.GeoMath
import com.example.ui.components.CentralPowerCoreDial
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
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
    onOpenInMaps: (Double, Double) -> Unit,
    onSaveLocation: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
                    text = "บันทึกตำแหน่งปัจจุบัน",
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
                    label = { Text("ชื่อสถานที่หรือจุดอ้างอิง") },
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
                        label = "บันทึก",
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
                Text(
                    text = uiState.permissionDeniedMessageTh ?: "ต้องการสิทธิ์การเข้าถึงตำแหน่งแม่นยำ",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChamferedActionButton(
                        label = "อนุญาตสิทธิ์",
                        icon = Icons.Default.Security,
                        isPrimaryCrimson = true,
                        onClick = onRequestLocationPermission,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_request_permission"
                    )
                    ChamferedActionButton(
                        label = "ตั้งค่าสิทธิ์แอป",
                        icon = Icons.Default.Settings,
                        onClick = onOpenAppSettings,
                        modifier = Modifier.weight(1f),
                        testTag = "btn_open_app_settings"
                    )
                }
            }
        }

        // 1. Centerpiece Power Toggle Dial & Radar
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

        // 2. Primary Coordinate Readout Panel (Latitude & Longitude)
        CrimsonPrecisionPanel(
            borderColor = if (hasCoordinates) CrimsonNeon.copy(alpha = 0.7f) else CrimsonBorderGlow,
            glowAlpha = if (hasCoordinates) 0.22f else 0.08f
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoordinateValueBox(
                    label = "ละติจูด",
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
                    label = "ลองจิจูด",
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

            // 4 Functional Coordinate Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChamferedActionButton(
                    label = "คัดลอกพิกัด",
                    icon = Icons.Default.ContentCopy,
                    enabled = hasCoordinates,
                    onClick = {
                        val lat = telemetry.latitude ?: return@ChamferedActionButton
                        val lng = telemetry.longitude ?: return@ChamferedActionButton
                        onCopyCoordinates(lat, lng)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_copy_coordinates"
                )
                ChamferedActionButton(
                    label = "แชร์พิกัด",
                    icon = Icons.Default.Share,
                    enabled = hasCoordinates,
                    onClick = {
                        val lat = telemetry.latitude ?: return@ChamferedActionButton
                        val lng = telemetry.longitude ?: return@ChamferedActionButton
                        onShareCoordinates(lat, lng)
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
                    label = "เปิดแผนที่",
                    icon = Icons.Default.Map,
                    enabled = hasCoordinates,
                    onClick = {
                        val lat = telemetry.latitude ?: return@ChamferedActionButton
                        val lng = telemetry.longitude ?: return@ChamferedActionButton
                        onOpenInMaps(lat, lng)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_open_google_maps"
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

        // 3. Real Sensor Telemetry Grid (Accuracy, Altitude, Speed, Direction, Satellites, Signal Quality)
        CrimsonPrecisionPanel {
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
                    title = "ความสูง",
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
                    title = "ดาวเทียมที่ใช้งาน",
                    value = if (uiState.isLockEnabled) {
                        "${telemetry.satellitesUsedInFix} / ${telemetry.satellitesVisible} ดวง"
                    } else {
                        "—"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_satellites")
                )
                SensorMetricTile(
                    icon = Icons.Default.SignalCellularAlt,
                    title = "คุณภาพสัญญาณ",
                    value = if (uiState.isLockEnabled) {
                        if (telemetry.averageSnrDbHz > 0f) {
                            "${telemetry.signalQualityLabelTh} (${String.format(Locale.US, "%.1f", telemetry.averageSnrDbHz)} dB-Hz)"
                        } else {
                            telemetry.signalQualityLabelTh
                        }
                    } else {
                        "—"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_signal_quality")
                )
            }

            // Live Satellite SNR Bars (when hardware reports visible satellites)
            if (uiState.isLockEnabled && telemetry.satelliteBars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(PrecisionButtonShape)
                        .background(ObsidianBlack)
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    telemetry.satelliteBars.forEach { sat ->
                        val ratio = (sat.cn0DbHz / 50f).coerceIn(0.12f, 1.0f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(ratio * 0.72f)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (sat.usedInFix) {
                                                listOf(CrimsonNeon, CrimsonRed)
                                            } else {
                                                listOf(Color(0xFF52525B), Color(0xFF27272F))
                                            }
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${sat.constellationLabel}${sat.svid}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                color = if (sat.usedInFix) CrimsonNeon else TextHudMuted
                            )
                        }
                    }
                }
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
            .background(Color(0xFF0A080C))
            .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
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
    Column(
        modifier = modifier
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0A090D))
            .border(1.dp, Color(0xFF2A1820), PrecisionButtonShape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CrimsonNeon,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextHudSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextHudPrimary
        )
    }
}
