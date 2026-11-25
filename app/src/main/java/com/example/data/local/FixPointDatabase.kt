package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedPinEntity::class,
        TransactionEntity::class,
        DailyMetricsEntity::class,
        WorkSessionEntity::class,
        AnalyzedOrderEntity::class,
        JobOfferEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FixPointDatabase : RoomDatabase() {
    abstract fun savedPinDao(): SavedPinDao
    abstract fun transactionDao(): TransactionDao
    abstract fun dailyMetricsDao(): DailyMetricsDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun analyzedOrderDao(): AnalyzedOrderDao
    abstract fun jobOfferDao(): JobOfferDao

    companion object {
        const val DB_NAME = "crimson_fixpoint_v2.db"

        /**
         * Schema v1 -> v2 migration:
         * Preserves existing saved_pins, transactions, and daily_metrics while adding
         * pin metadata columns plus work_sessions and analyzed_orders tables.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE saved_pins ADD COLUMN pin_category TEXT NOT NULL DEFAULT 'FIXPOINT'"
                )
                db.execSQL(
                    "ALTER TABLE saved_pins ADD COLUMN historical_order_count INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE saved_pins ADD COLUMN historical_avg_payout REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE saved_pins ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_sessions` (
                        `id` TEXT NOT NULL,
                        `date_key` TEXT NOT NULL,
                        `start_time_millis` INTEGER NOT NULL,
                        `end_time_millis` INTEGER,
                        `distance_km` REAL NOT NULL,
                        `completed_orders` INTEGER NOT NULL,
                        `gross_income_baht` REAL NOT NULL,
                        `fuel_cost_baht` REAL NOT NULL,
                        `is_active` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analyzed_orders` (
                        `id` TEXT NOT NULL,
                        `date_key` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `pickup_name` TEXT NOT NULL,
                        `dropoff_name` TEXT NOT NULL,
                        `payout_baht` REAL NOT NULL,
                        `distance_km` REAL NOT NULL,
                        `dead_mileage_km` REAL NOT NULL,
                        `estimated_minutes` INTEGER NOT NULL,
                        `fuel_cost_baht` REAL NOT NULL,
                        `net_profit_baht` REAL NOT NULL,
                        `earnings_per_km` REAL NOT NULL,
                        `earnings_per_hour` REAL NOT NULL,
                        `smart_score` INTEGER NOT NULL,
                        `grade` TEXT NOT NULL,
                        `confidence_score` REAL NOT NULL,
                        `was_user_corrected` INTEGER NOT NULL,
                        `saved_to_earnings` INTEGER NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `created_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Schema v2 -> v3 migration:
         * Adds job_offers table for Multi-Platform Job Radar persistence.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `job_offers` (
                        `id` TEXT NOT NULL,
                        `signature_hash` TEXT NOT NULL,
                        `platform` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `pickup_name` TEXT NOT NULL,
                        `dropoff_name` TEXT NOT NULL,
                        `payout_baht` REAL NOT NULL,
                        `trip_distance_km` REAL NOT NULL,
                        `dead_mileage_km` REAL NOT NULL,
                        `estimated_minutes` INTEGER,
                        `pickup_lat` REAL,
                        `pickup_lng` REAL,
                        `distance_from_rider_km` REAL,
                        `net_profit_baht` REAL NOT NULL,
                        `earnings_per_km` REAL NOT NULL,
                        `earnings_per_hour` REAL,
                        `smart_score` INTEGER NOT NULL,
                        `grade` TEXT NOT NULL,
                        `confidence_score` REAL NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        `expires_at_millis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `is_duplicate_merged` INTEGER NOT NULL,
                        `raw_summary` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: FixPointDatabase? = null

        fun getInstance(context: Context): FixPointDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FixPointDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
