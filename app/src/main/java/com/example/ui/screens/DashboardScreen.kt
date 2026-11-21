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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.model.AiRecommendationCard
import com.example.model.AppScreen
import com.example.model.DailyBreakEvenState
import com.example.model.EarningsPeriod
import com.example.model.GeoMath
import com.example.model.GpsLockState
import com.example.model.RecommendationPriority
import com.example.model.SmartEarningsCalculator
import com.example.model.ZoneMonitorStatus
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
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
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: FixPointUiState,
    onToggleWorkSession: () -> Unit,
    onToggleGpsLock: () -> Unit,
    onMuteZoneAlert: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onNavigateToScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { DailyBreakEvenState.todayDateKey() }

    val todaySummary = remember(
        uiState.smartEarningsState.transactions,
        uiState.smartEarningsState.dailyWorkMetrics,
        uiState.smartEarningsState.goals,
        uiState.fuelState.distanceDrivenSinceRefuelKm
    ) {
        SmartEarningsCalculator.computeSummary(
            state = uiState.smartEarningsState.copy(
                selectedPeriod = EarningsPeriod.DAILY,
                selectedDateKey = todayKey
            ),
            todayKey = todayKey,
            todayLiveFuelDistanceKm = uiState.fuelState.distanceDrivenSinceRefuelKm
        )
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

    val activeSession = uiState.activeWorkSession
    val activeSessionMinutes = activeSession?.durationMinutes(uiState.sessionClockTickMillis) ?: 0
    val totalTodayMinutes = (todaySummary.totalWorkHours * 60.0).toInt() +
        if (activeSession != null) activeSessionMinutes else 0

    val recommendations = remember(
        todaySummary,
        fuelIntel,
        uiState.zoneStatus,
        uiState.excessDistanceMeters,
        hotspots,
        uiState.analyzedOrders,
        activeSession != null
    ) {
        RiderIntelligenceEngine.generateDashboardRecommendations(
            todaySummary = todaySummary,
            fuelIntel = fuelIntel,
            zoneStatus = uiState.zoneStatus,
            excessDistanceMeters = uiState.excessDistanceMeters,
            hotspots = hotspots,
            analyzedOrders = uiState.analyzedOrders,
            isSessionActive = activeSession != null
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
                .testTag("dashboard_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Daily Earnings Hero Card
            item {
                CrimsonHeroCard(modifier = Modifier.testTag("dashboard_earnings_hero_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "รายได้วันนี้ (${uiState.riderProfile.riderName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "฿%,.0f", todaySummary.totalIncomeBaht),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 36.sp
                                ),
                                color = TextHudPrimary,
                                modifier = Modifier.testTag("dashboard_daily_income_value")
                            )
                        }
                        StatusPillBadge(
                            text = "${todaySummary.jobCount} ออเดอร์",
                            accentColor = if (todaySummary.isGoalReached) EmeraldSRank else CrimsonNeon,
                            icon = if (todaySummary.isGoalReached) Icons.Default.CheckCircle else Icons.Default.TrendingUp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Net profit & Expense inline bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x6607070A))
                            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("กำไรสุทธิ", style = MaterialTheme.typography.labelMedium, color = TextHudMuted)
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s฿%,.0f",
                                    if (todaySummary.netProfitBaht >= 0) "+" else "-",
                                    kotlin.math.abs(todaySummary.netProfitBaht)
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (todaySummary.netProfitBaht >= 0) EmeraldSRank else CrimsonNeon,
                                modifier = Modifier.testTag("dashboard_net_profit_value")
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ค่าใช้จ่าย / น้ำมัน", style = MaterialTheme.typography.labelMedium, color = TextHudMuted)
                            Text(
                                text = String.format(Locale.US, "฿%,.0f", todaySummary.totalExpenseBaht),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AmberWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Daily Income Target Progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "เป้ารายวัน ฿%,.0f", todaySummary.goalBaht),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudSecondary
                        )
                        Text(
                            text = if (todaySummary.isGoalReached) {
                                "ถึงเป้าแล้ว 100%"
                            } else {
                                String.format(
                                    Locale.US,
                                    "%.0f%% • เหลือ ฿%,.0f",
                                    todaySummary.goalProgressFraction * 100f,
                                    todaySummary.remainingToGoalBaht
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (todaySummary.isGoalReached) EmeraldSRank else CrimsonNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(todaySummary.goalProgressFraction.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (todaySummary.isGoalReached) EmeraldSRank else CrimsonRed)
                        )
                    }
                }
            }

            // 2. Work Session & GPS Lock Command Card
            item {
                PremiumCard(
                    accentBorder = if (activeSession != null) EmeraldSRank.copy(alpha = 0.6f) else CrimsonBorderGlow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeSession != null) "กำลังวิ่งรอบงาน" else "รอบวิ่งงานไรเดอร์",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activeSession != null) {
                                    String.format(
                                        Locale.US,
                                        "เริ่ม %s • %d นาที • %.1f กม. • %d ออเดอร์",
                                        activeSession.formattedStartTime(),
                                        activeSessionMinutes,
                                        activeSession.distanceKm,
                                        activeSession.completedOrders
                                    )
                                } else {
                                    "กดเริ่มรอบงานเพื่อบันทึกเวลา ระยะ GPS และสรุปรายได้ต่อรอบ"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudSecondary
                            )
                        }
                        if (activeSession != null) {
                            StatusPillBadge(text = "LIVE", accentColor = EmeraldSRank)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PremiumPrimaryButton(
                            text = if (activeSession != null) "หยุดรอบงาน" else "เริ่มรอบงาน",
                            icon = if (activeSession != null) Icons.Default.Stop else Icons.Default.PlayArrow,
                            containerColor = if (activeSession != null) Color(0xFF9F1239) else CrimsonRed,
                            onClick = onToggleWorkSession,
                            modifier = Modifier.weight(1.15f),
                            testTagName = "btn_toggle_work_session"
                        )
                        PremiumSecondaryButton(
                            text = if (uiState.isLockEnabled) "ปิด GPS Lock" else "ล็อก GPS",
                            icon = Icons.Default.GpsFixed,
                            accentColor = when (uiState.lockState) {
                                GpsLockState.LOCKED -> EmeraldSRank
                                GpsLockState.SEARCHING -> AmberWarning
                                GpsLockState.OFF -> CrimsonNeon
                            },
                            onClick = onToggleGpsLock,
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_toggle_gps_lock"
                        )
                    }

                    // Compact live telemetry strip when GPS Lock is active
                    if (uiState.isLockEnabled) {
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
                                text = "ความเร็ว ${GeoMath.formatSpeed(uiState.telemetry.speedMetersPerSec, uiState.measurementUnit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanTelemetry
                            )
                            Text(
                                text = "แม่นยำ ${GeoMath.formatDistanceMeters(uiState.telemetry.accuracyMeters, uiState.measurementUnit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHudSecondary
                            )
                            Text(
                                text = "ดาวเทียม ${uiState.telemetry.satellitesUsedInFix}/${uiState.telemetry.satellitesVisible}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSRank
                            )
                        }
                    }

                    if (uiState.permissionDeniedMessageTh != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CrimsonRed.copy(alpha = 0.14f))
                                .border(1.dp, CrimsonNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestLocationPermission)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = uiState.permissionDeniedMessageTh,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Active Zone Card (ONLY shown when an active pin exists — never shows "ไม่มีหมุดโซน" alert)
            val activePin = uiState.activePin
            if (activePin != null) {
                item {
                    val isOutside = uiState.zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE
                    val isInside = uiState.zoneStatus == ZoneMonitorStatus.INSIDE_ZONE
                    val accent = when {
                        isOutside -> CrimsonNeon
                        isInside -> EmeraldSRank
                        else -> AmberWarning
                    }
                    PremiumCard(
                        accentBorder = accent.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("dashboard_active_zone_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "โซนหลัก: ${activePin.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextHudPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        isOutside -> "ออกนอกโซนเกิน ${GeoMath.formatShortZoneDistanceTh(uiState.excessDistanceMeters)} (รัศมี ${String.format(Locale.US, "%.1f", uiState.zoneRadiusKm)} กม.)"
                                        isInside -> "อยู่ในโซน • ห่างจุดศูนย์กลาง ${GeoMath.formatShortZoneDistanceTh(uiState.currentDistanceFromPinMeters)}"
                                        else -> "รัศมีโซน ${String.format(Locale.US, "%.1f", uiState.zoneRadiusKm)} กม. • รอตำแหน่ง GPS"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextHudSecondary
                                )
                            }
                            StatusPillBadge(
                                text = when {
                                    isOutside -> "นอกโซน"
                                    isInside -> "ในโซน"
                                    else -> "รอ GPS"
                                },
                                accentColor = accent
                            )
                        }
                        if (isOutside && !uiState.isZoneAlertMuted) {
                            Spacer(modifier = Modifier.height(10.dp))
                            PremiumSecondaryButton(
                                text = "หยุดสั่นแจ้งเตือนชั่วคราว",
                                icon = Icons.Default.NotificationsOff,
                                accentColor = CrimsonNeon,
                                onClick = onMuteZoneAlert,
                                modifier = Modifier.fillMaxWidth(),
                                testTagName = "btn_dashboard_mute_zone"
                            )
                        }
                    }
                }
            }

            // 4. Rider Performance & Activity 2x2 Grid
            item {
                SectionHeaderRow(
                    title = "สรุปประสิทธิภาพวันนี้",
                    subtitle = "ข้อมูลจริงจากรอบงาน GPS และบัญชีในเครื่อง"
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val hoursLabel = if (totalTodayMinutes >= 60) {
                            String.format(Locale.US, "%d ชม. %d น.", totalTodayMinutes / 60, totalTodayMinutes % 60)
                        } else {
                            "$totalTodayMinutes นาที"
                        }
                        MetricStatCard(
                            label = "เวลาทำงาน",
                            value = hoursLabel,
                            subtext = if (todaySummary.avgIncomePerHour > 0.0) {
                                String.format(Locale.US, "฿%.0f / ชม.", todaySummary.avgIncomePerHour)
                            } else "ยังไม่ระบุเวลา",
                            icon = Icons.Default.Schedule,
                            accentColor = CyanTelemetry,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            label = "ระยะทางสะสม",
                            value = String.format(Locale.US, "%.1f กม.", todaySummary.totalDistanceKm),
                            subtext = if (todaySummary.avgIncomePerKm > 0.0) {
                                String.format(Locale.US, "เฉลี่ย ฿%.1f / กม.", todaySummary.avgIncomePerKm)
                            } else "ติดตามผ่าน GPS",
                            icon = Icons.Default.Route,
                            accentColor = VioletInsight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatCard(
                            label = "น้ำมันคงเหลือ",
                            value = String.format(Locale.US, "~%.0f กม.", fuelIntel.remainingRangeKm),
                            subtext = String.format(
                                Locale.US,
                                "%.2f ล. • ฿%.2f/กม.",
                                fuelIntel.remainingLiters,
                                fuelIntel.fuelCostPerKmBaht
                            ),
                            icon = Icons.Default.LocalGasStation,
                            accentColor = if (fuelIntel.isLowFuel) CrimsonNeon else AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            label = "ต้นทุนต่อ กม.",
                            value = if (todaySummary.costPerKm > 0.0) {
                                String.format(Locale.US, "฿%.2f", todaySummary.costPerKm)
                            } else {
                                String.format(Locale.US, "฿%.2f", fuelIntel.fuelCostPerKmBaht)
                            },
                            subtext = "รวมน้ำมันและรายจ่าย",
                            icon = Icons.Default.Speed,
                            accentColor = EmeraldSRank,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "วิเคราะห์ออเดอร์",
                        subtitle = "AI OCR",
                        icon = Icons.Default.AutoAwesome,
                        accent = CrimsonNeon,
                        onClick = { onNavigateToScreen(AppScreen.AI_ASSISTANT) },
                        modifier = Modifier.weight(1f).testTag("quick_action_ai")
                    )
                    QuickActionTile(
                        title = "แผนที่โซน",
                        subtitle = "${uiState.savedLocations.size} หมุด",
                        icon = Icons.Default.Map,
                        accent = CyanTelemetry,
                        onClick = { onNavigateToScreen(AppScreen.SMART_MAP) },
                        modifier = Modifier.weight(1f).testTag("quick_action_map")
                    )
                    QuickActionTile(
                        title = "บัญชีรายได้",
                        subtitle = "กราฟ & เป้า",
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = EmeraldSRank,
                        onClick = { onNavigateToScreen(AppScreen.EARNINGS) },
                        modifier = Modifier.weight(1f).testTag("quick_action_earnings")
                    )
                }
            }

            // 6. Important AI Recommendations
            item {
                SectionHeaderRow(
                    title = "คำแนะนำอัจฉริยะ (Offline AI)",
                    subtitle = "วิเคราะห์จากข้อมูลจริงในเครื่องแบบเรียลไทม์"
                )
            }
            items(recommendations.size, key = { recommendations[it].id }) { idx ->
                val rec = recommendations[idx]
                RecommendationCardRow(
                    card = rec,
                    onClick = { onNavigateToScreen(rec.targetScreen) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceLevel2,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextHudPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextHudMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecommendationCardRow(
    card: AiRecommendationCard,
    onClick: () -> Unit
) {
    val accent = when (card.priority) {
        RecommendationPriority.CRITICAL -> CrimsonNeon
        RecommendationPriority.HIGH -> AmberWarning
        RecommendationPriority.INFO -> CyanTelemetry
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ai_rec_${card.id}"),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceLevel2,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPillBadge(text = card.badgeText, accentColor = accent)
                Text(
                    text = card.titleTh,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = card.subtitleTh,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHudSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "ไปยังหน้าแนะนำ",
                tint = TextHudMuted
            )
        }
    }
}
