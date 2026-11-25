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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.model.DeliveryPlatform
import com.example.model.DetectedJobOffer
import com.example.model.JobOfferStatus
import com.example.model.JobRankingMode
import com.example.model.OrderGrade
import com.example.model.PlatformConnectionStatus
import com.example.ui.components.CrimsonEmptyStateCard
import com.example.ui.components.CrimsonErrorBanner
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.CrimsonLoadingState
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonSubtleBg
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.GrabGreen
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LineManGreen
import com.example.ui.theme.ShopeeOrange
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun JobRadarScreen(
    uiState: FixPointUiState,
    onSelectRankingMode: (JobRankingMode) -> Unit,
    onTogglePlatformAdapter: (DeliveryPlatform, Boolean) -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onImportScreenshotToRadar: () -> Unit,
    onIngestManualJobText: (rawText: String, title: String) -> Boolean,
    onLoadOfferIntoAi: (DetectedJobOffer) -> Unit,
    onAcceptAndLogJob: (String) -> Unit,
    onDismissJob: (String) -> Unit,
    onClearExpiredJobs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPasteDialog by remember { mutableStateOf(false) }
    val activeOffers = uiState.rankedActiveJobOffers
    val pastOffers = remember(uiState.detectedJobOffers) {
        uiState.detectedJobOffers.filter { it.status != JobOfferStatus.ACTIVE }.take(8)
    }
    val hasExpiredOffers = remember(uiState.detectedJobOffers) {
        uiState.detectedJobOffers.any { it.status == JobOfferStatus.EXPIRED }
    }

    if (showPasteDialog) {
        IngestJobOfferDialog(
            onDismiss = { showPasteDialog = false },
            onSubmit = { rawText, title ->
                val ok = onIngestManualJobText(rawText, title)
                if (ok) showPasteDialog = false
                ok
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
                .testTag("job_radar_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Multi-Platform Job Radar Hero Banner
            item {
                CrimsonHeroCard(modifier = Modifier.testTag("job_radar_hero_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multi-Platform Job Radar",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "รวมงานจากแจ้งเตือน OS และ OCR ของคุณ • ไม่มีระบบสุ่มงานปลอม",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(
                            text = "${activeOffers.size} งานเปิดรับ",
                            accentColor = if (activeOffers.isNotEmpty()) EmeraldSRank else CrimsonNeon,
                            icon = Icons.Default.Sensors
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ranking Mode Toggle (Nearest First vs Smart Best Job)
                    Text(
                        text = "โหมดจัดลำดับงาน (Ranking Mode)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextHudSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JobRankingMode.entries.forEach { mode ->
                            val selected = uiState.jobRankingMode == mode
                            Surface(
                                onClick = { onSelectRankingMode(mode) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) CrimsonRed else SurfaceLevel2,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) CrimsonNeon else HairlineBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("radar_mode_${mode.name.lowercase(Locale.ROOT)}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = mode.labelTh,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) Color.White else TextHudPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (mode == JobRankingMode.NEAREST_FIRST) "เน้นระยะใกล้จุดคุณ" else "เน้น Smart Score & ฿/กม.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White.copy(alpha = 0.85f) else TextHudMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PremiumPrimaryButton(
                            text = "สแกนภาพออเดอร์",
                            icon = Icons.Default.PhotoLibrary,
                            onClick = onImportScreenshotToRadar,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_job_radar_import_ocr"
                        )
                        PremiumSecondaryButton(
                            text = "วางข้อความงาน",
                            icon = Icons.Default.AddCircleOutline,
                            accentColor = CyanTelemetry,
                            onClick = { showPasteDialog = true },
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_job_radar_paste_input"
                        )
                    }
                }
            }

            // 2. NotificationListener Permission Prompt (Honest OS Consent)
            if (!uiState.notificationAccessGranted) {
                item {
                    CrimsonErrorBanner(
                        message = "ยังไม่ได้เปิดสิทธิ์อ่านแจ้งเตือน OS (Notification Access) — เปิดสิทธิ์เพื่อให้เรดาร์รับสัญญาณจาก LINE MAN, Grab และ ShopeeFood อัตโนมัติเมื่อมีแจ้งเตือนเข้า",
                        actionLabel = "ตั้งค่าสิทธิ์",
                        onAction = onOpenNotificationListenerSettings,
                        accentColor = AmberWarning,
                        testTagName = "btn_open_notification_listener_settings"
                    )
                }
            }

            if (uiState.isRunningOcr) {
                item {
                    CrimsonLoadingState(message = "กำลังสกัดข้อมูลงานจากภาพเข้าสู่ Job Radar...")
                }
            }

            // 3. Platform Status Monitoring Strip (Real states only)
            item {
                SectionHeaderRow(
                    title = "สถานะการเชื่อมต่อแพลตฟอร์ม",
                    subtitle = "ทุกค่ายปิด Public API — รับงานผ่านแจ้งเตือน OS และ OCR บนเครื่องเท่านั้น"
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uiState.platformConnectionStatuses.forEach { status ->
                        PlatformStatusMonitorCard(
                            status = status,
                            onToggle = { enabled -> onTogglePlatformAdapter(status.platform, enabled) }
                        )
                    }
                }
            }

            // 4. Active Ranked Jobs Section
            item {
                SectionHeaderRow(
                    title = "งานที่ตรวจพบ (${activeOffers.size})",
                    subtitle = uiState.jobRankingMode.subtitleTh,
                    trailing = if (hasExpiredOffers) {
                        {
                            FilterChip(
                                selected = false,
                                onClick = onClearExpiredJobs,
                                label = { Text("ล้างงานหมดเวลา", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("btn_radar_clear_expired")
                            )
                        }
                    } else null
                )
            }

            if (activeOffers.isEmpty()) {
                item {
                    CrimsonEmptyStateCard(
                        title = "ยังไม่มีออเดอร์เปิดรับในเรดาร์",
                        subtitle = if (uiState.notificationAccessGranted) {
                            "ระบบกำลังรอรับแจ้งเตือนงานจริงจาก LINE MAN / Grab / ShopeeFood หรือคุณสามารถกด 'สแกนภาพออเดอร์' / 'วางข้อความงาน' เพื่อประเมินทันที"
                        } else {
                            "เปิดสิทธิ์ Notification Access หรือนำเข้าภาพแคปหน้าจอออเดอร์เพื่อเริ่มจัดอันดับงานตามความคุ้มค่าและระยะทางจริง"
                        },
                        icon = Icons.Default.Sensors,
                        actionLabel = if (uiState.notificationAccessGranted) "นำเข้าข้อความออเดอร์" else "เปิดหน้าสิทธิ์อ่านแจ้งเตือน",
                        onAction = {
                            if (uiState.notificationAccessGranted) {
                                showPasteDialog = true
                            } else {
                                onOpenNotificationListenerSettings()
                            }
                        },
                        accentColor = CrimsonNeon,
                        testTagName = "job_radar_empty_state"
                    )
                }
            } else {
                items(activeOffers.size, key = { activeOffers[it].id }) { idx ->
                    val offer = activeOffers[idx]
                    RadarJobOfferCard(
                        rank = idx + 1,
                        offer = offer,
                        nowMillis = uiState.radarClockTickMillis,
                        onAnalyzeWithAi = { onLoadOfferIntoAi(offer) },
                        onAcceptAndLog = { onAcceptAndLogJob(offer.id) },
                        onDismiss = { onDismissJob(offer.id) }
                    )
                }
            }

            // 5. Recent Non-Active Radar History (Expired / Accepted / Dismissed)
            if (pastOffers.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "ประวัติเรดาร์ล่าสุด (${pastOffers.size})",
                        subtitle = "รายการที่รับแล้ว ข้าม หรือหมดเวลา"
                    )
                }
                items(pastOffers.size, key = { "past_${pastOffers[it].id}" }) { idx ->
                    val offer = pastOffers[idx]
                    PastRadarOfferRow(offer = offer)
                }
            }
        }
    }
}

@Composable
private fun PlatformStatusMonitorCard(
    status: PlatformConnectionStatus,
    onToggle: (Boolean) -> Unit
) {
    val brandColor = platformColor(status.platform)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceLevel1,
        border = BorderStroke(
            1.dp,
            if (status.isAdapterEnabled) brandColor.copy(alpha = 0.55f) else HairlineBorder
        ),
        modifier = Modifier
            .widthIn(min = 216.dp, max = 240.dp)
            .testTag("platform_status_${status.platform.name.lowercase(Locale.ROOT)}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (status.isAdapterEnabled) brandColor else TextHudMuted)
                    )
                    Text(
                        text = status.platform.displayNameTh,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = status.isAdapterEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = brandColor,
                        uncheckedThumbColor = TextHudMuted,
                        uncheckedTrackColor = SurfaceLevel2
                    )
                )
            }
            StatusPillBadge(
                text = status.connectionStateLabelTh,
                accentColor = when {
                    !status.isAdapterEnabled -> TextHudMuted
                    status.notificationListenerGranted -> EmeraldSRank
                    else -> AmberWarning
                },
                icon = if (status.notificationListenerGranted) Icons.Default.NotificationsActive else Icons.Default.Shield
            )
            Text(
                text = "Public API: ปิด (ใช้แจ้งเตือน OS/OCR) • ตรวจพบ ${status.detectedTodayCount} งาน",
                style = MaterialTheme.typography.labelSmall,
                color = TextHudMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RadarJobOfferCard(
    rank: Int,
    offer: DetectedJobOffer,
    nowMillis: Long,
    onAnalyzeWithAi: () -> Unit,
    onAcceptAndLog: () -> Unit,
    onDismiss: () -> Unit
) {
    val platformColor = platformColor(offer.platform)
    val gradeColor = when (offer.grade) {
        OrderGrade.S -> EmeraldSRank
        OrderGrade.A -> CyanTelemetry
        OrderGrade.B -> AmberWarning
        OrderGrade.C -> Color(0xFFFB923C)
        OrderGrade.D -> CrimsonNeon
    }
    val remainingSec = offer.remainingSeconds(nowMillis)
    val distKm = offer.effectiveDistanceToPickupKm()

    PremiumCard(
        accentBorder = if (rank == 1) CrimsonNeon.copy(alpha = 0.7f) else platformColor.copy(alpha = 0.45f),
        modifier = Modifier.testTag("job_radar_offer_card_${offer.id}")
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
                StatusPillBadge(
                    text = "#$rank ${offer.platform.displayNameTh}",
                    accentColor = platformColor
                )
                StatusPillBadge(
                    text = "เกรด ${offer.grade.label} (${offer.smartScore})",
                    accentColor = gradeColor
                )
                if (offer.isDuplicateMerged) {
                    StatusPillBadge(text = "รวมสัญญาณซ้ำ", accentColor = VioletInsight)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (remainingSec <= 20) CrimsonNeon else AmberWarning,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${remainingSec} วิ",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (remainingSec <= 20) CrimsonNeon else AmberWarning,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${offer.pickupName} → ${offer.dropoffName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(offer.source.labelTh)
                        if (distKm != null) {
                            append(String.format(Locale.US, " • ไปรับ %.1f กม.", distKm))
                        }
                        if (offer.tripDistanceKm > 0.0) {
                            append(String.format(Locale.US, " • ส่ง %.1f กม.", offer.tripDistanceKm))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHudSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "฿%.0f", offer.payoutBaht),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = EmeraldSRank
                )
                if (offer.earningsPerKm > 0.0) {
                    Text(
                        text = String.format(Locale.US, "฿%.1f/กม.", offer.earningsPerKm),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = CyanTelemetry
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLevel2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "กำไรสุทธิ ~฿%.0f", offer.netProfitBaht),
                style = MaterialTheme.typography.labelSmall,
                color = TextHudPrimary
            )
            Text(
                text = offer.estimatedMinutes?.let { "เวลา ~$it นาที" } ?: "ไม่ระบุเวลา",
                style = MaterialTheme.typography.labelSmall,
                color = TextHudSecondary
            )
            Text(
                text = offer.formattedCreatedTime(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = TextHudMuted
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PremiumPrimaryButton(
                text = "รับ & ลงบัญชี",
                icon = Icons.Default.CheckCircle,
                containerColor = EmeraldSRank,
                onClick = onAcceptAndLog,
                modifier = Modifier.weight(1.1f),
                testTagName = "btn_radar_accept_${offer.id}"
            )
            PremiumSecondaryButton(
                text = "AI วิเคราะห์",
                icon = Icons.Default.AutoAwesome,
                accentColor = CyanTelemetry,
                onClick = onAnalyzeWithAi,
                modifier = Modifier.weight(0.9f),
                testTagName = "btn_radar_ai_${offer.id}"
            )
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                color = SurfaceLevel2,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_radar_dismiss_${offer.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "ข้ามรายการงาน",
                        tint = TextHudMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PastRadarOfferRow(offer: DetectedJobOffer) {
    val statusColor = when (offer.status) {
        JobOfferStatus.ACCEPTED_LOGGED -> EmeraldSRank
        JobOfferStatus.EXPIRED -> AmberWarning
        JobOfferStatus.DISMISSED -> TextHudMuted
        JobOfferStatus.ACTIVE -> CyanTelemetry
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLevel1,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[${offer.platform.displayNameTh}] ${offer.pickupName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "฿%.0f • %.1f กม. • เกรด %s • %s",
                        offer.payoutBaht,
                        offer.tripDistanceKm,
                        offer.grade.label,
                        offer.formattedCreatedTime()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }
            StatusPillBadge(text = offer.status.labelTh, accentColor = statusColor)
        }
    }
}

@Composable
private fun IngestJobOfferDialog(
    onDismiss: () -> Unit,
    onSubmit: (rawText: String, title: String) -> Boolean
) {
    var titleText by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text("นำเข้าข้อความงานเข้า Job Radar", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "วางข้อความแจ้งเตือนหรือรายละเอียดออเดอร์จาก LINE MAN, Grab หรือ ShopeeFood",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("แพลตฟอร์ม / หัวข้อ (เช่น LINE MAN)") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        errorMsg = null
                    },
                    label = { Text("รายละเอียด (เช่น ร้านตี๋ ฿78 3.6 กม. ไปรับ 0.7 กม. 18 นาที)") },
                    maxLines = 4,
                    colors = crimsonFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_radar_manual_text")
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = CrimsonNeon)
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "ส่งเข้าเรดาร์",
                onClick = {
                    val ok = onSubmit(rawText, titleText)
                    if (!ok) {
                        errorMsg = "กรุณาระบุค่าส่ง (เช่น ฿65) หรือระยะทางในข้อความ"
                    }
                },
                testTagName = "btn_submit_radar_manual_text"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = TextHudMuted)
            }
        }
    )
}

private fun platformColor(platform: DeliveryPlatform): Color = when (platform) {
    DeliveryPlatform.LINE_MAN -> LineManGreen
    DeliveryPlatform.GRAB -> GrabGreen
    DeliveryPlatform.SHOPEE_FOOD -> ShopeeOrange
    DeliveryPlatform.OTHER -> CyanTelemetry
}
