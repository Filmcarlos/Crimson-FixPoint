package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AnalyzedOrderRecord
import com.example.model.DailyWorkMetrics
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.PinCategory
import com.example.model.SavedLocationItem
import com.example.model.WorkSessionRecord

@Entity(tableName = "saved_pins")
data class SavedPinEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Float? = null,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Double? = null,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pin_category", defaultValue = "FIXPOINT") val pinCategory: String = PinCategory.FIXPOINT.name,
    @ColumnInfo(name = "historical_order_count", defaultValue = "0") val historicalOrderCount: Int = 0,
    @ColumnInfo(name = "historical_avg_payout", defaultValue = "0.0") val historicalAvgPayout: Double = 0.0,
    @ColumnInfo(name = "notes", defaultValue = "") val notes: String = ""
) {
    fun toDomain(): SavedLocationItem = SavedLocationItem(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        altitudeMeters = altitudeMeters,
        timestampMillis = timestampMillis,
        category = runCatching { PinCategory.valueOf(pinCategory) }.getOrDefault(PinCategory.FIXPOINT),
        historicalOrderCount = historicalOrderCount,
        historicalAvgPayoutBaht = historicalAvgPayout,
        notes = notes
    )

    companion object {
        fun fromDomain(item: SavedLocationItem): SavedPinEntity = SavedPinEntity(
            id = item.id,
            name = item.name,
            latitude = item.latitude,
            longitude = item.longitude,
            accuracyMeters = item.accuracyMeters,
            altitudeMeters = item.altitudeMeters,
            timestampMillis = item.timestampMillis,
            pinCategory = item.category.name,
            historicalOrderCount = item.historicalOrderCount,
            historicalAvgPayout = item.historicalAvgPayoutBaht,
            notes = item.notes
        )
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "date_key") val dateKey: String,
    val title: String,
    val category: String,
    @ColumnInfo(name = "amount_baht") val amountBaht: Double,
    @ColumnInfo(name = "distance_km") val distanceKm: Double = 0.0,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 0,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): EarningsTransaction = EarningsTransaction(
        id = id,
        type = runCatching { EarningsRecordType.valueOf(type) }.getOrDefault(EarningsRecordType.INCOME),
        dateKey = dateKey,
        title = title,
        category = category,
        amountBaht = amountBaht,
        distanceKm = distanceKm,
        durationMinutes = durationMinutes,
        timestampMillis = timestampMillis
    )

    companion object {
        fun fromDomain(item: EarningsTransaction): TransactionEntity = TransactionEntity(
            id = item.id,
            type = item.type.name,
            dateKey = item.dateKey,
            title = item.title,
            category = item.category,
            amountBaht = item.amountBaht,
            distanceKm = item.distanceKm,
            durationMinutes = item.durationMinutes,
            timestampMillis = item.timestampMillis
        )
    }
}

@Entity(tableName = "daily_metrics")
data class DailyMetricsEntity(
    @PrimaryKey @ColumnInfo(name = "date_key") val dateKey: String,
    @ColumnInfo(name = "gps_distance_km") val gpsDistanceKm: Double = 0.0,
    @ColumnInfo(name = "work_minutes") val workMinutes: Int = 0
) {
    fun toDomain(): DailyWorkMetrics = DailyWorkMetrics(
        dateKey = dateKey,
        gpsDistanceKm = gpsDistanceKm,
        workMinutes = workMinutes
    )

    companion object {
        fun fromDomain(item: DailyWorkMetrics): DailyMetricsEntity = DailyMetricsEntity(
            dateKey = item.dateKey,
            gpsDistanceKm = item.gpsDistanceKm,
            workMinutes = item.workMinutes
        )
    }
}

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date_key") val dateKey: String,
    @ColumnInfo(name = "start_time_millis") val startTimeMillis: Long,
    @ColumnInfo(name = "end_time_millis") val endTimeMillis: Long? = null,
    @ColumnInfo(name = "distance_km") val distanceKm: Double = 0.0,
    @ColumnInfo(name = "completed_orders") val completedOrders: Int = 0,
    @ColumnInfo(name = "gross_income_baht") val grossIncomeBaht: Double = 0.0,
    @ColumnInfo(name = "fuel_cost_baht") val fuelCostBaht: Double = 0.0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false
) {
    fun toDomain(): WorkSessionRecord = WorkSessionRecord(
        id = id,
        dateKey = dateKey,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        distanceKm = distanceKm,
        completedOrders = completedOrders,
        grossIncomeBaht = grossIncomeBaht,
        fuelCostBaht = fuelCostBaht,
        isActive = isActive
    )

    companion object {
        fun fromDomain(item: WorkSessionRecord): WorkSessionEntity = WorkSessionEntity(
            id = item.id,
            dateKey = item.dateKey,
            startTimeMillis = item.startTimeMillis,
            endTimeMillis = item.endTimeMillis,
            distanceKm = item.distanceKm,
            completedOrders = item.completedOrders,
            grossIncomeBaht = item.grossIncomeBaht,
            fuelCostBaht = item.fuelCostBaht,
            isActive = item.isActive
        )
    }
}

