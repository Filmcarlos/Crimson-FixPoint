package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.RiderIntelligenceEngine
import com.example.model.AnalyzedOrderRecord
import com.example.model.AppScreen
import com.example.model.OrderGrade
import com.example.model.ScreenReaderState
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.SmartScoreRingCanvas
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
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
fun AiAssistantScreen(
    uiState: FixPointUiState,
    onRequestStartScreenReader: () -> Unit,
    onAcceptScreenReaderConsentAndStart: () -> Unit,
    onDismissScreenReaderConsent: () -> Unit,
    onTriggerCaptureNow: () -> Unit,
    onStopScreenReader: () -> Unit,
    onImportScreenshot: () -> Unit,
    onParseManualText: (String) -> Unit,
    onUpdateDraftFields: (
        payoutText: String?,
        distanceText: String?,
        deadMileageText: String?,
        minutesText: String?,
        pickupName: String?,
        dropoffName: String?
    ) -> Unit,
    onResetDraft: () -> Unit,
    onSaveAnalyzedOrder: (alsoLogToEarnings: Boolean) -> Unit,
    onDeleteAnalyzedOrder: (String) -> Unit,
    onNavigateToMapHotspot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = uiState.currentOrderDraft
    val analysis = uiState.currentOrderAnalysis

    var rawOrderText by remember { mutableStateOf("") }
    var payoutInput by remember { mutableStateOf(draft.payoutBaht?.toString() ?: "") }
    var distanceInput by remember { mutableStateOf(draft.distanceKm?.toString() ?: "") }
    var deadMileageInput by remember { mutableStateOf(draft.deadMileageKm?.toString() ?: "") }
    var minutesInput by remember { mutableStateOf(draft.estimatedMinutes?.toString() ?: "") }
    var pickupInput by remember { mutableStateOf(draft.pickupName ?: "") }
    var dropoffInput by remember { mutableStateOf(draft.dropoffName ?: "") }

    // Synchronize form inputs when a new OCR scan or reset happens
    LaunchedEffect(draft.source, draft.rawOcrText, draft.wasUserCorrected) {
        if (!draft.wasUserCorrected) {
            payoutInput = draft.payoutBaht?.let { formatCleanNum(it) } ?: ""
            distanceInput = draft.distanceKm?.let { formatCleanNum(it) } ?: ""
            deadMileageInput = draft.deadMileageKm?.let { formatCleanNum(it) } ?: ""
            minutesInput = draft.estimatedMinutes?.toString() ?: ""
            pickupInput = draft.pickupName ?: ""
            dropoffInput = draft.dropoffName ?: ""
        }
    }

    val fuelIntel = remember(uiState.fuelState, uiState.riderProfile) {
        RiderIntelligenceEngine.computeFuelIntelligence(uiState.fuelState, uiState.riderProfile)
    }

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

    // Explicit Screen Reader Consent Modal (MediaProjection Safety Policy)
    if (uiState.showScreenReaderConsentDialog) {
        AlertDialog(
            onDismissRequest = onDismissScreenReaderConsent,
            containerColor = SurfaceLevel1,
            titleContentColor = TextHudPrimary,
            textContentColor = TextHudSecondary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = CrimsonNeon)
                    Text("ยืนยันการเปิด Screen Reader", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Crimson FixPoint ใช้ Android MediaProjection เพื่ออ่านข้อมูลออเดอร์เฉพาะเวลาที่คุณกด 'จับภาพตอนนี้' เท่านั้น",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• แสดงแถบแจ้งเตือนสถานะตลอดเวลาที่เปิดใช้งาน\n" +
                            "• ประมวลผล OCR บนเครื่อง 100% ไม่ส่งภาพขึ้นเซิร์ฟเวอร์\n" +
                            "• ไม่มีการกดรับงาน ปฏิเสธงาน หรือควบคุมแอปอื่นอัตโนมัติ\n" +
                            "• คุณสามารถตรวจสอบและแก้ไขตัวเลขเองได้ทุกครั้งก่อนบันทึก",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHudSecondary
                    )
                }
            },
            confirmButton = {
                PremiumPrimaryButton(
                    text = "ยินยอมและขอสิทธิ์",
                    onClick = onAcceptScreenReaderConsentAndStart,
                    testTagName = "btn_confirm_screen_reader_consent"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissScreenReaderConsent,
                    modifier = Modifier.testTag("btn_cancel_screen_reader_consent")
                ) {
                    Text("ยกเลิก", color = TextHudMuted)
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("ai_assistant_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. User-Controlled Screen Reader & Screenshot Import Card
            item {
                val srState = uiState.screenReaderState
                val isSrActive = srState == ScreenReaderState.ACTIVE_READY || srState == ScreenReaderState.CAPTURING
                val srAccent = when (srState) {
                    ScreenReaderState.ACTIVE_READY -> EmeraldSRank
                    ScreenReaderState.CAPTURING -> CyanTelemetry
                    ScreenReaderState.PROTECTED_SCREEN, ScreenReaderState.PERMISSION_DENIED, ScreenReaderState.ERROR -> AmberWarning
                    ScreenReaderState.IDLE -> TextHudMuted
                }

                CrimsonHeroCard(modifier = Modifier.testTag("screen_reader_control_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Order Reader",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "อ่านและประเมินความคุ้มค่าออเดอร์แบบออฟไลน์ด้วย ML Kit OCR",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(
                            text = if (isSrActive) "ACTIVE" else "LOCAL OCR",
                            accentColor = if (isSrActive) EmeraldSRank else CrimsonNeon,
                            icon = Icons.AutoMirrored.Filled.ScreenShare
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Explicit status indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x6607070A))
                            .border(1.dp, srAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("screen_reader_status_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = srAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = srState.labelTh,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextHudPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isSrActive) {
                            PremiumPrimaryButton(
                                text = "เปิด Screen Reader",
                                icon = Icons.Default.PlayArrow,
                                onClick = onRequestStartScreenReader,
                                modifier = Modifier.weight(1f),
                                testTagName = "btn_start_screen_reader"
                            )
                        } else {
                            PremiumPrimaryButton(
                                text = "จับภาพตอนนี้",
                                icon = Icons.Default.CameraAlt,
                                containerColor = EmeraldSRank,
                                onClick = onTriggerCaptureNow,
                                modifier = Modifier.weight(1f),
                                testTagName = "btn_capture_screen_now"
                            )
                            PremiumSecondaryButton(
                                text = "หยุดจับภาพ",
                                icon = Icons.Default.Stop,
                                accentColor = CrimsonNeon,
                                onClick = onStopScreenReader,
                                modifier = Modifier.weight(0.85f),
                                testTagName = "btn_stop_screen_reader"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PremiumSecondaryButton(
                        text = "นำเข้าภาพแคปหน้าจอ (Photo Picker)",
                        icon = Icons.Default.PhotoLibrary,
                        accentColor = CyanTelemetry,
                        onClick = onImportScreenshot,
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "btn_import_screenshot_ocr"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ปลอดภัย 100%: ควบคุมโดยผู้ใช้เอง • ไม่มีการกดรับ/ปฏิเสธงานอัตโนมัติ • รองรับแชร์ภาพตรงจากแอปอื่น",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudMuted
                    )
                }
            }

            // Loading / OCR Error / Protected Screen feedback
            if (uiState.isRunningOcr) {
                item {
                    PremiumCard(accentBorder = CyanTelemetry.copy(alpha = 0.6f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = CrimsonNeon,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "กำลังประมวลผลข้อความ OCR ในเครื่อง...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHudPrimary
                            )
                        }
                    }
                }
            }

            if (uiState.ocrErrorMessageTh != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_error_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = AmberWarning.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = AmberWarning
                            )
                            Text(
                                text = uiState.ocrErrorMessageTh,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudPrimary
                            )
                        }
                    }
                }
            }

            // 2. Smart Order Editor & Manual Text Parser (Allows editing OCR results before saving)
            item {
                PremiumCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ตรวจสอบ / แก้ไขข้อมูลออเดอร์",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ระบบจะไม่เดาตัวเลขที่อ่านไม่พบ — กรอกหรือแก้ไขได้ทันที",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudMuted
                            )
                        }
                        IconButton(
                            onClick = {
                                rawOrderText = ""
                                payoutInput = ""
                                distanceInput = ""
                                deadMileageInput = ""
                                minutesInput = ""
                                pickupInput = ""
                                dropoffInput = ""
                                onResetDraft()
                            },
                            modifier = Modifier.testTag("btn_reset_order_draft")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "ล้างฟอร์ม", tint = TextHudMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick paste / parse raw text
                    OutlinedTextField(
                        value = rawOrderText,
                        onValueChange = { rawOrderText = it },
                        label = { Text("วางข้อความออเดอร์ (เช่น ฿65 4.2 กม. 18 นาที ไปรับ 0.8 กม.)") },
                        colors = crimsonFieldColors(),
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_paste_order_text")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PremiumSecondaryButton(
                        text = "แยกข้อมูลจากข้อความ (Offline Parser)",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = CrimsonNeon,
                        enabled = rawOrderText.isNotBlank(),
                        onClick = { onParseManualText(rawOrderText) },
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "btn_parse_order_text"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Editable Numeric Fields (Payout, Trip Distance, Dead Mileage, Est. Minutes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = payoutInput,
                            onValueChange = { v ->
                                payoutInput = v
                                onUpdateDraftFields(v, null, null, null, null, null)
                            },
                            label = { Text("ค่าส่ง (฿) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_payout")
                        )
                        OutlinedTextField(
                            value = distanceInput,
                            onValueChange = { v ->
                                distanceInput = v
                                onUpdateDraftFields(null, v, null, null, null, null)
                            },
                            label = { Text("ระยะทาง (กม.) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_distance")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = deadMileageInput,
                            onValueChange = { v ->
                                deadMileageInput = v
                                onUpdateDraftFields(null, null, v, null, null, null)
                            },
                            label = { Text("วิ่งไปรับ/เปล่า (กม.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_dead_mileage")
                        )
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { v ->
                                minutesInput = v
                                onUpdateDraftFields(null, null, null, v, null, null)
                            },
                            label = { Text("เวลา (นาที)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_minutes")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pickupInput,
                            onValueChange = { v ->
                                pickupInput = v
                                onUpdateDraftFields(null, null, null, null, v, null)
                            },
                            label = { Text("จุดรับ / ร้านค้า") },
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_pickup")
                        )
                        OutlinedTextField(
                            value = dropoffInput,
                            onValueChange = { v ->
                                dropoffInput = v
                                onUpdateDraftFields(null, null, null, null, null, v)
                            },
                            label = { Text("ปลายทางส่ง") },
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f).testTag("input_order_dropoff")
                        )
                    }
                }
            }

            // 3. Order Profitability & Smart Order Score Analysis Card
            if (analysis != null) {
                item {
                    val gradeColor = gradeAccentColor(analysis.grade)
                    PremiumCard(
                        accentBorder = gradeColor.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("order_analysis_result_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SmartScoreRingCanvas(
                                score = analysis.smartScore,
                                grade = analysis.grade
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusPillBadge(
                                    text = "ความเชื่อมั่น ${(analysis.confidenceScore * 100).toInt()}%",
                                    accentColor = if (analysis.confidenceScore >= 0.8f) EmeraldSRank else AmberWarning
                                )
                                Text(
                                    text = analysis.grade.verdictTh,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = gradeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "กำไรสุทธิ %s฿%.1f • ระยะรวม %.1f กม.",
                                        if (analysis.netProfitBaht >= 0) "+" else "-",
                                        kotlin.math.abs(analysis.netProfitBaht),
                                        analysis.totalDistanceKm
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextHudPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Breakdown KPI strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceLevel2)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MiniKpiCol(
                                label = "฿/กม.",
                                value = String.format(Locale.US, "฿%.1f", analysis.earningsPerKm),
                                color = CyanTelemetry
                            )
                            MiniKpiCol(
                                label = "฿/ชม.",
                                value = analysis.earningsPerHour?.let { String.format(Locale.US, "฿%.0f", it) } ?: "ไม่ระบุเวลา",
                                color = EmeraldSRank
                            )
                            MiniKpiCol(
                                label = "ค่าน้ำมัน",
                                value = String.format(Locale.US, "฿%.1f", analysis.fuelCostBaht),
                                color = AmberWarning
                            )
                            MiniKpiCol(
                                label = "วิ่งเปล่า",
                                value = String.format(Locale.US, "%.0f%%", analysis.deadMileageRatio * 100f),
                                color = if (analysis.deadMileageRatio > 0.3f) CrimsonNeon else TextHudSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explanations
                        analysis.explanations.forEach { exp ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSRank,
                                    modifier = Modifier.size(15.dp).padding(top = 2.dp)
                                )
                                Text(text = exp, style = MaterialTheme.typography.bodySmall, color = TextHudSecondary)
                            }
                        }

                        // Warnings
                        if (analysis.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            analysis.warnings.forEach { warn ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = AmberWarning,
                                        modifier = Modifier.size(15.dp).padding(top = 2.dp)
                                    )
                                    Text(text = warn, style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PremiumPrimaryButton(
                                text = "บันทึก + ลงบัญชีรายได้",
                                onClick = { onSaveAnalyzedOrder(true) },
                                modifier = Modifier.weight(1.25f),
                                testTagName = "btn_save_order_and_earnings"
                            )
                            PremiumSecondaryButton(
                                text = "บันทึกเฉพาะผล",
                                onClick = { onSaveAnalyzedOrder(false) },
                                modifier = Modifier.weight(0.85f),
                                testTagName = "btn_save_order_only"
                            )
                        }
                    }
                }
            }

            // 4. Fuel Intelligence Card
            item {
                PremiumCard(accentBorder = if (fuelIntel.isLowFuel) CrimsonNeon else HairlineBorder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = if (fuelIntel.isLowFuel) CrimsonNeon else AmberWarning
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fuel Intelligence (วิเคราะห์น้ำมัน)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = fuelIntel.adviceTh,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                    }
                }
            }

            // 5. Next Order & Historical Hotspot Recommendations (Explicitly Historical Estimate)
            item {
                SectionHeaderRow(
                    title = "โซนแนะนำถัดไป (Hotspots)",
                    subtitle = "ประเมินจากประวัติออเดอร์และหมุดในเครื่อง (ไม่ใช่ข้อมูลสดจากเซิร์ฟเวอร์)"
                )
            }
            if (hotspots.isEmpty()) {
                item {
                    PremiumCard {
                        Text(
                            text = "ยังไม่มีหมุด FixPoint หรือ Hotzone — เพิ่มหมุดบนแผนที่เพื่อรับคำแนะนำจุดจอดรอที่ใกล้และคุ้มค่าที่สุด",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextHudMuted
                        )
                    }
                }
            } else {
                items(hotspots.take(4).size, key = { hotspots[it].id }) { idx ->
                    val spot = hotspots[idx]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToMapHotspot(spot.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceLevel2,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = VioletInsight
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = spot.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextHudPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    StatusPillBadge(text = "ประวัติในเครื่อง", accentColor = VioletInsight)
                                }
                                Text(
                                    text = spot.reasonTh,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextHudSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 6. Recent Analyzed Orders History
            if (uiState.analyzedOrders.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "ประวัติการวิเคราะห์ออเดอร์ (${uiState.analyzedOrders.size})",
                        subtitle = "จัดเก็บในฐานข้อมูล Room ออฟไลน์"
                    )
                }
                items(uiState.analyzedOrders.take(12).size, key = { uiState.analyzedOrders[it].id }) { idx ->
                    AnalyzedOrderHistoryRow(
                        order = uiState.analyzedOrders[idx],
                        onDelete = { onDeleteAnalyzedOrder(uiState.analyzedOrders[idx].id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzedOrderHistoryRow(
    order: AnalyzedOrderRecord,
    onDelete: () -> Unit
) {
    val gradeColor = gradeAccentColor(order.grade)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceLevel1,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradeColor.copy(alpha = 0.16f))
                    .border(1.dp, gradeColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = order.grade.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = gradeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${order.smartScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudSecondary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${order.pickupName} → ${order.dropoffName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "฿%.0f • %.1f กม. • กำไรสุทธิ ฿%.0f • %s",
                        order.payoutBaht,
                        order.distanceKm + order.deadMileageKm,
                        order.netProfitBaht,
                        order.formattedTime()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "ลบประวัติออเดอร์",
                    tint = TextHudMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniKpiCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun gradeAccentColor(grade: OrderGrade): Color = when (grade) {
    OrderGrade.S -> EmeraldSRank
    OrderGrade.A -> CyanTelemetry
    OrderGrade.B -> AmberWarning
    OrderGrade.C -> Color(0xFFFB923C)
    OrderGrade.D -> CrimsonNeon
}

private fun formatCleanNum(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

@Composable
fun crimsonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CrimsonNeon,
    unfocusedBorderColor = HairlineBorder,
    focusedLabelColor = CrimsonNeon,
    unfocusedLabelColor = TextHudMuted,
    focusedTextColor = TextHudPrimary,
    unfocusedTextColor = TextHudPrimary,
    cursorColor = CrimsonNeon,
    focusedContainerColor = SurfaceLevel2,
    unfocusedContainerColor = SurfaceLevel2
)
