package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.MapTapActionMode
import com.example.model.SavedLocationItem
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.InteractiveZoneMapCanvas
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HotzonePinsTab(
    uiState: FixPointUiState,
    onSaveCurrentLocation: (String, Boolean) -> Unit,
    onMapTapPin: (Double, Double) -> Unit,
    onSetMapTapMode: (MapTapActionMode) -> Unit,
    onPinOrMoveFromCoordinates: (String, String, Boolean) -> Boolean,
    onSelectActivePin: (String) -> Unit,
    onMoveSavedPin: (String, Double, Double, String?) -> Boolean,
    onDeleteLocation: (String) -> Unit,
    onSetZoneRadiusKm: (Float) -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    onShareCoordinates: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasActiveFix = uiState.telemetry.latitude != null && uiState.telemetry.longitude != null
    val activePin = uiState.activePin

    var showCurrentGpsPinPanel by remember { mutableStateOf(false) }
    var showPasteCoordsPanel by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var pasteCoordinateInput by remember { mutableStateOf("") }
    var pasteNameInput by remember { mutableStateOf("") }
    var pasteErrorTh by remember { mutableStateOf<String?>(null) }

    var radiusTextInput by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", uiState.zoneRadiusKm))
    }

    LaunchedEffect(uiState.zoneRadiusKm) {
        radiusTextInput = String.format(Locale.US, "%.1f", uiState.zoneRadiusKm)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Real OpenStreetMap Dark Map & Real-Time Radius Visualizer
        CrimsonPrecisionPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "แผนที่หมุดและโซนรับงาน",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activePin?.let { "หมุดหลัก: ${it.name}" } ?: "หมุดหลัก: —",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = if (activePin != null) EmeraldSRank else TextHudMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapTapModeChip(
                    label = "ย้ายหมุดหลัก",
                    selected = uiState.mapTapActionMode == MapTapActionMode.MOVE_ACTIVE_PIN,
                    onClick = { onSetMapTapMode(MapTapActionMode.MOVE_ACTIVE_PIN) },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_tap_mode_move"
                )
                MapTapModeChip(
                    label = "เพิ่มหมุดใหม่",
                    selected = uiState.mapTapActionMode == MapTapActionMode.CREATE_NEW_PIN,
                    onClick = { onSetMapTapMode(MapTapActionMode.CREATE_NEW_PIN) },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_tap_mode_create"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            InteractiveZoneMapCanvas(
                activePin = activePin,
                allPins = uiState.savedLocations,
                userLatitude = uiState.telemetry.latitude,
                userLongitude = uiState.telemetry.longitude,
                radiusKm = uiState.zoneRadiusKm,
                gpsBufferMeters = uiState.gpsBufferMeters,
                zoneStatus = uiState.zoneStatus,
                distanceFromPinMeters = uiState.currentDistanceFromPinMeters,
                onMapTapPin = onMapTapPin,
                mapHeightDp = 265
            )
        }

        // 2. Pinning Actions Panel
        CrimsonPrecisionPanel {
            Text(
                text = "จัดการตำแหน่งหมุด",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChamferedActionButton(
                    label = "ตำแหน่งปัจจุบัน",
                    icon = Icons.Default.MyLocation,
                    enabled = hasActiveFix,
                    isPrimaryCrimson = hasActiveFix,
                    onClick = {
                        showCurrentGpsPinPanel = !showCurrentGpsPinPanel
                        showPasteCoordsPanel = false
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_save_current_in_list"
                )
                ChamferedActionButton(
                    label = "วางพิกัด",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        showPasteCoordsPanel = !showPasteCoordsPanel
                        showCurrentGpsPinPanel = false
                        pasteErrorTh = null
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_open_paste_coords"
                )
            }

            if (showCurrentGpsPinPanel && hasActiveFix) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF0A090E))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .padding(12.dp)
                        .testTag("dialog_save_from_list")
                ) {
                    Text(
                        text = "ตำแหน่ง GPS ปัจจุบัน",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = GeoMath.formatCoordinatePair(
                            uiState.telemetry.latitude,
                            uiState.telemetry.longitude,
                            uiState.coordinateFormat
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonNeon
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("ชื่อหมุด") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonNeon,
                            unfocusedBorderColor = CrimsonBorderGlow,
                            focusedTextColor = TextHudPrimary,
                            unfocusedTextColor = TextHudPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saved_screen_name")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChamferedActionButton(
                            label = "เพิ่มหมุดใหม่",
                            icon = Icons.Default.BookmarkAdd,
                            isPrimaryCrimson = true,
                            onClick = {
                                onSaveCurrentLocation(nameInput, false)
                                nameInput = ""
                                showCurrentGpsPinPanel = false
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "btn_confirm_save_from_list"
                        )
                        if (activePin != null) {
                            ChamferedActionButton(
                                label = "ย้ายหมุดหลัก",
                                icon = Icons.Default.EditLocationAlt,
                                onClick = {
                                    onSaveCurrentLocation(nameInput, true)
                                    nameInput = ""
                                    showCurrentGpsPinPanel = false
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "btn_move_active_to_current_gps"
                            )
                        }
                    }
                }
            }

            if (showPasteCoordsPanel) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF0A090E))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .padding(12.dp)
                        .testTag("panel_paste_coordinates")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "พิกัดหมุด",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(PrecisionButtonShape)
                                .background(Color(0xFF1A0F15))
                                .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                                .clickable {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                    if (!clipText.isNullOrBlank()) {
                                        pasteCoordinateInput = clipText
                                        pasteErrorTh = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("btn_paste_from_clipboard")
                        ) {
                            Text(
                                text = "ดึงจากคลิปบอร์ด",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonNeon
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pasteCoordinateInput,
                        onValueChange = {
                            pasteCoordinateInput = it
                            pasteErrorTh = null
                        },
                        label = { Text("ละติจูด, ลองจิจูด") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonNeon,
                            unfocusedBorderColor = CrimsonBorderGlow,
                            focusedTextColor = TextHudPrimary,
                            unfocusedTextColor = TextHudPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_paste_coordinates")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pasteNameInput,
                        onValueChange = { pasteNameInput = it },
                        label = { Text("ชื่อหมุด") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonNeon,
                            unfocusedBorderColor = CrimsonBorderGlow,
                            focusedTextColor = TextHudPrimary,
                            unfocusedTextColor = TextHudPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_paste_pin_name")
                    )

                    if (pasteErrorTh != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pasteErrorTh!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = CrimsonNeon
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChamferedActionButton(
                            label = "ปักหมุดใหม่",
                            icon = Icons.Default.AddLocationAlt,
                            isPrimaryCrimson = true,
                            onClick = {
                                val ok = onPinOrMoveFromCoordinates(
                                    pasteCoordinateInput,
                                    pasteNameInput,
                                    false
                                )
                                if (ok) {
                                    pasteCoordinateInput = ""
                                    pasteNameInput = ""
                                    pasteErrorTh = null
                                    showPasteCoordsPanel = false
                                } else {
                                    pasteErrorTh = "พิกัดไม่ถูกต้อง"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "btn_confirm_paste_new_pin"
                        )
                        if (activePin != null) {
                            ChamferedActionButton(
                                label = "ย้ายหมุดหลัก",
                                icon = Icons.Default.EditLocationAlt,
                                onClick = {
                                    val ok = onPinOrMoveFromCoordinates(
                                        pasteCoordinateInput,
                                        pasteNameInput,
                                        true
                                    )
                                    if (ok) {
                                        pasteCoordinateInput = ""
                                        pasteNameInput = ""
                                        pasteErrorTh = null
                                        showPasteCoordsPanel = false
                                    } else {
                                        pasteErrorTh = "พิกัดไม่ถูกต้อง"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "btn_confirm_paste_move_pin"
                            )
                        }
                    }
                }
            }
        }

        // 3. Work-Zone Radius Configuration Panel (0.0 - 30.0 กม.)
        CrimsonPrecisionPanel {
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
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = CrimsonNeon,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "รัศมีโซนรับงาน",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f กม.", uiState.zoneRadiusKm),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("zone_radius_current_label")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = uiState.zoneRadiusKm,
                onValueChange = { rawValue ->
                    val rounded = ((rawValue * 10f).roundToInt() / 10f).coerceIn(0.0f, 30.0f)
                    onSetZoneRadiusKm(rounded)
                },
                valueRange = 0.0f..30.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = Color(0xFF271B22)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_zone_radius")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(1, 5, 10, 20, 30)
                presets.forEach { presetKm ->
                    val isSelected = (uiState.zoneRadiusKm * 10).roundToInt() == presetKm * 10
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(42.dp)
                            .clip(PrecisionButtonShape)
                            .background(
                                if (isSelected) {
                                    Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF141016), Color(0xFF0C0A0F)))
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CrimsonNeon else CrimsonBorderGlow,
                                shape = PrecisionButtonShape
                            )
                            .clickable { onSetZoneRadiusKm(presetKm.toFloat()) }
                            .testTag("btn_radius_preset_$presetKm")
                    ) {
                        Text(
                            text = "$presetKm กม.",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                            color = if (isSelected) Color.White else TextHudSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = radiusTextInput,
                    onValueChange = { text ->
                        radiusTextInput = text
                        val parsed = text.toFloatOrNull()
                        if (parsed != null && parsed in 0.0f..30.0f) {
                            val oneDecimal = ((parsed * 10f).roundToInt() / 10f)
                            onSetZoneRadiusKm(oneDecimal)
                        }
                    },
                    label = { Text("รัศมี (กม.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_zone_radius_km")
                )

                ChamferedActionButton(
                    label = "ยืนยันรัศมี",
                    icon = Icons.Default.CheckCircle,
                    isPrimaryCrimson = true,
                    onClick = {
                        val parsed = radiusTextInput.toFloatOrNull()
                        if (parsed != null) {
                            val clamped = ((parsed.coerceIn(0.0f, 30.0f) * 10f).roundToInt() / 10f)
                            onSetZoneRadiusKm(clamped)
                            radiusTextInput = String.format(Locale.US, "%.1f", clamped)
                        }
                    },
                    modifier = Modifier.height(54.dp),
                    testTag = "btn_apply_radius_input"
                )
            }
        }

        // 4. Saved Pins List
        CrimsonPrecisionPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "รายการหมุด (${uiState.savedLocations.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activePin?.name ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (activePin != null) EmeraldSRank else TextHudMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.savedLocations.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp)
                        .testTag("empty_saved_locations_state")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = TextHudMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "ไม่มีรายการหมุด",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudSecondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saved_locations_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.savedLocations.forEach { item ->
                        val isActive = item.id == activePin?.id
                        SavedLocationCard(
                            item = item,
                            isActivePin = isActive,
                            uiState = uiState,
                            onSelectActive = { onSelectActivePin(item.id) },
                            onMovePin = { newLat, newLng, newName ->
                                onMoveSavedPin(item.id, newLat, newLng, newName)
                            },
                            onCopy = { onCopyCoordinates(item.latitude, item.longitude) },
                            onShare = { onShareCoordinates(item.latitude, item.longitude) },
                            onDelete = { onDeleteLocation(item.id) }
                        )
                    }
                }
            }
        }

        // Generous bottom spacing so all inputs & buttons scroll well past the bottom bar
        Spacer(modifier = Modifier.height(160.dp))
    }
}