@Entity(tableName = "analyzed_orders")
data class AnalyzedOrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date_key") val dateKey: String,
    val source: String,
    @ColumnInfo(name = "pickup_name") val pickupName: String,
    @ColumnInfo(name = "dropoff_name") val dropoffName: String,
    @ColumnInfo(name = "payout_baht") val payoutBaht: Double,
    @ColumnInfo(name = "distance_km") val distanceKm: Double,
    @ColumnInfo(name = "dead_mileage_km") val deadMileageKm: Double,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int,
    @ColumnInfo(name = "fuel_cost_baht") val fuelCostBaht: Double,
    @ColumnInfo(name = "net_profit_baht") val netProfitBaht: Double,
    @ColumnInfo(name = "earnings_per_km") val earningsPerKm: Double,
    @ColumnInfo(name = "earnings_per_hour") val earningsPerHour: Double,
    @ColumnInfo(name = "smart_score") val smartScore: Int,
    val grade: String,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float,
    @ColumnInfo(name = "was_user_corrected") val wasUserCorrected: Boolean,
    @ColumnInfo(name = "saved_to_earnings") val savedToEarnings: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): AnalyzedOrderRecord = AnalyzedOrderRecord(
        id = id,
        dateKey = dateKey,
        source = runCatching { OrderInputSource.valueOf(source) }.getOrDefault(OrderInputSource.MANUAL_OR_TEXT),
        pickupName = pickupName,
        dropoffName = dropoffName,
        payoutBaht = payoutBaht,
        distanceKm = distanceKm,
        deadMileageKm = deadMileageKm,
        estimatedMinutes = estimatedMinutes,
        fuelCostBaht = fuelCostBaht,
        netProfitBaht = netProfitBaht,
        earningsPerKm = earningsPerKm,
        earningsPerHour = earningsPerHour,
        smartScore = smartScore,
        grade = runCatching { OrderGrade.valueOf(grade) }.getOrDefault(OrderGrade.B),
        confidenceScore = confidenceScore,
        wasUserCorrected = wasUserCorrected,
        savedToEarnings = savedToEarnings,
        latitude = latitude,
        longitude = longitude,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromDomain(item: AnalyzedOrderRecord): AnalyzedOrderEntity = AnalyzedOrderEntity(
            id = item.id,
            dateKey = item.dateKey,
            source = item.source.name,
            pickupName = item.pickupName,
            dropoffName = item.dropoffName,
            payoutBaht = item.payoutBaht,
            distanceKm = item.distanceKm,
            deadMileageKm = item.deadMileageKm,
            estimatedMinutes = item.estimatedMinutes,
            fuelCostBaht = item.fuelCostBaht,
            netProfitBaht = item.netProfitBaht,
            earningsPerKm = item.earningsPerKm,
            earningsPerHour = item.earningsPerHour,
            smartScore = item.smartScore,
            grade = item.grade.name,
            confidenceScore = item.confidenceScore,
            wasUserCorrected = item.wasUserCorrected,
            savedToEarnings = item.savedToEarnings,
            latitude = item.latitude,
            longitude = item.longitude,
            createdAtMillis = item.createdAtMillis
        )
    }
}
