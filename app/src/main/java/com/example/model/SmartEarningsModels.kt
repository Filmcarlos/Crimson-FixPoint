package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Smart Earnings 2.0 — Period Filter (Daily / Weekly / Monthly).
 */
enum class EarningsPeriod(val labelTh: String) {
    DAILY("รายวัน"),
    WEEKLY("รายสัปดาห์"),
    MONTHLY("รายเดือน")
}

/**
 * Smart Earnings 2.0 — Transaction Type (Job Income vs Expense).
 */
enum class EarningsRecordType(val labelTh: String) {
    INCOME("รายได้"),
    EXPENSE("ค่าใช้จ่าย")
}

/**
 * Smart Earnings 2.0 — History Filter Chip.
 */
enum class EarningsHistoryFilter(val labelTh: String) {
    ALL("ทั้งหมด"),
    INCOME("รายได้"),
    EXPENSE("ค่าใช้จ่าย")
}

/**
 * Unified Income & Expense transaction record for Smart Earnings 2.0.
 * Compatible with existing DailyBreakEvenState entries and persisted locally.
 */
data class EarningsTransaction(
    val id: String,
    val type: EarningsRecordType,
    val dateKey: String,
    val title: String,
    val category: String,
    val amountBaht: Double,
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date(timestampMillis))
    }

    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"
        const val DUPLICATE_WINDOW_MS = 5_000L

        fun serializeList(items: List<EarningsTransaction>): String {
            return items.distinctBy { it.id }.joinToString(RECORD_SEP) { item ->
                val safeTitle = item.title.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                val safeCat = item.category.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                listOf(
                    item.id,
                    item.type.name,
                    item.dateKey,
                    safeTitle,
                    safeCat,
                    item.amountBaht.toString(),
                    item.distanceKm.toString(),
                    item.durationMinutes.toString(),
                    item.timestampMillis.toString()
                ).joinToString(FIELD_SEP)
            }
        }

        fun deserializeList(raw: String?): List<EarningsTransaction> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 9) return@mapNotNull null
                val type = runCatching { EarningsRecordType.valueOf(parts[1]) }.getOrNull()
                    ?: return@mapNotNull null
                val dateKey = parts[2].takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                    ?: return@mapNotNull null
                val amt = parts[5].toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return@mapNotNull null
                val dist = parts[6].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val dur = parts[7].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val ts = parts[8].toLongOrNull() ?: System.currentTimeMillis()
                EarningsTransaction(
                    id = parts[0].ifBlank { return@mapNotNull null },
                    type = type,
                    dateKey = dateKey,
                    title = parts[3].ifBlank { if (type == EarningsRecordType.INCOME) "รายได้รับงาน" else "ค่าใช้จ่าย" },
                    category = parts[4].ifBlank { if (type == EarningsRecordType.INCOME) "รับงาน" else "ทั่วไป" },
                    amountBaht = amt,
                    distanceKm = dist,
                    durationMinutes = dur,
                    timestampMillis = ts
                )
            }.distinctBy { it.id }
        }

        /**
         * Detects exact ID duplicates or accidental rapid double-submissions of the same entry.
         */
        fun isDuplicateCandidate(
            existing: List<EarningsTransaction>,
            candidate: EarningsTransaction
        ): Boolean {
            val normalizedTitle = candidate.title.trim().lowercase(Locale.ROOT)
            val normalizedCategory = candidate.category.trim().lowercase(Locale.ROOT)
            return existing.any { item ->
                if (item.id == candidate.id) return@any true
                item.type == candidate.type &&
                    item.dateKey == candidate.dateKey &&
                    item.title.trim().lowercase(Locale.ROOT) == normalizedTitle &&
                    item.category.trim().lowercase(Locale.ROOT) == normalizedCategory &&
                    abs(item.amountBaht - candidate.amountBaht) < 0.001 &&
                    abs(item.timestampMillis - candidate.timestampMillis) <= DUPLICATE_WINDOW_MS
            }
        }
    }
}

/**
 * Daily tracked GPS distance & work duration for accurate per-hour and per-km metrics.
 */
data class DailyWorkMetrics(
    val dateKey: String,
    val gpsDistanceKm: Double = 0.0,
    val workMinutes: Int = 0
) {
    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeMap(map: Map<String, DailyWorkMetrics>): String {
            return map.values.joinToString(RECORD_SEP) { item ->
                "${item.dateKey}$FIELD_SEP${item.gpsDistanceKm}$FIELD_SEP${item.workMinutes}"
            }
        }

        fun deserializeMap(raw: String?): Map<String, DailyWorkMetrics> {
            if (raw.isNullOrBlank()) return emptyMap()
            val result = linkedMapOf<String, DailyWorkMetrics>()
            raw.split(RECORD_SEP).forEach { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 3) return@forEach
                val key = parts[0]
                if (!key.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return@forEach
                val dist = parts[1].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val mins = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0
                result[key] = DailyWorkMetrics(
                    dateKey = key,
                    gpsDistanceKm = dist,
                    workMinutes = mins
                )
            }
            return result
        }
    }
}

