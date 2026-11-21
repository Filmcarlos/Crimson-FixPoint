package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Models for Feature 3: คำนวณจุดคุ้มทุนรายวัน (Daily Break-Even & Profit)
 */
data class CustomExpenseItem(
    val id: String,
    val title: String,
    val amountBaht: Double
) {
    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<CustomExpenseItem>): String {
            return items.joinToString(RECORD_SEP) { item ->
                val safeTitle = item.title.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                "${item.id}$FIELD_SEP$safeTitle$FIELD_SEP${item.amountBaht}"
            }
        }

        fun deserializeList(raw: String?): List<CustomExpenseItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 3) return@mapNotNull null
                val amt = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                CustomExpenseItem(
                    id = parts[0],
                    title = parts[1].ifBlank { "ค่าใช้จ่ายอื่นๆ" },
                    amountBaht = amt
                )
            }
        }
    }
}

data class JobIncomeEntry(
    val id: String,
    val label: String,
    val amountBaht: Double,
    val timestampMillis: Long
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date(timestampMillis))
    }

    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<JobIncomeEntry>): String {
            return items.joinToString(RECORD_SEP) { item ->
                val safeLabel = item.label.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                "${item.id}$FIELD_SEP$safeLabel$FIELD_SEP${item.amountBaht}$FIELD_SEP${item.timestampMillis}"
            }
        }

        fun deserializeList(raw: String?): List<JobIncomeEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 4) return@mapNotNull null
                val amt = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val ts = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                JobIncomeEntry(
                    id = parts[0],
                    label = parts[1].ifBlank { "งานวิ่งส่ง" },
                    amountBaht = amt,
                    timestampMillis = ts
                )
            }
        }
    }
}

data class DailyFinancialHistoryItem(
    val dateKey: String,
    val totalCostBaht: Double,
    val totalIncomeBaht: Double,
    val jobCount: Int
) {
    val netProfitBaht: Double
        get() = totalIncomeBaht - totalCostBaht

    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<DailyFinancialHistoryItem>): String {
            return items.joinToString(RECORD_SEP) { item ->
                "${item.dateKey}$FIELD_SEP${item.totalCostBaht}$FIELD_SEP${item.totalIncomeBaht}$FIELD_SEP${item.jobCount}"
            }
        }

        fun deserializeList(raw: String?): List<DailyFinancialHistoryItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 4) return@mapNotNull null
                val cost = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val inc = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val count = parts[3].toIntOrNull() ?: 0
                DailyFinancialHistoryItem(
                    dateKey = parts[0],
                    totalCostBaht = cost,
                    totalIncomeBaht = inc,
                    jobCount = count
                )
            }
        }
    }
}

data class DailyBreakEvenState(
    val currentDateKey: String = todayDateKey(),
    val fuelCostBaht: Double = 0.0,
    val customExpenses: List<CustomExpenseItem> = emptyList(),
    val jobIncomes: List<JobIncomeEntry> = emptyList(),
    val history: List<DailyFinancialHistoryItem> = emptyList()
) {
    val totalCostBaht: Double
        get() = fuelCostBaht + customExpenses.sumOf { it.amountBaht }

    val totalIncomeBaht: Double
        get() = jobIncomes.sumOf { it.amountBaht }

    val remainingToBreakEvenBaht: Double
        get() = max(0.0, totalCostBaht - totalIncomeBaht)

    val netProfitBaht: Double
        get() = max(0.0, totalIncomeBaht - totalCostBaht)

    val hasReachedBreakEven: Boolean
        get() = (totalCostBaht > 0.0 && totalIncomeBaht >= totalCostBaht) ||
            (totalCostBaht <= 0.0 && totalIncomeBaht > 0.0)

    val progressFraction: Float
        get() = when {
            totalCostBaht <= 0.0 -> if (totalIncomeBaht > 0.0) 1.0f else 0.0f
            else -> (totalIncomeBaht / totalCostBaht).toFloat().coerceIn(0.0f, 1.0f)
        }

    companion object {
        fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(nowMillis))
        }
    }
}

/**
 * Models for Feature 4: ประเมินน้ำมันที่เหลือ (Fuel Estimator)
 */
data class FuelEstimateState(
    val tankCapacityLiters: Float = 5.5f,
    val consumptionKmPerLiter: Float = 38.0f,
    val distanceDrivenSinceRefuelKm: Double = 0.0,
    val lowFuelWarningLiters: Float = 1.0f,
    val lowFuelAlertTriggered: Boolean = false
) {
    val usedLiters: Float
        get() = (distanceDrivenSinceRefuelKm / consumptionKmPerLiter.coerceAtLeast(0.1f)).toFloat()

    val remainingLiters: Float
        get() = (tankCapacityLiters - usedLiters).coerceIn(0f, tankCapacityLiters.coerceAtLeast(0.1f))

    val remainingFraction: Float
        get() = if (tankCapacityLiters > 0f) {
            (remainingLiters / tankCapacityLiters).coerceIn(0f, 1f)
        } else {
            0f
        }

    val estimatedRemainingRangeKm: Float
        get() = (remainingLiters * consumptionKmPerLiter).coerceAtLeast(0f)

    val isLowFuel: Boolean
        get() = remainingLiters <= lowFuelWarningLiters
}

/**
 * Models for Feature 5: หมุดปั๊มน้ำมันและจุดพัก (Fuel Stations & Rest Stops + Overpass API)
 */
enum class StopPoiCategory(val labelTh: String) {
    FUEL("ปั๊มน้ำมัน"),
    REST_AREA("จุดพัก")
}

data class StopPoiItem(
    val id: String,
    val name: String,
    val category: StopPoiCategory,
    val latitude: Double,
    val longitude: Double,
    val isUserSaved: Boolean,
    val timestampMillis: Long
) {
    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<StopPoiItem>): String {
            return items.joinToString(RECORD_SEP) { item ->
                val safeName = item.name.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                listOf(
                    item.id,
                    safeName,
                    item.category.name,
                    item.latitude.toString(),
                    item.longitude.toString(),
                    item.isUserSaved.toString(),
                    item.timestampMillis.toString()
                ).joinToString(FIELD_SEP)
            }
        }

        fun deserializeList(raw: String?): List<StopPoiItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 7) return@mapNotNull null
                val cat = runCatching { StopPoiCategory.valueOf(parts[2]) }.getOrDefault(StopPoiCategory.FUEL)
                val lat = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val lng = parts[4].toDoubleOrNull() ?: return@mapNotNull null
                val userSaved = parts[5].toBooleanStrictOrNull() ?: false
                val ts = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                StopPoiItem(
                    id = parts[0],
                    name = parts[1].ifBlank { cat.labelTh },
                    category = cat,
                    latitude = lat,
                    longitude = lng,
                    isUserSaved = userSaved,
                    timestampMillis = ts
                )
            }
        }
    }
}
