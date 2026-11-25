package com.example.ai

import com.example.model.AiRecommendationCard
import com.example.model.AnalyzedOrderRecord
import com.example.model.AppScreen
import com.example.model.DeliveryPlatform
import com.example.model.DetectedJobOffer
import com.example.model.EarningsRecordType
import com.example.model.EarningsSummaryMetrics
import com.example.model.EarningsTransaction
import com.example.model.ExpenseCategoryBreakdown
import com.example.model.FuelEstimateState
import com.example.model.FuelIntelligenceSummary
import com.example.model.GeoMath
import com.example.model.HotspotRecommendation
import com.example.model.JobOfferStatus
import com.example.model.OrderAnalysisResult
import com.example.model.OrderGrade
import com.example.model.ParsedOrderDraft
import com.example.model.PeakHourInsight
import com.example.model.PinCategory
import com.example.model.RadarComparisonInsight
import com.example.model.RecommendationPriority
import com.example.model.RiderProfileConfig
import com.example.model.SavedLocationItem
import com.example.model.SecondaryScreen
import com.example.model.ShiftPerformanceSummary
import com.example.model.WorkSessionRecord
import com.example.model.ZoneMonitorStatus
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure, deterministic, offline AI Rider Intelligence Engine for Crimson FixPoint 2.0.
 * Computes order profitability, Smart Order Score, dead mileage analysis, fuel intelligence,
 * historical hotspot rankings, expense breakdowns, and prioritized rider recommendations.
 */
object RiderIntelligenceEngine {

