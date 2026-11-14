package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GeoMath
import com.example.model.SavedLocationItem
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState

/**
 * Screen 2: พิกัดที่บันทึก (Saved Locations Management Screen)
 */
@Composable
fun HotzonePinsTab(
    uiState: FixPointUiState,
    onSaveCurrentLocation: (String) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    onShareCoordinates: (Double, Double) -> Unit,
    onOpenInMaps: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveFix = uiState.telemetry.latitude != null && uiState.telemetry.longitude != null
    var showAddDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CrimsonPrecisionPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "รายการพิกัดที่บันทึก",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextHudPrimary
                    )
                    Text(
                        text = "จำนวนทั้งหมด ${uiState.savedLocations.size} รายการ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHudMuted
                    )
                }

                ChamferedActionButton(
                    label = "บันทึกตำแหน่งปัจจุบัน",
                    icon = Icons.Default.BookmarkAdd,
                    enabled = hasActiveFix,
                    isPrimaryCrimson = hasActiveFix,
                    onClick = { showAddDialog = !showAddDialog },
                    testTag = "btn_save_current_in_list"
                )
            }
        }

        if (showAddDialog && hasActiveFix) {
            CrimsonPrecisionPanel(
                borderColor = CrimsonNeon,
                glowAlpha = 0.25f,
                modifier = Modifier.testTag("dialog_save_from_list")
            ) {
                Text(
                    text = "บันทึกตำแหน่งปัจจุบัน",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHudPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("ชื่อสถานที่") },
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
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChamferedActionButton(
                        label = "ยกเลิก",
                        icon = Icons.Default.Close,
                        onClick = { showAddDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    ChamferedActionButton(
                        label = "บันทึก",
                        icon = Icons.Default.BookmarkAdd,
                        isPrimaryCrimson = true,
                        onClick = {
                            onSaveCurrentLocation(nameInput)
                            nameInput = ""
                            showAddDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "btn_confirm_save_from_list"
                    )
                }
            }
        }

        if (uiState.savedLocations.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("empty_saved_locations_state")
            ) {
                CrimsonPrecisionPanel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = TextHudMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "ยังไม่มีรายการพิกัดที่บันทึกไว้",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("saved_locations_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.savedLocations, key = { it.id }) { item ->
                    SavedLocationCard(
                        item = item,
                        uiState = uiState,
                        onCopy = { onCopyCoordinates(item.latitude, item.longitude) },
                        onShare = { onShareCoordinates(item.latitude, item.longitude) },
                        onOpenMap = { onOpenInMaps(item.latitude, item.longitude) },
                        onDelete = { onDeleteLocation(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedLocationCard(
    item: SavedLocationItem,
    uiState: FixPointUiState,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit
) {
    CrimsonPrecisionPanel(
        modifier = Modifier.testTag("saved_location_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "เวลาที่บันทึก: ${item.formattedTimestamp()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }

            if (item.accuracyMeters != null) {
                Box(
                    modifier = Modifier
                        .clip(PrecisionButtonShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF140B10))
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = GeoMath.formatDistanceMeters(item.accuracyMeters, uiState.measurementUnit),
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonNeon
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PrecisionButtonShape)
                .background(androidx.compose.ui.graphics.Color(0xFF09080C))
                .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = GeoMath.formatCoordinatePair(
                    item.latitude,
                    item.longitude,
                    uiState.coordinateFormat
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                color = CrimsonNeon
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                label = "เปิดแผนที่",
                icon = Icons.Default.Map,
                isPrimaryCrimson = true,
                onClick = onOpenMap,
                modifier = Modifier.weight(1f),
                testTag = "btn_map_saved_${item.id}"
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
