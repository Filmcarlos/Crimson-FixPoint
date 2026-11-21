package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VehicleType(val labelTh: String) {
    MOTORCYCLE("มอเตอร์ไซค์"),
    EV_BIKE("มอเตอร์ไซค์ไฟฟ้า (EV)"),
    CAR("รถยนต์")
}

data class RiderProfileConfig(
    val riderName: String = "Rider FixPoint",
    val vehicleModel: String = "Honda Wave 125i",
    val vehicleType: VehicleType = VehicleType.MOTORCYCLE,
    val licensePlate: String = "",
    val fuelPricePerLiterBaht: Float = 38.5f,
    val maintenanceBahtPerKm: Float = 0.65f,
    val minAcceptableBahtPerKm: Float = 12.0f,
    val minAcceptableBahtPerHour: Float = 150.0f
)

data class PrivacyPreferences(
    val localOnlyMode: Boolean = true,
    val autoDeleteScreenshots: Boolean = true,
    val storeRawOcrText: Boolean = false,
    val screenReaderConsentAccepted: Boolean = false,
    val lowFuelAlertEnabled: Boolean = true,
    val sessionReminderEnabled: Boolean = true
)

data class WorkSessionRecord(
    val id: String,
    val dateKey: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val distanceKm: Double = 0.0,
    val completedOrders: Int = 0,
    val grossIncomeBaht: Double = 0.0,
    val fuelCostBaht: Double = 0.0,
    val isActive: Boolean = false
) {
    fun durationMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
        val end = endTimeMillis ?: nowMillis
        val diffMs = (end - startTimeMillis).coerceAtLeast(0L)
        return (diffMs / 60_000L).toInt()
    }

    fun formattedStartTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date(startTimeMillis))
    }
}

enum class OrderInputSource(val labelTh: String) {
    SCREEN_READER("Screen Reader (MediaProjection)"),
    SCREENSHOT_IMPORT("นำเข้าภาพหน้าจอ (Photo Picker)"),
    SHARED_IMAGE("แชร์ภาพจากแอปอื่น"),
    MANUAL_OR_TEXT("กรอก/วางข้อความเอง")
}

enum class ScreenReaderState(val labelTh: String) {
    IDLE("พร้อมใช้งาน (ยังไม่เปิดจับภาพ)"),
    ACTIVE_READY("กำลังเปิด Screen Reader (จับภาพเมื่อกด)"),
    CAPTURING("กำลังจับภาพหน้าจอและวิเคราะห์ OCR..."),
    PROTECTED_SCREEN("หน้าจอถูกป้องกัน (FLAG_SECURE)"),
    PERMISSION_DENIED("ไม่ได้รับสิทธิ์จับภาพหน้าจอ"),
    ERROR("เกิดข้อผิดพลาดในการอ่านหน้าจอ")
}

/**
 * Editable draft extracted via ML Kit OCR or manual input.
 * Strict rule: Missing fields stay null and are NEVER fabricated.
 */
data class ParsedOrderDraft(
    val source: OrderInputSource = OrderInputSource.MANUAL_OR_TEXT,
    val rawOcrText: String = "",
    val payoutBaht: Double? = null,
    val payoutConfidence: Float = 0f,
    val distanceKm: Double? = null,
    val distanceConfidence: Float = 0f,
    val estimatedMinutes: Int? = null,
    val durationConfidence: Float = 0f,
    val deadMileageKm: Double? = null,
    val pickupName: String? = null,
    val dropoffName: String? = null,
    val platformHint: String? = null,
    val overallConfidence: Float = 0f,
    val missingFields: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val wasUserCorrected: Boolean = false
)

enum class OrderGrade(val label: String, val verdictTh: String) {
    S("S", "คุ้มค่ามาก แนะนำรับงาน"),
    A("A", "คุ้มค่า น่ารับงาน"),
    B("B", "พอใช้ได้ พิจารณาก่อนรับ"),
    C("C", "กำไรต่ำ ระวังเสียเวลา"),
    D("D", "ไม่คุ้มค่า แนะนำข้าม")
}

data class OrderAnalysisResult(
    val smartScore: Int,
    val grade: OrderGrade,
    val confidenceScore: Float,
    val grossPayoutBaht: Double,
    val totalDistanceKm: Double,
    val tripDistanceKm: Double,
    val deadMileageKm: Double,
    val estimatedMinutes: Int?,
    val fuelCostBaht: Double,
    val maintenanceCostBaht: Double,
    val netProfitBaht: Double,
    val earningsPerKm: Double,
    val earningsPerHour: Double?,
    val deadMileageRatio: Float,
    val explanations: List<String>,
    val warnings: List<String>,
    val isIncompleteData: Boolean
)

data class AnalyzedOrderRecord(
    val id: String,
    val dateKey: String,
    val source: OrderInputSource,
    val pickupName: String,
    val dropoffName: String,
    val payoutBaht: Double,
    val distanceKm: Double,
    val deadMileageKm: Double,
    val estimatedMinutes: Int,
    val fuelCostBaht: Double,
    val netProfitBaht: Double,
    val earningsPerKm: Double,
    val earningsPerHour: Double,
    val smartScore: Int,
    val grade: OrderGrade,
    val confidenceScore: Float,
    val wasUserCorrected: Boolean,
    val savedToEarnings: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val createdAtMillis: Long
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        return sdf.format(Date(createdAtMillis))
    }
}

data class HotspotRecommendation(
    val id: String,
    val name: String,
    val category: PinCategory,
    val latitude: Double,
    val longitude: Double,
    val distanceFromRiderKm: Double?,
    val historicalAvgPayoutBaht: Double,
    val historicalOrderCount: Int,
    val estimatedHeatScore: Float,
    val isHistoricalEstimate: Boolean = true,
    val reasonTh: String
)

enum class RecommendationPriority {
    CRITICAL,
    HIGH,
    INFO
}

data class AiRecommendationCard(
    val id: String,
    val titleTh: String,
    val subtitleTh: String,
    val badgeText: String,
    val priority: RecommendationPriority,
    val targetScreen: AppScreen
)

data class ExpenseCategoryBreakdown(
    val category: String,
    val amountBaht: Double,
    val fraction: Float
)

data class FuelIntelligenceSummary(
    val fuelCostPerKmBaht: Double,
    val remainingLiters: Float,
    val remainingRangeKm: Float,
    val tankFillFraction: Float,
    val estimatedRefillCostBaht: Double,
    val isLowFuel: Boolean,
    val adviceTh: String
)

data class MapLayerFilters(
    val showFixPoints: Boolean = true,
    val showHotzones: Boolean = true,
    val showRestaurants: Boolean = true,
    val showHeatmap: Boolean = true,
    val showZoneRadius: Boolean = true,
    val showFuelStations: Boolean = true
)