    /**
     * Evaluates an order draft using actual rider fuel efficiency, fuel price, maintenance rate,
     * and minimum target earnings thresholds.
     */
    fun analyzeOrder(
        draft: ParsedOrderDraft,
        profile: RiderProfileConfig,
        fuelState: FuelEstimateState
    ): OrderAnalysisResult? {
        val payout = draft.payoutBaht ?: return null
        val tripDistance = draft.distanceKm ?: return null
        if (payout <= 0.0 || tripDistance <= 0.0) return null

        val deadKm = (draft.deadMileageKm ?: 0.0).coerceAtLeast(0.0)
        val totalDistance = (tripDistance + deadKm).coerceAtLeast(0.05)
        val kmPerLiter = fuelState.consumptionKmPerLiter.coerceAtLeast(1.0f).toDouble()
        val fuelPrice = profile.fuelPricePerLiterBaht.coerceAtLeast(0f).toDouble()
        val maintPerKm = profile.maintenanceBahtPerKm.coerceAtLeast(0f).toDouble()

        val fuelCost = (totalDistance / kmPerLiter) * fuelPrice
        val maintCost = totalDistance * maintPerKm
        val netProfit = payout - fuelCost - maintCost

        val earningsPerKm = payout / totalDistance
        val estMins = draft.estimatedMinutes?.takeIf { it > 0 }
        val earningsPerHour = estMins?.let { mins ->
            netProfit / (mins / 60.0)
        }

        val deadMileageRatio = (deadKm / totalDistance).toFloat().coerceIn(0f, 1f)

        // Score components (0..100)
        val targetPerKm = profile.minAcceptableBahtPerKm.coerceAtLeast(1.0f).toDouble()
        val kmScore = ((earningsPerKm / targetPerKm) * 35.0).coerceIn(0.0, 42.0)

        val profitMargin = (netProfit / payout).coerceIn(-0.5, 1.0)
        val marginScore = ((profitMargin / 0.85) * 26.0).coerceIn(0.0, 26.0)

        val hourScore = if (earningsPerHour != null) {
            val targetPerHr = profile.minAcceptableBahtPerHour.coerceAtLeast(20.0f).toDouble()
            ((earningsPerHour / targetPerHr) * 24.0).coerceIn(0.0, 26.0)
        } else {
            // Neutral partial weight when duration is unavailable — never fabricate duration
            14.0
        }

        val deadMileagePenalty = when {
            deadMileageRatio > 0.45f -> 18.0
            deadMileageRatio > 0.30f -> 11.0
            deadMileageRatio > 0.20f -> 5.0
            else -> 0.0
        }

        val bonusShortHighYield = if (totalDistance <= 4.0 && netProfit >= 35.0) 6.0 else 0.0

        val rawScore = (kmScore + marginScore + hourScore + bonusShortHighYield - deadMileagePenalty)
            .roundToInt()
            .coerceIn(0, 100)

        val grade = when {
            netProfit <= 0.0 -> OrderGrade.D
            rawScore >= 84 -> OrderGrade.S
            rawScore >= 70 -> OrderGrade.A
            rawScore >= 55 -> OrderGrade.B
            rawScore >= 40 -> OrderGrade.C
            else -> OrderGrade.D
        }

        val explanations = buildList {
            add(
                String.format(
                    Locale.US,
                    "กำไรสุทธิหลังหักน้ำมัน (฿%.1f) และค่าเสื่อม (฿%.1f) อยู่ที่ ฿%.1f",
                    fuelCost,
                    maintCost,
                    netProfit
                )
            )
            add(
                String.format(
                    Locale.US,
                    "รายได้เฉลี่ย ฿%.1f/กม. (เกณฑ์ขั้นต่ำที่ตั้งไว้ ฿%.1f/กม.)",
                    earningsPerKm,
                    targetPerKm
                )
            )
            if (earningsPerHour != null) {
                add(
                    String.format(
                        Locale.US,
                        "อัตราทำกำไร ~฿%.0f/ชม. จากเวลาประมาณ %d นาที",
                        earningsPerHour,
                        estMins
                    )
                )
            } else {
                add("ไม่ได้ระบุเวลาโดยประมาณ — กรอกจำนวนนาทีเพื่อคำนวณ ฿/ชม. ที่แม่นยำขึ้น")
            }
            if (deadKm > 0.0) {
                add(
                    String.format(
                        Locale.US,
                        "ระยะวิ่งเปล่า (Dead Mileage) %.1f กม. (%.0f%% ของระยะรวม)",
                        deadKm,
                        deadMileageRatio * 100f
                    )
                )
            }
        }

        val warnings = buildList {
            addAll(draft.warnings)
            if (netProfit <= 0.0) {
                add("งานนี้ขาดทุนสุทธิเมื่อหักค่าน้ำมันและค่าบำรุงรักษา")
            }
            if (deadMileageRatio > 0.30f) {
                add("สัดส่วนระยะวิ่งเปล่าสูงกว่า 30% เสี่ยงสิ้นเปลืองน้ำมันและเวลา")
            }
            if (fuelState.estimatedRemainingRangeKm < totalDistance + 5.0) {
                add("น้ำมันคงเหลือในถัง (~${String.format(Locale.US, "%.1f", fuelState.estimatedRemainingRangeKm)} กม.) อาจไม่พอสำหรับงานนี้")
            }
        }

        val effectiveConfidence = if (draft.wasUserCorrected) {
            if (estMins != null) 0.98f else 0.85f
        } else {
            draft.overallConfidence.coerceIn(0.25f, 1.0f)
        }

        return OrderAnalysisResult(
            smartScore = rawScore,
            grade = grade,
            confidenceScore = effectiveConfidence,
            grossPayoutBaht = payout,
            totalDistanceKm = totalDistance,
            tripDistanceKm = tripDistance,
            deadMileageKm = deadKm,
            estimatedMinutes = estMins,
            fuelCostBaht = fuelCost,
            maintenanceCostBaht = maintCost,
            netProfitBaht = netProfit,
            earningsPerKm = earningsPerKm,
            earningsPerHour = earningsPerHour,
            deadMileageRatio = deadMileageRatio,
            explanations = explanations,
            warnings = warnings,
            isIncompleteData = estMins == null
        )
    }

