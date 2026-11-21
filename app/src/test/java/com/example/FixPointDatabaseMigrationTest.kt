package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FixPointDatabase
import com.example.data.local.RiderPreferencesDataStore
import com.example.data.repository.FixPointRepository
import com.example.model.AnalyzedOrderRecord
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.PinCategory
import com.example.model.SavedLocationItem
import com.example.model.WorkSessionRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FixPointDatabaseMigrationTest {

    @Test
    fun roomMigration_v1ToV2_preservesExistingPinsTransactionsMetricsAndAddsNewTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v1_v2_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)

        // 1. Create a real SQLite v1 database matching v1 schema and seed records
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 1
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_pins` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `accuracy_meters` REAL,
                `altitude_meters` REAL,
                `timestamp_millis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `type` TEXT NOT NULL,
                `date_key` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `amount_baht` REAL NOT NULL,
                `distance_km` REAL NOT NULL,
                `duration_minutes` INTEGER NOT NULL,
                `timestamp_millis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_metrics` (
                `date_key` TEXT NOT NULL PRIMARY KEY,
                `gps_distance_km` REAL NOT NULL,
                `work_minutes` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            "INSERT INTO saved_pins VALUES ('PIN-V1', 'โซนอโศกเดิม', 13.7367, 100.5603, 4.0, 15.0, 100000)"
        )
        sqlite.execSQL(
            "INSERT INTO transactions VALUES ('TXN-V1', 'INCOME', '2026-11-20', 'งานเดิม', 'รับงาน', 320.0, 12.5, 45, 100001)"
        )
        sqlite.execSQL(
            "INSERT INTO daily_metrics VALUES ('2026-11-20', 48.2, 300)"
        )
        sqlite.close()

        // 2. Open with Room v2 and execute MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, FixPointDatabase::class.java, dbName)
            .addMigrations(FixPointDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val pins = migratedDb.savedPinDao().getAllPinsSnapshot()
            assertEquals(1, pins.size)
            assertEquals("PIN-V1", pins[0].id)
            assertEquals("โซนอโศกเดิม", pins[0].name)
            assertEquals("FIXPOINT", pins[0].pinCategory)
            assertEquals(0, pins[0].historicalOrderCount)

            val txns = migratedDb.transactionDao().getAllTransactionsSnapshot()
            assertEquals(1, txns.size)
            assertEquals(320.0, txns[0].amountBaht, 0.001)

            val metrics = migratedDb.dailyMetricsDao().getAllMetricsSnapshot()
            assertEquals(1, metrics.size)
            assertEquals(48.2, metrics[0].gpsDistanceKm, 0.001)

            // Verify new v2 tables work cleanly
            migratedDb.workSessionDao().upsertSession(
                com.example.data.local.WorkSessionEntity.fromDomain(
                    WorkSessionRecord("SES-1", "2026-11-21", 1000L, 4600_000L, 25.0, 6, 480.0, 28.0, false)
                )
            )
            assertEquals(1, migratedDb.workSessionDao().getAllSessionsSnapshot().size)

            migratedDb.analyzedOrderDao().upsertOrder(
                com.example.data.local.AnalyzedOrderEntity.fromDomain(
                    AnalyzedOrderRecord(
                        id = "ORD-1", dateKey = "2026-11-21", source = OrderInputSource.SCREENSHOT_IMPORT,
                        pickupName = "ร้าน A", dropoffName = "ลูกค้า B", payoutBaht = 75.0, distanceKm = 3.2,
                        deadMileageKm = 0.5, estimatedMinutes = 16, fuelCostBaht = 3.8, netProfitBaht = 68.8,
                        earningsPerKm = 20.2, earningsPerHour = 258.0, smartScore = 88, grade = OrderGrade.S,
                        confidenceScore = 0.92f, wasUserCorrected = false, savedToEarnings = true,
                        latitude = 13.73, longitude = 100.56, createdAtMillis = 2000L
                    )
                )
            )
            assertEquals(1, migratedDb.analyzedOrderDao().getAllOrdersSnapshot().size)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun repository_localJsonBackupAndRestore_roundTripsOfflineDataSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, FixPointDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val db2 = Room.inMemoryDatabaseBuilder(context, FixPointDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val prefsStore = RiderPreferencesDataStore(context)
            val sourceRepo = FixPointRepository(context, db1, prefsStore)
            sourceRepo.upsertPin(
                SavedLocationItem("P-EXP", "โซนพระราม 9", 13.758, 100.565, null, null, 1000L, PinCategory.HOTZONE, 5, 72.0)
            )
            sourceRepo.upsertTransaction(
                EarningsTransaction("T-EXP", EarningsRecordType.INCOME, "2026-11-21", "งาน Grab", "รับงาน", 180.0, 6.0, 25, 2000L)
            )

            val json = sourceRepo.exportLocalBackupJson()
            assertTrue(json.contains("โซนพระราม 9"))
            assertTrue(json.contains("T-EXP"))

            val targetRepo = FixPointRepository(context, db2, prefsStore)
            val result = targetRepo.importLocalBackupJson(json)
            assertTrue(result.isSuccess)
            assertTrue((result.getOrNull() ?: 0) >= 2)

            val restoredPins = targetRepo.savedPinsFlow.first()
            val restoredTxns = targetRepo.transactionsFlow.first()
            assertEquals(1, restoredPins.size)
            assertEquals(PinCategory.HOTZONE, restoredPins[0].category)
            assertEquals(1, restoredTxns.size)
            assertEquals(180.0, restoredTxns[0].amountBaht, 0.001)
            assertNotNull(restoredPins.firstOrNull { it.id == "P-EXP" })
        } finally {
            db1.close()
            db2.close()
        }
    }
}
