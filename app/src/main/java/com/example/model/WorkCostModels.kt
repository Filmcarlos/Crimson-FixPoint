package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Delivery stop item for Feature 2: เรียงลำดับจุดส่งอัตโนมัติ (สูงสุด 15 จุด)
 */
data class DeliveryStopItem(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isDelivered: Boolean = false,
    val sequenceOrder: Int = 0,
    val legDistanceKm: Double? = null
) {
    companion object {
        const val MAX_STOPS = 15
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<DeliveryStopItem>): String {
            return items.joinToString(RECORD_SEP) { item ->
                val safeName = item.name.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                listOf(
                    item.id,
                    safeName,
                    item.latitude.toString(),
                    item.longitude.toString(),
                    item.isDelivered.toString(),
                    item.sequenceOrder.toString(),
                    item.legDistanceKm?.toString() ?: ""
                ).joinToString(FIELD_SEP)
            }
        }

        fun deserializeList(raw: String?): List<DeliveryStopItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 6) return@mapNotNull null
                val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val lng = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val delivered = parts[4].toBooleanStrictOrNull() ?: false
                val seq = parts[5].toIntOrNull() ?: 0
                val legDist = parts.getOrNull(6)?.toDoubleOrNull()
                DeliveryStopItem(
                    id = parts[0].ifBlank { "STOP-${System.currentTimeMillis()}" },
                    name = parts[1].ifBlank { "จุดส่งงาน" },
                    latitude = lat,
                    longitude = lng,
                    isDelivered = delivered,
                    sequenceOrder = seq,
                    legDistanceKm = legDist
                )
            }
        }
    }
}

data class RouteOptimizationResult(
    val orderedStops: List<DeliveryStopItem>,
    val totalPendingDistanceKm: Double
)

/**
 * Nearest Neighbor + 2-opt Route Optimizer using Haversine formula (no external API calls).
 */
object RouteOptimizer {

