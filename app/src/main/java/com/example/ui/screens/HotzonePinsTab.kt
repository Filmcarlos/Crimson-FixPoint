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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.HotzonePin
import com.example.ui.components.CrimsonGlassCard
import com.example.ui.components.TacticalLeafletMapView
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import java.util.Locale

/**
 * Tab 3: HOTZONE PINS & CENTROID (คลังหมุดทองคำและคำนวณจุดศูนย์กลางดงร้าน + แผนที่จริง CartoDB Dark Matter)
 */
@Composable
fun HotzonePinsTab(
    uiState: FixPointUiState,
    onSelectPin: (HotzonePin) -> Unit,
    onMapPinClicked: (Double, Double) -> Unit,
    onUseCurrentGpsAsPin: () -> Unit,
    onAddCustomPin: (String, String, Double, Double, String) -> Unit,
    onAddMerchantToCluster: (String, String, Int, Int) -> Unit,
    onRemoveMerchantFromCluster: (String) -> Unit,
    onSnapPinToGoldenCentroid: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddPinForm by remember { mutableStateOf(false) }
    var newPinName by remember { mutableStateOf("") }
    var newPinZone by remember { mutableStateOf("") }
    var newPinLat by remember { mutableStateOf(String.format(Locale.US, "%.4f", uiState.activePin.lat + 0.004)) }
    var newPinLng by remember { mutableStateOf(String.format(Locale.US, "%.4f", uiState.activePin.lng + 0.003)) }
    var newPinDesc by remember { mutableStateOf("") }

    var newMerchantName by remember { mutableStateOf("") }
    var newMerchantCategory by remember { mutableStateOf("สตรีทฟู้ด / คาเฟ่") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Interactive Leaflet.js CartoDB Dark Matter Map for Tap-to-Pin
        TacticalLeafletMapView(
            activePin = uiState.activePin,
            maxPickupRadiusKm = uiState.maxPickupRadiusKm,
            maxDropoffRadiusKm = uiState.maxDropoffRadiusKm,
            isLocked = uiState.isFixPointLocked,
            merchants = uiState.clusterMerchants,
            goldenCentroidLat = uiState.goldenCentroidLat,
            goldenCentroidLng = uiState.goldenCentroidLng,
            orderEvaluation = uiState.currentOrderEvaluation,
            onMapPinClicked = onMapPinClicked,
            mapHeight = 290.dp,
            testTagName = "hotzone_leaflet_map"
        )

        // 2. Action Bar: Pull Current GPS as Pin & Add Unlimited New Hotzone Pin
        CrimsonGlassCard {
            Text(
                text = "NONTHABURI & BANGKOK RIDER HOTZONES (คลังหมุดทองคำ)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonNeon
            )
            Text(
                text = "แตะปักหมุดบนแผนที่จริงด้านบน เลือกหมุดโซนทำเงิน หรือดึงพิกัดปัจจุบันของคุณตั้งเป็นหมุดหลักทันที",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextHudSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUseCurrentGpsAsPin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("use_current_gps_pin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ดึงพิกัดปัจจุบันตั้งหมุด",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Button(
                    onClick = { showAddPinForm = !showAddPinForm },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarbonDark,
                        contentColor = EmeraldSRank
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, EmeraldSRank.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .testTag("toggle_add_pin_form_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showAddPinForm) "ปิดฟอร์ม" else "เพิ่มหมุดใหม่",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp)
                    )
                }
            }

            if (showAddPinForm) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBlack.copy(alpha = 0.85f))
                        .border(1.dp, EmeraldSRank.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "เพิ่มหมุดทองคำใหม่ (ไม่จำกัดจำนวน)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldSRank
                    )
                    OutlinedTextField(
                        value = newPinName,
                        onValueChange = { newPinName = it },
                        label = { Text("ชื่อหมุด (เช่น โซนรัตนาธิเบศร์ - เซ็นทรัล)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_name_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPinZone,
                            onValueChange = { newPinZone = it },
                            label = { Text("ป้ายโซนสั้นๆ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newPinLat,
                            onValueChange = { newPinLat = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newPinLng,
                            onValueChange = { newPinLng = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = newPinDesc,
                        onValueChange = { newPinDesc = it },
                        label = { Text("รายละเอียดจุดจอด/ดงร้านอาหาร") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val lat = newPinLat.toDoubleOrNull() ?: uiState.activePin.lat
                            val lng = newPinLng.toDoubleOrNull() ?: uiState.activePin.lng
                            onAddCustomPin(newPinName, newPinZone, lat, lng, newPinDesc)
                            newPinName = ""
                            newPinZone = ""
                            newPinDesc = ""
                            showAddPinForm = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSRank,
                            contentColor = ObsidianBlack
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("save_new_pin_button")
                    ) {
                        Text(
                            text = "บันทึกและล็อคหมุดนี้ทันที",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 3. Pre-loaded & Custom Hotzone Pins List
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            uiState.hotzonePins.forEach { pin ->
                val isSelected = pin.id == uiState.activePin.id
                CrimsonGlassCard(
                    borderColor = if (isSelected) CrimsonNeon else CrimsonBorderGlow,
                    modifier = Modifier
                        .clickable { onSelectPin(pin) }
                        .testTag("hotzone_pin_card_${pin.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CrimsonRed else ObsidianBlack)
                                    .border(
                                        1.dp,
                                        if (isSelected) CrimsonNeon else CrimsonBorderGlow,
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.GpsFixed else Icons.Default.PinDrop,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else CrimsonNeon
                                )
                            }

                            Column {
                                Text(
                                    text = pin.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextHudPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(Locale.US, "%.4f°N, %.4f°E • ร้านค้า ~%d ร้าน", pin.lat, pin.lng, pin.merchantCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) EmeraldSRank.copy(alpha = 0.2f)
                                    else ObsidianBlack
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) EmeraldSRank else CrimsonBorderGlow,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSelected) "LOCKED PIN" else "กดตั้งหมุด",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) EmeraldSRank else TextHudSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pin.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🔥 ความหนาแน่นออเดอร์: ${pin.avgOrderRate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
            }
        }

        // 4. Merchant Cluster Centroid Calculator
        CrimsonGlassCard(borderColor = CyanTelemetry.copy(alpha = 0.55f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = CyanTelemetry
                )
                Column {
                    Text(
                        text = "MERCHANT CLUSTER CENTROID CALCULATOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanTelemetry
                    )
                    Text(
                        text = "คำนวณ 'พิกัดกึ่งกลางทองคำ (Golden Centroid Point)' ที่อยู่ใกล้ทุกร้านเฉลี่ยสั้นที่สุด",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBlack.copy(alpha = 0.9f))
                    .border(1.5.dp, EmeraldSRank, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GOLDEN CENTROID POINT (จุดยืนกึ่งกลางดงร้าน)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldSRank,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.clusterMerchants.size} ร้านในคลัสเตอร์",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanTelemetry
                    )
                }

                Text(
                    text = String.format(
                        Locale.US,
                        "%.5f°N, %.5f°E (เฉลี่ยห่างทุกร้านเพียง %s กม.)",
                        uiState.goldenCentroidLat,
                        uiState.goldenCentroidLng,
                        GeoMath.formatKm(uiState.goldenCentroidAvgDistKm)
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                    color = TextHudPrimary
                )

                Button(
                    onClick = onSnapPinToGoldenCentroid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldSRank,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snap_to_golden_centroid_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ล็อคหมุดเข้าจุดกึ่งกลางทองคำนี้ทันที",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.clusterMerchants.forEachIndexed { idx, merchant ->
                    val distToCentroidMeters = (GeoMath.haversineKm(
                        uiState.goldenCentroidLat,
                        uiState.goldenCentroidLng,
                        merchant.lat,
                        merchant.lng
                    ) * 1000).toInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBlack.copy(alpha = 0.72f))
                            .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = merchant.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextHudPrimary
                                )
                                Text(
                                    text = "${merchant.category} • ห่างจุด Centroid ${distToCentroidMeters}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextHudMuted
                                )
                            }
                        }

                        if (uiState.clusterMerchants.size > 2) {
                            IconButton(
                                onClick = { onRemoveMerchantFromCluster(merchant.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "ลบร้านออกจากคลัสเตอร์",
                                    tint = TextHudMuted
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newMerchantName,
                    onValueChange = { newMerchantName = it },
                    label = { Text("เพิ่มชื่อร้านอาหารในละแวกนี้") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanTelemetry,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_cluster_merchant_input")
                )

                Button(
                    onClick = {
                        val offsetN = ((-220..240).random())
                        val offsetE = ((-220..240).random())
                        onAddMerchantToCluster(newMerchantName, newMerchantCategory, offsetN, offsetE)
                        newMerchantName = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanTelemetry,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("add_cluster_merchant_button")
                ) {
                    Icon(imageVector = Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("เพิ่มร้าน", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