    fun computeFuelIntelligence(
        fuelState: FuelEstimateState,
        profile: RiderProfileConfig
    ): FuelIntelligenceSummary {
        val kmPerL = fuelState.consumptionKmPerLiter.coerceAtLeast(1.0f).toDouble()
        val fuelCostPerKm = profile.fuelPricePerLiterBaht.toDouble() / kmPerL
        val missingLiters = (fuelState.tankCapacityLiters - fuelState.remainingLiters).coerceAtLeast(0f)
        val refillCost = missingLiters * profile.fuelPricePerLiterBaht
        val advice = when {
            fuelState.isLowFuel -> String.format(
                Locale.US,
                "น้ำมันใกล้หมด (เหลือ ~%.2f ลิตร / วิ่งได้ ~%.0f กม.) ควรแวะเติมประมาณ ฿%.0f",
                fuelState.remainingLiters,
                fuelState.estimatedRemainingRangeKm,
                refillCost
            )
            fuelCostPerKm > 1.8 -> String.format(
                Locale.US,
                "ต้นทุนน้ำมัน ฿%.2f/กม. แนะนำเน้นงานระยะสั้นภายในโซนเพื่อลด Dead Mileage",
                fuelCostPerKm
            )
            else -> String.format(
                Locale.US,
                "ต้นทุนน้ำมันประหยัด ฿%.2f/กม. วิ่งต่อได้อีก ~%.0f กม.",
                fuelCostPerKm,
                fuelState.estimatedRemainingRangeKm
            )
        }
        return FuelIntelligenceSummary(
            fuelCostPerKmBaht = fuelCostPerKm,
            remainingLiters = fuelState.remainingLiters,
            remainingRangeKm = fuelState.estimatedRemainingRangeKm,
            tankFillFraction = fuelState.remainingFraction,
            estimatedRefillCostBaht = refillCost.toDouble(),
            isLowFuel = fuelState.isLowFuel,
            adviceTh = advice
        )
    }

    /**
     * Ranks saved FixPoints, Hotzones, and Restaurants + historical analyzed order spots.
     * Always explicitly flagged with [HotspotRecommendation.isHistoricalEstimate] = true so riders
     * clearly know these come from local historical records, NOT fabricated real-time server demand.
     */
    fun computeHotspotRecommendations(
        savedPins: List<SavedLocationItem>,
        analyzedOrders: List<AnalyzedOrderRecord>,
        riderLat: Double?,
        riderLng: Double?
    ): List<HotspotRecommendation> {
        val candidates = savedPins.filter {
            it.category == PinCategory.HOTZONE ||
                it.category == PinCategory.RESTAURANT ||
                it.category == PinCategory.FIXPOINT
        }

        val scored = candidates.map { pin ->
            val distKm = if (riderLat != null && riderLng != null) {
                GeoMath.haversineKm(riderLat, riderLng, pin.latitude, pin.longitude)
            } else {
                null
            }

            // Aggregate local historical analyzed orders within 1.5 km of this marker
            val nearbyOrders = analyzedOrders.filter { ord ->
                ord.latitude != null && ord.longitude != null &&
                    GeoMath.haversineKm(pin.latitude, pin.longitude, ord.latitude, ord.longitude) <= 1.5
            }
            val totalHistCount = pin.historicalOrderCount + nearbyOrders.size
            val avgPayout = when {
                nearbyOrders.isNotEmpty() -> nearbyOrders.map { it.payoutBaht }.average()
                pin.historicalAvgPayoutBaht > 0.0 -> pin.historicalAvgPayoutBaht
                else -> 0.0
            }

            val proximityBonus = if (distKm != null) (1f / (1f + distKm.toFloat() * 0.45f)) else 0.5f
            val categoryBonus = when (pin.category) {
                PinCategory.HOTZONE -> 0.35f
                PinCategory.RESTAURANT -> 0.25f
                PinCategory.FIXPOINT -> 0.20f
                else -> 0.05f
            }
            val historyBonus = (totalHistCount.coerceAtMost(20) / 20f) * 0.35f
            val heat = (proximityBonus * 0.4f + categoryBonus + historyBonus).coerceIn(0.15f, 1.0f)

            val reason = buildString {
                append(pin.category.labelTh)
                if (distKm != null) {
                    append(String.format(Locale.US, " • ห่าง %.1f กม.", distKm))
                }
                if (totalHistCount > 0) {
                    append(" • ประวัติ $totalHistCount งาน")
                    if (avgPayout > 0.0) {
                        append(String.format(Locale.US, " (เฉลี่ย ฿%.0f)", avgPayout))
                    }
                } else {
                    append(" • จุดอ้างอิงที่บันทึกไว้ (ประเมินจากประวัติในเครื่อง)")
                }
            }

            HotspotRecommendation(
                id = pin.id,
                name = pin.name,
                category = pin.category,
                latitude = pin.latitude,
                longitude = pin.longitude,
                distanceFromRiderKm = distKm,
                historicalAvgPayoutBaht = avgPayout,
                historicalOrderCount = totalHistCount,
                estimatedHeatScore = heat,
                isHistoricalEstimate = true,
                reasonTh = reason
            )
        }

        return scored.sortedByDescending { it.estimatedHeatScore }.take(6)
    }

