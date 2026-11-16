package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
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
import java.util.Locale

@Composable
fun FuelAndRestStopsPanel(
    uiState: FixPointUiState,
    onAddCustomPoi: (String, StopPoiCategory, Double, Double) -> Boolean,
    onDeleteSavedPoi: (String) -> Unit,
    onSetOverpassRadiusKm: (Int) -> Unit,
    onSearchNearbyFuel: (Boolean) -> Unit,
    onSaveOverpassToMyPins: (StopPoiItem) -> Unit
) {
    val context = LocalContext.current
    val userLat = uiState.telemetry.latitude
    val userLng = uiState.telemetry.longitude
    val hasGps = userLat != null && userLng != null

    var selectedCategory by remember { mutableStateOf(StopPoiCategory.FUEL) }
    var customPoiName by remember { mutableStateOf("") }
    var customPoiCoords by remember { mutableStateOf("") }
    var customPoiError by remember { mutableStateOf<String?>(null) }
    var showAddPoiForm by remember { mutableStateOf(false) }

    val openNavigationToLatLng: (Double, Double, String) -> Unit = { lat, lng, label ->
        runCatching {
            val encodedLabel = Uri.encode(label)
            val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
        }
    }

    // Sort user-saved POIs nearest-to-farthest from real GPS
    val sortedSavedPois = remember(uiState.savedPois, userLat, userLng) {
        if (userLat != null && userLng != null) {
            uiState.savedPois.sortedBy {
                GeoMath.haversineMeters(userLat, userLng, it.latitude, it.longitude)
            }
        } else {
            uiState.savedPois
        }
    }

    // Sort Overpass fuel stations nearest-to-farthest from real GPS
    val sortedOverpassPois = remember(uiState.nearbyOverpassFuelPois, userLat, userLng) {
        if (userLat != null && userLng != null) {
            uiState.nearbyOverpassFuelPois.sortedBy {
                GeoMath.haversineMeters(userLat, userLng, it.latitude, it.longitude)
            }
        } else {
            uiState.nearbyOverpassFuelPois
        }
    }

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
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "หมุดปั๊มน้ำมันและจุดพัก",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF1A0F15))
                    .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                    .clickable { showAddPoiForm = !showAddPoiForm }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("btn_toggle_add_poi_form")
            ) {
                Text(
                    text = if (showAddPoiForm) "ซ่อนฟอร์ม" else "+ บันทึกจุดแวะ",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showAddPoiForm) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF0A090E))
                    .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                    .padding(12.dp)
                    .testTag("panel_add_custom_poi")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StopPoiCategory.entries.forEach { cat ->
                        val isSel = selectedCategory == cat
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .height(40.dp)
                                .clip(PrecisionButtonShape)
                                .background(
                                    if (isSel) Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                                    else Brush.linearGradient(listOf(Color(0xFF141016), Color(0xFF0C0A0F)))
                                )
                                .border(1.dp, if (isSel) CrimsonNeon else CrimsonBorderGlow, PrecisionButtonShape)
                                .clickable { selectedCategory = cat }
                                .testTag("btn_poi_cat_${cat.name.lowercase()}")
                        ) {
                            Text(
                                text = cat.labelTh,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSel) Color.White else TextHudSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customPoiName,
                    onValueChange = { customPoiName = it },
                    label = { Text("ชื่อปั๊มน้ำมัน / จุดพัก") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_custom_poi_name")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customPoiCoords,
                    onValueChange = {
                        customPoiCoords = it
                        customPoiError = null
                    },
                    label = { Text("ละติจูด, ลองจิจูด (เว้นว่างหากใช้ GPS ปัจจุบัน)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_custom_poi_coords")
                )

                if (customPoiError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(customPoiError!!, style = MaterialTheme.typography.labelMedium, color = CrimsonNeon)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChamferedActionButton(
                        label = "ใช้พิกัดปัจจุบัน",
                        icon = Icons.Default.MyLocation,
                        enabled = hasGps,
                        isPrimaryCrimson = hasGps,
                        onClick = {
                            if (userLat != null && userLng != null) {
                                onAddCustomPoi(customPoiName, selectedCategory, userLat, userLng)
                                customPoiName = ""
                                customPoiCoords = ""
                                showAddPoiForm = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_save_poi_current_gps"
                    )
                    ChamferedActionButton(
                        label = "บันทึกจากพิกัด",
                        icon = Icons.Default.AddLocationAlt,
                        onClick = {
                            val parsed = GeoMath.parseCoordinateInput(customPoiCoords)
                            if (parsed != null) {
                                onAddCustomPoi(customPoiName, selectedCategory, parsed.first, parsed.second)
                                customPoiName = ""
                                customPoiCoords = ""
                                customPoiError = null
                                showAddPoiForm = false
                            } else {
                                customPoiError = "พิกัดไม่ถูกต้อง"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_save_poi_from_coords"
                    )
                }
            }
        }

        // User-saved Fuel & Rest Stops (sorted nearest to farthest)
        if (sortedSavedPois.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "จุดแวะที่บันทึกไว้ (เรียงใกล้ไปไกล ${sortedSavedPois.size} จุด)",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedSavedPois.forEach { poi ->
                    val distMeters = if (userLat != null && userLng != null) {
                        GeoMath.haversineMeters(userLat, userLng, poi.latitude, poi.longitude)
                    } else null
                    val bearingDeg = if (userLat != null && userLng != null) {
                        GeoMath.bearingDegrees(userLat, userLng, poi.latitude, poi.longitude)
                    } else null

                    PoiRowCard(
                        poi = poi,
                        distanceMeters = distMeters,
                        bearingDegrees = bearingDeg,
                        onNavigate = { openNavigationToLatLng(poi.latitude, poi.longitude, poi.name) },
                        onDelete = { onDeleteSavedPoi(poi.id) },
                        onSaveToMyPins = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Overpass API Nearby Fuel Station Search
        Text(
            text = "ค้นหาปั๊มน้ำมันใกล้ฉัน (Overpass API)",
            style = MaterialTheme.typography.titleMedium,
            color = TextHudPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Radius selector: 2 / 5 / 10 กม.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(2, 5, 10).forEach { rKm ->
                val isSelected = uiState.overpassSearchRadiusKm == rKm
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(38.dp)
                        .clip(PrecisionButtonShape)
                        .background(
                            if (isSelected) Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                            else Brush.linearGradient(listOf(Color(0xFF141016), Color(0xFF0C0A0F)))
                        )
                        .border(1.dp, if (isSelected) CrimsonNeon else CrimsonBorderGlow, PrecisionButtonShape)
                        .clickable { onSetOverpassRadiusKm(rKm) }
                        .testTag("btn_overpass_radius_$rKm")
                ) {
                    Text(
                        text = "รัศมี $rKm กม.",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Color.White else TextHudSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChamferedActionButton(
                label = if (uiState.isSearchingOverpass) "กำลังค้นหา..." else "ค้นหาปั๊มน้ำมันใกล้ฉัน",
                icon = Icons.Default.Search,
                enabled = !uiState.isSearchingOverpass,
                isPrimaryCrimson = true,
                onClick = { onSearchNearbyFuel(false) },
                modifier = Modifier.weight(1.3f),
                testTag = "btn_search_nearby_fuel"
            )
            ChamferedActionButton(
                label = "รีเฟรช",
                icon = Icons.Default.Refresh,
                enabled = !uiState.isSearchingOverpass,
                onClick = { onSearchNearbyFuel(true) },
                modifier = Modifier.weight(0.7f),
                testTag = "btn_refresh_nearby_fuel"
            )
        }

        if (uiState.overpassErrorOrStatusTh != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.overpassErrorOrStatusTh,
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.overpassErrorOrStatusTh.contains("ไม่สำเร็จ")) CrimsonNeon else EmeraldSRank,
                modifier = Modifier.testTag("text_overpass_status")
            )
        }

        if (sortedOverpassPois.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedOverpassPois.take(20).forEach { poi ->
                    val distMeters = if (userLat != null && userLng != null) {
                        GeoMath.haversineMeters(userLat, userLng, poi.latitude, poi.longitude)
                    } else null
                    val bearingDeg = if (userLat != null && userLng != null) {
                        GeoMath.bearingDegrees(userLat, userLng, poi.latitude, poi.longitude)
                    } else null

                    PoiRowCard(
                        poi = poi,
                        distanceMeters = distMeters,
                        bearingDegrees = bearingDeg,
                        onNavigate = { openNavigationToLatLng(poi.latitude, poi.longitude, poi.name) },
                        onDelete = null,
                        onSaveToMyPins = { onSaveOverpassToMyPins(poi) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ข้อมูลปั๊มน้ำมัน: © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = TextHudMuted
        )
    }
}

@Composable
private fun PoiRowCard(
    poi: StopPoiItem,
    distanceMeters: Double?,
    bearingDegrees: Float?,
    onNavigate: () -> Unit,
    onDelete: (() -> Unit)?,
    onSaveToMyPins: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PrecisionButtonShape)
            .background(Color(0xFF0A090E))
            .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
            .padding(10.dp)
            .testTag("poi_card_${poi.id}")
    ) {
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
                    imageVector = if (poi.category == StopPoiCategory.FUEL) {
                        Icons.Default.LocalGasStation
                    } else {
                        Icons.Default.LocalParking
                    },
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = poi.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${poi.category.labelTh} • ทิศ${GeoMath.shortCardinalDirectionTh(bearingDegrees)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudSecondary
                    )
                }
            }

            Text(
                text = GeoMath.formatShortZoneDistanceTh(distanceMeters),
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldSRank,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChamferedActionButton(
                label = "นำทาง Google Maps",
                icon = Icons.Default.Navigation,
                isPrimaryCrimson = true,
                onClick = onNavigate,
                modifier = Modifier.weight(1f),
                testTag = "btn_navigate_poi_${poi.id}"
            )
            if (onSaveToMyPins != null) {
                ChamferedActionButton(
                    label = "บันทึกเป็นหมุดของฉัน",
                    icon = Icons.Default.BookmarkAdd,
                    onClick = onSaveToMyPins,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_save_overpass_poi_${poi.id}"
                )
            }
            if (onDelete != null) {
                ChamferedActionButton(
                    label = "ลบ",
                    icon = Icons.Default.DeleteOutline,
                    onClick = onDelete,
                    modifier = Modifier.width(86.dp),
                    testTag = "btn_delete_poi_${poi.id}"
                )
            }
        }
    }
}
