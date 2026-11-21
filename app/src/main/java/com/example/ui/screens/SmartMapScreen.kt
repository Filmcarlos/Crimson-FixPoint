package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.RiderIntelligenceEngine
import com.example.model.GeoMath
import com.example.model.MapLayerFilters
import com.example.model.MapTapActionMode
import com.example.model.PinCategory
import com.example.model.SavedLocationItem
import com.example.ui.components.InteractiveZoneMapCanvas
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
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
import com.example.ui.theme.VioletInsight
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun SmartMapScreen(
    uiState: FixPointUiState,
    onMapTapPin: (Double, Double) -> Unit,
    onSetMapTapMode: (MapTapActionMode) -> Unit,
    onSetNewPinCategory: (PinCategory) -> Unit,
    onUpdateLayerFilters: ((MapLayerFilters) -> MapLayerFilters) -> Unit,
    onSelectMapPinForSheet: (String?) -> Unit,
    onSelectActivePin: (String) -> Unit,
    onSaveCurrentLocation: (String, Boolean, PinCategory) -> Unit,
    onPinFromCoordinates: (String, String, Boolean, PinCategory) -> Boolean,
    onDeletePin: (String) -> Unit,
    onSetZoneRadiusKm: (Float) -> Unit,
    onSearchNearbyFuel: () -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    onShareCoordinates: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddPinDialog by remember { mutableStateOf(false) }
    var showDestinationsDrawer by remember { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val hotspots = remember(
        uiState.savedLocations,
        uiState.analyzedOrders,
        uiState.telemetry.latitude,
        uiState.telemetry.longitude
    ) {
        RiderIntelligenceEngine.computeHotspotRecommendations(
            savedPins = uiState.savedLocations,
            analyzedOrders = uiState.analyzedOrders,
            riderLat = uiState.telemetry.latitude,
            riderLng = uiState.telemetry.longitude
        )
    }

    val selectedPin = uiState.selectedMapPin

    if (showAddPinDialog) {
        AddPinDialog(
            defaultCategory = uiState.newPinCategory,
            hasLiveGps = uiState.telemetry.hasFix,
            onDismiss = { showAddPinDialog = false },
            onSaveLiveGps = { name, category ->
                onSaveCurrentLocation(name, false, category)
                showAddPinDialog = false
            },
            onSaveCoordinates = { coords, name, category ->
                val ok = onPinFromCoordinates(coords, name, false, category)
                if (ok) showAddPinDialog = false
                ok
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_map_screen")
    ) {
        // Full-Screen Dark OpenStreetMap Canvas
        InteractiveZoneMapCanvas(
            activePin = uiState.activePin,
            allPins = uiState.savedLocations,
            userLatitude = uiState.telemetry.latitude,
            userLongitude = uiState.telemetry.longitude,
            radiusKm = uiState.zoneRadiusKm,
            gpsBufferMeters = uiState.gpsBufferMeters,
            zoneStatus = uiState.zoneStatus,
            distanceFromPinMeters = uiState.currentDistanceFromPinMeters,
            onMapTapPin = onMapTapPin,
            mapHeightDp = null,
            layerFilters = uiState.mapLayerFilters,
            hotspots = hotspots,
            poiPins = uiState.nearbyOverpassFuelPois,
            focusTargetLatLng = focusTarget,
            onMarkerSelected = { pin -> onSelectMapPinForSheet(pin.id) },
            modifier = Modifier.fillMaxSize(),
            testTagName = "smart_map_osm_canvas"
        )

        // Top Floating Layer Filter Chips Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapLayerChip(
                    label = "FixPoint",
                    selected = uiState.mapLayerFilters.showFixPoints,
                    accent = CrimsonNeon,
                    onClick = { onUpdateLayerFilters { it.copy(showFixPoints = !it.showFixPoints) } },
                    testTag = "chip_layer_fixpoint"
                )
                MapLayerChip(
                    label = "Hotzone",
                    selected = uiState.mapLayerFilters.showHotzones,
                    accent = VioletInsight,
                    onClick = { onUpdateLayerFilters { it.copy(showHotzones = !it.showHotzones) } },
                    testTag = "chip_layer_hotzone"
                )
                MapLayerChip(
                    label = "ร้านอาหาร",
                    selected = uiState.mapLayerFilters.showRestaurants,
                    accent = AmberWarning,
                    onClick = { onUpdateLayerFilters { it.copy(showRestaurants = !it.showRestaurants) } },
                    testTag = "chip_layer_restaurant"
                )
                MapLayerChip(
                    label = "Heatmap (ประวัติ)",
                    selected = uiState.mapLayerFilters.showHeatmap,
                    accent = CrimsonRed,
                    onClick = { onUpdateLayerFilters { it.copy(showHeatmap = !it.showHeatmap) } },
                    testTag = "chip_layer_heatmap"
                )
                MapLayerChip(
                    label = "รัศมีโซน",
                    selected = uiState.mapLayerFilters.showZoneRadius,
                    accent = EmeraldSRank,
                    onClick = { onUpdateLayerFilters { it.copy(showZoneRadius = !it.showZoneRadius) } },
                    testTag = "chip_layer_zone_radius"
                )
                MapLayerChip(
                    label = "ปั๊มน้ำมัน",
                    selected = uiState.mapLayerFilters.showFuelStations,
                    accent = CyanTelemetry,
                    onClick = { onUpdateLayerFilters { it.copy(showFuelStations = !it.showFuelStations) } },
                    testTag = "chip_layer_fuel"
                )
            }

            // Compact Tap Mode Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE6120F17))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    MapTapActionMode.entries.forEach { mode ->
                        val active = uiState.mapTapActionMode == mode
                        Text(
                            text = mode.labelTh,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else TextHudMuted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) CrimsonRed else Color.Transparent)
                                .clickable { onSetMapTapMode(mode) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("tap_mode_${mode.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }

                // Quick search fuel & open add pin dialog
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xE6120F17),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable(onClick = onSearchNearbyFuel)
                            .testTag("btn_map_search_fuel")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.LocalGasStation, contentDescription = "ค้นหาปั๊ม", tint = AmberWarning, modifier = Modifier.size(15.dp))
                            Text("ปั๊ม", style = MaterialTheme.typography.labelSmall, color = TextHudPrimary)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CrimsonRed,
                        modifier = Modifier
                            .clickable { showAddPinDialog = true }
                            .testTag("btn_map_open_add_pin")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = "เพิ่มหมุด", tint = Color.White, modifier = Modifier.size(15.dp))
                            Text("เพิ่มหมุด", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom Interactive Destination Detail Sheet OR Compact Drawer Launcher
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedPin != null) {
                DestinationDetailBottomCard(
                    pin = selectedPin,
                    isActivePin = uiState.activePin?.id == selectedPin.id,
                    userLat = uiState.telemetry.latitude,
                    userLng = uiState.telemetry.longitude,
                    coordinateFormat = uiState.coordinateFormat,
                    zoneRadiusKm = uiState.zoneRadiusKm,
                    onSetActive = { onSelectActivePin(selectedPin.id) },
                    onSetRadiusKm = onSetZoneRadiusKm,
                    onCopy = { onCopyCoordinates(selectedPin.latitude, selectedPin.longitude) },
                    onShare = { onShareCoordinates(selectedPin.latitude, selectedPin.longitude) },
                    onDelete = { onDeletePin(selectedPin.id) },
                    onClose = { onSelectMapPinForSheet(null) }
                )
            } else if (showDestinationsDrawer) {
                SavedPinsAndHotspotsSheet(
                    pins = uiState.savedLocations,
                    activePinId = uiState.activePin?.id,
                    userLat = uiState.telemetry.latitude,
                    userLng = uiState.telemetry.longitude,
                    zoneRadiusKm = uiState.zoneRadiusKm,
                    onSetRadiusKm = onSetZoneRadiusKm,
                    onSelectPin = { pin ->
                        onSelectMapPinForSheet(pin.id)
                        focusTarget = pin.latitude to pin.longitude
                        showDestinationsDrawer = false
                    },
                    onClose = { showDestinationsDrawer = false }
                )
            } else {
                // Sleek floating bottom summary bar to open destinations / adjust radius
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDestinationsDrawer = true }
                        .testTag("btn_open_map_destinations_sheet"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF013111A),
                    border = BorderStroke(1.dp, CrimsonBorderGlow)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = CrimsonNeon)
                            Column {
                                Text(
                                    text = uiState.activePin?.let { "โซนหลัก: ${it.name}" }
                                        ?: "แตะแผนที่เพื่อปักหมุด หรือเปิดรายการหมุด",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextHudPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "รัศมี %.1f กม. • %d หมุด • %d โซนแนะนำ (ประวัติ)",
                                        uiState.zoneRadiusKm,
                                        uiState.savedLocations.size,
                                        hotspots.size
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextHudSecondary
                                )
                            }
                        }
                        StatusPillBadge(text = "จัดการโซน", accentColor = CrimsonNeon)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLayerChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xE613111A),
            labelColor = TextHudSecondary,
            selectedContainerColor = accent.copy(alpha = 0.25f),
            selectedLabelColor = TextHudPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = HairlineBorder,
            selectedBorderColor = accent
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun DestinationDetailBottomCard(
    pin: SavedLocationItem,
    isActivePin: Boolean,
    userLat: Double?,
    userLng: Double?,
    coordinateFormat: com.example.model.CoordinateFormat,
    zoneRadiusKm: Float,
    onSetActive: () -> Unit,
    onSetRadiusKm: (Float) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val distMeters = if (userLat != null && userLng != null) {
        GeoMath.haversineMeters(userLat, userLng, pin.latitude, pin.longitude)
    } else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_destination_detail_sheet"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF714111B),
        border = BorderStroke(1.dp, CrimsonNeon.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPillBadge(text = pin.category.labelTh, accentColor = CrimsonNeon)
                        if (isActivePin) {
                            StatusPillBadge(text = "หมุดโซนหลัก", accentColor = EmeraldSRank, icon = Icons.Default.Star)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pin.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = GeoMath.formatCoordinatePair(pin.latitude, pin.longitude, coordinateFormat) +
                            (distMeters?.let { " • ห่าง ${GeoMath.formatShortZoneDistanceTh(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudSecondary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "ปิดรายละเอียด", tint = TextHudMuted)
                }
            }

            if (pin.historicalOrderCount > 0) {
                Text(
                    text = String.format(
                        Locale.US,
                        "ประวัติในเครื่อง: %d ออเดอร์ • ค่าส่งเฉลี่ย ฿%.0f (ประเมินจากประวัติในเครื่อง)",
                        pin.historicalOrderCount,
                        pin.historicalAvgPayoutBaht
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = VioletInsight
                )
            }

            // Zone Radius Slider (0 - 30 km preserved!)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("รัศมีโซนรับงาน (0 – 30 กม.)", style = MaterialTheme.typography.labelMedium, color = TextHudSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f กม.", zoneRadiusKm),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = CrimsonNeon
                    )
                }
                Slider(
                    value = zoneRadiusKm,
                    onValueChange = onSetRadiusKm,
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = CrimsonNeon,
                        activeTrackColor = CrimsonRed,
                        inactiveTrackColor = SurfaceElevated
                    ),
                    modifier = Modifier.testTag("slider_zone_radius_km")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1f, 3f, 5f, 10f, 20f, 30f).forEach { preset ->
                        val active = kotlin.math.abs(zoneRadiusKm - preset) < 0.05f
                        Text(
                            text = "${preset.toInt()}กม.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else TextHudSecondary,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) CrimsonRed else SurfaceLevel2)
                                .clickable { onSetRadiusKm(preset) }
                                .padding(vertical = 6.dp)
                                .testTag("preset_radius_${preset.toInt()}"),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActivePin) {
                    PremiumPrimaryButton(
                        text = "ตั้งเป็นโซนหลัก",
                        icon = Icons.Default.Star,
                        onClick = onSetActive,
                        modifier = Modifier.weight(1.2f),
                        testTagName = "btn_set_active_pin"
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLevel2)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "คัดลอกพิกัด", tint = CyanTelemetry)
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLevel2)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "แชร์พิกัด", tint = EmeraldSRank)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CrimsonSubtleBg)
                        .testTag("btn_delete_selected_pin")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "ลบหมุด", tint = CrimsonNeon)
                }
            }
        }
    }
}