    fun computeExpenseBreakdown(transactions: List<EarningsTransaction>): List<ExpenseCategoryBreakdown> {
        val expenses = transactions.filter { it.type == EarningsRecordType.EXPENSE && it.amountBaht > 0.0 }
        val total = expenses.sumOf { it.amountBaht }
        if (total <= 0.0) return emptyList()
        return expenses
            .groupBy { it.category.ifBlank { "อื่นๆ" } }
            .map { (cat, rows) ->
                val sum = rows.sumOf { it.amountBaht }
                ExpenseCategoryBreakdown(
                    category = cat,
                    amountBaht = sum,
                    fraction = (sum / total).toFloat().coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.amountBaht }
    }

    fun generateDashboardRecommendations(
        todaySummary: EarningsSummaryMetrics,
        fuelIntel: FuelIntelligenceSummary,
        zoneStatus: ZoneMonitorStatus,
        excessDistanceMeters: Double,
        hotspots: List<HotspotRecommendation>,
        analyzedOrders: List<AnalyzedOrderRecord>,
        isSessionActive: Boolean,
        activeRadarJobsCount: Int = 0
    ): List<AiRecommendationCard> {
        val list = mutableListOf<AiRecommendationCard>()

        if (activeRadarJobsCount > 0) {
            list.add(
                AiRecommendationCard(
                    id = "rec_radar_active",
                    titleTh = "พบงานเข้าใหม่ในเรดาร์ $activeRadarJobsCount รายการ",
                    subtitleTh = "เปิด Job Radar เพื่อเทียบระยะทางและคะแนนความคุ้มค่า S–D ก่อนกดรับในแอปหลัก",
                    badgeText = "JOB RADAR",
                    priority = RecommendationPriority.HIGH,
                    targetScreen = AppScreen.JOB_RADAR
                )
            )
        }

        if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE) {
            list.add(
                AiRecommendationCard(
                    id = "rec_out_of_zone",
                    titleTh = "ออกนอกโซนรับงานแล้ว",
                    subtitleTh = "เกินรัศมี ${GeoMath.formatShortZoneDistanceTh(excessDistanceMeters)} แนะนำกลับเข้าโซนหลักเพื่อลดระยะวิ่งเปล่า",
                    badgeText = "ZONE ALERT",
                    priority = RecommendationPriority.CRITICAL,
                    targetScreen = AppScreen.LIVE_MAP
                )
            )
        }

        if (fuelIntel.isLowFuel) {
            list.add(
                AiRecommendationCard(
                    id = "rec_low_fuel",
                    titleTh = "ระดับน้ำมันต่ำกว่าเกณฑ์แจ้งเตือน",
                    subtitleTh = fuelIntel.adviceTh,
                    badgeText = "FUEL INTEL",
                    priority = RecommendationPriority.CRITICAL,
                    targetScreen = AppScreen.HOME,
                    targetSecondaryScreen = SecondaryScreen.PROFILE
                )
            )
        }

        if (!isSessionActive && todaySummary.jobCount == 0) {
            list.add(
                AiRecommendationCard(
                    id = "rec_start_shift",
                    titleTh = "พร้อมเริ่มรอบวิ่งงานวันนี้",
                    subtitleTh = "กดเริ่มรอบงานเพื่อติดตามระยะทาง GPS เวลาทำงาน และวิเคราะห์ความคุ้มค่าออเดอร์อัตโนมัติ",
                    badgeText = "SHIFT READY",
                    priority = RecommendationPriority.HIGH,
                    targetScreen = AppScreen.HOME
                )
            )
        } else if (todaySummary.goalBaht > 0.0 && !todaySummary.isGoalReached) {
            list.add(
                AiRecommendationCard(
                    id = "rec_goal_pace",
                    titleTh = String.format(Locale.US, "อีก ฿%.0f จะถึงเป้ารายวัน", todaySummary.remainingToGoalBaht),
                    subtitleTh = if (todaySummary.avgIncomePerKm > 0.0) {
                        String.format(
                            Locale.US,
                            "ค่าเฉลี่ยวันนี้ ฿%.1f/กม. • เลือกงานคะแนน A/S เพื่อปิดเป้าเร็วขึ้น",
                            todaySummary.avgIncomePerKm
                        )
                    } else {
                        "ใช้ Smart Order Reader วิเคราะห์ออเดอร์ก่อนรับเพื่อรักษากำไรสุทธิต่อกิโลเมตร"
                    },
                    badgeText = "TARGET PACE",
                    priority = RecommendationPriority.HIGH,
                    targetScreen = AppScreen.AI_ASSISTANT
                )
            )
        } else if (todaySummary.isGoalReached) {
            list.add(
                AiRecommendationCard(
                    id = "rec_goal_done",
                    titleTh = "บรรลุเป้ารายวันแล้ว!",
                    subtitleTh = String.format(
                        Locale.US,
                        "กำไรสุทธิวันนี้ ฿%.0f • พิจารณาคัดเฉพาะงานเกรด S เพื่อลดความล้าและค่าน้ำมัน",
                        todaySummary.netProfitBaht
                    ),
                    badgeText = "GOAL HIT",
                    priority = RecommendationPriority.INFO,
                    targetScreen = AppScreen.EARNINGS
                )
            )
        }

        val avgDeadMileageRatio = analyzedOrders
            .take(10)
            .mapNotNull { o ->
                val tot = o.distanceKm + o.deadMileageKm
                if (tot > 0.0) o.deadMileageKm / tot else null
            }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: 0.0

        if (avgDeadMileageRatio > 0.25) {
            list.add(
                AiRecommendationCard(
                    id = "rec_dead_mileage",
                    titleTh = "ระยะวิ่งเปล่าเฉลี่ยสูง (${(avgDeadMileageRatio * 100).roundToInt()}%)",
                    subtitleTh = "งานล่าสุดมีระยะวิ่งไปรับร้านค่อนข้างไกล แนะนำจอดรอใกล้โซนฮอตสปอตที่บันทึกไว้",
                    badgeText = "DEAD MILEAGE",
                    priority = RecommendationPriority.HIGH,
                    targetScreen = AppScreen.LIVE_MAP
                )
            )
        }

        val topHotspot = hotspots.firstOrNull()
        if (topHotspot != null) {
            list.add(
                AiRecommendationCard(
                    id = "rec_hotspot_${topHotspot.id}",
                    titleTh = "จุดแนะนำถัดไป: ${topHotspot.name}",
                    subtitleTh = "${topHotspot.reasonTh} (ข้อมูลประวัติในเครื่อง)",
                    badgeText = "HISTORICAL",
                    priority = RecommendationPriority.INFO,
                    targetScreen = AppScreen.LIVE_MAP
                )
            )
        }

        if (analyzedOrders.isEmpty()) {
            list.add(
                AiRecommendationCard(
                    id = "rec_try_ocr",
                    titleTh = "ทดลองวิเคราะห์ออเดอร์ด้วย AI Assistant",
                    subtitleTh = "นำเข้าภาพแคปหน้าจอหรือเปิด Screen Reader เพื่อคำนวณ Smart Order Score แบบออฟไลน์ 100%",
                    badgeText = "ORDER AI",
                    priority = RecommendationPriority.INFO,
                    targetScreen = AppScreen.AI_ASSISTANT
                )
            )
        }

        return list.take(4)
    }

