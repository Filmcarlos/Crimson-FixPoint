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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeliveryStopItem
import com.example.model.GeoMath
import com.example.ui.components.ChamferedActionButton
import com.example.ui.components.CrimsonPrecisionPanel
import com.example.ui.components.InteractiveZoneMapCanvas
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
import java.util.Locale

@Composable
fun WorkAndCostTab(
    uiState: FixPointUiState,
    onAddDeliveryStop: (String, Double, Double) -> Boolean,
    onAddDeliveryStopFromCoords: (String, String) -> Boolean,
    onEditDeliveryStop: (String, String, Double, Double) -> Boolean,
    onToggleDeliveryStopDelivered: (String) -> Unit,
    onDeleteDeliveryStop: (String) -> Unit,
    onClearAllDeliveryStops: () -> Unit,
    onOptimizeDeliveryRoute: () -> Unit,
    onUpdateFixedDailyCosts: (Double) -> Unit,
    onAddCustomExpense: (String, Double) -> Boolean,
    onDeleteCustomExpense: (String) -> Unit,
    onUpdateFuelSettings: (Float, Float, Float) -> Unit,
    onMarkRefueledFullTank: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Section 1 state (Delivery Route)
    var showPickFromPinPanel by remember { mutableStateOf(false) }
    var showPasteStopPanel by remember { mutableStateOf(false) }
    var pasteStopCoordsInput by remember { mutableStateOf("") }
    var pasteStopNameInput by remember { mutableStateOf("") }
    var pasteStopError by remember { mutableStateOf<String?>(null) }

    // Section 2 state (Daily Break-Even)
    val breakEven = uiState.breakEvenState
    var dailyFuelCostText by remember(breakEven.fuelCostBaht) {
        mutableStateOf(
            if (breakEven.fuelCostBaht > 0.0) String.format(Locale.US, "%.0f", breakEven.fuelCostBaht) else ""
        )
    }
    var customExpTitleInput by remember { mutableStateOf("") }
    var customExpAmountInput by remember { mutableStateOf("") }

    // Section 3 state (Fuel Estimator)
    val fuel = uiState.fuelState
    var tankCapText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", fuel.tankCapacityLiters))
    }
    var consumptionText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", fuel.consumptionKmPerLiter))
    }
    var lowWarnText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", fuel.lowFuelWarningLiters))
    }

    LaunchedEffect(fuel.tankCapacityLiters, fuel.consumptionKmPerLiter, fuel.lowFuelWarningLiters) {
        tankCapText = String.format(Locale.US, "%.1f", fuel.tankCapacityLiters)
        consumptionText = String.format(Locale.US, "%.1f", fuel.consumptionKmPerLiter)
        lowWarnText = String.format(Locale.US, "%.1f", fuel.lowFuelWarningLiters)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // 1. เรียงลำดับจุดส่งอัตโนมัติ (สูงสุด 15 จุด • Nearest Neighbor + 2-opt)
        // =====================================================================
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
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = CrimsonNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "เรียงลำดับจุดส่งอัตโนมัติ",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${uiState.deliveryStops.size}/${DeliveryStopItem.MAX_STOPS} จุด",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.telemetry.latitude != null && uiState.telemetry.longitude != null) {
                        String.format(
                            Locale.US,
                            "จุดเริ่มต้น GPS: %.5f, %.5f",
                            uiState.telemetry.latitude,
                            uiState.telemetry.longitude
                        )
                    } else {
                        "จุดเริ่มต้น GPS: รอสัญญาณตำแหน่งจริง"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.telemetry.latitude != null) EmeraldSRank else AmberWarning
                )
                Text(
                    text = "ระยะโดยประมาณ (Haversine)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route Map (Tap to add stop, shows numbered badges and red route polyline)
            InteractiveZoneMapCanvas(
                activePin = null,
                allPins = emptyList(),
                userLatitude = uiState.telemetry.latitude,
                userLongitude = uiState.telemetry.longitude,
                radiusKm = uiState.zoneRadiusKm,
                gpsBufferMeters = uiState.gpsBufferMeters,
                zoneStatus = uiState.zoneStatus,
                distanceFromPinMeters = null,
                onMapTapPin = { lat, lng ->
                    onAddDeliveryStop(
                        "จุดส่ง #${uiState.deliveryStops.size + 1}",
                        lat,
                        lng
                    )
                },
                mapHeightDp = 240,
                deliveryStops = uiState.deliveryStops,
                isRouteMapMode = true,
                testTagName = "delivery_route_map_canvas"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Total Estimated Route Distance Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF0A090E))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ระยะทางรวมโดยประมาณ (เฉพาะจุดที่ยังไม่ส่ง)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f กม. (ระยะโดยประมาณ)", uiState.totalDeliveryRouteKm),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        color = CrimsonNeon,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("text_total_route_distance_km")
                    )
                }
                Text(
                    text = "ส่งแล้ว ${uiState.deliveryStops.count { it.isDelivered }}/${uiState.deliveryStops.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSRank,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Stop Options: เลือกจากหมุด / วางพิกัด
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChamferedActionButton(
                    label = "เลือกจากหมุด",
                    icon = Icons.Default.Bookmarks,
                    onClick = {
                        showPickFromPinPanel = !showPickFromPinPanel
                        showPasteStopPanel = false
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_open_pick_stop_from_pin"
                )
                ChamferedActionButton(
                    label = "วางพิกัดจุดส่ง",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        showPasteStopPanel = !showPasteStopPanel
                        showPickFromPinPanel = false
                        pasteStopError = null
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "btn_open_paste_stop_coords"
                )
            }

            if (showPickFromPinPanel) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF0A090E))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .padding(12.dp)
                        .testTag("panel_pick_stop_from_pin"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "เลือกหมุดเพื่อเพิ่มเป็นจุดส่ง",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.savedLocations.isEmpty() && uiState.savedPois.isEmpty()) {
                        Text(
                            text = "ยังไม่มีหมุดที่บันทึกไว้",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHudMuted
                        )
                    } else {
                        uiState.savedLocations.forEach { pin ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PrecisionButtonShape)
                                    .background(Color(0xFF141016))
                                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                                    .clickable {
                                        onAddDeliveryStop(pin.name, pin.latitude, pin.longitude)
                                        showPickFromPinPanel = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("btn_pick_saved_pin_${pin.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pin.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextHudPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "+ เพิ่มจุดส่ง",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrimsonNeon
                                )
                            }
                        }
                    }
                }
            }

            if (showPasteStopPanel) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF0A090E))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .padding(12.dp)
                        .testTag("panel_paste_stop_coords")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "เพิ่มจุดส่งด้วยพิกัด",
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
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val txt = cb?.primaryClip?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!txt.isNullOrBlank()) {
                                        pasteStopCoordsInput = txt
                                        pasteStopError = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("ดึงจากคลิปบอร์ด", style = MaterialTheme.typography.labelSmall, color = CrimsonNeon)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteStopNameInput,
                        onValueChange = { pasteStopNameInput = it },
                        label = { Text("ชื่อจุดส่ง") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonNeon,
                            unfocusedBorderColor = CrimsonBorderGlow,
                            focusedTextColor = TextHudPrimary,
                            unfocusedTextColor = TextHudPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_paste_stop_name")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteStopCoordsInput,
                        onValueChange = {
                            pasteStopCoordsInput = it
                            pasteStopError = null
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
                            .testTag("input_paste_stop_coords")
                    )
                    if (pasteStopError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(pasteStopError!!, style = MaterialTheme.typography.labelMedium, color = CrimsonNeon)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ChamferedActionButton(
                        label = "บันทึกจุดส่ง",
                        icon = Icons.Default.AddCircleOutline,
                        isPrimaryCrimson = true,
                        onClick = {
                            val ok = onAddDeliveryStopFromCoords(pasteStopCoordsInput, pasteStopNameInput)
                            if (ok) {
                                pasteStopCoordsInput = ""
                                pasteStopNameInput = ""
                                pasteStopError = null
                                showPasteStopPanel = false
                            } else {
                                pasteStopError = "พิกัดไม่ถูกต้อง หรือครบ 15 จุดแล้ว"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "btn_confirm_paste_stop"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route Optimization Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChamferedActionButton(
                    label = "เรียงเส้นทาง",
                    icon = Icons.Default.AutoFixHigh,
                    enabled = uiState.deliveryStops.any { !it.isDelivered },
                    isPrimaryCrimson = uiState.deliveryStops.any { !it.isDelivered },
                    onClick = onOptimizeDeliveryRoute,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_optimize_delivery_route"
                )
                ChamferedActionButton(
                    label = "เรียงใหม่จากปัจจุบัน",
                    icon = Icons.Default.MyLocation,
                    enabled = uiState.deliveryStops.any { !it.isDelivered },
                    onClick = onOptimizeDeliveryRoute,
                    modifier = Modifier.weight(1f),
                    testTag = "btn_reoptimize_from_current_gps"
                )
            }

            // Delivery Stops List
            if (uiState.deliveryStops.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.deliveryStops.forEachIndexed { idx, stop ->
                        DeliveryStopRowCard(
                            stop = stop,
                            index = idx,
                            onToggleDelivered = { onToggleDeliveryStopDelivered(stop.id) },
                            onEditStop = { newName, newLat, newLng ->
                                onEditDeliveryStop(stop.id, newName, newLat, newLng)
                            },
                            onDeleteStop = { onDeleteDeliveryStop(stop.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ChamferedActionButton(
                    label = "ล้างจุดส่งทั้งหมด",
                    icon = Icons.Default.DeleteOutline,
                    onClick = onClearAllDeliveryStops,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_clear_all_stops"
                )
            }
        }

        // =====================================================================
        // 2. คำนวณจุดคุ้มทุนรายวัน (รีเซ็ตทุกเที่ยงคืน • บันทึกประวัติรายวัน)
        // =====================================================================
        CrimsonPrecisionPanel(
            borderColor = if (breakEven.hasReachedBreakEven) EmeraldSRank else CrimsonBorderGlow
        ) {
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
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = if (breakEven.hasReachedBreakEven) EmeraldSRank else CrimsonNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "คำนวณจุดคุ้มทุนรายวัน",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = breakEven.currentDateKey,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextHudSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Break-even Summary
            val otherCostTotal = breakEven.customExpenses.sumOf { it.amountBaht }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF0A090E))
                    .border(
                        1.dp,
                        CrimsonBorderGlow,
                        PrecisionButtonShape
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("ยอดคุ้มทุนวันนี้", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                        Text(
                            text = String.format(Locale.US, "฿%.0f", breakEven.totalCostBaht),
                            style = MaterialTheme.typography.titleMedium,
                            color = CrimsonNeon,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("text_total_breakeven")
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ค่าน้ำมัน", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                        Text(
                            text = String.format(Locale.US, "฿%.0f", breakEven.fuelCostBaht),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("รวมค่าใช้จ่ายอื่นๆ", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                        Text(
                            text = String.format(Locale.US, "฿%.0f", otherCostTotal),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Daily Fuel Cost: ค่าน้ำมัน
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dailyFuelCostText,
                    onValueChange = { dailyFuelCostText = it },
                    label = { Text("ค่าน้ำมัน (บาท)") },
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
                        .testTag("input_daily_fuel_cost")
                )
                ChamferedActionButton(
                    label = "บันทึกค่าน้ำมัน",
                    icon = Icons.Default.Save,
                    onClick = {
                        val fuelAmt = dailyFuelCostText.toDoubleOrNull() ?: 0.0
                        onUpdateFixedDailyCosts(fuelAmt)
                    },
                    testTag = "btn_save_fixed_costs"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Expenses (ค่าอื่นๆ เพิ่มรายการเองได้)
            Text(
                text = "ค่าใช้จ่ายอื่นๆ",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customExpTitleInput,
                    onValueChange = { customExpTitleInput = it },
                    label = { Text("รายการ") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_custom_expense_title")
                )
                OutlinedTextField(
                    value = customExpAmountInput,
                    onValueChange = { customExpAmountInput = it },
                    label = { Text("บาท") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("input_custom_expense_amount")
                )
                ChamferedActionButton(
                    label = "เพิ่ม",
                    icon = Icons.Default.AddCircleOutline,
                    onClick = {
                        val amt = customExpAmountInput.toDoubleOrNull()
                        if (amt != null && amt > 0.0) {
                            onAddCustomExpense(customExpTitleInput, amt)
                            customExpTitleInput = ""
                            customExpAmountInput = ""
                        }
                    },
                    modifier = Modifier.height(54.dp),
                    testTag = "btn_add_custom_expense"
                )
            }

            if (breakEven.customExpenses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakEven.customExpenses.forEach { exp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PrecisionButtonShape)
                                .background(Color(0xFF0C0A0F))
                                .border(1.dp, Color(0xFF281C23), PrecisionButtonShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(exp.title, style = MaterialTheme.typography.bodyMedium, color = TextHudSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "฿%.0f", exp.amountBaht),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CrimsonNeon,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "ลบรายการ",
                                    tint = TextHudMuted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onDeleteCustomExpense(exp.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Daily Financial History
            if (breakEven.history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CrimsonNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ประวัติรายวันย้อนหลัง",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHudSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakEven.history.take(7).forEach { h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PrecisionButtonShape)
                                .background(Color(0xFF09080C))
                                .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = h.dateKey,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextHudSecondary
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "ยอดคุ้มทุน ฿%.0f",
                                    h.totalCostBaht
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. ประเมินน้ำมันที่เหลือ (คำนวณจากระยะ GPS จริง + เตือนสั่น/เสียง)
        // =====================================================================
        CrimsonPrecisionPanel(
            borderColor = if (fuel.isLowFuel) CrimsonNeon else CrimsonBorderGlow
        ) {
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
                        tint = if (fuel.isLowFuel) AmberWarning else CrimsonNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ประเมินน้ำมันที่เหลือ",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (fuel.isLowFuel) "น้ำมันใกล้หมด!" else "ระดับปกติ",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (fuel.isLowFuel) AmberWarning else EmeraldSRank,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF0A090E))
                    .border(
                        1.dp,
                        if (fuel.isLowFuel) AmberWarning else CrimsonBorderGlow,
                        PrecisionButtonShape
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("น้ำมันที่เหลือโดยประมาณ", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.2f / %.1f ลิตร",
                                fuel.remainingLiters,
                                fuel.tankCapacityLiters
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (fuel.isLowFuel) AmberWarning else TextHudPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("text_remaining_fuel_liters")
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ระยะทางที่วิ่งได้อีก", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                        Text(
                            text = String.format(Locale.US, "~%.1f กม.", fuel.estimatedRemainingRangeKm),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (fuel.isLowFuel) CrimsonNeon else EmeraldSRank,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("text_estimated_fuel_range_km")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = String.format(
                        Locale.US,
                        "ระยะทาง GPS สะสมตั้งแต่เติมล่าสุด: %.2f กม.",
                        fuel.distanceDrivenSinceRefuelKm
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(PrecisionButtonShape)
                        .background(Color(0xFF1A141B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fuel.remainingFraction)
                            .background(
                                if (fuel.isLowFuel) {
                                    Brush.horizontalGradient(listOf(CrimsonRed, AmberWarning))
                                } else {
                                    Brush.horizontalGradient(listOf(CrimsonRed, EmeraldSRank))
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ChamferedActionButton(
                label = "เติมน้ำมันแล้ว (รีเซ็ตเต็มถัง)",
                icon = Icons.Default.LocalGasStation,
                isPrimaryCrimson = true,
                onClick = onMarkRefueledFullTank,
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_refuel_full_tank"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tankCapText,
                    onValueChange = { tankCapText = it },
                    label = { Text("ถัง (ลิตร)") },
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
                        .testTag("input_fuel_tank_liters")
                )
                OutlinedTextField(
                    value = consumptionText,
                    onValueChange = { consumptionText = it },
                    label = { Text("กม./ลิตร") },
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
                        .testTag("input_fuel_consumption_km_l")
                )
                OutlinedTextField(
                    value = lowWarnText,
                    onValueChange = { lowWarnText = it },
                    label = { Text("เตือนเมื่อเหลือ (ลิตร)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("input_fuel_low_warning_liters")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ChamferedActionButton(
                label = "บันทึกการตั้งค่าน้ำมัน",
                icon = Icons.Default.Save,
                onClick = {
                    val cap = tankCapText.toFloatOrNull() ?: fuel.tankCapacityLiters
                    val cons = consumptionText.toFloatOrNull() ?: fuel.consumptionKmPerLiter
                    val warn = lowWarnText.toFloatOrNull() ?: fuel.lowFuelWarningLiters
                    onUpdateFuelSettings(cap, cons, warn)
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_save_fuel_settings"
            )
        }

        // Generous bottom clearance so all inputs & buttons scroll well past the bottom bar
        Spacer(modifier = Modifier.height(160.dp))
    }
}

@Composable
private fun DeliveryStopRowCard(
    stop: DeliveryStopItem,
    index: Int,
    onToggleDelivered: () -> Unit,
    onEditStop: (String, Double, Double) -> Boolean,
    onDeleteStop: () -> Unit
) {
    var showEditPanel by remember { mutableStateOf(false) }
    var editName by remember(stop.name) { mutableStateOf(stop.name) }
    var editCoords by remember(stop.latitude, stop.longitude) {
        mutableStateOf(String.format(Locale.US, "%.6f, %.6f", stop.latitude, stop.longitude))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PrecisionButtonShape)
            .background(if (stop.isDelivered) Color(0xFF0A100D) else Color(0xFF0D0A0F))
            .border(
                width = 1.dp,
                color = if (stop.isDelivered) EmeraldSRank.copy(alpha = 0.6f) else CrimsonBorderGlow,
                shape = PrecisionButtonShape
            )
            .padding(10.dp)
            .testTag("delivery_stop_card_${stop.id}")
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(PrecisionButtonShape)
                        .background(if (stop.isDelivered) EmeraldSRank else CrimsonRed)
                ) {
                    Text(
                        text = if (stop.isDelivered) "✓" else "${if (stop.sequenceOrder > 0) stop.sequenceOrder else index + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Column {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (stop.isDelivered) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (stop.isDelivered) TextHudMuted else TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(String.format(Locale.US, "%.5f, %.5f", stop.latitude, stop.longitude))
                            if (!stop.isDelivered && stop.legDistanceKm != null) {
                                append(String.format(Locale.US, " • ช่วงนี้ ~%.2f กม.", stop.legDistanceKm))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stop.isDelivered) TextHudMuted else CrimsonNeon
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(PrecisionButtonShape)
                        .background(if (stop.isDelivered) EmeraldSRank.copy(alpha = 0.2f) else Color(0xFF1A1116))
                        .border(
                            1.dp,
                            if (stop.isDelivered) EmeraldSRank else CrimsonNeon,
                            PrecisionButtonShape
                        )
                        .clickable { onToggleDelivered() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("btn_toggle_delivered_${stop.id}")
                ) {
                    Text(
                        text = if (stop.isDelivered) "ส่งแล้ว" else "กดส่งแล้ว",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stop.isDelivered) EmeraldSRank else TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "แก้ไขจุดส่ง",
                    tint = TextHudSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { showEditPanel = !showEditPanel }
                        .testTag("btn_edit_stop_${stop.id}")
                )

                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "ลบจุดส่ง",
                    tint = CrimsonNeon,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDeleteStop() }
                        .testTag("btn_delete_stop_${stop.id}")
                )
            }
        }

        if (showEditPanel) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("ชื่อจุดส่ง") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonNeon,
                    unfocusedBorderColor = CrimsonBorderGlow,
                    focusedTextColor = TextHudPrimary,
                    unfocusedTextColor = TextHudPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = editCoords,
                onValueChange = { editCoords = it },
                label = { Text("ละติจูด, ลองจิจูด") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonNeon,
                    unfocusedBorderColor = CrimsonBorderGlow,
                    focusedTextColor = TextHudPrimary,
                    unfocusedTextColor = TextHudPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChamferedActionButton(
                    label = "ยกเลิก",
                    icon = Icons.Default.Close,
                    onClick = { showEditPanel = false },
                    modifier = Modifier.weight(1f)
                )
                ChamferedActionButton(
                    label = "บันทึก",
                    icon = Icons.Default.CheckCircle,
                    isPrimaryCrimson = true,
                    onClick = {
                        val parsed = GeoMath.parseCoordinateInput(editCoords)
                        if (parsed != null) {
                            onEditStop(editName, parsed.first, parsed.second)
                            showEditPanel = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
