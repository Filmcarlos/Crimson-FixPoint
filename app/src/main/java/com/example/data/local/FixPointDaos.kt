package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPinDao {
    @Query("SELECT * FROM saved_pins ORDER BY timestamp_millis DESC")
    fun observeAllPins(): Flow<List<SavedPinEntity>>

    @Query("SELECT * FROM saved_pins ORDER BY timestamp_millis DESC")
    suspend fun getAllPinsSnapshot(): List<SavedPinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPin(pin: SavedPinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPins(pins: List<SavedPinEntity>)

    @Query("DELETE FROM saved_pins WHERE id = :id")
    suspend fun deletePinById(id: String)

    @Query("DELETE FROM saved_pins")
    suspend fun clearAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp_millis DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp_millis DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(txn: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(txns: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface DailyMetricsDao {
    @Query("SELECT * FROM daily_metrics ORDER BY date_key DESC")
    fun observeAllMetrics(): Flow<List<DailyMetricsEntity>>

    @Query("SELECT * FROM daily_metrics ORDER BY date_key DESC")
    suspend fun getAllMetricsSnapshot(): List<DailyMetricsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetric(metric: DailyMetricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetrics(metrics: List<DailyMetricsEntity>)

    @Query("DELETE FROM daily_metrics")
    suspend fun clearAll()
}

@Dao
interface WorkSessionDao {
    @Query("SELECT * FROM work_sessions ORDER BY start_time_millis DESC")
    fun observeAllSessions(): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions ORDER BY start_time_millis DESC")
    suspend fun getAllSessionsSnapshot(): List<WorkSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<WorkSessionEntity>)

    @Query("DELETE FROM work_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM work_sessions")
    suspend fun clearAll()
}

@Dao
interface AnalyzedOrderDao {
    @Query("SELECT * FROM analyzed_orders ORDER BY created_at_millis DESC")
    fun observeAllOrders(): Flow<List<AnalyzedOrderEntity>>

    @Query("SELECT * FROM analyzed_orders ORDER BY created_at_millis DESC")
    suspend fun getAllOrdersSnapshot(): List<AnalyzedOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: AnalyzedOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(orders: List<AnalyzedOrderEntity>)

    @Query("DELETE FROM analyzed_orders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM analyzed_orders")
    suspend fun clearAll()
}

@Dao
interface JobOfferDao {
    @Query("SELECT * FROM job_offers ORDER BY created_at_millis DESC")
    fun observeAllOffers(): Flow<List<JobOfferEntity>>

    @Query("SELECT * FROM job_offers ORDER BY created_at_millis DESC")
    suspend fun getAllOffersSnapshot(): List<JobOfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOffer(offer: JobOfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOffers(offers: List<JobOfferEntity>)

    @Query("DELETE FROM job_offers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM job_offers")
    suspend fun clearAll()
}