    /**
     * Compares currently active detected radar jobs to recommend both the Best Smart Score job
     * and the Nearest Pickup job with transparent ฿/km and profit rationale.
     */
    fun analyzeRadarComparison(
        offers: List<DetectedJobOffer>,
        profile: RiderProfileConfig,
        nowMillis: Long = System.currentTimeMillis()
    ): RadarComparisonInsight? {
        val active = offers.filter {
            it.status == JobOfferStatus.ACTIVE && nowMillis < it.expiresAtMillis
        }
        if (active.isEmpty()) return null

        val bestSmart = active.maxWithOrNull(
            compareBy<DetectedJobOffer> { it.smartScore }
                .thenBy { it.netProfitBaht }
        )
        val nearest = active.minWithOrNull(
            compareBy<DetectedJobOffer> {
                it.effectiveDistanceToPickupKm() ?: (it.tripDistanceKm.takeIf { d -> d > 0.0 } ?: Double.MAX_VALUE)
            }.thenByDescending { it.smartScore }
        )

        val validRateList = active.mapNotNull { it.earningsPerKm.takeIf { r -> r > 0.0 } }
        val avgRate = if (validRateList.isNotEmpty()) validRateList.average() else 0.0

        val bestHeadline = bestSmart?.let {
            String.format(
                Locale.US,
                "[%s] %s • เกรด %s (%d คะแนน) • กำไร ~฿%.0f",
                it.platform.displayNameTh,
                it.pickupName,
                it.grade.label,
                it.smartScore,
                it.netProfitBaht
            )
        } ?: "—"

        val nearestDist = nearest?.effectiveDistanceToPickupKm()
        val nearestHeadline = nearest?.let {
            val distText = if (nearestDist != null) {
                String.format(Locale.US, "ห่าง %.1f กม.", nearestDist)
            } else if (it.tripDistanceKm > 0.0) {
                String.format(Locale.US, "ระยะงาน %.1f กม.", it.tripDistanceKm)
            } else {
                "ไม่ระบุระยะ"
            }
            "[${it.platform.displayNameTh}] ${it.pickupName} • $distText • ฿${it.payoutBaht.roundToInt()}"
        } ?: "—"

        val advice = when {
            bestSmart != null && bestSmart.earningsPerKm >= profile.minAcceptableBahtPerKm ->
                String.format(
                    Locale.US,
                    "แนะนำงาน %s (%s) เพราะทำได้ ฿%.1f/กม. สูงกว่าเกณฑ์ขั้นต่ำ ฿%.1f/กม.",
                    bestSmart.platform.displayNameTh,
                    bestSmart.pickupName,
                    bestSmart.earningsPerKm,
                    profile.minAcceptableBahtPerKm
                )
            bestSmart != null ->
                "งานที่มีอยู่ในเรดาร์ยังต่ำกว่าเกณฑ์ขั้นต่ำ หรือข้อมูลระยะทางยังไม่ครบ — ตรวจสอบตัวเลขก่อนตัดสินใจ"
            else -> "กำลังติดตามงานจากแจ้งเตือนและ OCR"
        }

        return RadarComparisonInsight(
            bestSmartOfferId = bestSmart?.id,
            bestSmartHeadlineTh = bestHeadline,
            nearestOfferId = nearest?.id,
            nearestHeadlineTh = nearestHeadline,
            averageEarningsPerKm = avgRate,
            adviceTh = advice,
            isLiveEstimate = true
        )
    }

