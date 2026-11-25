package com.example

import com.example.ai.RiderIntelligenceEngine
import com.example.integration.DeliveryAdapterRegistry
import com.example.integration.GrabAdapter
import com.example.integration.JobRadarEngine
import com.example.integration.LineManAdapter
import com.example.integration.ShopeeFoodAdapter
import com.example.model.AnalyzedOrderRecord
import com.example.model.DeliveryPlatform
import com.example.model.DetectedJobOffer
import com.example.model.EarningsPeriod
import com.example.model.EarningsRecordType
import com.example.model.EarningsSummaryMetrics
import com.example.model.EarningsTransaction
import com.example.model.FuelEstimateState
import com.example.model.JobOfferSource
import com.example.model.JobOfferStatus
import com.example.model.JobRankingMode
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.RiderProfileConfig
import com.example.model.ScreenReaderState
import com.example.model.WorkSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class JobRadarAndAdaptersTest {

    private val defaultProfile = RiderProfileConfig()
    private val defaultFuel = FuelEstimateState()

    @Test
    fun deliveryAdapters_detectPackagesAndParseMultiPlatformJobs() {
        assertTrue(LineManAdapter.supportsPackage("com.linecorp.linemanth"))
        assertTrue(GrabAdapter.supportsPackage("com.grabtaxi.driver2"))
        assertTrue(ShopeeFoodAdapter.supportsPackage("com.shopeefood.driver"))

        assertEquals(LineManAdapter, DeliveryAdapterRegistry.resolveForPackage("com.linecorp.linemanth"))
        assertEquals(GrabAdapter, DeliveryAdapterRegistry.resolveForPackage("com.grabtaxi.driver2"))
        assertEquals(ShopeeFoodAdapter, DeliveryAdapterRegistry.resolveForPackage("com.shopeefood.driver"))
        assertNull(DeliveryAdapterRegistry.resolveForPackage("com.unknown.socialapp"))

        val lmDraft = LineManAdapter.extractDraft(
            title = "งานใหม่ LINE MAN",
            body = "ร้านข้าวมันไก่ประตูน้ำ ค่าส่ง ฿85 3.2 กม. ไปรับ 0.4 กม. 16 นาที",
            source = OrderInputSource.MANUAL_OR_TEXT
        )
        assertNotNull(lmDraft)
        assertEquals(DeliveryPlatform.LINE_MAN.displayNameTh, lmDraft?.platformHint)
        assertEquals(85.0, lmDraft?.payoutBaht ?: 0.0, 0.01)

        val spfDraft = ShopeeFoodAdapter.extractDraft(
            title = "ShopeeFood",
            body = "ค่าส่ง ฿58 ระยะ 2.8 กม.",
            source = OrderInputSource.MANUAL_OR_TEXT
        )
        assertNotNull(spfDraft)
        assertEquals(DeliveryPlatform.SHOPEE_FOOD.displayNameTh, spfDraft?.platformHint)
    }

    @Test
    fun jobRadarEngine_detectsDuplicatesComputesGpsDistanceAndExpiresOffers() {
        val t0 = 1_700_000_000_000L
        val first = JobRadarEngine.parseToJobOffer(
            rawText = "LINE MAN ร้านเจ๊ไฝ ฿95 4.0 กม. ไปรับ 0.5 กม. 20 นาที",
            source = JobOfferSource.NOTIFICATION_LISTENER,
            profile = defaultProfile,
            fuelState = defaultFuel,
            riderLat = 13.7563,
            riderLng = 100.5018,
            pickupLat = 13.7600,
            pickupLng = 100.5050,
            packageName = "com.linecorp.linemanth",
            nowMillis = t0,
            ttlMillis = 90_000L
        )
        assertNotNull(first)
        assertNotNull(first!!.distanceFromRiderKm)
        assertTrue(first.distanceFromRiderKm!! > 0.1)

        val duplicateWithinWindow = JobRadarEngine.parseToJobOffer(
            rawText = "LINE MAN ร้านเจ๊ไฝ ค่าส่ง ฿95 4.0 กม.",
            source = JobOfferSource.OCR_SCREEN_READER,
            profile = defaultProfile,
            fuelState = defaultFuel,
            packageName = "com.linecorp.linemanth",
            nowMillis = t0 + 30_000L,
            ttlMillis = 90_000L
        )
        assertNotNull(duplicateWithinWindow)
        assertNotNull(JobRadarEngine.isDuplicateOffer(listOf(first), duplicateWithinWindow!!))

        val laterDifferent = JobRadarEngine.parseToJobOffer(
            rawText = "GrabFood ร้านอื่น ฿120 4.5 กม.",
            source = JobOfferSource.NOTIFICATION_LISTENER,
            profile = defaultProfile,
            fuelState = defaultFuel,
            packageName = "com.grabtaxi.driver2",
            nowMillis = t0 + 120_000L,
            ttlMillis = 90_000L
        )
        assertNotNull(laterDifferent)
        assertNull(JobRadarEngine.isDuplicateOffer(listOf(first), laterDifferent!!))

        val expired = JobRadarEngine.expireOutdatedOffers(
            offers = listOf(first),
            nowMillis = t0 + 91_000L
        )
        assertEquals(JobOfferStatus.EXPIRED, expired.first().status)
    }

    @Test
    fun jobRadarEngine_ranksNearestFirstVsSmartBestJobAndMonitorsRealPlatformStates() {
        val now = 1_700_000_000_000L
        val nearLowPay = DetectedJobOffer(
            id = "JOB-NEAR",
            signatureHash = "SIG-1",
            platform = DeliveryPlatform.GRAB,
            source = JobOfferSource.MANUAL_OR_SHARED,
            pickupName = "ร้านใกล้",
            dropoffName = "ซอยข้างๆ",
            payoutBaht = 42.0,
            tripDistanceKm = 3.5,
            deadMileageKm = 0.2,
            distanceFromRiderKm = 0.2,
            netProfitBaht = 36.0,
            earningsPerKm = 11.3,
            smartScore = 58,
            grade = OrderGrade.B,
            createdAtMillis = now,
            expiresAtMillis = now + 90_000L
        )
        val highProfitFarther = DetectedJobOffer(
            id = "JOB-SMART",
            signatureHash = "SIG-2",
            platform = DeliveryPlatform.LINE_MAN,
            source = JobOfferSource.NOTIFICATION_LISTENER,
            pickupName = "ร้านใหญ่สยาม",
            dropoffName = "สาทร",
            payoutBaht = 140.0,
            tripDistanceKm = 4.2,
            deadMileageKm = 1.1,
            distanceFromRiderKm = 1.1,
            netProfitBaht = 132.0,
            earningsPerKm = 26.4,
            smartScore = 94,
            grade = OrderGrade.S,
            createdAtMillis = now + 1000L,
            expiresAtMillis = now + 90_000L
        )

        val nearestRanked = JobRadarEngine.rankOffers(
            offers = listOf(highProfitFarther, nearLowPay),
            mode = JobRankingMode.NEAREST_FIRST,
            nowMillis = now + 5000L
        )
        assertEquals("JOB-NEAR", nearestRanked.first().id)

        val smartRanked = JobRadarEngine.rankOffers(
            offers = listOf(nearLowPay, highProfitFarther),
            mode = JobRankingMode.SMART_BEST_JOB,
            nowMillis = now + 5000L
        )
        assertEquals("JOB-SMART", smartRanked.first().id)

        val statuses = JobRadarEngine.evaluatePlatformStatuses(
            notificationAccessGranted = false,
            screenReaderState = ScreenReaderState.IDLE,
            offers = listOf(nearLowPay, highProfitFarther),
            adapterEnabledMap = mapOf(
                DeliveryPlatform.LINE_MAN to true,
                DeliveryPlatform.GRAB to true,
                DeliveryPlatform.SHOPEE_FOOD to false
            )
        )
        assertEquals(3, statuses.size)
        statuses.forEach { st ->
            assertFalse("Closed third-party public API must stay false", st.publicDispatchApiAvailable)
            assertFalse(st.notificationListenerGranted)
            assertTrue(st.ocrEngineReady)
        }
        val spf = statuses.first { it.platform == DeliveryPlatform.SHOPEE_FOOD }
        assertFalse(spf.isAdapterEnabled)
    }

    @Test
    fun aiCopilot_computesRadarComparisonRealPeakHoursAndShiftSummary() {
        val now = System.currentTimeMillis()
        val sampleOffer = DetectedJobOffer(
            id = "JOB-AI",
            signatureHash = "SIG-AI",
            platform = DeliveryPlatform.LINE_MAN,
            source = JobOfferSource.MANUAL_OR_SHARED,
            pickupName = "ร้านทองหล่อ",
            dropoffName = "เอกมัย",
            payoutBaht = 95.0,
            tripDistanceKm = 3.0,
            deadMileageKm = 0.4,
            distanceFromRiderKm = 0.4,
            netProfitBaht = 88.0,
            earningsPerKm = 27.9,
            smartScore = 91,
            grade = OrderGrade.S,
            createdAtMillis = now,
            expiresAtMillis = now + 90_000L
        )
        val comparison = RiderIntelligenceEngine.analyzeRadarComparison(
            offers = listOf(sampleOffer),
            profile = defaultProfile,
            nowMillis = now + 1000L
        )
        assertNotNull(comparison)
        assertEquals("JOB-AI", comparison?.bestSmartOfferId)

        // Peak hours empty when there is no local history
        assertTrue(RiderIntelligenceEngine.analyzePeakHoursFromHistory(emptyList(), emptyList()).isEmpty())

        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 15)
        }
        val lunchTimestamp = cal.timeInMillis
        val txns = listOf(
            EarningsTransaction("T1", EarningsRecordType.INCOME, "2026-11-25", "งาน 1", "รับงาน", 110.0, 4.0, 20, lunchTimestamp),
            EarningsTransaction("T2", EarningsRecordType.INCOME, "2026-11-25", "งาน 2", "รับงาน", 95.0, 3.5, 18, lunchTimestamp + 60_000L)
        )
        val unsavedOrder = AnalyzedOrderRecord(
            id = "O1", dateKey = "2026-11-25", source = OrderInputSource.MANUAL_OR_TEXT,
            pickupName = "A", dropoffName = "B", payoutBaht = 65.0, distanceKm = 2.5,
            deadMileageKm = 0.3, estimatedMinutes = 14, fuelCostBaht = 2.5, netProfitBaht = 60.0,
            earningsPerKm = 23.2, earningsPerHour = 250.0, smartScore = 86, grade = OrderGrade.S,
            confidenceScore = 0.9f, wasUserCorrected = false, savedToEarnings = false,
            latitude = null, longitude = null, createdAtMillis = lunchTimestamp + 120_000L
        )
        val peaks = RiderIntelligenceEngine.analyzePeakHoursFromHistory(txns, listOf(unsavedOrder))
        assertEquals(1, peaks.size)
        assertEquals(12, peaks.first().hourStart)
        assertEquals(3, peaks.first().orderCount)
        assertEquals(270.0, peaks.first().totalIncomeBaht, 0.01)

        val summary = EarningsSummaryMetrics(
            period = EarningsPeriod.DAILY,
            selectedDateKey = "2026-11-25",
            periodLabelTh = "วันนี้",
            totalIncomeBaht = 1200.0,
            totalExpenseBaht = 220.0,
            netProfitBaht = 980.0,
            totalWorkHours = 5.0,
            totalDistanceKm = 52.0,
            avgIncomePerHour = 196.0,
            avgIncomePerKm = 23.07,
            costPerKm = 4.23,
            jobCount = 14,
            expenseCount = 2,
            goalBaht = 1000.0,
            goalProgressFraction = 1f,
            remainingToGoalBaht = 0.0,
            isGoalReached = true,
            chartBuckets = emptyList(),
            filteredTransactions = emptyList()
        )
        val shiftSummary = RiderIntelligenceEngine.summarizeShiftAndDailyPerformance(
            todaySummary = summary,
            activeSession = WorkSessionRecord("SES", "2026-11-25", now - 3_600_000L, null, 20.0, 6, 480.0, 22.0, true),
            nowMillis = now
        )
        assertEquals(14, shiftSummary.completedJobsToday)
        assertEquals(980.0, shiftSummary.netProfitBaht, 0.01)
    }
}
