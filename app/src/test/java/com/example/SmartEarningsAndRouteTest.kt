package com.example

import com.example.model.DailyFinancialHistoryItem
import com.example.model.DailyWorkMetrics
import com.example.model.EarningsGoals
import com.example.model.EarningsHistoryFilter
import com.example.model.EarningsPeriod
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.SmartEarningsCalculator
import com.example.model.SmartEarningsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEarningsAndRouteTest {

    // =========================================================================
    // Smart Earnings 2.0 Calculations, Date Filters, Goals & Duplicate Guard
    // =========================================================================

    @Test
    fun smartEarnings_dailyWeeklyMonthlyAndKpis_calculateAccurately() {
        // Week of 2026-11-16 (Mon) .. 2026-11-22 (Sun), Month = 2026-11
        val txns = listOf(
            // Selected day: 2026-11-20 (Fri)
            EarningsTransaction("T1", EarningsRecordType.INCOME, "2026-11-20", "งาน #1", "รับงาน", 400.0, distanceKm = 20.0, durationMinutes = 90, timestampMillis = 1000L),
            EarningsTransaction("T2", EarningsRecordType.INCOME, "2026-11-20", "งาน #2", "รับงาน", 200.0, distanceKm = 10.0, durationMinutes = 30, timestampMillis = 2000L),
            EarningsTransaction("T3", EarningsRecordType.EXPENSE, "2026-11-20", "ค่าน้ำมัน", "น้ำมัน", 150.0, timestampMillis = 3000L),
            // Earlier in same week: 2026-11-17 (Tue)
            EarningsTransaction("T4", EarningsRecordType.INCOME, "2026-11-17", "งานอังคาร", "รับงาน", 500.0, distanceKm = 25.0, durationMinutes = 120, timestampMillis = 4000L),
            EarningsTransaction("T5", EarningsRecordType.EXPENSE, "2026-11-17", "ค่าทางด่วน", "ค่าทางด่วน", 50.0, timestampMillis = 5000L),
            // Earlier in same month, different week: 2026-11-05
            EarningsTransaction("T6", EarningsRecordType.INCOME, "2026-11-05", "งานต้นเดือน", "รับงาน", 900.0, distanceKm = 45.0, durationMinutes = 180, timestampMillis = 6000L),
            EarningsTransaction("T7", EarningsRecordType.EXPENSE, "2026-11-05", "เปลี่ยนน้ำมันเครื่อง", "ค่าบำรุงรักษา", 300.0, timestampMillis = 7000L),
            // Previous month (must NOT be counted in 2026-11)
            EarningsTransaction("T8", EarningsRecordType.INCOME, "2026-10-28", "งานเดือนก่อน", "รับงาน", 1000.0, distanceKm = 50.0, durationMinutes = 240, timestampMillis = 8000L)
        )

        val baseState = SmartEarningsState(
            selectedPeriod = EarningsPeriod.DAILY,
            selectedDateKey = "2026-11-20",
            historyFilter = EarningsHistoryFilter.ALL,
            transactions = txns,
            goals = EarningsGoals(dailyGoalBaht = 500.0, weeklyGoalBaht = 1500.0, monthlyGoalBaht = 3000.0)
        )

        // 1. DAILY (2026-11-20): Income = 600, Expense = 150, Net = +450, Hours = 2.0, Km = 30.0
        val daily = SmartEarningsCalculator.computeSummary(baseState, todayKey = "2026-11-20")
        assertEquals(600.0, daily.totalIncomeBaht, 0.001)
        assertEquals(150.0, daily.totalExpenseBaht, 0.001)
        assertEquals(450.0, daily.netProfitBaht, 0.001)
        assertEquals(2.0, daily.totalWorkHours, 0.001)
        assertEquals(30.0, daily.totalDistanceKm, 0.001)
        assertEquals(300.0, daily.avgIncomePerHour, 0.001) // 600 / 2.0
        assertEquals(20.0, daily.avgIncomePerKm, 0.001)    // 600 / 30.0
        assertEquals(5.0, daily.costPerKm, 0.001)          // 150 / 30.0
        assertTrue(daily.isGoalReached)                    // 600 >= 500
        assertEquals(0.0, daily.remainingToGoalBaht, 0.001)
        assertEquals(1.0f, daily.goalProgressFraction, 0.001f)
        assertEquals(7, daily.chartBuckets.size)
        assertEquals(3, daily.filteredTransactions.size)

        // 2. WEEKLY (2026-11-16..2026-11-22): Income = 1100, Expense = 200, Net = +900, Hours = 4.0, Km = 55.0
        val weekly = SmartEarningsCalculator.computeSummary(
            baseState.copy(selectedPeriod = EarningsPeriod.WEEKLY),
            todayKey = "2026-11-20"
        )
        assertEquals(1100.0, weekly.totalIncomeBaht, 0.001)
        assertEquals(200.0, weekly.totalExpenseBaht, 0.001)
        assertEquals(900.0, weekly.netProfitBaht, 0.001)
        assertEquals(4.0, weekly.totalWorkHours, 0.001)
        assertEquals(55.0, weekly.totalDistanceKm, 0.001)
        assertEquals(275.0, weekly.avgIncomePerHour, 0.001) // 1100 / 4.0
        assertEquals(20.0, weekly.avgIncomePerKm, 0.001)    // 1100 / 55.0
        assertEquals(200.0 / 55.0, weekly.costPerKm, 0.001)
        assertFalse(weekly.isGoalReached)                   // 1100 < 1500
        assertEquals(400.0, weekly.remainingToGoalBaht, 0.001)
        assertEquals(7, weekly.chartBuckets.size)

        // 3. MONTHLY (2026-11): Income = 2000, Expense = 500, Net = +1500, Hours = 7.0, Km = 100.0
        val monthly = SmartEarningsCalculator.computeSummary(
            baseState.copy(selectedPeriod = EarningsPeriod.MONTHLY),
            todayKey = "2026-11-20"
        )
        assertEquals(2000.0, monthly.totalIncomeBaht, 0.001)
        assertEquals(500.0, monthly.totalExpenseBaht, 0.001)
        assertEquals(1500.0, monthly.netProfitBaht, 0.001)
        assertEquals(7.0, monthly.totalWorkHours, 0.001)
        assertEquals(100.0, monthly.totalDistanceKm, 0.001)
        assertEquals(20.0, monthly.avgIncomePerKm, 0.001)
        assertEquals(5.0, monthly.costPerKm, 0.001)
        assertTrue(monthly.chartBuckets.size in 4..5)

        // 4. History Filter Chip: EXPENSE only
        val expenseOnly = SmartEarningsCalculator.computeSummary(
            baseState.copy(historyFilter = EarningsHistoryFilter.EXPENSE),
            todayKey = "2026-11-20"
        )
        assertEquals(1, expenseOnly.filteredTransactions.size)
        assertEquals("T3", expenseOnly.filteredTransactions.first().id)
    }

    @Test
    fun smartEarnings_negativeNetProfitAndZeroDivisionSafety_workCorrectly() {
        val state = SmartEarningsState(
            selectedPeriod = EarningsPeriod.DAILY,
            selectedDateKey = "2026-11-20",
            transactions = listOf(
                EarningsTransaction("E1", EarningsRecordType.EXPENSE, "2026-11-20", "ซ่อมยาง", "ค่าบำรุงรักษา", 250.0, timestampMillis = 1000L),
                EarningsTransaction("I1", EarningsRecordType.INCOME, "2026-11-20", "งานสั้น", "รับงาน", 100.0, timestampMillis = 2000L)
            )
        )
        val summary = SmartEarningsCalculator.computeSummary(state, todayKey = "2026-11-20")
        assertEquals(-150.0, summary.netProfitBaht, 0.001)
        assertEquals(0.0, summary.avgIncomePerHour, 0.001)
        assertEquals(0.0, summary.avgIncomePerKm, 0.001)
        assertEquals(0.0, summary.costPerKm, 0.001)
    }

    @Test
    fun smartEarnings_serializationDuplicateGuardAndLegacyHistoryMerge_workCorrectly() {
        val t1 = EarningsTransaction("TX-1", EarningsRecordType.INCOME, "2026-11-20", "ส่งอาหาร", "รับงาน", 120.0, 4.5, 25, 10_000L)
        val duplicateRapid = EarningsTransaction("TX-2", EarningsRecordType.INCOME, "2026-11-20", "ส่งอาหาร", "รับงาน", 120.0, 4.5, 25, 12_000L)
        val distinctLater = EarningsTransaction("TX-3", EarningsRecordType.INCOME, "2026-11-20", "ส่งอาหาร", "รับงาน", 120.0, 4.5, 25, 25_000L)

        assertTrue(EarningsTransaction.isDuplicateCandidate(listOf(t1), duplicateRapid))
        assertFalse(EarningsTransaction.isDuplicateCandidate(listOf(t1), distinctLater))

        val raw = EarningsTransaction.serializeList(listOf(t1, t1, distinctLater))
        val restored = EarningsTransaction.deserializeList(raw)
        assertEquals(2, restored.size)
        assertEquals(4.5, restored[0].distanceKm, 0.001)
        assertEquals(25, restored[0].durationMinutes)

        val metricsRaw = DailyWorkMetrics.serializeMap(mapOf("2026-11-20" to DailyWorkMetrics("2026-11-20", 42.5, 360)))
        val restoredMetrics = DailyWorkMetrics.deserializeMap(metricsRaw)
        assertEquals(42.5, restoredMetrics["2026-11-20"]!!.gpsDistanceKm, 0.001)
        assertEquals(360, restoredMetrics["2026-11-20"]!!.workMinutes)

        // Legacy history should appear for dates without explicit ledger rows, and NOT double-count dates that already have rows
        val legacyHistory = listOf(
            DailyFinancialHistoryItem("2026-11-19", totalCostBaht = 100.0, totalIncomeBaht = 500.0, jobCount = 5),
            DailyFinancialHistoryItem("2026-11-20", totalCostBaht = 999.0, totalIncomeBaht = 999.0, jobCount = 9)
        )
        val state = SmartEarningsState(
            selectedPeriod = EarningsPeriod.WEEKLY,
            selectedDateKey = "2026-11-20",
            transactions = listOf(t1) // only 2026-11-20 has ledger rows (120 Baht)
        )
        val mergedSummary = SmartEarningsCalculator.computeSummary(state, legacyHistory = legacyHistory, todayKey = "2026-11-20")
        assertEquals(620.0, mergedSummary.totalIncomeBaht, 0.001) // 500 from 11-19 legacy + 120 from 11-20 ledger
        assertEquals(100.0, mergedSummary.totalExpenseBaht, 0.001) // 100 from 11-19 legacy only
    }
}