@Composable
private fun SavedPinsAndHotspotsSheet(
    pins: List<SavedLocationItem>,
    activePinId: String?,
    userLat: Double?,
    userLng: Double?,
    zoneRadiusKm: Float,
    onSetRadiusKm: (Float) -> Unit,
    onSelectPin: (SavedLocationItem) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .testTag("map_destinations_list_sheet"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF714111B),
        border = BorderStroke(1.dp, CrimsonBorderGlow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "หมุดที่บันทึกและโซนรับงาน (${pins.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "ปิดรายการ", tint = TextHudMuted)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("รัศมีโซน: ${String.format(Locale.US, "%.1f", zoneRadiusKm)} กม.", style = MaterialTheme.typography.labelMedium, color = CrimsonNeon)
                Slider(
                    value = zoneRadiusKm,
                    onValueChange = onSetRadiusKm,
                    valueRange = 0f..30f,
                    modifier = Modifier.width(180.dp),
                    colors = SliderDefaults.colors(thumbColor = CrimsonNeon, activeTrackColor = CrimsonRed)
                )
            }
            if (pins.isEmpty()) {
                Text(
                    text = "ยังไม่มีหมุดในเครื่อง แตะบนแผนที่หรือกด 'เพิ่มหมุด' เพื่อเริ่มตั้งโซนรับงาน",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHudMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(pins.size, key = { pins[it].id }) { idx ->
                        val pin = pins[idx]
                        val isActive = pin.id == activePinId
                        val dist = if (userLat != null && userLng != null) {
                            GeoMath.formatShortZoneDistanceTh(GeoMath.haversineMeters(userLat, userLng, pin.latitude, pin.longitude))
                        } else null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPin(pin) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) CrimsonSubtleBg else SurfaceLevel2,
                            border = BorderStroke(1.dp, if (isActive) CrimsonNeon else HairlineBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isActive) CrimsonNeon else TextHudSecondary
                                    )
                                    Column {
                                        Text(
                                            text = pin.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextHudPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pin.category.labelTh + (dist?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextHudMuted
                                        )
                                    }
                                }
                                if (isActive) {
                                    StatusPillBadge(text = "หลัก", accentColor = EmeraldSRank)
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
private fun AddPinDialog(
    defaultCategory: PinCategory,
    hasLiveGps: Boolean,
    onDismiss: () -> Unit,
    onSaveLiveGps: (String, PinCategory) -> Unit,
    onSaveCoordinates: (String, String, PinCategory) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var coordText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text("เพิ่มหมุดบน Smart Map", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อสถานที่ / โซน") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("input_add_pin_name")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PinCategory.FIXPOINT,
                        PinCategory.HOTZONE,
                        PinCategory.RESTAURANT,
                        PinCategory.FUEL
                    ).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.labelTh, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = coordText,
                    onValueChange = {
                        coordText = it
                        errorText = null
                    },
                    label = { Text("พิกัด (เช่น 13.7563, 100.5018)") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("input_add_pin_coords")
                )
                if (errorText != null) {
                    Text(errorText!!, style = MaterialTheme.typography.labelSmall, color = CrimsonNeon)
                }
                if (hasLiveGps) {
                    PremiumSecondaryButton(
                        text = "ใช้ตำแหน่ง GPS ปัจจุบัน",
                        icon = Icons.Default.MyLocation,
                        accentColor = EmeraldSRank,
                        onClick = { onSaveLiveGps(name, selectedCategory) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "บันทึกหมุด",
                onClick = {
                    val ok = onSaveCoordinates(coordText, name, selectedCategory)
                    if (!ok) {
                        errorText = "รูปแบบพิกัดไม่ถูกต้อง กรุณากรอก ละติจูด, ลองจิจูด"
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