    /**
     * Aggregates real local income & analyzed order timestamps into 2-hour windows to identify
     * verified peak earning hours from the rider's actual device history.
     * Returns empty list when no records exist (never fabricates fake hours).
     */
    fun analyzePeakHoursFromHistory(
        transactions: List<EarningsTransaction>,
        analyzedOrders: List<AnalyzedOrderRecord>
    ): List<PeakHourInsight> {
        data class TimedPayout(val timestamp: Long, val amount: Double)

        val entries = mutableListOf<TimedPayout>()
        transactions
            .filter { it.type == EarningsRecordType.INCOME && it.amountBaht > 0.0 && it.timestampMillis > 0L }
            .forEach { entries.add(TimedPayout(it.timestampMillis, it.amountBaht)) }

        // Avoid double-counting orders already logged to earnings
        analyzedOrders
            .filter { !it.savedToEarnings && it.payoutBaht > 0.0 && it.createdAtMillis > 0L }
            .forEach { entries.add(TimedPayout(it.createdAtMillis, it.payoutBaht)) }

        if (entries.isEmpty()) return emptyList()

        val cal = Calendar.getInstance(Locale.US)
        val buckets = linkedMapOf<Int, MutableList<Double>>()
        for (item in entries) {
            cal.timeInMillis = item.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            val bucketStart = (hour / 2) * 2
            buckets.getOrPut(bucketStart) { mutableListOf() }.add(item.amount)
        }

        return buckets.entries
            .map { (start, amounts) ->
                PeakHourInsight(
                    hourStart = start,
                    hourEnd = start + 2,
                    orderCount = amounts.size,
                    avgPayoutBaht = amounts.average(),
                    totalIncomeBaht = amounts.sum(),
                    isVerifiedLocalData = true
                )
            }
            .sortedWith(
                compareByDescending<PeakHourInsight> { it.totalIncomeBaht }
                    .thenByDescending { it.orderCount }
            )
            .take(4)
    }

