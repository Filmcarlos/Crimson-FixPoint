package com.example

import com.example.ai.OrderOcrParser
import com.example.ai.RiderIntelligenceEngine
import com.example.model.AnalyzedOrderRecord
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.FuelEstimateState
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft
import com.example.model.PinCategory
import com.example.model.RiderProfileConfig
import com.example.model.SavedLocationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAiAndOcrUnitTest {

    @Test
    fun ocrParser_completeThaiOrder_extractsAllFieldsAccurately() {
        val sampleText = """
            GrabFood
            ค่าส่ง: ฿85.50
            ไปรับ 0.9 กม.
            รวม 4.5 กม.
            เวลาประมาณ 22 นาที
            ร้าน: ข้าวมันไก่ประตูน้ำ
            ส่ง: คอนโดอโศก ทาวเวอร์
        """.trimIndent()

        val draft = OrderOcrParser.parseOrderText(sampleText, OrderInputSource.SCREENSHOT_IMPORT)
        assertEquals("Grab", draft.platformHint)
        assertEquals(85.5, draft.payoutBaht ?: 0.0, 0.001)
        assertEquals(4.5, draft.distanceKm ?: 0.0, 0.001)
        assertEquals(0.9, draft.deadMileageKm ?: 0.0, 0.001)
        assertEquals(22, draft.estimatedMinutes)
        assertEquals("ข้าวมันไก่ประตูน้ำ", draft.pickupName)
        assertEquals("คอนโดอโศก ทาวเวอร์", draft.dropoffName)
        assertTrue(draft.missingFields.isEmpty())
        assertTrue(draft.overallConfidence > 0.75f)
    }

    @Test
    fun ocrParser_partialOrder_neverFabricatesMissingFields() {
        val partialText = "LINE MAN รายได้ ฿62"
        val draft = OrderOcrParser.parseOrderText(partialText, OrderInputSource.SCREEN_READER)

        assertEquals("LINE MAN", draft.platformHint)
        assertEquals(62.0, draft.payoutBaht ?: 0.0, 0.001)
        // Must remain null — NEVER fabricate distance or duration
        assertNull(draft.distanceKm)
        assertNull(draft.estimatedMinutes)
        assertNull(draft.deadMileageKm)
        assertTrue(draft.missingFields.contains("ระยะทาง (กม.)"))
        assertTrue(draft.missingFields.contains("เวลาโดยประมาณ (นาที)"))
        assertTrue(draft.warnings.isNotEmpty())
    }

    @Test
    fun ocrParser_emptyOrGarbageText_returnsZeroConfidenceAndWarnings() {
        val emptyDraft = OrderOcrParser.parseOrderText("   ", OrderInputSource.MANUAL_OR_TEXT)
        assertNull(emptyDraft.payoutBaht)
        assertNull(emptyDraft.distanceKm)
        assertEquals(0f, emptyDraft.overallConfidence, 0.001f)
        assertEquals(3, emptyDraft.missingFields.size)
        assertTrue(emptyDraft.warnings.isNotEmpty())
    }

    @Test
    fun riderIntelligence_highYieldVsHighDeadMileageOrders_scoreDifferently() {
        val profile = RiderProfileConfig(
            fuelPricePerLiterBaht = 40.0f,
            maintenanceBahtPerKm = 0.5f,
            minAcceptableBahtPerKm = 12.0f,
            minAcceptableBahtPerHour = 150.0f
        )
        val fuel = FuelEstimateState(
            tankCapacityLiters = 5.5f,
            consumptionKmPerLiter = 40.0f, // 1.0 Baht/km fuel
            distanceDrivenSinceRefuelKm = 20.0
        )

        val greatOrder = ParsedOrderDraft(
            payoutBaht = 95.0,
            distanceKm = 3.0,
            deadMileageKm = 0.2,
            estimatedMinutes = 15,
            overallConfidence = 0.95f
        )
        val poorOrder = ParsedOrderDraft(
            payoutBaht = 35.0,
            distanceKm = 5.0,
            deadMileageKm = 5.0, // 50% dead mileage
            estimatedMinutes = 40,
            overallConfidence = 0.90f
        )

        val greatResult = RiderIntelligenceEngine.analyzeOrder(greatOrder, profile, fuel)
        val poorResult = RiderIntelligenceEngine.analyzeOrder(poorOrder, profile, fuel)

        assertNotNull(greatResult)
        assertNotNull(poorResult)
        assertTrue(greatResult!!.smartScore >= 80)
        assertTrue(greatResult.grade == OrderGrade.S || greatResult.grade == OrderGrade.A)
        assertTrue(greatResult.netProfitBaht > 85.0)

        assertTrue(poorResult!!.smartScore < 45)
        assertTrue(poorResult.grade == OrderGrade.C || poorResult.grade == OrderGrade.D)
        assertEquals(0.5f, poorResult.deadMileageRatio, 0.01f)
        assertTrue(poorResult.warnings.any { it.contains("ระยะวิ่งเปล่า") })
    }

    @Test
    fun riderIntelligence_missingDuration_returnsNullEarningsPerHourWithoutFabricating() {
        val draft = ParsedOrderDraft(
            payoutBaht = 70.0,
            distanceKm = 4.0,
            estimatedMinutes = null
        )
        val result = RiderIntelligenceEngine.analyzeOrder(
            draft,
            RiderProfileConfig(),
            FuelEstimateState()
        )
        assertNotNull(result)
        assertNull(result!!.earningsPerHour)
        assertTrue(result.isIncompleteData)
    }

    @Test
    fun riderIntelligence_hotspots_alwaysMarkedAsHistoricalEstimates() {
        val pins = listOf(
            SavedLocationItem("P1", "โซนสยาม", 13.7465, 100.5347, null, null, 1000L, PinCategory.HOTZONE, 12, 78.0),
            SavedLocationItem("P2", "ร้านไก่ทอด", 13.7500, 100.5360, null, null, 2000L, PinCategory.RESTAURANT, 3, 55.0)
        )
        val orders = listOf(
            AnalyzedOrderRecord(
                id = "O1", dateKey = "2026-11-21", source = OrderInputSource.MANUAL_OR_TEXT,
                pickupName = "สยาม", dropoffName = "สีลม", payoutBaht = 90.0, distanceKm = 3.5,
                deadMileageKm = 0.5, estimatedMinutes = 18, fuelCostBaht = 4.0, netProfitBaht = 83.0,
                earningsPerKm = 22.5, earningsPerHour = 276.0, smartScore = 90, grade = OrderGrade.S,
                confidenceScore = 0.95f, wasUserCorrected = false, savedToEarnings = true,
                latitude = 13.7466, longitude = 100.5348, createdAtMillis = 5000L
            )
        )

        val hotspots = RiderIntelligenceEngine.computeHotspotRecommendations(
            savedPins = pins,
            analyzedOrders = orders,
            riderLat = 13.7460,
            riderLng = 100.5340
        )
        assertEquals(2, hotspots.size)
        assertEquals("P1", hotspots.first().id)
        assertTrue(hotspots.all { it.isHistoricalEstimate })
        assertFalse(hotspots.first().reasonTh.isBlank())
    }

    @Test
    fun riderIntelligence_expenseBreakdown_sumsToOneHundredPercent() {
        val txns = listOf(
            EarningsTransaction("E1", EarningsRecordType.EXPENSE, "2026-11-21", "E20", "น้ำมัน", 150.0),
            EarningsTransaction("E2", EarningsRecordType.EXPENSE, "2026-11-21", "กาแฟ", "อาหาร", 50.0),
            EarningsTransaction("I1", EarningsRecordType.INCOME, "2026-11-21", "งาน #1", "รับงาน", 400.0)
        )
        val breakdown = RiderIntelligenceEngine.computeExpenseBreakdown(txns)
        assertEquals(2, breakdown.size)
        assertEquals("น้ำมัน", breakdown[0].category)
        assertEquals(0.75f, breakdown[0].fraction, 0.001f)
        assertEquals(0.25f, breakdown[1].fraction, 0.001f)
    }
}
