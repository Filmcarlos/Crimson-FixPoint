package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AnalyzedOrderRecord
import com.example.model.DetectedJobOffer
import com.example.model.JobOfferStatus
import com.example.ui.components.CrimsonEmptyStateCard
import com.example.ui.components.CrimsonErrorBanner
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import com.example.viewmodel.FixPointUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncSecondaryScreen(
    uiState: FixPointUiState,
    onToggleCloudSyncOptIn: (Boolean) -> Unit,
    onTriggerCloudSync: () -> Unit,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sync = uiState.cloudSyncState
    val lastBackupText = sync.lastLocalBackupMillis?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(it))
    } ?: "ยังไม่เคยสำรอง"

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("secondary_cloud_sync_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CrimsonHeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Sync & Local Backup",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sync.syncStatusLabelTh,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(
                            text = if (sync.backendProvisioned) "CLOUD CONNECTED" else "OFFLINE-FIRST",
                            accentColor = if (sync.backendProvisioned) EmeraldSRank else AmberWarning,
                            icon = if (sync.backendProvisioned) Icons.Default.CloudSync else Icons.Default.CloudOff
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = sync.syncDetailTh,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHudSecondary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        label = "รายการใน Room DB",
                        value = "${sync.pendingSyncRecordsCount}",
                        subtext = "หมุด บัญชี ออเดอร์ เรดาร์",
                        icon = Icons.Default.CloudSync,
                        accentColor = CyanTelemetry,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = "สำรอง JSON ล่าสุด",
                        value = if (sync.lastLocalBackupMillis != null) "มีไฟล์แล้ว" else "ยังไม่มี",
                        subtext = lastBackupText,
                        icon = Icons.Default.Backup,
                        accentColor = EmeraldSRank,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "เตรียมซิงก์ข้อมูลคลาวด์ (Cloud Readiness)",
                        subtitle = "จัดเตรียมโครงสร้างข้อมูล Room สำหรับเชื่อมต่อเซิร์ฟเวอร์สมาชิกในอนาคต"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "เปิดโหมดเตรียมซิงก์ Cloud Sync",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "รวบรวมจำนวนเรคคอร์ดและสคีมาเวอร์ชัน v3 เพื่อพร้อมอัปโหลดเมื่อเชื่อมโปรเจกต์คลาวด์",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudMuted
                            )
                        }
                        Switch(
                            checked = sync.cloudSyncOptIn,
                            onCheckedChange = onToggleCloudSyncOptIn,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CrimsonRed
                            ),
                            modifier = Modifier.testTag("switch_cloud_sync_opt_in")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PremiumPrimaryButton(
                        text = "ตรวจสอบและทดสอบซิงก์คลาวด์",
                        icon = Icons.Default.Sync,
                        onClick = onTriggerCloudSync,
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "btn_trigger_cloud_sync"
                    )
                    if (!sync.backendProvisioned) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CrimsonErrorBanner(
                            message = "บิลด์นี้ยังไม่เชื่อม Cloud Server จริง (ไม่มี Backend Provisioned) — ใช้การสำรอง/กู้คืนไฟล์ JSON ด้านล่างเพื่อย้ายเครื่องได้ทันที",
                            accentColor = CyanTelemetry
                        )
                    }
                }
            }

            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "สำรองและกู้คืนข้อมูลแบบออฟไลน์ 100%",
                        subtitle = "ส่งออกทุกตารางใน Room + การตั้งค่าเป็นไฟล์ JSON มาตรฐาน"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PremiumPrimaryButton(
                            text = "ส่งออกไฟล์ JSON",
                            icon = Icons.Default.Backup,
                            onClick = onExportLocalBackup,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_cloud_export_json"
                        )
                        PremiumSecondaryButton(
                            text = "นำเข้า JSON",
                            icon = Icons.Default.Restore,
                            accentColor = CyanTelemetry,
                            onClick = onImportLocalBackup,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_cloud_import_json"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JobHistorySecondaryScreen(
    uiState: FixPointUiState,
    onDeleteAnalyzedOrder: (String) -> Unit,
    onClearAnalyzedOrders: () -> Unit,
    onClearAllRadarOffers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("secondary_job_history_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CrimsonHeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ประวัติงานและผลวิเคราะห์ AI",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "บันทึกถาวรใน Room Database บนอุปกรณ์ของคุณ",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(
                            text = "${uiState.analyzedOrders.size + uiState.detectedJobOffers.size} รายการ",
                            accentColor = VioletInsight,
                            icon = Icons.Default.History
                        )
                    }
                }
            }

            if (uiState.analyzedOrders.isEmpty() && uiState.detectedJobOffers.isEmpty()) {
                item {
                    CrimsonEmptyStateCard(
                        title = "ยังไม่มีประวัติงานในเครื่อง",
                        subtitle = "เมื่อคุณวิเคราะห์ออเดอร์ด้วย AI Assistant หรือรับ/สแกนงานใน Job Radar ประวัติทั้งหมดจะแสดงที่นี่",
                        icon = Icons.Default.History,
                        accentColor = VioletInsight,
                        testTagName = "job_history_empty_state"
                    )
                }
            }

            if (uiState.analyzedOrders.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "ออเดอร์ที่วิเคราะห์แล้ว (${uiState.analyzedOrders.size})",
                        trailing = {
                            IconButton(onClick = onClearAnalyzedOrders) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "ล้างประวัติวิเคราะห์", tint = CrimsonNeon)
                            }
                        }
                    )
                }
                items(uiState.analyzedOrders.size, key = { "hist_ord_${uiState.analyzedOrders[it].id}" }) { idx ->
                    HistoryAnalyzedOrderRow(
                        order = uiState.analyzedOrders[idx],
                        onDelete = { onDeleteAnalyzedOrder(uiState.analyzedOrders[idx].id) }
                    )
                }
            }

            if (uiState.detectedJobOffers.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "ประวัติสัญญาณ Job Radar (${uiState.detectedJobOffers.size})",
                        trailing = {
                            IconButton(onClick = onClearAllRadarOffers) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "ล้างประวัติเรดาร์", tint = AmberWarning)
                            }
                        }
                    )
                }
                items(uiState.detectedJobOffers.size, key = { "hist_rad_${uiState.detectedJobOffers[it].id}" }) { idx ->
                    HistoryRadarOfferRow(offer = uiState.detectedJobOffers[idx])
                }
            }
        }
    }
}