    /**
     * Summarizes today's verified work performance and net profit after costs.
     */
    fun summarizeShiftAndDailyPerformance(
        todaySummary: EarningsSummaryMetrics,
        activeSession: WorkSessionRecord?,
        nowMillis: Long = System.currentTimeMillis()
    ): ShiftPerformanceSummary {
        val activeMinutes = activeSession?.durationMinutes(nowMillis) ?: 0
        val totalMinutes = max((todaySummary.totalWorkHours * 60.0).roundToInt(), activeMinutes)

        val message = when {
            todaySummary.jobCount == 0 && todaySummary.totalExpenseBaht == 0.0 ->
                "ยังไม่มีรายการรายได้หรือค่าใช้จ่ายวันนี้ — เริ่มรอบงานหรือสแกนออเดอร์เพื่อบันทึกผลงานจริง"
            todaySummary.netProfitBaht < 0.0 ->
                String.format(
                    Locale.US,
                    "วันนี้รายจ่ายสูงกว่ารายรับ ฿%.0f (ต้องเก็บอีก ฿%.0f เพื่อคุ้มทุน)",
                    kotlin.math.abs(todaySummary.netProfitBaht),
                    kotlin.math.abs(todaySummary.netProfitBaht)
                )
            todaySummary.isGoalReached ->
                String.format(
                    Locale.US,
                    "ทำได้ %d งาน กำไรสุทธิ +฿%.0f ทะลุเป้ารายวันแล้ว (เฉลี่ย ฿%.1f/กม.)",
                    todaySummary.jobCount,
                    todaySummary.netProfitBaht,
                    todaySummary.avgIncomePerKm
                )
            else ->
                String.format(
                    Locale.US,
                    "ทำแล้ว %d งาน กำไรสุทธิ +฿%.0f เหลืออีก ฿%.0f ถึงเป้ารายวัน",
                    todaySummary.jobCount,
                    todaySummary.netProfitBaht,
                    todaySummary.remainingToGoalBaht
                )
        }

        return ShiftPerformanceSummary(
            completedJobsToday = todaySummary.jobCount,
            grossIncomeBaht = todaySummary.totalIncomeBaht,
            fuelAndExpenseBaht = todaySummary.totalExpenseBaht,
            netProfitBaht = todaySummary.netProfitBaht,
            avgBahtPerKm = todaySummary.avgIncomePerKm,
            avgBahtPerHour = todaySummary.avgIncomePerHour,
            activeShiftMinutes = totalMinutes,
            summaryMessageTh = message,
            isVerifiedLocalData = true
        )
    }
}
