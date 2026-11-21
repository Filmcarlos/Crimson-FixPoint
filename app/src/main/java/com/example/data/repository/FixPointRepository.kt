package com.example.data.repository

import android.content.Context
import com.example.data.local.AnalyzedOrderEntity
import com.example.data.local.DailyMetricsEntity
import com.example.data.local.FixPointDatabase
import com.example.data.local.RiderPreferencesDataStore
import com.example.data.local.SavedPinEntity
import com.example.data.local.StoredRiderPreferences
import com.example.data.local.TransactionEntity
import com.example.data.local.WorkSessionEntity
import com.example.model.AnalyzedOrderRecord
import com.example.model.CoordinateFormat
import com.example.model.CustomExpenseItem
import com.example.model.DailyBreakEvenState
import com.example.model.DailyFinancialHistoryItem
import com.example.model.DailyWorkMetrics
import com.example.model.EarningsGoals
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.FuelEstimateState
import com.example.model.JobIncomeEntry
import com.example.model.MeasurementUnit
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.PinCategory
import com.example.model.PrivacyPreferences
import com.example.model.RiderProfileConfig
import com.example.model.SavedLocationItem
import com.example.model.StopPoiItem
import com.example.model.UpdateFrequency
import com.example.model.VehicleType
import com.example.model.WorkSessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FixPointRepository(
    private val context: Context,
    private val database: FixPointDatabase = FixPointDatabase.getInstance(context),
    val preferencesStore: RiderPreferencesDataStore = RiderPreferencesDataStore(context)
) {
    private val pinDao = database.savedPinDao()
    private val txnDao = database.transactionDao()
    private val metricsDao = database.dailyMetricsDao()
    private val sessionDao = database.workSessionDao()
    private val orderDao = database.analyzedOrderDao()

    val savedPinsFlow: Flow<List<SavedLocationItem>> =
        pinDao.observeAllPins().map { list -> list.map { it.toDomain() } }

    val transactionsFlow: Flow<List<EarningsTransaction>> =
        txnDao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    val dailyMetricsFlow: Flow<Map<String, DailyWorkMetrics>> =
        metricsDao.observeAllMetrics().map { list -> list.associate { it.dateKey to it.toDomain() } }

    val workSessionsFlow: Flow<List<WorkSessionRecord>> =
        sessionDao.observeAllSessions().map { list -> list.map { it.toDomain() } }

    val analyzedOrdersFlow: Flow<List<AnalyzedOrderRecord>> =
        orderDao.observeAllOrders().map { list -> list.map { it.toDomain() } }

    val preferencesFlow: Flow<StoredRiderPreferences> = preferencesStore.preferencesFlow

    /**
     * Safely migrates all legacy v1.0.1 SharedPreferences records (pins, transactions,
     * daily break-even costs, fuel estimator, work metrics, goals, and GPS settings)
     * into Room + DataStore exactly once, preventing data loss on upgrade.
     */
    suspend fun migrateLegacySharedPrefsIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val currentPrefs = preferencesStore.getSnapshot()
        if (currentPrefs.legacyMigrated) return@withContext false

        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = DailyBreakEvenState.todayDateKey()

        val legacyPins = SavedLocationItem.deserializeList(legacy.getString("key_saved_locations", null))
        val legacyPois = StopPoiItem.deserializeList(legacy.getString("key_saved_pois", null))
        val convertedPoiPins = legacyPois.map { poi ->
            SavedLocationItem(
                id = poi.id,
                name = poi.name,
                latitude = poi.latitude,
                longitude = poi.longitude,
                accuracyMeters = null,
                altitudeMeters = null,
                timestampMillis = poi.timestampMillis,
                category = when (poi.category) {
                    com.example.model.StopPoiCategory.FUEL -> PinCategory.FUEL
                    com.example.model.StopPoiCategory.REST_AREA -> PinCategory.REST_AREA
                }
            )
        }
        val allLegacyPins = (legacyPins + convertedPoiPins).distinctBy { it.id }
        if (allLegacyPins.isNotEmpty()) {
            pinDao.upsertPins(allLegacyPins.map { SavedPinEntity.fromDomain(it) })
        }

        // Migrate transactions + break-even entries safely without duplicates
        val loadedTxns = EarningsTransaction.deserializeList(
            legacy.getString("key_earnings_transactions", null)
        ).toMutableList()
        val seenIds = loadedTxns.map { it.id }.toMutableSet()

        val dailyFuel = legacy.getFloat("key_daily_fuel_cost", 0f).toDouble()
        if (dailyFuel > 0.0) {
            val fuelId = "DAILY-FUEL-$todayKey"
            if (seenIds.add(fuelId)) {
                loadedTxns.add(
                    EarningsTransaction(
                        id = fuelId,
                        type = EarningsRecordType.EXPENSE,
                        dateKey = todayKey,
                        title = "ค่าน้ำมันรายวัน",
                        category = "น้ำมัน",
                        amountBaht = dailyFuel
                    )
                )
            }
        }
        CustomExpenseItem.deserializeList(legacy.getString("key_custom_expenses", null)).forEach { exp ->
            if (seenIds.add(exp.id) && loadedTxns.none { it.id == "LEGACY-EXP-${exp.id}" }) {
                loadedTxns.add(
                    EarningsTransaction(
                        id = exp.id,
                        type = EarningsRecordType.EXPENSE,
                        dateKey = todayKey,
                        title = exp.title,
                        category = "ค่าใช้จ่ายอื่นๆ",
                        amountBaht = exp.amountBaht
                    )
                )
            }
        }
        JobIncomeEntry.deserializeList(legacy.getString("key_job_incomes", null)).forEach { job ->
            if (seenIds.add(job.id) && loadedTxns.none { it.id == "LEGACY-JOB-${job.id}" }) {
                loadedTxns.add(
                    EarningsTransaction(
                        id = job.id,
                        type = EarningsRecordType.INCOME,
                        dateKey = todayKey,
                        title = job.label,
                        category = "รับงาน",
                        amountBaht = job.amountBaht,
                        timestampMillis = job.timestampMillis
                    )
                )
            }
        }
        val existingByDate = loadedTxns.groupBy { it.dateKey }
        DailyFinancialHistoryItem.deserializeList(legacy.getString("key_financial_history", null)).forEach { h ->
            if (existingByDate[h.dateKey].isNullOrEmpty()) {
                val anchorTs = com.example.model.SmartEarningsCalculator.parseDateKey(h.dateKey).timeInMillis
                if (h.totalIncomeBaht > 0.0) {
                    val id = "LEGACY-HIST-INC-${h.dateKey}"
                    if (seenIds.add(id)) {
                        loadedTxns.add(
                            EarningsTransaction(
                                id = id,
                                type = EarningsRecordType.INCOME,
                                dateKey = h.dateKey,
                                title = "รายได้รวมย้อนหลัง (${h.jobCount} งาน)",
                                category = "รับงาน",
                                amountBaht = h.totalIncomeBaht,
                                timestampMillis = anchorTs
                            )
                        )
                    }
                }
                if (h.totalCostBaht > 0.0) {
                    val id = "LEGACY-HIST-COST-${h.dateKey}"
                    if (seenIds.add(id)) {
                        loadedTxns.add(
                            EarningsTransaction(
                                id = id,
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
        if (loadedTxns.isNotEmpty()) {
            txnDao.upsertTransactions(loadedTxns.distinctBy { it.id }.map { TransactionEntity.fromDomain(it) })
        }

        val legacyMetrics = DailyWorkMetrics.deserializeMap(legacy.getString("key_earnings_work_metrics", null))
        if (legacyMetrics.isNotEmpty()) {
            metricsDao.upsertMetrics(legacyMetrics.values.map { DailyMetricsEntity.fromDomain(it) })
        }

        val hasLegacyPrefs = legacy.all.isNotEmpty()
        val migratedPrefs = if (hasLegacyPrefs) {
            currentPrefs.copy(
                legacyMigrated = true,
                fuel = FuelEstimateState(
                    tankCapacityLiters = legacy.getFloat("key_fuel_tank_liters", 5.5f).coerceAtLeast(0.5f),
                    consumptionKmPerLiter = legacy.getFloat("key_fuel_km_per_liter", 38.0f).coerceAtLeast(1.0f),
                    distanceDrivenSinceRefuelKm = legacy.getFloat("key_fuel_dist_driven_km", 0f).toDouble().coerceAtLeast(0.0),
                    lowFuelWarningLiters = legacy.getFloat("key_fuel_low_warn_liters", 1.0f).coerceAtLeast(0.1f)
                ),
                goals = EarningsGoals(
                    dailyGoalBaht = legacy.getFloat("key_earnings_goal_daily", 1000f).toDouble().coerceAtLeast(0.0),
                    weeklyGoalBaht = legacy.getFloat("key_earnings_goal_weekly", 6000f).toDouble().coerceAtLeast(0.0),
                    monthlyGoalBaht = legacy.getFloat("key_earnings_goal_monthly", 24000f).toDouble().coerceAtLeast(0.0)
                ),
                activePinId = legacy.getString("key_active_pin_id", null) ?: allLegacyPins.firstOrNull()?.id,
                zoneRadiusKm = legacy.getFloat("key_zone_radius_km", 5.0f).coerceIn(0f, 30f),
                gpsBufferMeters = legacy.getInt("key_gps_buffer_meters", 20).coerceIn(0, 100),
                alertVibrationEnabled = legacy.getBoolean("key_alert_vibration", true),
                measurementUnit = runCatching {
                    MeasurementUnit.valueOf(legacy.getString("key_measurement_unit", "") ?: "")
                }.getOrDefault(MeasurementUnit.METRIC),
                updateFrequency = runCatching {
                    UpdateFrequency.valueOf(legacy.getString("key_update_frequency", "") ?: "")
                }.getOrDefault(UpdateFrequency.STANDARD),
                coordinateFormat = runCatching {
                    CoordinateFormat.valueOf(legacy.getString("key_coordinate_format", "") ?: "")
                }.getOrDefault(CoordinateFormat.DECIMAL),
                keepScreenOn = legacy.getBoolean("key_keep_screen_on", true),
                lockEnabled = legacy.getBoolean("key_lock_enabled", false)
            )
        } else {
            currentPrefs.copy(legacyMigrated = true)
        }
        preferencesStore.saveAll(migratedPrefs)
        return@withContext hasLegacyPrefs
    }

    suspend fun upsertPin(pin: SavedLocationItem) = pinDao.upsertPin(SavedPinEntity.fromDomain(pin))
    suspend fun deletePin(id: String) = pinDao.deletePinById(id)

    suspend fun upsertTransaction(txn: EarningsTransaction) =
        txnDao.upsertTransaction(TransactionEntity.fromDomain(txn))
    suspend fun deleteTransaction(id: String) = txnDao.deleteById(id)

    suspend fun upsertDailyMetric(metric: DailyWorkMetrics) =
        metricsDao.upsertMetric(DailyMetricsEntity.fromDomain(metric))

    suspend fun upsertWorkSession(session: WorkSessionRecord) =
        sessionDao.upsertSession(WorkSessionEntity.fromDomain(session))
    suspend fun deleteWorkSession(id: String) = sessionDao.deleteById(id)

    suspend fun upsertAnalyzedOrder(order: AnalyzedOrderRecord) =
        orderDao.upsertOrder(AnalyzedOrderEntity.fromDomain(order))
    suspend fun deleteAnalyzedOrder(id: String) = orderDao.deleteById(id)
    suspend fun clearAnalyzedOrders() = orderDao.clearAll()

    /**
     * Exports all Room records + DataStore preferences into a portable offline JSON document.
     */
    suspend fun exportLocalBackupJson(): String = withContext(Dispatchers.IO) {
        val prefs = preferencesStore.getSnapshot()
        val pins = pinDao.getAllPinsSnapshot()
        val txns = txnDao.getAllTransactionsSnapshot()
        val metrics = metricsDao.getAllMetricsSnapshot()
        val sessions = sessionDao.getAllSessionsSnapshot()
        val orders = orderDao.getAllOrdersSnapshot()

        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("appVersion", "2.0.0")
            put("exportedAtMillis", System.currentTimeMillis())

            put("profile", JSONObject().apply {
                put("riderName", prefs.profile.riderName)
                put("vehicleModel", prefs.profile.vehicleModel)
                put("vehicleType", prefs.profile.vehicleType.name)
                put("licensePlate", prefs.profile.licensePlate)
                put("fuelPricePerLiterBaht", prefs.profile.fuelPricePerLiterBaht.toDouble())
                put("maintenanceBahtPerKm", prefs.profile.maintenanceBahtPerKm.toDouble())
                put("minAcceptableBahtPerKm", prefs.profile.minAcceptableBahtPerKm.toDouble())
                put("minAcceptableBahtPerHour", prefs.profile.minAcceptableBahtPerHour.toDouble())
            })

            put("fuel", JSONObject().apply {
                put("tankCapacityLiters", prefs.fuel.tankCapacityLiters.toDouble())
                put("consumptionKmPerLiter", prefs.fuel.consumptionKmPerLiter.toDouble())
                put("distanceDrivenSinceRefuelKm", prefs.fuel.distanceDrivenSinceRefuelKm)
                put("lowFuelWarningLiters", prefs.fuel.lowFuelWarningLiters.toDouble())
            })

            put("goals", JSONObject().apply {
                put("dailyGoalBaht", prefs.goals.dailyGoalBaht)
                put("weeklyGoalBaht", prefs.goals.weeklyGoalBaht)
                put("monthlyGoalBaht", prefs.goals.monthlyGoalBaht)
            })

            put("settings", JSONObject().apply {
                put("activePinId", prefs.activePinId ?: "")
                put("zoneRadiusKm", prefs.zoneRadiusKm.toDouble())
                put("gpsBufferMeters", prefs.gpsBufferMeters)
                put("alertVibrationEnabled", prefs.alertVibrationEnabled)
                put("measurementUnit", prefs.measurementUnit.name)
                put("updateFrequency", prefs.updateFrequency.name)
                put("coordinateFormat", prefs.coordinateFormat.name)
                put("keepScreenOn", prefs.keepScreenOn)
            })

            put("pins", JSONArray().apply {
                pins.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("timestampMillis", p.timestampMillis)
                        put("pinCategory", p.pinCategory)
                        put("historicalOrderCount", p.historicalOrderCount)
                        put("historicalAvgPayout", p.historicalAvgPayout)
                        put("notes", p.notes)
                    })
                }
            })

            put("transactions", JSONArray().apply {
                txns.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("type", t.type)
                        put("dateKey", t.dateKey)
                        put("title", t.title)
                        put("category", t.category)
                        put("amountBaht", t.amountBaht)
                        put("distanceKm", t.distanceKm)
                        put("durationMinutes", t.durationMinutes)
                        put("timestampMillis", t.timestampMillis)
                    })
                }
            })

            put("dailyMetrics", JSONArray().apply {
                metrics.forEach { m ->
                    put(JSONObject().apply {
                        put("dateKey", m.dateKey)
                        put("gpsDistanceKm", m.gpsDistanceKm)
                        put("workMinutes", m.workMinutes)
                    })
                }
            })

            put("workSessions", JSONArray().apply {
                sessions.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("dateKey", s.dateKey)
                        put("startTimeMillis", s.startTimeMillis)
                        put("endTimeMillis", s.endTimeMillis ?: JSONObject.NULL)
                        put("distanceKm", s.distanceKm)
                        put("completedOrders", s.completedOrders)
                        put("grossIncomeBaht", s.grossIncomeBaht)
                        put("fuelCostBaht", s.fuelCostBaht)
                        put("isActive", false)
                    })
                }
            })

            put("analyzedOrders", JSONArray().apply {
                orders.forEach { o ->
                    put(JSONObject().apply {
                        put("id", o.id)
                        put("dateKey", o.dateKey)
                        put("source", o.source)
                        put("pickupName", o.pickupName)
                        put("dropoffName", o.dropoffName)
                        put("payoutBaht", o.payoutBaht)
                        put("distanceKm", o.distanceKm)
                        put("deadMileageKm", o.deadMileageKm)
                        put("estimatedMinutes", o.estimatedMinutes)
                        put("fuelCostBaht", o.fuelCostBaht)
                        put("netProfitBaht", o.netProfitBaht)
                        put("earningsPerKm", o.earningsPerKm)
                        put("earningsPerHour", o.earningsPerHour)
                        put("smartScore", o.smartScore)
                        put("grade", o.grade)
                        put("confidenceScore", o.confidenceScore.toDouble())
                        put("wasUserCorrected", o.wasUserCorrected)
                        put("savedToEarnings", o.savedToEarnings)
                        put("latitude", o.latitude ?: JSONObject.NULL)
                        put("longitude", o.longitude ?: JSONObject.NULL)
                        put("createdAtMillis", o.createdAtMillis)
                    })
                }
            })
        }
        root.toString(2)
    }

    /**
     * Restores an offline JSON backup safely into Room and DataStore.
     */
    suspend fun importLocalBackupJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(rawJson)
            var restoredCount = 0

            val pinsArray = root.optJSONArray("pins") ?: JSONArray()
            val pins = mutableListOf<SavedPinEntity>()
            for (i in 0 until pinsArray.length()) {
                val obj = pinsArray.optJSONObject(i) ?: continue
                pins.add(
                    SavedPinEntity(
                        id = obj.optString("id").ifBlank { "LOC-IMP-$i" },
                        name = obj.optString("name", "หมุดที่กู้คืน"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        pinCategory = obj.optString("pinCategory", PinCategory.FIXPOINT.name),
                        historicalOrderCount = obj.optInt("historicalOrderCount", 0),
                        historicalAvgPayout = obj.optDouble("historicalAvgPayout", 0.0),
                        notes = obj.optString("notes", "")
                    )
                )
            }
            if (pins.isNotEmpty()) {
                pinDao.upsertPins(pins)
                restoredCount += pins.size
            }

            val txnsArray = root.optJSONArray("transactions") ?: JSONArray()
            val txns = mutableListOf<TransactionEntity>()
            for (i in 0 until txnsArray.length()) {
                val obj = txnsArray.optJSONObject(i) ?: continue
                txns.add(
                    TransactionEntity(
                        id = obj.optString("id").ifBlank { "TXN-IMP-$i" },
                        type = obj.optString("type", EarningsRecordType.INCOME.name),
                        dateKey = obj.optString("dateKey", DailyBreakEvenState.todayDateKey()),
                        title = obj.optString("title", "รายการกู้คืน"),
                        category = obj.optString("category", "ทั่วไป"),
                        amountBaht = obj.optDouble("amountBaht", 0.0),
                        distanceKm = obj.optDouble("distanceKm", 0.0),
                        durationMinutes = obj.optInt("durationMinutes", 0),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis())
                    )
                )
            }
            if (txns.isNotEmpty()) {
                txnDao.upsertTransactions(txns)
                restoredCount += txns.size
            }

            val metricsArray = root.optJSONArray("dailyMetrics") ?: JSONArray()
            val metrics = mutableListOf<DailyMetricsEntity>()
            for (i in 0 until metricsArray.length()) {
                val obj = metricsArray.optJSONObject(i) ?: continue
                val key = obj.optString("dateKey")
                if (key.isNotBlank()) {
                    metrics.add(
                        DailyMetricsEntity(
                            dateKey = key,
                            gpsDistanceKm = obj.optDouble("gpsDistanceKm", 0.0),
                            workMinutes = obj.optInt("workMinutes", 0)
                        )
                    )
                }
            }
            if (metrics.isNotEmpty()) {
                metricsDao.upsertMetrics(metrics)
                restoredCount += metrics.size
            }

            val sessionsArray = root.optJSONArray("workSessions") ?: JSONArray()
            val sessions = mutableListOf<WorkSessionEntity>()
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.optJSONObject(i) ?: continue
                sessions.add(
                    WorkSessionEntity(
                        id = obj.optString("id").ifBlank { "SES-IMP-$i" },
                        dateKey = obj.optString("dateKey", DailyBreakEvenState.todayDateKey()),
                        startTimeMillis = obj.optLong("startTimeMillis", System.currentTimeMillis()),
                        endTimeMillis = if (obj.isNull("endTimeMillis")) null else obj.optLong("endTimeMillis"),
                        distanceKm = obj.optDouble("distanceKm", 0.0),
                        completedOrders = obj.optInt("completedOrders", 0),
                        grossIncomeBaht = obj.optDouble("grossIncomeBaht", 0.0),
                        fuelCostBaht = obj.optDouble("fuelCostBaht", 0.0),
                        isActive = false
                    )
                )
            }
            if (sessions.isNotEmpty()) {
                sessionDao.upsertSessions(sessions)
                restoredCount += sessions.size
            }

            val ordersArray = root.optJSONArray("analyzedOrders") ?: JSONArray()
            val orders = mutableListOf<AnalyzedOrderEntity>()
            for (i in 0 until ordersArray.length()) {
                val obj = ordersArray.optJSONObject(i) ?: continue
                orders.add(
                    AnalyzedOrderEntity(
                        id = obj.optString("id").ifBlank { "ORD-IMP-$i" },
                        dateKey = obj.optString("dateKey", DailyBreakEvenState.todayDateKey()),
                        source = obj.optString("source", OrderInputSource.MANUAL_OR_TEXT.name),
                        pickupName = obj.optString("pickupName", ""),
                        dropoffName = obj.optString("dropoffName", ""),
                        payoutBaht = obj.optDouble("payoutBaht", 0.0),
                        distanceKm = obj.optDouble("distanceKm", 0.0),
                        deadMileageKm = obj.optDouble("deadMileageKm", 0.0),
                        estimatedMinutes = obj.optInt("estimatedMinutes", 0),
                        fuelCostBaht = obj.optDouble("fuelCostBaht", 0.0),
                        netProfitBaht = obj.optDouble("netProfitBaht", 0.0),
                        earningsPerKm = obj.optDouble("earningsPerKm", 0.0),
                        earningsPerHour = obj.optDouble("earningsPerHour", 0.0),
                        smartScore = obj.optInt("smartScore", 0),
                        grade = obj.optString("grade", OrderGrade.B.name),
                        confidenceScore = obj.optDouble("confidenceScore", 0.0).toFloat(),
                        wasUserCorrected = obj.optBoolean("wasUserCorrected", false),
                        savedToEarnings = obj.optBoolean("savedToEarnings", false),
                        latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
                        longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude"),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
            if (orders.isNotEmpty()) {
                orderDao.upsertOrders(orders)
                restoredCount += orders.size
            }

            val cur = preferencesStore.getSnapshot()
            val profObj = root.optJSONObject("profile")
            val fuelObj = root.optJSONObject("fuel")
            val goalsObj = root.optJSONObject("goals")
            val setObj = root.optJSONObject("settings")

            preferencesStore.saveAll(
                cur.copy(
                    legacyMigrated = true,
                    profile = if (profObj != null) {
                        RiderProfileConfig(
                            riderName = profObj.optString("riderName", cur.profile.riderName),
                            vehicleModel = profObj.optString("vehicleModel", cur.profile.vehicleModel),
                            vehicleType = runCatching {
                                VehicleType.valueOf(profObj.optString("vehicleType", cur.profile.vehicleType.name))
                            }.getOrDefault(cur.profile.vehicleType),
                            licensePlate = profObj.optString("licensePlate", cur.profile.licensePlate),
                            fuelPricePerLiterBaht = profObj.optDouble("fuelPricePerLiterBaht", cur.profile.fuelPricePerLiterBaht.toDouble()).toFloat(),
                            maintenanceBahtPerKm = profObj.optDouble("maintenanceBahtPerKm", cur.profile.maintenanceBahtPerKm.toDouble()).toFloat(),
                            minAcceptableBahtPerKm = profObj.optDouble("minAcceptableBahtPerKm", cur.profile.minAcceptableBahtPerKm.toDouble()).toFloat(),
                            minAcceptableBahtPerHour = profObj.optDouble("minAcceptableBahtPerHour", cur.profile.minAcceptableBahtPerHour.toDouble()).toFloat()
                        )
                    } else cur.profile,
                    fuel = if (fuelObj != null) {
                        FuelEstimateState(
                            tankCapacityLiters = fuelObj.optDouble("tankCapacityLiters", cur.fuel.tankCapacityLiters.toDouble()).toFloat(),
                            consumptionKmPerLiter = fuelObj.optDouble("consumptionKmPerLiter", cur.fuel.consumptionKmPerLiter.toDouble()).toFloat(),
                            distanceDrivenSinceRefuelKm = fuelObj.optDouble("distanceDrivenSinceRefuelKm", cur.fuel.distanceDrivenSinceRefuelKm),
                            lowFuelWarningLiters = fuelObj.optDouble("lowFuelWarningLiters", cur.fuel.lowFuelWarningLiters.toDouble()).toFloat()
                        )
                    } else cur.fuel,
                    goals = if (goalsObj != null) {
                        EarningsGoals(
                            dailyGoalBaht = goalsObj.optDouble("dailyGoalBaht", cur.goals.dailyGoalBaht),
                            weeklyGoalBaht = goalsObj.optDouble("weeklyGoalBaht", cur.goals.weeklyGoalBaht),
                            monthlyGoalBaht = goalsObj.optDouble("monthlyGoalBaht", cur.goals.monthlyGoalBaht)
                        )
                    } else cur.goals,
                    activePinId = setObj?.optString("activePinId")?.takeIf { it.isNotBlank() } ?: cur.activePinId,
                    zoneRadiusKm = setObj?.optDouble("zoneRadiusKm", cur.zoneRadiusKm.toDouble())?.toFloat()?.coerceIn(0f, 30f) ?: cur.zoneRadiusKm,
                    gpsBufferMeters = setObj?.optInt("gpsBufferMeters", cur.gpsBufferMeters)?.coerceIn(0, 100) ?: cur.gpsBufferMeters,
                    alertVibrationEnabled = setObj?.optBoolean("alertVibrationEnabled", cur.alertVibrationEnabled) ?: cur.alertVibrationEnabled,
                    keepScreenOn = setObj?.optBoolean("keepScreenOn", cur.keepScreenOn) ?: cur.keepScreenOn
                )
            )

            restoredCount
        }
    }

    companion object {
        const val LEGACY_PREFS_NAME = "crimson_fixpoint_prefs"
    }
}