    fun optimizeStops(
        startLat: Double,
        startLng: Double,
        stops: List<DeliveryStopItem>
    ): RouteOptimizationResult {
        val pending = stops.filter { !it.isDelivered }
        val completed = stops.filter { it.isDelivered }.map {
            it.copy(sequenceOrder = 0, legDistanceKm = null)
        }

        if (pending.isEmpty()) {
            return RouteOptimizationResult(
                orderedStops = completed,
                totalPendingDistanceKm = 0.0
            )
        }

        // Step 1: Nearest Neighbor construction starting from real GPS (startLat, startLng)
        val unvisited = pending.toMutableList()
        val route = mutableListOf<DeliveryStopItem>()
        var curLat = startLat
        var curLng = startLng

        while (unvisited.isNotEmpty()) {
            var bestIdx = 0
            var bestDist = Double.MAX_VALUE
            for (i in unvisited.indices) {
                val candidate = unvisited[i]
                val d = GeoMath.haversineKm(curLat, curLng, candidate.latitude, candidate.longitude)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            val chosen = unvisited.removeAt(bestIdx)
            route.add(chosen)
            curLat = chosen.latitude
            curLng = chosen.longitude
        }

        // Step 2: 2-opt local refinement on the open route anchored at (startLat, startLng)
        if (route.size >= 3) {
            var improved = true
            var iteration = 0
            val maxIterations = 80
            while (improved && iteration < maxIterations) {
                improved = false
                iteration++
                val currentCost = computeRouteDistanceKm(startLat, startLng, route)
                for (i in 0 until route.size - 1) {
                    for (k in i + 1 until route.size) {
                        val candidateRoute = twoOptSwap(route, i, k)
                        val candidateCost = computeRouteDistanceKm(startLat, startLng, candidateRoute)
                        if (candidateCost + 1e-6 < currentCost) {
                            route.clear()
                            route.addAll(candidateRoute)
                            improved = true
                            break
                        }
                    }
                    if (improved) break
                }
            }
        }

        // Step 3: Assign 1-based sequence numbers and per-leg Haversine distances
        var totalKm = 0.0
        var prevLat = startLat
        var prevLng = startLng
        val numberedPending = route.mapIndexed { index, stop ->
            val legKm = GeoMath.haversineKm(prevLat, prevLng, stop.latitude, stop.longitude)
            totalKm += legKm
            prevLat = stop.latitude
            prevLng = stop.longitude
            stop.copy(
                sequenceOrder = index + 1,
                legDistanceKm = legKm
            )
        }

        return RouteOptimizationResult(
            orderedStops = numberedPending + completed,
            totalPendingDistanceKm = totalKm
        )
    }

    fun recalculateCurrentOrderDistances(
        startLat: Double?,
        startLng: Double?,
        stops: List<DeliveryStopItem>
    ): RouteOptimizationResult {
        val pending = stops.filter { !it.isDelivered }
        val completed = stops.filter { it.isDelivered }.map {
            it.copy(sequenceOrder = 0, legDistanceKm = null)
        }
        if (pending.isEmpty()) {
            return RouteOptimizationResult(completed, 0.0)
        }
        var totalKm = 0.0
        var prevLat = startLat ?: pending.first().latitude
        var prevLng = startLng ?: pending.first().longitude
        val updatedPending = pending.mapIndexed { index, stop ->
            val legKm = if (startLat == null && index == 0) {
                0.0
            } else {
                GeoMath.haversineKm(prevLat, prevLng, stop.latitude, stop.longitude)
            }
            totalKm += legKm
            prevLat = stop.latitude
            prevLng = stop.longitude
            stop.copy(
                sequenceOrder = index + 1,
                legDistanceKm = legKm
            )
        }
        return RouteOptimizationResult(updatedPending + completed, totalKm)
    }

    private fun computeRouteDistanceKm(
        startLat: Double,
        startLng: Double,
        route: List<DeliveryStopItem>
    ): Double {
        if (route.isEmpty()) return 0.0
        var sum = GeoMath.haversineKm(startLat, startLng, route[0].latitude, route[0].longitude)
        for (i in 0 until route.size - 1) {
            sum += GeoMath.haversineKm(
                route[i].latitude,
                route[i].longitude,
                route[i + 1].latitude,
                route[i + 1].longitude
            )
        }
        return sum
    }

    private fun twoOptSwap(
        route: List<DeliveryStopItem>,
        i: Int,
        k: Int
    ): List<DeliveryStopItem> {
        val result = ArrayList<DeliveryStopItem>(route.size)
        for (idx in 0 until i) {
            result.add(route[idx])
        }
        for (idx in k downTo i) {
            result.add(route[idx])
        }
        for (idx in k + 1 until route.size) {
            result.add(route[idx])
        }
        return result
    }
}

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

/**
 * Models for Feature 6: เตือนฝนใกล้ตก (Open-Meteo Rain Forecast & Alert)
 */
enum class RainLeadTimeOption(val minutes: Int, val labelTh: String) {
    MIN_15(15, "15 นาที"),
    MIN_30(30, "30 นาที"),
    MIN_60(60, "60 นาที")
}

enum class WeatherForecastStatus {
    IDLE,
    LOADING,
    NO_RAIN,
    RAIN_COMING,
    ERROR
}

data class RainForecastState(
    val alertEnabled: Boolean = true,
    val leadTimeOption: RainLeadTimeOption = RainLeadTimeOption.MIN_30,
    val probabilityThresholdPercent: Int = 50,
    val status: WeatherForecastStatus = WeatherForecastStatus.IDLE,
    val minutesUntilRain: Int? = null,
    val maxRainProbabilityPercent: Int = 0,
    val expectedPrecipitationMm: Double = 0.0,
    val lastUpdatedEpochMillis: Long? = null,
    val lastFetchedLat: Double? = null,
    val lastFetchedLng: Double? = null,
    val lastAlertFiredEpochMillis: Long = 0L,
    val errorMessageTh: String? = null
) {
    val cardHeadlineTh: String
        get() = when (status) {
            WeatherForecastStatus.IDLE -> "รอพิกัด GPS เพื่อตรวจสภาพอากาศ"
            WeatherForecastStatus.LOADING -> "กำลังดึงพยากรณ์อากาศ..."
            WeatherForecastStatus.NO_RAIN -> "ไม่มีฝน"
            WeatherForecastStatus.RAIN_COMING -> {
                val mins = (minutesUntilRain ?: 15).coerceAtLeast(1)
                "ฝนจะตกในอีก $mins นาที โอกาส $maxRainProbabilityPercent%"
            }
            WeatherForecastStatus.ERROR -> "ดึงพยากรณ์ไม่ได้"
        }
}
