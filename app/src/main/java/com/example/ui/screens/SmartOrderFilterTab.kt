package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.IncomingOrderCard
import com.example.model.OrderGrade
import com.example.ui.components.CrimsonGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState
import kotlin.math.roundToInt

/**
 * Tab 2: SMART ORDER FILTER (ระบบสแกนและคัดกรองงาน "รับใกล้-ส่งใกล้หมุด")
 * Includes Signature Animation 3: Live Order Card Scanner Animation (Crimson Laser Scanline + Stamp).
 */
@Composable
fun SmartOrderFilterTab(
    uiState: FixPointUiState,
    onSimulateNewOrder: () -> Unit,
    onSelectPresetOrder: (IncomingOrderCard) -> Unit,
    onEvaluateCustomOrder: (String, String, Float, Float, Int, Boolean) -> Unit,
    onUpdateAutoFilterRules: (Boolean?, Boolean?, Boolean?, Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val eval = uiState.currentOrderEvaluation
    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crimson_laser_y"
    )

    var showCustomCalculator by remember { mutableStateOf(false) }
    var customMerchant by remember { mutableStateOf("สุกี้ตี๋น้อย สาขาหน้าหมุด") }
    var customDropoff by remember { mutableStateOf("หมู่บ้านพฤกษา ซอย 3") }
    var customPickupKm by remember { mutableFloatStateOf(0.35f) }
    var customDropoffKm by remember { mutableFloatStateOf(1.20f) }
    var customPayout by remember { mutableFloatStateOf(52f) }
    var customCrossBridge by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Quick Preset Switcher + Simulate Button
        CrimsonGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE ORDER CARD SCANNER (สแกนการ์ดงานเด้ง)",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonNeon
                    )
                    Text(
                        text = "หมุดอ้างอิง: ${uiState.activePin.name}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextHudSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBlack)
                        .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "S-Rank: ${uiState.sRankAcceptedCount} | บล็อก: ${uiState.outOfZoneRejectedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSRank
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preset Orders Row (S-Rank / A-Rank / Out-of-Zone Demo Cards)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.presetOrders.forEachIndexed { idx, preset ->
                    val label = when (idx) {
                        0 -> "ตัวอย่าง S-Rank (วนหมุด)"
                        1 -> "ตัวอย่าง A-Rank (ในโซน)"
                        else -> "ตัวอย่าง หลุดโซน (5.3 กม.)"
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBlack.copy(alpha = 0.8f))
                            .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(8.dp))
                            .clickable { onSelectPresetOrder(preset) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("preset_order_button_$idx")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextHudPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSimulateNewOrder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("simulate_incoming_order_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "จำลองงานเด้งใหม่",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = { showCustomCalculator = !showCustomCalculator },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarbonDark,
                        contentColor = CyanTelemetry
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, CyanTelemetry.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .testTag("toggle_custom_order_form_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditLocationAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showCustomCalculator) "ซ่อนฟอร์ม" else "กรอกระยะเอง",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Interactive Custom Order Distance & Payout Calculator Form
        if (showCustomCalculator) {
            CrimsonGlassCard(borderColor = CyanTelemetry.copy(alpha = 0.6f)) {
                Text(
                    text = "CUSTOM ORDER EVALUATOR (จำลองระยะจุดรับ-จุดส่งด้วยตัวเอง)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanTelemetry
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customMerchant,
                    onValueChange = { customMerchant = it },
                    label = { Text("ชื่อร้านค้า (Pickup Merchant)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_merchant_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = customDropoff,
                    onValueChange = { customDropoff = it },
                    label = { Text("จุดส่งลูกค้า (Drop-off Destination)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonNeon,
                        unfocusedBorderColor = CrimsonBorderGlow,
                        focusedTextColor = TextHudPrimary,
                        unfocusedTextColor = TextHudPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dropoff_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ระยะร้านห่างหมุดหลัก: ${GeoMath.formatKm(customPickupKm.toDouble())} กม. • จุดส่งห่างหมุด: ${GeoMath.formatKm(customDropoffKm.toDouble())} กม.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextHudPrimary
                )

                Slider(
                    value = customPickupKm,
                    onValueChange = { customPickupKm = it },
                    valueRange = 0.05f..2.8f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonNeon, activeTrackColor = CrimsonRed)
                )

                Slider(
                    value = customDropoffKm,
                    onValueChange = { customDropoffKm = it },
                    valueRange = 0.3f..7.5f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldSRank, activeTrackColor = EmeraldSRank)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ค่ารอบงานนี้: ${customPayout.roundToInt()} บาท",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarning
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("งานข้ามสะพาน/ข้ามเขต", style = MaterialTheme.typography.bodyMedium, color = TextHudSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = customCrossBridge,
                            onCheckedChange = { customCrossBridge = it }
                        )
                    }
                }

                Slider(
                    value = customPayout,
                    onValueChange = { customPayout = it },
                    valueRange = 32f..120f,
                    colors = SliderDefaults.colors(thumbColor = AmberWarning, activeTrackColor = AmberWarning)
                )

                Button(
                    onClick = {
                        onEvaluateCustomOrder(
                            customMerchant,
                            customDropoff,
                            customPickupKm,
                            customDropoffKm,
                            customPayout.roundToInt(),
                            customCrossBridge
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSRank, contentColor = ObsidianBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calculate_custom_order_button")
                ) {
                    Text(
                        text = "สแกนและคำนวณความคุ้มค่าทันที",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Signature Animation 3: Live Order Card with Crimson Laser Scanline & Verdict Stamp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1C1016),
                            ObsidianBlack
                        )
                    )
                )
                .border(2.dp, eval.grade.color, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Order Card Header: Platform Badge + Order ID + Payout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(eval.order.platform.accentColor.copy(alpha = 0.22f))
                                .border(1.dp, eval.order.platform.accentColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = eval.order.platform.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = eval.order.platform.accentColor
                            )
                        }
                        Text(
                            text = "#${eval.order.id}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "฿${eval.order.payoutBaht}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = EmeraldSRank,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "(~${eval.order.estMinutes} นาที)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextHudSecondary
                        )
                    }
                }

                // Pickup & Drop-off Route Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarbonDark.copy(alpha = 0.85f))
                        .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = CrimsonNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "จุดรับร้านค้า (PICKUP)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHudMuted
                            )
                            Text(
                                text = eval.order.merchantName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = eval.grade.color,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "จุดส่งลูกค้า (DROP-OFF)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHudMuted
                            )
                            Text(
                                text = eval.order.dropoffName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 4 Real-Time Haversine Evaluation Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrderEvalMetricBox(
                        title = "1. ระยะร้านห่างหมุด",
                        value = "${GeoMath.formatKm(eval.pickupToPinKm)} km",
                        status = if (eval.isPickupWithinRadius) {
                            "ผ่านเกณฑ์ (≤ ${GeoMath.format1Dec(uiState.maxPickupRadiusKm.toDouble())} km)"
                        } else {
                            "ไกลเกินรัศมีรับ!"
                        },
                        isPositive = eval.isPickupWithinRadius,
                        modifier = Modifier.weight(1f)
                    )

                    OrderEvalMetricBox(
                        title = "2. จุดส่งห่างหมุดเดิม",
                        value = "${GeoMath.formatKm(eval.dropoffToPinKm)} km",
                        status = if (eval.isDropoffWithinRadius) {
                            "ไม่หลุดโซน (≤ ${GeoMath.format1Dec(uiState.maxDropoffRadiusKm.toDouble())} km)"
                        } else {
                            "ฉีกออกนอกหมุด!"
                        },
                        isPositive = eval.isDropoffWithinRadius,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrderEvalMetricBox(
                        title = "3. ระยะวิ่งรวมตีรถกลับ",
                        value = "${GeoMath.formatKm(eval.totalLoopKm)} km",
                        status = "ส่ง ${GeoMath.formatKm(eval.orderTripKm)} + กลับ ${GeoMath.formatKm(eval.loopBackKm)} km",
                        isPositive = eval.totalLoopKm <= (uiState.maxDropoffRadiusKm * 2.3f),
                        modifier = Modifier.weight(1f)
                    )

                    OrderEvalMetricBox(
                        title = "4. ความคุ้มค่ารอบวิ่ง",
                        value = "${GeoMath.format1Dec(eval.bahtPerLoopKm)} ฿/km",
                        status = if (eval.bahtPerLoopKm >= uiState.autoFilterRules.minBahtPerLoopKm) {
                            "คุ้มรอบวิ่ง (≥ ${uiState.autoFilterRules.minBahtPerLoopKm.toInt()} ฿/km)"
                        } else {
                            "ต่ำกว่าเกณฑ์ขั้นต่ำ"
                        },
                        isPositive = eval.bahtPerLoopKm >= uiState.autoFilterRules.minBahtPerLoopKm,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. Instant Verdict Badge (ป้ายตัดสินใจด่วนขนาดใหญ่)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(eval.grade.color.copy(alpha = 0.18f))
                        .border(2.dp, eval.grade.color, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .testTag("instant_verdict_badge")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (eval.grade == OrderGrade.REJECT_OUT_OF_ZONE) {
                                    Icons.Default.WarningAmber
                                } else {
                                    Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = eval.grade.color,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = eval.grade.badgeTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                                color = eval.grade.color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = eval.verdictDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHudPrimary
                        )
                    }
                }
            }

            // Crimson Laser Scanline Overlay across the card
            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {
                val scanY = size.height * laserYFraction
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CrimsonNeon.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        startY = (scanY - 26.dp.toPx()).coerceAtLeast(0f),
                        endY = (scanY + 26.dp.toPx()).coerceAtMost(size.height)
                    )
                )
                drawLine(
                    color = CrimsonNeon.copy(alpha = 0.75f),
                    start = Offset(0f, scanY),
                    end = Offset(size.width, scanY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Auto-Filter Rules (ตั้งค่าเงื่อนไขคัดงานอัตโนมัติ)
        CrimsonGlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = CrimsonNeon
                )
                Text(
                    text = "AUTO-FILTER RULES (ตั้งค่าเงื่อนไขคัดงานรับใกล้-ส่งใกล้หมุด)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonNeon
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FilterRuleSwitchRow(
                title = "แจ้งเตือนเฉพาะงานที่จุดส่งห่างหมุดไม่เกินรัศมี (${GeoMath.format1Dec(uiState.maxDropoffRadiusKm.toDouble())} กม.)",
                subtitle = "คัดออกทันทีหากจุดส่งลูกค้าฉีกออกนอกวงแหวนรอบหมุดหลัก",
                checked = uiState.autoFilterRules.notifyOnlyWithinDropoffRadius,
                onCheckedChange = { onUpdateAutoFilterRules(it, null, null, null) },
                testTag = "rule_switch_dropoff_guard"
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterRuleSwitchRow(
                title = "บล็อกแจ้งเตือนงานข้ามเขต / ข้ามสะพานใหญ่",
                subtitle = "ป้องกันงานข้ามแม่น้ำเจ้าพระยาหรือข้ามเขตที่ทำให้ตีรถกลับหมุดยาก",
                checked = uiState.autoFilterRules.blockCrossBridgeOrZone,
                onCheckedChange = { onUpdateAutoFilterRules(null, it, null, null) },
                testTag = "rule_switch_block_bridge"
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterRuleSwitchRow(
                title = "เน้นงานสั้นทำรอบ (Short-Loop Priority)",
                subtitle = "ให้เกรด S-Rank เฉพาะงานที่ระยะวิ่งรวมตีรถกลับหมุดสั้นกระชับ",
                checked = uiState.autoFilterRules.shortLoopPriority,
                onCheckedChange = { onUpdateAutoFilterRules(null, null, it, null) },
                testTag = "rule_switch_short_loop"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "เกณฑ์ความคุ้มค่าขั้นต่ำ (รวมขากลับหมุด)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHudSecondary
                )
                Text(
                    text = "≥ ${uiState.autoFilterRules.minBahtPerLoopKm.toInt()} บาท/กม.",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSRank
                )
            }

            Slider(
                value = uiState.autoFilterRules.minBahtPerLoopKm,
                onValueChange = { onUpdateAutoFilterRules(null, null, null, it) },
                valueRange = 8f..25f,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonNeon,
                    activeTrackColor = CrimsonRed,
                    inactiveTrackColor = ObsidianBlack
                ),
                modifier = Modifier.testTag("min_baht_per_km_slider")
            )
        }
    }
}

@Composable
private fun OrderEvalMetricBox(
    title: String,
    value: String,
    status: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBlack.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (isPositive) EmeraldSRank.copy(alpha = 0.45f) else AmberWarning.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextHudMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
            color = if (isPositive) EmeraldSRank else AmberWarning,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextHudSecondary
        )
    }
}

@Composable
private fun FilterRuleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBlack.copy(alpha = 0.72f))
            .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                color = TextHudMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CrimsonRed,
                uncheckedThumbColor = TextHudMuted,
                uncheckedTrackColor = CarbonDark
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