@Composable
fun MembershipSecondaryScreen(
    uiState: FixPointUiState,
    onToggleMembershipProReady: (Boolean) -> Unit,
    onNavigateToCloudSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPro = uiState.cloudSyncState.isMembershipProEnabled
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("secondary_membership_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CrimsonHeroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Crimson Rider Membership",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ความสามารถหลักทั้งหมดใช้งานได้ฟรี 100% แบบออฟไลน์ในเครื่อง",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        StatusPillBadge(
                            text = if (isPro) "PRO READY" else "CORE FREE",
                            accentColor = if (isPro) AmberWarning else EmeraldSRank,
                            icon = Icons.Default.WorkspacePremium
                        )
                    }
                }
            }

            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "สิทธิ์ความสามารถที่มีให้ใช้งานทันที",
                        subtitle = "ไม่มีการล็อกฟีเจอร์หลักหรือบังคับจ่ายเงินในแอป"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "Multi-Platform Job Radar (แจ้งเตือน OS + OCR)",
                        "AI Order Profitability & Smart Score S–D",
                        "Live Work Zone GPS Monitoring & Anti-Bleed Map",
                        "Smart Earnings, Break-Even & Fuel Intelligence",
                        "Room v3 Offline Database + JSON Backup/Restore"
                    ).forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSRank,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(feature, style = MaterialTheme.typography.bodySmall, color = TextHudPrimary)
                        }
                    }
                }
            }

            item {
                PremiumCard {
                    SectionHeaderRow(
                        title = "เตรียมโปรไฟล์สมาชิก Cloud Sync (Preview Flag)",
                        subtitle = "บันทึกสถานะเตรียมพร้อมสำหรับเชื่อมบัญชีคลาวด์ในเวอร์ชันถัดไป"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "ตั้งสถานะ Rider Pro Cloud Readiness",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHudPrimary
                            )
                            Text(
                                text = "ใช้คู่กับหน้า Cloud Sync (ไม่มีระบบตัดเงินหรือ Backend ในเครื่องนี้)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudMuted
                            )
                        }
                        Switch(
                            checked = isPro,
                            onCheckedChange = onToggleMembershipProReady,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AmberWarning
                            ),
                            modifier = Modifier.testTag("switch_membership_pro_ready")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PremiumSecondaryButton(
                        text = "ไปยังหน้า Cloud Sync & สำรองข้อมูล",
                        icon = Icons.Default.CloudSync,
                        accentColor = CyanTelemetry,
                        onClick = onNavigateToCloudSync,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryAnalyzedOrderRow(
    order: AnalyzedOrderRecord,
    onDelete: () -> Unit
) {
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
                    text = "${order.pickupName} → ${order.dropoffName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "฿%.0f • %.1f กม. • เกรด %s (%d) • %s",
                        order.payoutBaht,
                        order.distanceKm + order.deadMileageKm,
                        order.grade.label,
                        order.smartScore,
                        order.formattedTime()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "ลบรายการ", tint = TextHudMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HistoryRadarOfferRow(offer: DetectedJobOffer) {
    val badgeColor = when (offer.status) {
        JobOfferStatus.ACCEPTED_LOGGED -> EmeraldSRank
        JobOfferStatus.EXPIRED -> AmberWarning
        JobOfferStatus.DISMISSED -> TextHudMuted
        JobOfferStatus.ACTIVE -> CyanTelemetry
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceLevel2,
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
                        "฿%.0f • %.1f กม. • %s • %s",
                        offer.payoutBaht,
                        offer.tripDistanceKm,
                        offer.source.labelTh,
                        offer.formattedCreatedTime()
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextHudMuted
                )
            }
            StatusPillBadge(text = offer.status.labelTh, accentColor = badgeColor)
        }
    }
}