/**
 * Income Goals (Daily / Weekly / Monthly).
 */
data class EarningsGoals(
    val dailyGoalBaht: Double = 1000.0,
    val weeklyGoalBaht: Double = 6000.0,
    val monthlyGoalBaht: Double = 24000.0
) {
    fun goalForPeriod(period: EarningsPeriod): Double = when (period) {
        EarningsPeriod.DAILY -> dailyGoalBaht
        EarningsPeriod.WEEKLY -> weeklyGoalBaht
        EarningsPeriod.MONTHLY -> monthlyGoalBaht
    }
}

/**
 * Single bar group for the Income vs Expense summary chart.
 */
data class EarningsChartBucket(
    val label: String,
    val dateRangeKey: String,
    val incomeBaht: Double,
    val expenseBaht: Double,
    val isHighlighted: Boolean = false
)

/**
 * Computed KPI snapshot for Smart Earnings 2.0 Dashboard.
 */
data class EarningsSummaryMetrics(
    val period: EarningsPeriod,
    val selectedDateKey: String,
    val periodLabelTh: String,
    val totalIncomeBaht: Double,
    val totalExpenseBaht: Double,
    val netProfitBaht: Double,
    val totalWorkHours: Double,
    val totalDistanceKm: Double,
    val avgIncomePerHour: Double,
    val avgIncomePerKm: Double,
    val costPerKm: Double,
    val jobCount: Int,
    val expenseCount: Int,
    val goalBaht: Double,
    val goalProgressFraction: Float,
    val remainingToGoalBaht: Double,
    val isGoalReached: Boolean,
    val chartBuckets: List<EarningsChartBucket>,
    val filteredTransactions: List<EarningsTransaction>
)

/**
 * State container held inside FixPointUiState for Smart Earnings 2.0.
 */
data class SmartEarningsState(
    val selectedPeriod: EarningsPeriod = EarningsPeriod.DAILY,
    val selectedDateKey: String = DailyBreakEvenState.todayDateKey(),
    val historyFilter: EarningsHistoryFilter = EarningsHistoryFilter.ALL,
    val transactions: List<EarningsTransaction> = emptyList(),
    val dailyWorkMetrics: Map<String, DailyWorkMetrics> = emptyMap(),
    val goals: EarningsGoals = EarningsGoals()
)

/**
 * Pure, deterministic date & financial calculation engine for Smart Earnings 2.0.
 * Uses java.util.Calendar so it is 100% compatible with minSdk 24 and local JVM tests.
 */
object SmartEarningsCalculator {

    private val THAI_MONTHS_SHORT = arrayOf(
        "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
    )

    private val THAI_DAYS_SHORT = arrayOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")

