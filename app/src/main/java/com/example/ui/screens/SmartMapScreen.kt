package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CoordinateFormat
import com.example.model.GeoMath
import com.example.model.MapTapActionMode
import com.example.model.PinCategory
import com.example.model.SavedLocationItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.components.InteractiveZoneMapCanvas
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonSubtleBg
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale
import kotlin.math.abs

/**
 * Redesigned Work Zone Map ("โซนพื้นที่รับงาน"):
 * - Clean, distraction-free dark OpenStreetMap focused strictly on delivery work zones & radius (0–30 km)
 * - Safe pan/zoom by default: tapping the map never drops accidental pins unless "ย้ายจุด" edit mode is turned ON
 * - Isolated overlay controls (+, −, Auto-Fit, GPS, header, bottom dock) so touches NEVER bleed through to the map
 * - Streamlined bottom radius & zone control dock with 1-tap extra pin cleanup
 */
@Composable
fun SmartMapScreen(
    uiState: FixPointUiState,
    onMapTapPin: (Double, Double) -> Unit,
    onSetMapTapMode: (MapTapActionMode) -> Unit,
    onSelectActivePin: (String) -> Unit,
    onSaveCurrentLocation: (String, Boolean, PinCategory) -> Unit,
    onPinFromCoordinates: (String, String, Boolean, PinCategory) -> Boolean,
    onDeletePin: (String) -> Unit,
    onSetZoneRadiusKm: (Float) -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    onShareCoordinates: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddZoneDialog by remember { mutableStateOf(false) }
    var showZoneListSheet by remember { mutableStateOf(false) }
    var isTapToMoveEnabled by remember { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val activePin = uiState.activePin

    if (showAddZoneDialog) {
        AddWorkZoneDialog(
            hasLiveGps = uiState.telemetry.hasFix,
            onDismiss = { showAddZoneDialog = false },
            onSaveLiveGps = { name ->
                onSaveCurrentLocation(name, false, PinCategory.FIXPOINT)
                showAddZoneDialog = false
            },
            onSaveCoordinates = { coords, name ->
                val ok = onPinFromCoordinates(coords, name, false, PinCategory.FIXPOINT)
                if (ok) showAddZoneDialog = false
                ok
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_map_screen")
    ) {
        // Full-Screen Dark Work Zone Map Canvas (pointer gestures strictly isolated to Canvas layer)
        InteractiveZoneMapCanvas(
            activePin = activePin,
            allPins = uiState.savedLocations,
            userLatitude = uiState.telemetry.latitude,
            userLongitude = uiState.telemetry.longitude,
            radiusKm = uiState.zoneRadiusKm,
            gpsBufferMeters = uiState.gpsBufferMeters,
            zoneStatus = uiState.zoneStatus,
            distanceFromPinMeters = uiState.currentDistanceFromPinMeters,
            onMapTapPin = { lat, lng ->
                // Always move active zone cleanly (or create first zone if none exists) — never spam duplicates
                onSetMapTapMode(MapTapActionMode.MOVE_ACTIVE_PIN)
                onMapTapPin(lat, lng)
            },
            mapHeightDp = null,
            focusTargetLatLng = focusTarget,
            onMarkerSelected = { tappedPin ->
                onSelectActivePin(tappedPin.id)
                focusTarget = tappedPin.latitude to tappedPin.longitude
            },
            workZoneOnlyMode = true,
            allowMapTapToPin = isTapToMoveEnabled,
            controlsBottomPaddingDp = if (showZoneListSheet) 230 else 156,
            attributionTopPaddingDp = if (isTapToMoveEnabled) 128 else 82,
            modifier = Modifier.fillMaxSize(),
            testTagName = "smart_map_osm_canvas"
        )

        // Top Floating Obsidian Glass Header Bar (Work Zone Summary + Move Toggle + Add Zone)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkZoneTopBar(
                activePin = activePin,
                zoneStatus = uiState.zoneStatus,
                zoneRadiusKm = uiState.zoneRadiusKm,
                distanceFromPinMeters = uiState.currentDistanceFromPinMeters,
                isTapToMoveEnabled = isTapToMoveEnabled,
                onToggleTapToMove = {
                    isTapToMoveEnabled = !isTapToMoveEnabled
                    if (isTapToMoveEnabled) {
                        onSetMapTapMode(MapTapActionMode.MOVE_ACTIVE_PIN)
                        showZoneListSheet = false
                    }
                },
                onOpenAddZone = { showAddZoneDialog = true }
            )

            // Crisp Animated Guide Pill ONLY when Tap-to-Move Mode is ON
            AnimatedVisibility(
                visible = isTapToMoveEnabled,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xF2240A12),
                    border = BorderStroke(1.dp, CrimsonNeon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .testTag("work_zone_tap_move_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (activePin != null) {
                                "แตะบนแผนที่เพื่อย้ายจุดศูนย์กลางโซนรับงาน"
                            } else {
                                "แตะบนแผนที่เพื่อปักจุดโซนรับงานแรก"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = { isTapToMoveEnabled = false },
                            shape = RoundedCornerShape(10.dp),
                            color = CrimsonRed,
                            modifier = Modifier.testTag("btn_finish_tap_move_zone")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "เสร็จสิ้น",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Streamlined Work Zone Control Panel (consumes empty clicks so touches NEVER reach Canvas)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("work_zone_control_panel")
        ) {
            AnimatedContent(
                targetState = showZoneListSheet,
                transitionSpec = {
                    fadeIn(tween(180)).togetherWith(fadeOut(tween(140)))
                },
                label = "work_zone_bottom_sheet_anim"
            ) { expandedList ->
                if (expandedList) {
                    SavedWorkZonesSheet(
                        pins = uiState.savedLocations,
                        activePinId = activePin?.id,
                        userLat = uiState.telemetry.latitude,
                        userLng = uiState.telemetry.longitude,
                        onSelectPin = { pin ->
                            onSelectActivePin(pin.id)
                            focusTarget = pin.latitude to pin.longitude
                            showZoneListSheet = false
                        },
                        onDeletePin = onDeletePin,
                        onKeepOnlyActivePin = {
                            val keepId = activePin?.id
                            uiState.savedLocations
                                .filter { it.id != keepId }
                                .forEach { onDeletePin(it.id) }
                        },
                        onOpenAddZone = {
                            showZoneListSheet = false
                            showAddZoneDialog = true
                        },
                        onClose = { showZoneListSheet = false }
                    )
                } else {
                    CompactWorkZoneCard(
                        activePin = activePin,
                        totalZonesCount = uiState.savedLocations.size,
                        coordinateFormat = uiState.coordinateFormat,
                        zoneRadiusKm = uiState.zoneRadiusKm,
                        onSetRadiusKm = onSetZoneRadiusKm,
                        onOpenZoneList = { showZoneListSheet = true },
                        onCopy = { activePin?.let { onCopyCoordinates(it.latitude, it.longitude) } },
                        onShare = { activePin?.let { onShareCoordinates(it.latitude, it.longitude) } },
                        onDeleteActive = { activePin?.let { onDeletePin(it.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkZoneTopBar(
    activePin: SavedLocationItem?,
    zoneStatus: ZoneMonitorStatus,
    zoneRadiusKm: Float,
    distanceFromPinMeters: Double?,
    isTapToMoveEnabled: Boolean,
    onToggleTapToMove: () -> Unit,
    onOpenAddZone: () -> Unit
) {
    val statusDotColor = when {
        activePin == null -> TextHudMuted
        zoneStatus == ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank
        zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
        else -> CrimsonRed
    }

    val subtitleText = when {
        activePin == null -> String.format(Locale.US, "รัศมี %.1f กม. • แตะปุ่มเพื่อกำหนดจุดโซน", zoneRadiusKm)
        distanceFromPinMeters != null -> {
            val statusWord = if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE) "นอกโซน" else "ในโซน"
            "$statusWord • ห่าง ${GeoMath.formatShortZoneDistanceTh(distanceFromPinMeters)}"
        }
        else -> String.format(Locale.US, "โซนพื้นที่รับงาน • รัศมี %.1f กม.", zoneRadiusKm)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF0121018),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTapToMoveEnabled) CrimsonNeon else CrimsonBorderGlow
        ),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("work_zone_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status Indicator Dot + Active Zone Title
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activePin?.name ?: "โซนพื้นที่รับงาน",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE && activePin != null) {
                        CrimsonNeon
                    } else {
                        TextHudSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Toggle Tap-to-Move Mode Button (48dp height target)
            Surface(
                onClick = onToggleTapToMove,
                shape = RoundedCornerShape(12.dp),
                color = if (isTapToMoveEnabled) CrimsonRed else SurfaceLevel2,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isTapToMoveEnabled) CrimsonNeon else HairlineBorder
                ),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("btn_toggle_tap_move_zone")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditLocationAlt,
                        contentDescription = if (isTapToMoveEnabled) "ปิดโหมดย้ายจุดโซน" else "เปิดโหมดแตะย้ายจุดโซน",
                        tint = if (isTapToMoveEnabled) Color.White else CrimsonNeon,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (isTapToMoveEnabled) "กำลังย้าย" else if (activePin != null) "ย้ายจุด" else "ปักจุด",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTapToMoveEnabled) Color.White else TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Add New Work Zone Button (48dp accessible square button)
            Surface(
                onClick = onOpenAddZone,
                shape = RoundedCornerShape(12.dp),
                color = CrimsonSubtleBg,
                border = BorderStroke(1.dp, CrimsonNeon.copy(alpha = 0.6f)),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("btn_map_open_add_pin")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "เพิ่มโซนพื้นที่รับงานใหม่",
                        tint = CrimsonNeon,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactWorkZoneCard(
    activePin: SavedLocationItem?,
    totalZonesCount: Int,
    coordinateFormat: CoordinateFormat,
    zoneRadiusKm: Float,
    onSetRadiusKm: (Float) -> Unit,
    onOpenZoneList: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDeleteActive: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF2121019),
        border = BorderStroke(1.dp, CrimsonBorderGlow),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Radius Label + Value + Saved Zones Drawer Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "รัศมีโซนพื้นที่รับงาน",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextHudSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f กม.", zoneRadiusKm),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CrimsonNeon
                    )
                }

                Surface(
                    onClick = onOpenZoneList,
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceLevel2,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("btn_open_map_destinations_sheet")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CrimsonNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "โซนของฉัน ($totalZonesCount)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "เปิดรายการโซนที่บันทึก",
                            tint = TextHudMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Row 2: Smooth 0–30 km Slider
            Slider(
                value = zoneRadiusKm,
                onValueChange = onSetRadiusKm,
                valueRange = 0f..30f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = SurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("slider_zone_radius_km")
            )

            // Row 3: Quick Radius Presets (1, 3, 5, 10, 20, 30 km)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(1f, 3f, 5f, 10f, 20f, 30f).forEach { preset ->
                    val active = abs(zoneRadiusKm - preset) < 0.05f
                    Surface(
                        onClick = { onSetRadiusKm(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) CrimsonRed else SurfaceLevel2,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (active) CrimsonNeon else HairlineBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_radius_${preset.toInt()}")
                    ) {
                        Text(
                            text = "${preset.toInt()} กม.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (active) Color.White else TextHudSecondary,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Row 4: Active Zone Coordinates + Quick Copy / Share / Delete Strip
            if (activePin != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x990A080E))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = GeoMath.formatCoordinatePair(
                            activePin.latitude,
                            activePin.longitude,
                            coordinateFormat
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = TextHudSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "คัดลอกพิกัดโซน",
                                tint = CyanTelemetry,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "แชร์พิกัดโซน",
                                tint = EmeraldSRank,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteActive,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("btn_delete_selected_pin")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "ลบโซนหลักนี้",
                                tint = CrimsonNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedWorkZonesSheet(
    pins: List<SavedLocationItem>,
    activePinId: String?,
    userLat: Double?,
    userLng: Double?,
    onSelectPin: (SavedLocationItem) -> Unit,
    onDeletePin: (String) -> Unit,
    onKeepOnlyActivePin: () -> Unit,
    onOpenAddZone: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .testTag("map_destinations_list_sheet"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF7121019),
        border = BorderStroke(1.dp, CrimsonNeon.copy(alpha = 0.65f)),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "โซนพื้นที่รับงานที่บันทึก (${pins.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "แตะเพื่อเลือกเป็นโซนหลัก หรือลบโซนที่ไม่ใช้",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudMuted
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "ซ่อนรายการโซน",
                        tint = TextHudMuted
                    )
                }
            }

            if (pins.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = onKeepOnlyActivePin,
                    shape = RoundedCornerShape(12.dp),
                    color = CrimsonSubtleBg,
                    border = BorderStroke(1.dp, CrimsonNeon.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_clear_extra_zone_pins")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = CrimsonNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ล้างหมุดอื่นทั้งหมด (เก็บเฉพาะโซนหลัก)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CrimsonNeon,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pins.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ยังไม่มีโซนพื้นที่รับงานที่บันทึกไว้",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHudMuted
                    )
                    PremiumPrimaryButton(
                        text = "เพิ่มโซนพื้นที่รับงาน",
                        icon = Icons.Default.Add,
                        onClick = onOpenAddZone,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(pins.size, key = { pins[it].id }) { idx ->
                        val pin = pins[idx]
                        val isActive = pin.id == activePinId
                        val dist = if (userLat != null && userLng != null) {
                            GeoMath.formatShortZoneDistanceTh(
                                GeoMath.haversineMeters(userLat, userLng, pin.latitude, pin.longitude)
                            )
                        } else null

                        Surface(
                            onClick = { onSelectPin(pin) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) CrimsonSubtleBg else SurfaceLevel2,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) CrimsonNeon else HairlineBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.Star else Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isActive) CrimsonNeon else TextHudSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = pin.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextHudPrimary,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "%.5f, %.5f",
                                                pin.latitude,
                                                pin.longitude
                                            ) + (dist?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = TextHudMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isActive) {
                                        StatusPillBadge(text = "โซนหลัก", accentColor = EmeraldSRank)
                                    }
                                    IconButton(
                                        onClick = { onDeletePin(pin.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "ลบโซน ${pin.name}",
                                            tint = TextHudMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddWorkZoneDialog(
    hasLiveGps: Boolean,
    onDismiss: () -> Unit,
    onSaveLiveGps: (String) -> Unit,
    onSaveCoordinates: (String, String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var coordText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text("เพิ่มโซนพื้นที่รับงาน", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อโซนรับงาน (เช่น โซนสยาม / อโศก)") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_pin_name")
                )
                OutlinedTextField(
                    value = coordText,
                    onValueChange = {
                        coordText = it
                        errorText = null
                    },
                    label = { Text("พิกัดศูนย์กลางโซน (ละติจูด, ลองจิจูด)") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_pin_coords")
                )
                if (errorText != null) {
                    Text(errorText!!, style = MaterialTheme.typography.labelSmall, color = CrimsonNeon)
                }
                if (hasLiveGps) {
                    PremiumSecondaryButton(
                        text = "ใช้ตำแหน่ง GPS ปัจจุบันเป็นโซนหลัก",
                        icon = Icons.Default.MyLocation,
                        accentColor = EmeraldSRank,
                        onClick = { onSaveLiveGps(name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "บันทึกโซน",
                onClick = {
                    val ok = onSaveCoordinates(coordText, name)
                    if (!ok) {
                        errorText = "รูปแบบพิกัดไม่ถูกต้อง กรุณากรอกเช่น 13.7563, 100.5018"
                    }
                },
                testTagName = "btn_confirm_add_pin"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = TextHudMuted)
            }
        }
    )
}