@Composable
private fun MapTapModeChip(
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
            .height(40.dp)
            .clip(PrecisionButtonShape)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(CrimsonRed.copy(alpha = 0.35f), DeepCrimsonGlow.copy(alpha = 0.25f)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF100E14), Color(0xFF0A090D)))
                }
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) CrimsonNeon else CrimsonBorderGlow,
                shape = PrecisionButtonShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (selected) CrimsonNeon else TextHudMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (selected) TextHudPrimary else TextHudSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SavedLocationCard(
    item: SavedLocationItem,
    isActivePin: Boolean,
    uiState: FixPointUiState,
    onSelectActive: () -> Unit,
    onMovePin: (Double, Double, String?) -> Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMoveEditor by remember { mutableStateOf(false) }
    var editCoordText by remember(item.latitude, item.longitude) {
        mutableStateOf(String.format(Locale.US, "%.6f, %.6f", item.latitude, item.longitude))
    }
    var editNameText by remember(item.name) { mutableStateOf(item.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0D0A0F))
            .border(
                width = if (isActivePin) 1.5.dp else 1.dp,
                color = if (isActivePin) EmeraldSRank else CrimsonBorderGlow,
                shape = PrecisionButtonShape
            )
            .padding(14.dp)
            .testTag("saved_location_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = if (isActivePin) EmeraldSRank else CrimsonNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.formattedTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(PrecisionButtonShape)
                    .background(
                        if (isActivePin) EmeraldSRank.copy(alpha = 0.18f) else Color(0xFF171116)
                    )
                    .border(
                        1.dp,
                        if (isActivePin) EmeraldSRank else CrimsonBorderGlow,
                        PrecisionButtonShape
                    )
                    .clickable(onClick = onSelectActive)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("btn_select_active_pin_${item.id}")
            ) {
                Text(
                    text = if (isActivePin) "ใช้งานอยู่" else "เลือกใช้",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActivePin) EmeraldSRank else TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PrecisionButtonShape)
                .background(Color(0xFF08070B))
                .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = GeoMath.formatCoordinatePair(
                    item.latitude,
                    item.longitude,
                    uiState.coordinateFormat
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = CrimsonNeon
            )
        }

        if (showMoveEditor) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF120C11))
                    .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                    .padding(10.dp)
                    .testTag("panel_move_pin_${item.id}")
            ) {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text("ชื่อหมุด") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_move_pin_name_${item.id}")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editCoordText,
                    onValueChange = { editCoordText = it },
                    label = { Text("ละติจูด, ลองจิจูด") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_move_pin_coords_${item.id}")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChamferedActionButton(
                        label = "ยกเลิก",
                        icon = Icons.Default.Close,
                        onClick = { showMoveEditor = false },
                        modifier = Modifier.weight(1f)
                    )
                    ChamferedActionButton(
                        label = "บันทึก",
                        icon = Icons.Default.CheckCircle,
                        isPrimaryCrimson = true,
                        onClick = {
                            val parsed = GeoMath.parseCoordinateInput(editCoordText)
                            if (parsed != null) {
                                onMovePin(parsed.first, parsed.second, editNameText)
                                showMoveEditor = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_confirm_move_pin_${item.id}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChamferedActionButton(
                label = "คัดลอก",
                icon = Icons.Default.ContentCopy,
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                testTag = "btn_copy_saved_${item.id}"
            )
            ChamferedActionButton(
                label = "แชร์",
                icon = Icons.Default.Share,
                onClick = onShare,
                modifier = Modifier.weight(1f),
                testTag = "btn_share_saved_${item.id}"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChamferedActionButton(
                label = "ย้ายหมุด",
                icon = Icons.Default.EditLocationAlt,
                isPrimaryCrimson = true,
                onClick = { showMoveEditor = !showMoveEditor },
                modifier = Modifier.weight(1f),
                testTag = "btn_move_pin_${item.id}"
            )
            ChamferedActionButton(
                label = "ลบรายการ",
                icon = Icons.Default.DeleteOutline,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                testTag = "btn_delete_saved_${item.id}"
            )
        }
    }
}