    fun parseDateKey(dateKey: String): Calendar {
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val parts = dateKey.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m - 1)
                cal.set(Calendar.DAY_OF_MONTH, d)
            }
        }
        return cal
    }

    fun formatDateKey(cal: Calendar): String {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun shiftDateKey(dateKey: String, period: EarningsPeriod, delta: Int): String {
        val cal = parseDateKey(dateKey)
        when (period) {
            EarningsPeriod.DAILY -> cal.add(Calendar.DAY_OF_MONTH, delta)
            EarningsPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, delta * 7)
            EarningsPeriod.MONTHLY -> cal.add(Calendar.MONTH, delta)
        }
        return formatDateKey(cal)
    }

    fun getWeekDateKeys(anchorDateKey: String): List<String> {
        val cal = parseDateKey(anchorDateKey)
        // Monday as start of week
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val offsetFromMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dayOfWeek)
        cal.add(Calendar.DAY_OF_MONTH, offsetFromMonday)
        return (0 until 7).map {
            val key = formatDateKey(cal)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            key
        }
    }

    fun getMonthDateKeys(anchorDateKey: String): List<String> {
        val cal = parseDateKey(anchorDateKey)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return (0 until daysInMonth).map {
            val key = formatDateKey(cal)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            key
        }
    }

    fun getTrailingSevenDateKeys(endDateKey: String): List<String> {
        val cal = parseDateKey(endDateKey)
        cal.add(Calendar.DAY_OF_MONTH, -6)
        return (0 until 7).map {
            val key = formatDateKey(cal)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            key
        }
    }

    fun formatPeriodLabelTh(period: EarningsPeriod, selectedDateKey: String): String {
        val cal = parseDateKey(selectedDateKey)
        val yearBe = cal.get(Calendar.YEAR) + 543
        val monthShort = THAI_MONTHS_SHORT[cal.get(Calendar.MONTH).coerceIn(0, 11)]
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return when (period) {
            EarningsPeriod.DAILY -> "$day $monthShort $yearBe ($selectedDateKey)"
            EarningsPeriod.WEEKLY -> {
                val weekKeys = getWeekDateKeys(selectedDateKey)
                val startCal = parseDateKey(weekKeys.first())
                val endCal = parseDateKey(weekKeys.last())
                val sMonth = THAI_MONTHS_SHORT[startCal.get(Calendar.MONTH).coerceIn(0, 11)]
                val eMonth = THAI_MONTHS_SHORT[endCal.get(Calendar.MONTH).coerceIn(0, 11)]
                "${startCal.get(Calendar.DAY_OF_MONTH)} $sMonth – ${endCal.get(Calendar.DAY_OF_MONTH)} $eMonth ${endCal.get(Calendar.YEAR) + 543}"
            }
            EarningsPeriod.MONTHLY -> "$monthShort $yearBe"
        }
    }

    /**
     * Computes complete Smart Earnings 2.0 KPIs, chart buckets, and filtered history.
     * Seamlessly incorporates legacy DailyFinancialHistoryItem entries for dates without explicit transactions.
     */
    fun computeSummary(
        state: SmartEarningsState,
        legacyHistory: List<DailyFinancialHistoryItem> = emptyList(),
        todayKey: String = DailyBreakEvenState.todayDateKey(),
        todayLiveFuelDistanceKm: Double = 0.0
    ): EarningsSummaryMetrics {
        val activeDateKeys: List<String> = when (state.selectedPeriod) {
            EarningsPeriod.DAILY -> listOf(state.selectedDateKey)
            EarningsPeriod.WEEKLY -> getWeekDateKeys(state.selectedDateKey)
            EarningsPeriod.MONTHLY -> getMonthDateKeys(state.selectedDateKey)
        }
        val activeDateSet = activeDateKeys.toSet()

        // Deduplicate transactions by ID and synthesize fallback records from legacyHistory if a date has no ledger rows
        val ledgerByDate = state.transactions.distinctBy { it.id }.groupBy { it.dateKey }
        val combinedTransactions = buildList {
            addAll(state.transactions.distinctBy { it.id })
            legacyHistory.forEach { h ->
                if (ledgerByDate[h.dateKey].isNullOrEmpty()) {
                    val anchorTs = parseDateKey(h.dateKey).timeInMillis
                    if (h.totalIncomeBaht > 0.0) {
                        add(
                            EarningsTransaction(
                                id = "LEGACY-HIST-INC-${h.dateKey}",
                                type = EarningsRecordType.INCOME,
                                dateKey = h.dateKey,
                                title = "รายได้รวมย้อนหลัง (${h.jobCount} งาน)",
                                category = "รับงาน",
                                amountBaht = h.totalIncomeBaht,
                                timestampMillis = anchorTs
                            )
                        )
                    }
                    if (h.totalCostBaht > 0.0) {
                        add(
                            EarningsTransaction(
                                id = "LEGACY-HIST-COST-${h.dateKey}",
                                type = EarningsRecordType.EXPENSE,
                                dateKey = h.dateKey,
                                title = "ต้นทุนรวมย้อนหลัง",
                                category = "ต้นทุน",
                                amountBaht = h.totalCostBaht,
                                timestampMillis = anchorTs
                            )
                        )
                    }
                }
            }
        }

        val periodTxns = combinedTransactions
            .filter { it.dateKey in activeDateSet }
            .sortedByDescending { it.timestampMillis }

        val incomeTxns = periodTxns.filter { it.type == EarningsRecordType.INCOME }
        val expenseTxns = periodTxns.filter { it.type == EarningsRecordType.EXPENSE }

        val totalIncome = incomeTxns.sumOf { it.amountBaht }
        val totalExpense = expenseTxns.sumOf { it.amountBaht }
        val netProfit = totalIncome - totalExpense

        // Compute distance & work hours per day without double-counting GPS vs job-logged entries
        var totalDistanceKm = 0.0
        var totalWorkMinutes = 0
        val periodByDate = periodTxns.groupBy { it.dateKey }
        for (dateKey in activeDateKeys) {
            val dayTxns = periodByDate[dateKey].orEmpty()
            val txDist = dayTxns.sumOf { it.distanceKm }
            val txMins = dayTxns.sumOf { it.durationMinutes }
            val dayMetric = state.dailyWorkMetrics[dateKey]
            val trackedGpsKm = max(
                dayMetric?.gpsDistanceKm ?: 0.0,
                if (dateKey == todayKey) todayLiveFuelDistanceKm else 0.0
            )
            val trackedMins = dayMetric?.workMinutes ?: 0
            totalDistanceKm += max(txDist, trackedGpsKm)
            totalWorkMinutes += max(txMins, trackedMins)
        }

        val totalWorkHours = totalWorkMinutes / 60.0
        val avgIncomePerHour = if (totalWorkHours > 0.0) totalIncome / totalWorkHours else 0.0
        val avgIncomePerKm = if (totalDistanceKm > 0.0) totalIncome / totalDistanceKm else 0.0
        val costPerKm = if (totalDistanceKm > 0.0) totalExpense / totalDistanceKm else 0.0

        val goalBaht = state.goals.goalForPeriod(state.selectedPeriod).coerceAtLeast(0.0)
        val goalProgressFraction = if (goalBaht > 0.0) {
            (totalIncome / goalBaht).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val remainingToGoal = max(0.0, goalBaht - totalIncome)
        val isGoalReached = goalBaht > 0.0 && totalIncome >= goalBaht

        // Chart buckets
        val allByDate = combinedTransactions.groupBy { it.dateKey }
        val chartBuckets: List<EarningsChartBucket> = when (state.selectedPeriod) {
            EarningsPeriod.DAILY -> {
                getTrailingSevenDateKeys(state.selectedDateKey).map { dk ->
                    val c = parseDateKey(dk)
                    val label = "${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}"
                    val rows = allByDate[dk].orEmpty()
                    EarningsChartBucket(
                        label = label,
                        dateRangeKey = dk,
                        incomeBaht = rows.filter { it.type == EarningsRecordType.INCOME }.sumOf { it.amountBaht },
                        expenseBaht = rows.filter { it.type == EarningsRecordType.EXPENSE }.sumOf { it.amountBaht },
                        isHighlighted = dk == state.selectedDateKey
                    )
                }
            }
            EarningsPeriod.WEEKLY -> {
                activeDateKeys.map { dk ->
                    val c = parseDateKey(dk)
                    val dow = c.get(Calendar.DAY_OF_WEEK) - 1
                    val label = THAI_DAYS_SHORT[dow.coerceIn(0, 6)]
                    val rows = allByDate[dk].orEmpty()
                    EarningsChartBucket(
                        label = label,
                        dateRangeKey = dk,
                        incomeBaht = rows.filter { it.type == EarningsRecordType.INCOME }.sumOf { it.amountBaht },
                        expenseBaht = rows.filter { it.type == EarningsRecordType.EXPENSE }.sumOf { it.amountBaht },
                        isHighlighted = dk == state.selectedDateKey
                    )
                }
            }
            EarningsPeriod.MONTHLY -> {
                // Group days of month into 4–5 weekly buckets (1-7, 8-14, 15-21, 22-28, 29+)
                activeDateKeys.chunked(7).mapIndexed { idx, chunk ->
                    val rows = chunk.flatMap { dk -> allByDate[dk].orEmpty() }
                    EarningsChartBucket(
                        label = "ส.${idx + 1}",
                        dateRangeKey = "${chunk.first()}..${chunk.last()}",
                        incomeBaht = rows.filter { it.type == EarningsRecordType.INCOME }.sumOf { it.amountBaht },
                        expenseBaht = rows.filter { it.type == EarningsRecordType.EXPENSE }.sumOf { it.amountBaht },
                        isHighlighted = state.selectedDateKey in chunk
                    )
                }
            }
        }

        val filteredHistory = when (state.historyFilter) {
            EarningsHistoryFilter.ALL -> periodTxns
            EarningsHistoryFilter.INCOME -> incomeTxns
            EarningsHistoryFilter.EXPENSE -> expenseTxns
        }

        return EarningsSummaryMetrics(
            period = state.selectedPeriod,
            selectedDateKey = state.selectedDateKey,
            periodLabelTh = formatPeriodLabelTh(state.selectedPeriod, state.selectedDateKey),
            totalIncomeBaht = totalIncome,
            totalExpenseBaht = totalExpense,
            netProfitBaht = netProfit,
            totalWorkHours = totalWorkHours,
            totalDistanceKm = totalDistanceKm,
            avgIncomePerHour = avgIncomePerHour,
            avgIncomePerKm = avgIncomePerKm,
            costPerKm = costPerKm,
            jobCount = incomeTxns.size,
            expenseCount = expenseTxns.size,
            goalBaht = goalBaht,
            goalProgressFraction = goalProgressFraction,
            remainingToGoalBaht = remainingToGoal,
            isGoalReached = isGoalReached,
            chartBuckets = chartBuckets,
            filteredTransactions = filteredHistory
        )
    }
}
