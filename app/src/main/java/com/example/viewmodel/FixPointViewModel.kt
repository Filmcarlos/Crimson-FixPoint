package com.example.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppScreen
import com.example.model.CoordinateFormat
import com.example.model.CustomExpenseItem
import com.example.model.DailyBreakEvenState
import com.example.model.DailyFinancialHistoryItem
import com.example.model.DeliveryStopItem
import com.example.model.FuelEstimateState
import com.example.model.GeoMath
import com.example.model.GpsLockState
import com.example.model.JobIncomeEntry
import com.example.model.LiveGnssTelemetry
import com.example.model.MapTapActionMode
import com.example.model.MeasurementUnit
import com.example.model.RainForecastState
import com.example.model.RainLeadTimeOption
import com.example.model.RouteOptimizer
import com.example.model.SatelliteBarItem
import com.example.model.SavedLocationItem
import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
import com.example.model.UpdateFrequency
import com.example.model.WeatherForecastStatus
import com.example.model.ZoneAlertTone
import com.example.model.ZoneMonitorStatus
import com.example.service.GpsKeepAliveService
import com.example.service.GpsServiceRuntimeBus
import com.example.service.LocationService
import com.example.service.ServiceZoneConfig
import com.example.service.SmoothedLocationUpdate
import com.example.util.NetworkClients
import com.example.util.OpenMeteoResult
import com.example.util.OverpassFuelResult
import com.example.util.TacticalSoundEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class FixPointUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val isLockEnabled: Boolean = false,
    val lockState: GpsLockState = GpsLockState.OFF,
    val telemetry: LiveGnssTelemetry = LiveGnssTelemetry(),
    val savedLocations: List<SavedLocationItem> = emptyList(),
    val activePinId: String? = null,
    val zoneRadiusKm: Float = 5.0f,
    val gpsBufferMeters: Int = 20,
    val mapTapActionMode: MapTapActionMode = MapTapActionMode.MOVE_ACTIVE_PIN,
    val zoneStatus: ZoneMonitorStatus = ZoneMonitorStatus.NO_ACTIVE_PIN,
    val currentDistanceFromPinMeters: Double? = null,
    val excessDistanceMeters: Double = 0.0,
    val isZoneAlertMuted: Boolean = false,
    val alertVibrationEnabled: Boolean = true,
    val alertSoundEnabled: Boolean = true,
    val alertTone: ZoneAlertTone = ZoneAlertTone.SIREN_DUAL,
    val alertVolumePercent: Int = 85,
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = true,
    // Feature 2: Delivery Route Stops (max 15)
    val deliveryStops: List<DeliveryStopItem> = emptyList(),
    val totalDeliveryRouteKm: Double = 0.0,
    // Feature 3: Daily Break-Even & Profit
    val breakEvenState: DailyBreakEvenState = DailyBreakEvenState(),
    // Feature 4: Fuel Estimator
    val fuelState: FuelEstimateState = FuelEstimateState(),
    // Feature 5: Fuel Stations & Rest Stops (User Saved + Overpass 24h Cache)
    val savedPois: List<StopPoiItem> = emptyList(),
    val nearbyOverpassFuelPois: List<StopPoiItem> = emptyList(),
    val overpassSearchRadiusKm: Int = 5,
    val isSearchingOverpass: Boolean = false,
    val overpassErrorOrStatusTh: String? = null,
    val overpassCacheTimestampMillis: Long = 0L,
    val overpassLastSearchLat: Double? = null,
    val overpassLastSearchLng: Double? = null,
    // Feature 6: Rain Forecast & Alert (Open-Meteo)
    val rainForecast: RainForecastState = RainForecastState(),
    // System Permissions
    val hasFineLocationPermission: Boolean = false,
    val hasCoarseLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val permissionDeniedMessageTh: String? = null,
    val statusBannerMessageTh: String? = null
) {
    val hasAnyLocationPermission: Boolean
        get() = hasFineLocationPermission || hasCoarseLocationPermission

    val activePin: SavedLocationItem?
        get() = savedLocations.firstOrNull { it.id == activePinId } ?: savedLocations.firstOrNull()
}

class FixPointViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FixPointUiState())
    val uiState: StateFlow<FixPointUiState> = _uiState.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var locationService: LocationService? = null
    private var appContextRef: Context? = null
    private var isInitializedWithContext = false
    private var viewModelRepeatAlertJob: Job? = null
    private var periodicMaintenanceJob: Job? = null

    private var lastFuelTrackLat: Double? = null
    private var lastFuelTrackLng: Double? = null

    init {
        viewModelScope.launch {
            GpsServiceRuntimeBus.lastServiceLocation.collectLatest { loc ->
                if (loc != null && _uiState.value.isLockEnabled) {
                    applyHardwareLocation(loc)
                }
            }
        }
        viewModelScope.launch {
            GpsServiceRuntimeBus.satelliteSnapshot.collectLatest { (used, visible, bars) ->
                if (_uiState.value.isLockEnabled && (used > 0 || visible > 0)) {
                    updateSatellitesInternal(used, visible, bars)
                }
            }
        }
        viewModelScope.launch {
            GpsServiceRuntimeBus.zoneConfig.collectLatest { cfg ->
                if (cfg.isAlertMuted != _uiState.value.isZoneAlertMuted) {
                    _uiState.update { it.copy(isZoneAlertMuted = cfg.isAlertMuted) }
                    if (cfg.isAlertMuted) {
                        stopViewModelRepeatAlertLoop()
                    }
                }
            }
        }
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        refreshPermissionFlags(appContext)

        if (isInitializedWithContext) return
        isInitializedWithContext = true

        val sharedPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs

        val savedLockEnabled = sharedPrefs.getBoolean(KEY_LOCK_ENABLED, false)
        val unitName = sharedPrefs.getString(KEY_MEASUREMENT_UNIT, MeasurementUnit.METRIC.name)
        val freqName = sharedPrefs.getString(KEY_UPDATE_FREQUENCY, UpdateFrequency.STANDARD.name)
        val coordName = sharedPrefs.getString(KEY_COORDINATE_FORMAT, CoordinateFormat.DECIMAL.name)
        val keepScreen = sharedPrefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        val savedItemsRaw = sharedPrefs.getString(KEY_SAVED_LOCATIONS, null)
        val savedActivePinId = sharedPrefs.getString(KEY_ACTIVE_PIN_ID, null)
        val savedRadiusKm = sharedPrefs.getFloat(KEY_ZONE_RADIUS_KM, 5.0f).coerceIn(0.0f, 30.0f)
        val savedBufferMeters = sharedPrefs.getInt(KEY_GPS_BUFFER_METERS, 20).coerceIn(0, 100)
        val savedVib = sharedPrefs.getBoolean(KEY_ALERT_VIBRATION, true)
        val savedSound = sharedPrefs.getBoolean(KEY_ALERT_SOUND, true)
        val savedToneName = sharedPrefs.getString(KEY_ALERT_TONE, ZoneAlertTone.SIREN_DUAL.name)
        val savedVolume = sharedPrefs.getInt(KEY_ALERT_VOLUME, 85).coerceIn(0, 100)

        // Load Feature 2: Delivery Stops
        val savedStops = DeliveryStopItem.deserializeList(sharedPrefs.getString(KEY_DELIVERY_STOPS, null))
        val routeCalc = RouteOptimizer.recalculateCurrentOrderDistances(null, null, savedStops)

        // Load Feature 3: Daily Break-Even
        val todayKey = DailyBreakEvenState.todayDateKey()
        val storedDateKey = sharedPrefs.getString(KEY_BREAKEVEN_DATE, todayKey) ?: todayKey
        val dailyFuelCost = sharedPrefs.getFloat(KEY_DAILY_FUEL_COST, 0f).toDouble()
        var customExpenses = CustomExpenseItem.deserializeList(sharedPrefs.getString(KEY_CUSTOM_EXPENSES, null))
        var jobIncomes = JobIncomeEntry.deserializeList(sharedPrefs.getString(KEY_JOB_INCOMES, null))
        var historyList = DailyFinancialHistoryItem.deserializeList(sharedPrefs.getString(KEY_FINANCIAL_HISTORY, null))

        if (storedDateKey != todayKey) {
            val prevTotalCost = dailyFuelCost + customExpenses.sumOf { it.amountBaht }
            val prevTotalIncome = jobIncomes.sumOf { it.amountBaht }
            if (prevTotalCost > 0.0 || prevTotalIncome > 0.0) {
                val archived = DailyFinancialHistoryItem(
                    dateKey = storedDateKey,
                    totalCostBaht = prevTotalCost,
                    totalIncomeBaht = prevTotalIncome,
                    jobCount = jobIncomes.size
                )
                historyList = (listOf(archived) + historyList).take(30)
            }
            customExpenses = emptyList()
            jobIncomes = emptyList()
            sharedPrefs.edit()
                .putString(KEY_BREAKEVEN_DATE, todayKey)
                .putString(KEY_CUSTOM_EXPENSES, "")
                .putString(KEY_JOB_INCOMES, "")
                .putString(KEY_FINANCIAL_HISTORY, DailyFinancialHistoryItem.serializeList(historyList))
                .apply()
        }

        val initialBreakEven = DailyBreakEvenState(
            currentDateKey = todayKey,
            fuelCostBaht = dailyFuelCost,
            customExpenses = customExpenses,
            jobIncomes = jobIncomes,
            history = historyList
        )

        // Load Feature 4: Fuel Estimator
        val tankCap = sharedPrefs.getFloat(KEY_FUEL_TANK_LITERS, 5.5f).coerceAtLeast(0.5f)
        val consumption = sharedPrefs.getFloat(KEY_FUEL_KM_PER_LITER, 38.0f).coerceAtLeast(1.0f)
        val distSinceRefuel = sharedPrefs.getFloat(KEY_FUEL_DIST_DRIVEN_KM, 0f).toDouble().coerceAtLeast(0.0)
        val lowFuelWarn = sharedPrefs.getFloat(KEY_FUEL_LOW_WARN_LITERS, 1.0f).coerceAtLeast(0.2f)
        val initialFuelState = FuelEstimateState(
            tankCapacityLiters = tankCap,
            consumptionKmPerLiter = consumption,
            distanceDrivenSinceRefuelKm = distSinceRefuel,
            lowFuelWarningLiters = lowFuelWarn,
            lowFuelAlertTriggered = false
        )

        // Load Feature 5: Saved POIs & Overpass 24h Cache
        val savedPoisList = StopPoiItem.deserializeList(sharedPrefs.getString(KEY_SAVED_POIS, null))
        val cachedOverpassAt = sharedPrefs.getLong(KEY_OVERPASS_CACHE_TS, 0L)
        val isCacheFresh = (System.currentTimeMillis() - cachedOverpassAt) < 24 * 60 * 60 * 1000L
        val cachedOverpassList = if (isCacheFresh) {
            StopPoiItem.deserializeList(sharedPrefs.getString(KEY_OVERPASS_CACHE_ITEMS, null))
        } else {
            emptyList()
        }
        val cachedSearchLat = sharedPrefs.getString(KEY_OVERPASS_LAST_LAT, null)?.toDoubleOrNull()
        val cachedSearchLng = sharedPrefs.getString(KEY_OVERPASS_LAST_LNG, null)?.toDoubleOrNull()
        val savedOverpassRadiusKm = sharedPrefs.getInt(KEY_OVERPASS_RADIUS_KM, 5).let {
            if (it in listOf(2, 5, 10)) it else 5
        }

        // Load Feature 6: Rain Forecast Settings
        val rainEnabled = sharedPrefs.getBoolean(KEY_RAIN_ALERT_ENABLED, true)
        val rainLeadName = sharedPrefs.getString(KEY_RAIN_LEAD_OPTION, RainLeadTimeOption.MIN_30.name)
        val rainLead = runCatching { RainLeadTimeOption.valueOf(rainLeadName ?: "") }
            .getOrDefault(RainLeadTimeOption.MIN_30)
        val rainProbThreshold = sharedPrefs.getInt(KEY_RAIN_PROB_THRESHOLD, 50).coerceIn(10, 95)

        val unit = runCatching { MeasurementUnit.valueOf(unitName ?: "") }.getOrDefault(MeasurementUnit.METRIC)
        val freq = runCatching { UpdateFrequency.valueOf(freqName ?: "") }.getOrDefault(UpdateFrequency.STANDARD)
        val coordFmt = runCatching { CoordinateFormat.valueOf(coordName ?: "") }.getOrDefault(CoordinateFormat.DECIMAL)
        val tone = runCatching { ZoneAlertTone.valueOf(savedToneName ?: "") }.getOrDefault(ZoneAlertTone.SIREN_DUAL)
        val savedItems = SavedLocationItem.deserializeList(savedItemsRaw)
        val resolvedActiveId = savedActivePinId?.takeIf { id -> savedItems.any { it.id == id } }
            ?: savedItems.firstOrNull()?.id

        _uiState.update { state ->
            state.copy(
                measurementUnit = unit,
                updateFrequency = freq,
                coordinateFormat = coordFmt,
                keepScreenOn = keepScreen,
                savedLocations = savedItems,
                activePinId = resolvedActiveId,
                zoneRadiusKm = savedRadiusKm,
                gpsBufferMeters = savedBufferMeters,
                alertVibrationEnabled = savedVib,
                alertSoundEnabled = savedSound,
                alertTone = tone,
                alertVolumePercent = savedVolume,
                deliveryStops = routeCalc.orderedStops,
                totalDeliveryRouteKm = routeCalc.totalPendingDistanceKm,
                breakEvenState = initialBreakEven,
                fuelState = initialFuelState,
                savedPois = savedPoisList,
                nearbyOverpassFuelPois = cachedOverpassList,
                overpassSearchRadiusKm = savedOverpassRadiusKm,
                overpassCacheTimestampMillis = if (isCacheFresh) cachedOverpassAt else 0L,
                overpassLastSearchLat = cachedSearchLat,
                overpassLastSearchLng = cachedSearchLng,
                rainForecast = RainForecastState(
                    alertEnabled = rainEnabled,
                    leadTimeOption = rainLead,
                    probabilityThresholdPercent = rainProbThreshold
                ),
                zoneStatus = if (resolvedActiveId != null) ZoneMonitorStatus.WAITING_FOR_GPS else ZoneMonitorStatus.NO_ACTIVE_PIN
            )
        }
        syncZoneConfigToBus()
        startPeriodicMaintenanceLoop()

        val service = LocationService(appContext)
        locationService = service

        viewModelScope.launch {
            service.compassAzimuthDeg.collectLatest { azimuth ->
                if (_uiState.value.isLockEnabled && azimuth != null) {
                    _uiState.update { s ->
                        s.copy(
                            telemetry = s.telemetry.copy(compassAzimuthDegrees = azimuth)
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            service.gnssSatellites.collectLatest { (used, visible, bars) ->
                if (_uiState.value.isLockEnabled) {
                    updateSatellitesInternal(used, visible, bars)
                }
            }
        }

        if (savedLockEnabled && _uiState.value.hasAnyLocationPermission) {
            startGpsLockEngine(appContext)
        } else if (savedLockEnabled && !_uiState.value.hasAnyLocationPermission) {
            _uiState.update {
                it.copy(
                    isLockEnabled = false,
                    lockState = GpsLockState.OFF
                )
            }
        }
    }

    private fun startPeriodicMaintenanceLoop() {
        periodicMaintenanceJob?.cancel()
        periodicMaintenanceJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                checkMidnightBreakEvenReset()
                val lat = _uiState.value.telemetry.latitude
                val lng = _uiState.value.telemetry.longitude
                if (lat != null && lng != null) {
                    val lastFetch = _uiState.value.rainForecast.lastUpdatedEpochMillis ?: 0L
                    if (System.currentTimeMillis() - lastFetch >= 10 * 60 * 1000L) {
                        refreshRainForecast(force = false)
                    }
                }
            }
        }
    }

    fun checkMidnightBreakEvenReset(nowMillis: Long = System.currentTimeMillis()) {
        val todayKey = DailyBreakEvenState.todayDateKey(nowMillis)
        val current = _uiState.value.breakEvenState
        if (current.currentDateKey != todayKey) {
            val prevCost = current.totalCostBaht
            val prevIncome = current.totalIncomeBaht
            val newHistory = if (prevCost > 0.0 || prevIncome > 0.0) {
                val item = DailyFinancialHistoryItem(
                    dateKey = current.currentDateKey,
                    totalCostBaht = prevCost,
                    totalIncomeBaht = prevIncome,
                    jobCount = current.jobIncomes.size
                )
                (listOf(item) + current.history).take(30)
            } else {
                current.history
            }
            val updated = current.copy(
                currentDateKey = todayKey,
                customExpenses = emptyList(),
                jobIncomes = emptyList(),
                history = newHistory
            )
            prefs?.edit()
                ?.putString(KEY_BREAKEVEN_DATE, todayKey)
                ?.putString(KEY_CUSTOM_EXPENSES, "")
                ?.putString(KEY_JOB_INCOMES, "")
                ?.putString(KEY_FINANCIAL_HISTORY, DailyFinancialHistoryItem.serializeList(newHistory))
                ?.apply()
            _uiState.update {
                it.copy(
                    breakEvenState = updated,
                    statusBannerMessageTh = "รีเซ็ตยอดคุ้มทุนรายวันสำหรับวันใหม่ ($todayKey) และบันทึกประวัติแล้ว"
                )
            }
        }
    }

    fun refreshPermissionFlags(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            fine || coarse
        }
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val batteryExempt = runCatching {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
                pm.isIgnoringBatteryOptimizations(appContext.packageName)
            } else {
                true
            }
        }.getOrDefault(false)

        _uiState.update {
            it.copy(
                hasFineLocationPermission = fine,
                hasCoarseLocationPermission = coarse,
                hasBackgroundLocationPermission = bg,
                hasNotificationPermission = notif,
                isIgnoringBatteryOptimizations = batteryExempt,
                permissionDeniedMessageTh = if (fine || coarse) null else it.permissionDeniedMessageTh
            )
        }
    }

    fun onBatteryExemptionResult(context: Context) {
        refreshPermissionFlags(context)
        if (_uiState.value.isIgnoringBatteryOptimizations) {
            _uiState.update {
                it.copy(statusBannerMessageTh = "อนุญาตการข้ามประหยัดแบตเตอรี่เรียบร้อยแล้ว")
            }
        } else {
            _uiState.update {
                it.copy(
                    statusBannerMessageTh = "ยังไม่ได้ยกเว้นการประหยัดแบตเตอรี่ ระบบอาจถูกจำกัดเมื่อปิดหน้าจอ"
                )
            }
        }
    }

    fun onBackgroundLocationResult(context: Context, granted: Boolean) {
        refreshPermissionFlags(context)
        if (granted) {
            _uiState.update {
                it.copy(statusBannerMessageTh = "อนุญาตสิทธิ์ตำแหน่งเบื้องหลังเรียบร้อยแล้ว")
            }
        } else {
            _uiState.update {
                it.copy(
                    permissionDeniedMessageTh = "ไม่ได้รับสิทธิ์ตำแหน่งเบื้องหลัง กรุณาเลือก 'อนุญาตตลอดเวลา' ในการตั้งค่าเพื่อให้ตรวจโซนขณะปิดหน้าจอได้ต่อเนื่อง"
                )
            }
        }
    }

    fun selectScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setMapTapActionMode(mode: MapTapActionMode) {
        _uiState.update { it.copy(mapTapActionMode = mode) }
    }

    fun onPermissionResult(context: Context, granted: Boolean, startLockIfGranted: Boolean) {
        refreshPermissionFlags(context)
        if (granted) {
            _uiState.update { it.copy(permissionDeniedMessageTh = null) }
            if (startLockIfGranted) {
                startGpsLockEngine(context)
            }
        } else {
            stopGpsLockEngine(context)
            _uiState.update {
                it.copy(
                    permissionDeniedMessageTh = "ไม่ได้รับสิทธิ์การเข้าถึงตำแหน่ง กรุณาอนุญาตสิทธิ์ตำแหน่งแม่นยำเพื่อเริ่มล็อกสัญญาณและตรวจโซน"
                )
            }
        }
    }

    fun toggleMainPowerLock(context: Context): Boolean {
        refreshPermissionFlags(context)
        val currentlyEnabled = _uiState.value.isLockEnabled
        return if (currentlyEnabled) {
            stopGpsLockEngine(context)
            true
        } else {
            if (!_uiState.value.hasAnyLocationPermission) {
                false
            } else {
                startGpsLockEngine(context)
                true
            }
        }
    }

    fun startGpsLockEngine(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        refreshPermissionFlags(appContext)
        if (!_uiState.value.hasAnyLocationPermission) {
            _uiState.update {
                it.copy(
                    permissionDeniedMessageTh = "จำเป็นต้องได้รับสิทธิ์ตำแหน่งก่อนเริ่มล็อกสัญญาณ GPS"
                )
            }
            return
        }

        prefs?.edit()?.putBoolean(KEY_LOCK_ENABLED, true)?.apply()
        val freq = _uiState.value.updateFrequency
        syncZoneConfigToBus()

        _uiState.update { state ->
            val nextLockState = if (state.telemetry.hasFix) GpsLockState.LOCKED else GpsLockState.SEARCHING
            state.copy(
                isLockEnabled = true,
                lockState = nextLockState,
                permissionDeniedMessageTh = null,
                statusBannerMessageTh = "เริ่มทำงานระบบล็อกสัญญาณและตรวจโซนรับงานเบื้องหลังแล้ว"
            )
        }

        TacticalSoundEngine.playLockChime(
            isMuted = !_uiState.value.alertSoundEnabled,
            volumePercent = _uiState.value.alertVolumePercent
        )

        try {
            val serviceIntent = Intent(appContext, GpsKeepAliveService::class.java).apply {
                putExtra(GpsKeepAliveService.EXTRA_INTERVAL_MS, freq.intervalMillis)
            }
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (_: Throwable) {}

        val svc = locationService ?: LocationService(appContext).also { locationService = it }
        svc.resetKalmanFilter()
        svc.startLocationUpdates(
            intervalMillis = freq.intervalMillis,
            minUpdateIntervalMillis = freq.minIntervalMillis
        ) { smoothedUpdate ->
            applySmoothedLocation(smoothedUpdate)
        }
    }

    fun stopGpsLockEngine(context: Context) {
        val appContext = context.applicationContext
        prefs?.edit()?.putBoolean(KEY_LOCK_ENABLED, false)?.apply()
        stopViewModelRepeatAlertLoop()
        lastFuelTrackLat = null
        lastFuelTrackLng = null

        try {
            locationService?.stopLocationUpdates()
            locationService?.resetKalmanFilter()
        } catch (_: Throwable) {}

        try {
            val stopIntent = Intent(appContext, GpsKeepAliveService::class.java).apply {
                action = GpsKeepAliveService.ACTION_STOP
            }
            appContext.stopService(stopIntent)
        } catch (_: Throwable) {}

        _uiState.update { state ->
            state.copy(
                isLockEnabled = false,
                lockState = GpsLockState.OFF,
                telemetry = LiveGnssTelemetry(),
                zoneStatus = if (state.activePin != null) ZoneMonitorStatus.WAITING_FOR_GPS else ZoneMonitorStatus.NO_ACTIVE_PIN,
                currentDistanceFromPinMeters = null,
                excessDistanceMeters = 0.0,
                isZoneAlertMuted = false,
                statusBannerMessageTh = "หยุดการทำงานและปล่อยทรัพยากร GPS เรียบร้อยแล้ว"
            )
        }
    }

    private fun applySmoothedLocation(update: SmoothedLocationUpdate) {
        _uiState.update { state ->
            if (!state.isLockEnabled) return@update state
            val wasSearching = state.lockState != GpsLockState.LOCKED
            if (wasSearching) {
                TacticalSoundEngine.playSRankMatchBeep(
                    isMuted = !state.alertSoundEnabled,
                    volumePercent = state.alertVolumePercent
                )
            }
            state.copy(
                lockState = GpsLockState.LOCKED,
                telemetry = state.telemetry.copy(
                    hasFix = true,
                    latitude = update.smoothedLat,
                    longitude = update.smoothedLng,
                    rawLatitude = update.rawLat,
                    rawLongitude = update.rawLng,
                    accuracyMeters = update.accuracyMeters,
                    altitudeMeters = update.altitudeMeters ?: state.telemetry.altitudeMeters,
                    speedMetersPerSec = update.speedMps,
                    bearingDegrees = update.bearingDeg ?: state.telemetry.bearingDegrees,
                    lastFixTimestampMillis = update.timestampMillis
                )
            )
        }
        onNewGpsPositionEvaluated(update.smoothedLat, update.smoothedLng)
    }

    private fun applyHardwareLocation(location: Location) {
        val svc = locationService
        if (svc != null) {
            applySmoothedLocation(svc.processRawLocation(location))
        } else {
            _uiState.update { state ->
                if (!state.isLockEnabled) return@update state
                state.copy(
                    lockState = GpsLockState.LOCKED,
                    telemetry = state.telemetry.copy(
                        hasFix = true,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        rawLatitude = location.latitude,
                        rawLongitude = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                        speedMetersPerSec = if (location.hasSpeed()) location.speed else 0f,
                        bearingDegrees = if (location.hasBearing()) location.bearing else null,
                        lastFixTimestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }
            onNewGpsPositionEvaluated(location.latitude, location.longitude)
        }
    }

    private fun onNewGpsPositionEvaluated(lat: Double, lng: Double) {
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        accumulateRealGpsDistanceForFuel(lat, lng)
        recalculateDeliveryStopDistancesFromGps(lat, lng)
        checkWeatherAndOverpassMovementTriggers(lat, lng)
    }

    private fun accumulateRealGpsDistanceForFuel(lat: Double, lng: Double) {
        val prevLat = lastFuelTrackLat
        val prevLng = lastFuelTrackLng
        lastFuelTrackLat = lat
        lastFuelTrackLng = lng
        if (prevLat == null || prevLng == null) return

        val stepKm = GeoMath.haversineKm(prevLat, prevLng, lat, lng)
        // Accumulate genuine movement steps between 2 meters and 5 km
        if (stepKm in 0.002..5.0) {
            val currentFuel = _uiState.value.fuelState
            val newDistKm = currentFuel.distanceDrivenSinceRefuelKm + stepKm
            val updatedFuel = currentFuel.copy(distanceDrivenSinceRefuelKm = newDistKm)
            prefs?.edit()?.putFloat(KEY_FUEL_DIST_DRIVEN_KM, newDistKm.toFloat())?.apply()

            val shouldTriggerLowFuelAlert = updatedFuel.isLowFuel && !currentFuel.lowFuelAlertTriggered
            val finalFuel = if (shouldTriggerLowFuelAlert) {
                updatedFuel.copy(lowFuelAlertTriggered = true)
            } else {
                updatedFuel
            }

            _uiState.update {
                it.copy(
                    fuelState = finalFuel,
                    statusBannerMessageTh = if (shouldTriggerLowFuelAlert) {
                        String.format(
                            Locale.US,
                            "แจ้งเตือนน้ำมันใกล้หมด: เหลือประมาณ %.2f ลิตร (วิ่งได้อีก ~%.1f กม.)",
                            finalFuel.remainingLiters,
                            finalFuel.estimatedRemainingRangeKm
                        )
                    } else {
                        it.statusBannerMessageTh
                    }
                )
            }

            if (shouldTriggerLowFuelAlert) {
                appContextRef?.let { ctx ->
                    TacticalSoundEngine.triggerLowFuelWarning(
                        context = ctx,
                        vibrationEnabled = _uiState.value.alertVibrationEnabled,
                        soundEnabled = _uiState.value.alertSoundEnabled,
                        volumePercent = _uiState.value.alertVolumePercent
                    )
                }
            }
        }
    }

    private fun recalculateDeliveryStopDistancesFromGps(lat: Double, lng: Double) {
        val stops = _uiState.value.deliveryStops
        if (stops.isEmpty()) return
        val recalc = RouteOptimizer.recalculateCurrentOrderDistances(lat, lng, stops)
        _uiState.update {
            it.copy(
                deliveryStops = recalc.orderedStops,
                totalDeliveryRouteKm = recalc.totalPendingDistanceKm
            )
        }
    }

    private fun checkWeatherAndOverpassMovementTriggers(lat: Double, lng: Double) {
        // Feature 6: Update rain forecast every 10 mins OR when location changes > 5 km
        val rf = _uiState.value.rainForecast
        val lastLat = rf.lastFetchedLat
        val lastLng = rf.lastFetchedLng
        val lastAt = rf.lastUpdatedEpochMillis ?: 0L
        val now = System.currentTimeMillis()
        val movedOver5Km = lastLat != null && lastLng != null &&
            GeoMath.haversineKm(lastLat, lastLng, lat, lng) >= 5.0
        val tenMinElapsed = now - lastAt >= 10 * 60 * 1000L

        if ((lastAt == 0L || movedOver5Km || tenMinElapsed) && rf.status != WeatherForecastStatus.LOADING) {
            refreshRainForecast(force = false)
        }

        // Feature 5: Re-query Overpass if user previously searched and moved > 2 km
        val ovLat = _uiState.value.overpassLastSearchLat
        val ovLng = _uiState.value.overpassLastSearchLng
        if (ovLat != null && ovLng != null && !_uiState.value.isSearchingOverpass) {
            val movedKm = GeoMath.haversineKm(ovLat, ovLng, lat, lng)
            if (movedKm >= 2.0) {
                searchNearbyFuelStations(forceRefresh = true)
            }
        }
    }

    private fun updateSatellitesInternal(
        used: Int,
        visible: Int,
        bars: List<SatelliteBarItem>
    ) {
        val avgSnr = if (bars.isNotEmpty()) {
            bars.map { it.cn0DbHz }.average().toFloat()
        } else {
            0f
        }
        _uiState.update { state ->
            state.copy(
                telemetry = state.telemetry.copy(
                    satellitesUsedInFix = used,
                    satellitesVisible = visible,
                    averageSnrDbHz = avgSnr,
                    satelliteBars = bars
                )
            )
        }
    }

    private fun recalculateZoneEvaluation(triggerAlertsOnTransition: Boolean) {
        val state = _uiState.value
        val activePin = state.activePin
        if (activePin == null) {
            stopViewModelRepeatAlertLoop()
            _uiState.update {
                it.copy(
                    zoneStatus = ZoneMonitorStatus.NO_ACTIVE_PIN,
                    currentDistanceFromPinMeters = null,
                    excessDistanceMeters = 0.0,
                    isZoneAlertMuted = false
                )
            }
            return
        }

        val lat = state.telemetry.latitude
        val lng = state.telemetry.longitude
        if (lat == null || lng == null) {
            stopViewModelRepeatAlertLoop()
            _uiState.update {
                it.copy(
                    zoneStatus = ZoneMonitorStatus.WAITING_FOR_GPS,
                    currentDistanceFromPinMeters = null,
                    excessDistanceMeters = 0.0
                )
            }
            return
        }

        val distanceMeters = GeoMath.haversineMeters(lat, lng, activePin.latitude, activePin.longitude)
        val thresholdMeters = (state.zoneRadiusKm * 1000.0) + state.gpsBufferMeters
        val isOutside = distanceMeters > thresholdMeters
        val excessMeters = max(0.0, distanceMeters - thresholdMeters)
        val previousStatus = state.zoneStatus

        if (isOutside) {
            val transitionedToOutside = previousStatus != ZoneMonitorStatus.OUTSIDE_ZONE
            val nextMuted = if (transitionedToOutside) false else state.isZoneAlertMuted
            _uiState.update {
                it.copy(
                    zoneStatus = ZoneMonitorStatus.OUTSIDE_ZONE,
                    currentDistanceFromPinMeters = distanceMeters,
                    excessDistanceMeters = excessMeters,
                    isZoneAlertMuted = nextMuted,
                    statusBannerMessageTh = if (transitionedToOutside) {
                        "ออกนอกโซนรับงานแล้ว (เกินรัศมี ${GeoMath.formatShortZoneDistanceTh(excessMeters)})"
                    } else {
                        it.statusBannerMessageTh
                    }
                )
            }
            if (transitionedToOutside) {
                GpsServiceRuntimeBus.setAlertMuted(false)
                if (triggerAlertsOnTransition && !GpsServiceRuntimeBus.isServiceRunning.value) {
                    fireViewModelOutOfZoneAlert()
                    startViewModelRepeatAlertLoop()
                }
            }
        } else {
            val wasOutside = previousStatus == ZoneMonitorStatus.OUTSIDE_ZONE
            stopViewModelRepeatAlertLoop()
            _uiState.update {
                it.copy(
                    zoneStatus = ZoneMonitorStatus.INSIDE_ZONE,
                    currentDistanceFromPinMeters = distanceMeters,
                    excessDistanceMeters = 0.0,
                    isZoneAlertMuted = false,
                    statusBannerMessageTh = if (wasOutside) "กลับเข้าโซนแล้ว" else it.statusBannerMessageTh
                )
            }
            if (wasOutside) {
                GpsServiceRuntimeBus.setAlertMuted(false)
                if (triggerAlertsOnTransition && !GpsServiceRuntimeBus.isServiceRunning.value) {
                    appContextRef?.let { ctx ->
                        TacticalSoundEngine.vibrateReturnToZoneSingleShort(ctx, state.alertVibrationEnabled)
                    }
                    TacticalSoundEngine.playReturnToZoneChime(state.alertSoundEnabled, state.alertVolumePercent)
                }
            }
        }
    }

    private fun fireViewModelOutOfZoneAlert() {
        val s = _uiState.value
        if (s.isZoneAlertMuted) return
        appContextRef?.let { ctx ->
            TacticalSoundEngine.vibrateOutOfZone(ctx, s.alertVibrationEnabled)
        }
        TacticalSoundEngine.playZoneAlertTone(
            soundEnabled = s.alertSoundEnabled,
            tone = s.alertTone,
            volumePercent = s.alertVolumePercent
        )
    }

    private fun startViewModelRepeatAlertLoop() {
        stopViewModelRepeatAlertLoop()
        viewModelRepeatAlertJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                val s = _uiState.value
                if (s.zoneStatus != ZoneMonitorStatus.OUTSIDE_ZONE || s.isZoneAlertMuted || GpsServiceRuntimeBus.isServiceRunning.value) {
                    break
                }
                fireViewModelOutOfZoneAlert()
            }
        }
    }

    private fun stopViewModelRepeatAlertLoop() {
        viewModelRepeatAlertJob?.cancel()
        viewModelRepeatAlertJob = null
    }

    fun muteZoneAlert(context: Context? = null) {
        stopViewModelRepeatAlertLoop()
        GpsServiceRuntimeBus.setAlertMuted(true)
        _uiState.update {
            it.copy(
                isZoneAlertMuted = true,
                statusBannerMessageTh = "ปิดเสียงเตือนชั่วคราวแล้ว (จะรีเซ็ตเมื่อกลับเข้าโซน)"
            )
        }
        val ctx = context?.applicationContext ?: appContextRef
        if (ctx != null && GpsServiceRuntimeBus.isServiceRunning.value) {
            runCatching {
                val muteIntent = Intent(ctx, GpsKeepAliveService::class.java).apply {
                    action = GpsKeepAliveService.ACTION_MUTE_ALERT
                }
                ctx.startService(muteIntent)
            }
        }
    }

    private fun syncZoneConfigToBus() {
        val s = _uiState.value
        GpsServiceRuntimeBus.updateZoneConfig(
            ServiceZoneConfig(
                activePin = s.activePin,
                radiusKm = s.zoneRadiusKm,
                gpsBufferMeters = s.gpsBufferMeters,
                alertVibrationEnabled = s.alertVibrationEnabled,
                alertSoundEnabled = s.alertSoundEnabled,
                alertTone = s.alertTone,
                alertVolumePercent = s.alertVolumePercent,
                isAlertMuted = s.isZoneAlertMuted,
                rainAlertEnabled = s.rainForecast.alertEnabled,
                rainLeadTimeMinutes = s.rainForecast.leadTimeOption.minutes,
                rainProbabilityThresholdPercent = s.rainForecast.probabilityThresholdPercent
            )
        )
    }

    // =========================================================================
    // Feature 2: เรียงลำดับจุดส่งอัตโนมัติ (Delivery Stops & Route Optimizer)
    // =========================================================================

    fun addDeliveryStop(name: String, latitude: Double, longitude: Double): Boolean {
        val current = _uiState.value.deliveryStops
        if (current.size >= DeliveryStopItem.MAX_STOPS) {
            _uiState.update {
                it.copy(statusBannerMessageTh = "เพิ่มจุดส่งได้สูงสุด ${DeliveryStopItem.MAX_STOPS} จุด")
            }
            return false
        }
        val cleanName = name.trim().ifBlank { "จุดส่งที่ #${current.size + 1}" }
        val newStop = DeliveryStopItem(
            id = "STOP-${System.currentTimeMillis()}-${current.size + 1}",
            name = cleanName,
            latitude = latitude.coerceIn(-85.0, 85.0),
            longitude = longitude.coerceIn(-180.0, 180.0),
            isDelivered = false,
            sequenceOrder = current.count { !it.isDelivered } + 1
        )
        val combined = current + newStop
        val recalc = RouteOptimizer.recalculateCurrentOrderDistances(
            _uiState.value.telemetry.latitude,
            _uiState.value.telemetry.longitude,
            combined
        )
        persistDeliveryStops(recalc.orderedStops)
        _uiState.update {
            it.copy(
                deliveryStops = recalc.orderedStops,
                totalDeliveryRouteKm = recalc.totalPendingDistanceKm,
                statusBannerMessageTh = "เพิ่มจุดส่ง \"$cleanName\" เรียบร้อยแล้ว"
            )
        }
        return true
    }

    fun addDeliveryStopFromCoordinatesText(rawCoordinateText: String, name: String): Boolean {
        val parsed = GeoMath.parseCoordinateInput(rawCoordinateText) ?: return false
        return addDeliveryStop(name, parsed.first, parsed.second)
    }

    fun editDeliveryStop(id: String, newName: String, newLatitude: Double, newLongitude: Double): Boolean {
        if (newLatitude !in -90.0..90.0 || newLongitude !in -180.0..180.0) return false
        val updated = _uiState.value.deliveryStops.map { stop ->
            if (stop.id == id) {
                stop.copy(
                    name = newName.trim().ifBlank { stop.name },
                    latitude = newLatitude,
                    longitude = newLongitude
                )
            } else {
                stop
            }
        }
        val recalc = RouteOptimizer.recalculateCurrentOrderDistances(
            _uiState.value.telemetry.latitude,
            _uiState.value.telemetry.longitude,
            updated
        )
        persistDeliveryStops(recalc.orderedStops)
        _uiState.update {
            it.copy(
                deliveryStops = recalc.orderedStops,
                totalDeliveryRouteKm = recalc.totalPendingDistanceKm,
                statusBannerMessageTh = "แก้ไขข้อมูลจุดส่งเรียบร้อยแล้ว"
            )
        }
        return true
    }

    fun toggleDeliveryStopDelivered(id: String) {
        val updated = _uiState.value.deliveryStops.map { stop ->
            if (stop.id == id) stop.copy(isDelivered = !stop.isDelivered) else stop
        }
        val recalc = RouteOptimizer.recalculateCurrentOrderDistances(
            _uiState.value.telemetry.latitude,
            _uiState.value.telemetry.longitude,
            updated
        )
        persistDeliveryStops(recalc.orderedStops)
        _uiState.update {
            it.copy(
                deliveryStops = recalc.orderedStops,
                totalDeliveryRouteKm = recalc.totalPendingDistanceKm
            )
        }
    }

    fun deleteDeliveryStop(id: String) {
        val updated = _uiState.value.deliveryStops.filterNot { it.id == id }
        val recalc = RouteOptimizer.recalculateCurrentOrderDistances(
            _uiState.value.telemetry.latitude,
            _uiState.value.telemetry.longitude,
            updated
        )
        persistDeliveryStops(recalc.orderedStops)
        _uiState.update {
            it.copy(
                deliveryStops = recalc.orderedStops,
                totalDeliveryRouteKm = recalc.totalPendingDistanceKm,
                statusBannerMessageTh = "ลบจุดส่งเรียบร้อยแล้ว"
            )
        }
    }

    fun clearAllDeliveryStops() {
        persistDeliveryStops(emptyList())
        _uiState.update {
            it.copy(
                deliveryStops = emptyList(),
                totalDeliveryRouteKm = 0.0,
                statusBannerMessageTh = "ล้างรายการจุดส่งทั้งหมดแล้ว"
            )
        }
    }

    fun optimizeDeliveryRouteFromCurrentGps() {
        val stops = _uiState.value.deliveryStops
        if (stops.none { !it.isDelivered }) {
            _uiState.update {
                it.copy(statusBannerMessageTh = "ไม่มีจุดส่งที่รอดำเนินการสำหรับการเรียงเส้นทาง")
            }
            return
        }
        val startLat = _uiState.value.telemetry.latitude
            ?: _uiState.value.activePin?.latitude
            ?: stops.first().latitude
        val startLng = _uiState.value.telemetry.longitude
            ?: _uiState.value.activePin?.longitude
            ?: stops.first().longitude

        val result = RouteOptimizer.optimizeStops(startLat, startLng, stops)
        persistDeliveryStops(result.orderedStops)
        _uiState.update {
            it.copy(
                deliveryStops = result.orderedStops,
                totalDeliveryRouteKm = result.totalPendingDistanceKm,
                statusBannerMessageTh = String.format(
                    Locale.US,
                    "เรียงเส้นทางอัตโนมัติสำเร็จ (ระยะรวมโดยประมาณ %.2f กม.)",
                    result.totalPendingDistanceKm
                )
            )
        }
    }

    private fun persistDeliveryStops(stops: List<DeliveryStopItem>) {
        prefs?.edit()?.putString(KEY_DELIVERY_STOPS, DeliveryStopItem.serializeList(stops))?.apply()
    }

    // =========================================================================
    // Feature 3: คำนวณจุดคุ้มทุนรายวัน (Daily Break-Even & Profit)
    // =========================================================================

    fun updateFixedDailyCosts(fuelCostBaht: Double) {
        checkMidnightBreakEvenReset()
        val safeFuel = fuelCostBaht.coerceAtLeast(0.0)
        prefs?.edit()
            ?.putFloat(KEY_DAILY_FUEL_COST, safeFuel.toFloat())
            ?.apply()
        _uiState.update { state ->
            state.copy(
                breakEvenState = state.breakEvenState.copy(
                    fuelCostBaht = safeFuel
                ),
                statusBannerMessageTh = "บันทึกค่าน้ำมันรายวันเรียบร้อยแล้ว"
            )
        }
    }

    fun addCustomExpense(title: String, amountBaht: Double): Boolean {
        if (amountBaht <= 0.0) return false
        checkMidnightBreakEvenReset()
        val cleanTitle = title.trim().ifBlank { "ค่าใช้จ่ายอื่นๆ" }
        val newItem = CustomExpenseItem(
            id = "EXP-${System.currentTimeMillis()}",
            title = cleanTitle,
            amountBaht = amountBaht
        )
        val updatedList = _uiState.value.breakEvenState.customExpenses + newItem
        prefs?.edit()?.putString(KEY_CUSTOM_EXPENSES, CustomExpenseItem.serializeList(updatedList))?.apply()
        _uiState.update { state ->
            state.copy(
                breakEvenState = state.breakEvenState.copy(customExpenses = updatedList),
                statusBannerMessageTh = "เพิ่มค่าใช้จ่าย \"$cleanTitle\" เรียบร้อยแล้ว"
            )
        }
        return true
    }

    fun deleteCustomExpense(id: String) {
        val updatedList = _uiState.value.breakEvenState.customExpenses.filterNot { it.id == id }
        prefs?.edit()?.putString(KEY_CUSTOM_EXPENSES, CustomExpenseItem.serializeList(updatedList))?.apply()
        _uiState.update { state ->
            state.copy(
                breakEvenState = state.breakEvenState.copy(customExpenses = updatedList)
            )
        }
    }

    fun addJobIncome(label: String, amountBaht: Double): Boolean {
        if (amountBaht <= 0.0) return false
        checkMidnightBreakEvenReset()
        val currentJobs = _uiState.value.breakEvenState.jobIncomes
        val cleanLabel = label.trim().ifBlank { "งานที่ #${currentJobs.size + 1}" }
        val entry = JobIncomeEntry(
            id = "JOB-${System.currentTimeMillis()}",
            label = cleanLabel,
            amountBaht = amountBaht,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedJobs = listOf(entry) + currentJobs
        prefs?.edit()?.putString(KEY_JOB_INCOMES, JobIncomeEntry.serializeList(updatedJobs))?.apply()
        _uiState.update { state ->
            val nextBreakEven = state.breakEvenState.copy(jobIncomes = updatedJobs)
            state.copy(
                breakEvenState = nextBreakEven,
                statusBannerMessageTh = if (nextBreakEven.hasReachedBreakEven) {
                    String.format(Locale.US, "เริ่มมีกำไร! กำไรสุทธิวันนี้ +฿%.0f", nextBreakEven.netProfitBaht)
                } else {
                    String.format(Locale.US, "เพิ่มรายได้ +฿%.0f แล้ว", amountBaht)
                }
            )
        }
        return true
    }

    fun deleteJobIncome(id: String) {
        val updatedJobs = _uiState.value.breakEvenState.jobIncomes.filterNot { it.id == id }
        prefs?.edit()?.putString(KEY_JOB_INCOMES, JobIncomeEntry.serializeList(updatedJobs))?.apply()
        _uiState.update { state ->
            state.copy(
                breakEvenState = state.breakEvenState.copy(jobIncomes = updatedJobs)
            )
        }
    }

    // =========================================================================
    // Feature 4: ประเมินน้ำมันที่เหลือ (Fuel Estimator)
    // =========================================================================

    fun updateFuelSettings(
        tankCapacityLiters: Float,
        consumptionKmPerLiter: Float,
        lowFuelWarningLiters: Float
    ) {
        val safeCap = tankCapacityLiters.coerceIn(0.5f, 200f)
        val safeCons = consumptionKmPerLiter.coerceIn(1.0f, 120f)
        val safeWarn = lowFuelWarningLiters.coerceIn(0.1f, safeCap)

        prefs?.edit()
            ?.putFloat(KEY_FUEL_TANK_LITERS, safeCap)
            ?.putFloat(KEY_FUEL_KM_PER_LITER, safeCons)
            ?.putFloat(KEY_FUEL_LOW_WARN_LITERS, safeWarn)
            ?.apply()

        val currentFuel = _uiState.value.fuelState
        val updated = currentFuel.copy(
            tankCapacityLiters = safeCap,
            consumptionKmPerLiter = safeCons,
            lowFuelWarningLiters = safeWarn
        )
        _uiState.update {
            it.copy(
                fuelState = updated,
                statusBannerMessageTh = "บันทึกค่าถังน้ำมันและอัตราสิ้นเปลืองเรียบร้อยแล้ว"
            )
        }
    }

    fun markRefueledFullTank() {
        prefs?.edit()?.putFloat(KEY_FUEL_DIST_DRIVEN_KM, 0f)?.apply()
        _uiState.update { state ->
            state.copy(
                fuelState = state.fuelState.copy(
                    distanceDrivenSinceRefuelKm = 0.0,
                    lowFuelAlertTriggered = false
                ),
                statusBannerMessageTh = "รีเซ็ตปริมาณน้ำมันเต็มถังเรียบร้อยแล้ว"
            )
        }
    }

    // =========================================================================
    // Feature 5: หมุดปั๊มน้ำมันและจุดพัก (Fuel Stations & Rest Stops + Overpass)
    // =========================================================================

    fun addCustomPoi(name: String, category: StopPoiCategory, latitude: Double, longitude: Double): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        val cleanName = name.trim().ifBlank { category.labelTh }
        val item = StopPoiItem(
            id = "POI-${System.currentTimeMillis()}",
            name = cleanName,
            category = category,
            latitude = latitude,
            longitude = longitude,
            isUserSaved = true,
            timestampMillis = System.currentTimeMillis()
        )
        val updated = listOf(item) + _uiState.value.savedPois
        prefs?.edit()?.putString(KEY_SAVED_POIS, StopPoiItem.serializeList(updated))?.apply()
        _uiState.update {
            it.copy(
                savedPois = updated,
                statusBannerMessageTh = "บันทึก${category.labelTh} \"$cleanName\" เรียบร้อยแล้ว"
            )
        }
        return true
    }

    fun deleteSavedPoi(id: String) {
        val updated = _uiState.value.savedPois.filterNot { it.id == id }
        prefs?.edit()?.putString(KEY_SAVED_POIS, StopPoiItem.serializeList(updated))?.apply()
        _uiState.update {
            it.copy(
                savedPois = updated,
                statusBannerMessageTh = "ลบหมุดจุดแวะพักเรียบร้อยแล้ว"
            )
        }
    }

    fun saveOverpassStationToMyPins(poi: StopPoiItem) {
        if (_uiState.value.savedPois.none { it.id == poi.id }) {
            val savedPoi = poi.copy(isUserSaved = true, timestampMillis = System.currentTimeMillis())
            val updatedPois = listOf(savedPoi) + _uiState.value.savedPois
            prefs?.edit()?.putString(KEY_SAVED_POIS, StopPoiItem.serializeList(updatedPois))?.apply()
            _uiState.update { it.copy(savedPois = updatedPois) }
        }
        val newPin = SavedLocationItem(
            id = "LOC-${System.currentTimeMillis()}",
            name = poi.name,
            latitude = poi.latitude,
            longitude = poi.longitude,
            accuracyMeters = null,
            altitudeMeters = null,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedPins = listOf(newPin) + _uiState.value.savedLocations
        persistPinsAndActiveId(updatedPins, _uiState.value.activePinId ?: newPin.id)
        _uiState.update {
            it.copy(
                savedLocations = updatedPins,
                activePinId = it.activePinId ?: newPin.id,
                statusBannerMessageTh = "บันทึก \"${poi.name}\" เป็นหมุดของฉันเรียบร้อยแล้ว"
            )
        }
    }

    fun setOverpassSearchRadiusKm(radiusKm: Int) {
        val valid = if (radiusKm in listOf(2, 5, 10)) radiusKm else 5
        prefs?.edit()?.putInt(KEY_OVERPASS_RADIUS_KM, valid)?.apply()
        _uiState.update { it.copy(overpassSearchRadiusKm = valid) }
    }

    fun searchNearbyFuelStations(forceRefresh: Boolean = false) {
        val lat = _uiState.value.telemetry.latitude ?: _uiState.value.activePin?.latitude
        val lng = _uiState.value.telemetry.longitude ?: _uiState.value.activePin?.longitude
        if (lat == null || lng == null) {
            _uiState.update {
                it.copy(
                    overpassErrorOrStatusTh = "กรุณาเปิดล็อกสัญญาณ GPS หรือเลือกหมุดก่อนค้นหาปั๊มน้ำมันใกล้ฉัน"
                )
            }
            return
        }

        val state = _uiState.value
        val now = System.currentTimeMillis()
        val cacheAgeMs = now - state.overpassCacheTimestampMillis
        val movedKm = if (state.overpassLastSearchLat != null && state.overpassLastSearchLng != null) {
            GeoMath.haversineKm(state.overpassLastSearchLat, state.overpassLastSearchLng, lat, lng)
        } else {
            Double.MAX_VALUE
        }

        if (!forceRefresh &&
            state.nearbyOverpassFuelPois.isNotEmpty() &&
            cacheAgeMs < 24 * 60 * 60 * 1000L &&
            movedKm < 2.0
        ) {
            _uiState.update {
                it.copy(overpassErrorOrStatusTh = "แสดงข้อมูลจากแคช (ไม่เกิน 24 ชม.) • กดรีเฟรชเพื่ออัปเดตใหม่")
            }
            return
        }

        val radiusKm = state.overpassSearchRadiusKm
        _uiState.update {
            it.copy(
                isSearchingOverpass = true,
                overpassErrorOrStatusTh = "กำลังค้นหาปั๊มน้ำมันในรัศมี $radiusKm กม. จาก Overpass API..."
            )
        }

        viewModelScope.launch {
            when (val res = NetworkClients.fetchNearbyFuelStationsOverpass(lat, lng, radiusKm)) {
                is OverpassFuelResult.Success -> {
                    val sorted = res.stations.sortedBy {
                        GeoMath.haversineMeters(lat, lng, it.latitude, it.longitude)
                    }
                    val ts = System.currentTimeMillis()
                    prefs?.edit()
                        ?.putString(KEY_OVERPASS_CACHE_ITEMS, StopPoiItem.serializeList(sorted))
                        ?.putLong(KEY_OVERPASS_CACHE_TS, ts)
                        ?.putString(KEY_OVERPASS_LAST_LAT, lat.toString())
                        ?.putString(KEY_OVERPASS_LAST_LNG, lng.toString())
                        ?.apply()
                    _uiState.update {
                        it.copy(
                            isSearchingOverpass = false,
                            nearbyOverpassFuelPois = sorted,
                            overpassCacheTimestampMillis = ts,
                            overpassLastSearchLat = lat,
                            overpassLastSearchLng = lng,
                            overpassErrorOrStatusTh = if (sorted.isEmpty()) {
                                "ไม่พบปั๊มน้ำมันในรัศมี $radiusKm กม."
                            } else {
                                "พบปั๊มน้ำมัน ${sorted.size} แห่งในรัศมี $radiusKm กม."
                            }
                        )
                    }
                }
                is OverpassFuelResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSearchingOverpass = false,
                            overpassErrorOrStatusTh = res.reasonTh
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // Feature 6: เตือนฝนใกล้ตก (Open-Meteo Rain Forecast & Alert)
    // =========================================================================

    fun setRainAlertEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_RAIN_ALERT_ENABLED, enabled)?.apply()
        _uiState.update { state ->
            state.copy(
                rainForecast = state.rainForecast.copy(alertEnabled = enabled),
                statusBannerMessageTh = if (enabled) "เปิดการแจ้งเตือนฝนใกล้ตกแล้ว" else "ปิดการแจ้งเตือนฝนใกล้ตกแล้ว"
            )
        }
        syncZoneConfigToBus()
        if (enabled) {
            refreshRainForecast(force = true)
        }
    }

    fun setRainLeadTimeOption(option: RainLeadTimeOption) {
        prefs?.edit()?.putString(KEY_RAIN_LEAD_OPTION, option.name)?.apply()
        _uiState.update { state ->
            state.copy(
                rainForecast = state.rainForecast.copy(leadTimeOption = option),
                statusBannerMessageTh = "ตั้งเวลาเตือนฝนล่วงหน้าเป็น ${option.labelTh}"
            )
        }
        syncZoneConfigToBus()
        refreshRainForecast(force = true)
    }

    fun setRainProbabilityThreshold(percent: Int) {
        val clamped = percent.coerceIn(10, 95)
        prefs?.edit()?.putInt(KEY_RAIN_PROB_THRESHOLD, clamped)?.apply()
        _uiState.update { state ->
            state.copy(
                rainForecast = state.rainForecast.copy(probabilityThresholdPercent = clamped)
            )
        }
        syncZoneConfigToBus()
    }

    fun refreshRainForecast(force: Boolean = false) {
        val lat = _uiState.value.telemetry.latitude ?: _uiState.value.activePin?.latitude
        val lng = _uiState.value.telemetry.longitude ?: _uiState.value.activePin?.longitude
        if (lat == null || lng == null) {
            _uiState.update { state ->
                state.copy(
                    rainForecast = state.rainForecast.copy(
                        status = WeatherForecastStatus.IDLE,
                        errorMessageTh = null
                    )
                )
            }
            return
        }

        val currentRf = _uiState.value.rainForecast
        if (!force && currentRf.status == WeatherForecastStatus.LOADING) return

        _uiState.update { state ->
            state.copy(
                rainForecast = state.rainForecast.copy(
                    status = WeatherForecastStatus.LOADING,
                    errorMessageTh = null
                )
            )
        }

        viewModelScope.launch {
            val threshold = _uiState.value.rainForecast.probabilityThresholdPercent
            val leadMinutes = _uiState.value.rainForecast.leadTimeOption.minutes
            when (val res = NetworkClients.fetchRainForecastOpenMeteo(lat, lng, threshold, leadMinutes)) {
                is OpenMeteoResult.Success -> {
                    val fc = res.forecast
                    val nextStatus = if (fc.willRainSoon) {
                        WeatherForecastStatus.RAIN_COMING
                    } else {
                        WeatherForecastStatus.NO_RAIN
                    }
                    _uiState.update { state ->
                        state.copy(
                            rainForecast = state.rainForecast.copy(
                                status = nextStatus,
                                minutesUntilRain = fc.minutesUntilRain,
                                maxRainProbabilityPercent = fc.probabilityPercent,
                                expectedPrecipitationMm = fc.precipitationMm,
                                lastUpdatedEpochMillis = fc.fetchedAtMillis,
                                lastFetchedLat = lat,
                                lastFetchedLng = lng,
                                errorMessageTh = null
                            )
                        )
                    }

                    // Trigger vibration + notification if rain is within user's lead-time window (max once per 30 mins)
                    val s = _uiState.value
                    val mins = fc.minutesUntilRain ?: leadMinutes
                    if (fc.willRainSoon && s.rainForecast.alertEnabled && mins <= leadMinutes) {
                        val now = System.currentTimeMillis()
                        if (GpsServiceRuntimeBus.recordRainAlertFired(now)) {
                            _uiState.update { st ->
                                st.copy(
                                    rainForecast = st.rainForecast.copy(lastAlertFiredEpochMillis = now),
                                    statusBannerMessageTh = "ฝนใกล้ตก เตรียมเสื้อกันฝน (อีก $mins นาที โอกาส ${fc.probabilityPercent}%)"
                                )
                            }
                            appContextRef?.let { ctx ->
                                TacticalSoundEngine.triggerRainApproachingWarning(
                                    context = ctx,
                                    vibrationEnabled = s.alertVibrationEnabled,
                                    soundEnabled = s.alertSoundEnabled,
                                    volumePercent = s.alertVolumePercent
                                )
                                GpsServiceRuntimeBus.postRainNotification(
                                    context = ctx,
                                    minutesUntilRain = mins,
                                    probabilityPercent = fc.probabilityPercent
                                )
                            }
                        }
                    }
                }
                is OpenMeteoResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            rainForecast = state.rainForecast.copy(
                                status = WeatherForecastStatus.ERROR,
                                errorMessageTh = "ดึงพยากรณ์ไม่ได้"
                            )
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // Existing Pin & Zone Functions (Preserved 100%)
    // =========================================================================

    fun saveCurrentLocation(customName: String, moveActivePinInstead: Boolean = false): Boolean {
        val currentTelemetry = _uiState.value.telemetry
        val lat = currentTelemetry.latitude ?: return false
        val lng = currentTelemetry.longitude ?: return false

        val existingActive = _uiState.value.activePin
        if (moveActivePinInstead && existingActive != null) {
            return moveSavedPin(
                id = existingActive.id,
                newLatitude = lat,
                newLongitude = lng,
                newName = customName.trim().takeIf { it.isNotEmpty() }
            )
        }

        val cleanName = customName.trim().ifBlank {
            "หมุดโซนรับงาน #${_uiState.value.savedLocations.size + 1}"
        }
        val newItem = SavedLocationItem(
            id = "LOC-${System.currentTimeMillis()}",
            name = cleanName,
            latitude = lat,
            longitude = lng,
            accuracyMeters = currentTelemetry.accuracyMeters,
            altitudeMeters = currentTelemetry.altitudeMeters,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedList = listOf(newItem) + _uiState.value.savedLocations
        persistPinsAndActiveId(updatedList, newItem.id)

        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = newItem.id,
                statusBannerMessageTh = "ปักหมุดและเลือกใช้งาน \"$cleanName\" เรียบร้อยแล้ว"
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        return true
    }

    fun onMapTapPin(latitude: Double, longitude: Double, customName: String = "") {
        val clampedLat = latitude.coerceIn(-85.0, 85.0)
        val clampedLng = longitude.coerceIn(-180.0, 180.0)
        val currentActive = _uiState.value.activePin
        val mode = _uiState.value.mapTapActionMode

        if (mode == MapTapActionMode.MOVE_ACTIVE_PIN && currentActive != null) {
            moveSavedPin(
                id = currentActive.id,
                newLatitude = clampedLat,
                newLongitude = clampedLng,
                newName = customName.trim().takeIf { it.isNotEmpty() }
            )
        } else {
            val cleanName = customName.trim().ifBlank {
                "หมุดแผนที่ #${_uiState.value.savedLocations.size + 1}"
            }
            val newItem = SavedLocationItem(
                id = "LOC-${System.currentTimeMillis()}",
                name = cleanName,
                latitude = clampedLat,
                longitude = clampedLng,
                accuracyMeters = null,
                altitudeMeters = null,
                timestampMillis = System.currentTimeMillis()
            )
            val updatedList = listOf(newItem) + _uiState.value.savedLocations
            persistPinsAndActiveId(updatedList, newItem.id)
            _uiState.update {
                it.copy(
                    savedLocations = updatedList,
                    activePinId = newItem.id,
                    statusBannerMessageTh = "ปักหมุด \"$cleanName\" จากแผนที่เรียบร้อยแล้ว"
                )
            }
            syncZoneConfigToBus()
            recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        }
    }

    fun pinOrMoveFromCoordinatesText(
        rawCoordinateText: String,
        customName: String,
        moveActivePinInstead: Boolean = false
    ): Boolean {
        val parsed = GeoMath.parseCoordinateInput(rawCoordinateText) ?: return false
        val (lat, lng) = parsed
        val currentActive = _uiState.value.activePin

        if (moveActivePinInstead && currentActive != null) {
            return moveSavedPin(
                id = currentActive.id,
                newLatitude = lat,
                newLongitude = lng,
                newName = customName.trim().takeIf { it.isNotEmpty() }
            )
        }

        val cleanName = customName.trim().ifBlank {
            "หมุดพิกัด #${_uiState.value.savedLocations.size + 1}"
        }
        val newItem = SavedLocationItem(
            id = "LOC-${System.currentTimeMillis()}",
            name = cleanName,
            latitude = lat,
            longitude = lng,
            accuracyMeters = null,
            altitudeMeters = null,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedList = listOf(newItem) + _uiState.value.savedLocations
        persistPinsAndActiveId(updatedList, newItem.id)

        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = newItem.id,
                statusBannerMessageTh = "ปักหมุด \"$cleanName\" จากพิกัดที่วางเรียบร้อยแล้ว"
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        return true
    }

    fun selectActivePin(id: String) {
        val target = _uiState.value.savedLocations.firstOrNull { it.id == id } ?: return
        prefs?.edit()?.putString(KEY_ACTIVE_PIN_ID, id)?.apply()
        _uiState.update {
            it.copy(
                activePinId = id,
                isZoneAlertMuted = false,
                statusBannerMessageTh = "เลือกหมุดใช้งาน: \"${target.name}\""
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
    }

    fun moveSavedPin(
        id: String,
        newLatitude: Double,
        newLongitude: Double,
        newName: String? = null
    ): Boolean {
        if (newLatitude !in -90.0..90.0 || newLongitude !in -180.0..180.0) return false
        val currentList = _uiState.value.savedLocations
        if (currentList.none { it.id == id }) return false

        var movedName = ""
        val updatedList = currentList.map { item ->
            if (item.id == id) {
                val resolvedName = newName?.trim()?.takeIf { it.isNotEmpty() } ?: item.name
                movedName = resolvedName
                item.copy(
                    name = resolvedName,
                    latitude = newLatitude,
                    longitude = newLongitude,
                    timestampMillis = System.currentTimeMillis()
                )
            } else {
                item
            }
        }
        persistPinsAndActiveId(updatedList, _uiState.value.activePinId ?: id)
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = it.activePinId ?: id,
                statusBannerMessageTh = "ย้ายตำแหน่งหมุด \"$movedName\" เรียบร้อยแล้ว"
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        return true
    }

    fun renameSavedPin(id: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        val updatedList = _uiState.value.savedLocations.map { item ->
            if (item.id == id) item.copy(name = clean) else item
        }
        persistPinsAndActiveId(updatedList, _uiState.value.activePinId)
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                statusBannerMessageTh = "เปลี่ยนชื่อหมุดเป็น \"$clean\" เรียบร้อยแล้ว"
            )
        }
        syncZoneConfigToBus()
        return true
    }

    fun deleteSavedLocation(id: String) {
        val updatedList = _uiState.value.savedLocations.filterNot { it.id == id }
        val nextActiveId = if (_uiState.value.activePinId == id) {
            updatedList.firstOrNull()?.id
        } else {
            _uiState.value.activePinId
        }
        persistPinsAndActiveId(updatedList, nextActiveId)
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = nextActiveId,
                statusBannerMessageTh = "ลบหมุดที่บันทึกเรียบร้อยแล้ว"
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = false)
    }

    private fun persistPinsAndActiveId(list: List<SavedLocationItem>, activeId: String?) {
        prefs?.edit()?.apply {
            putString(KEY_SAVED_LOCATIONS, SavedLocationItem.serializeList(list))
            if (activeId != null) {
                putString(KEY_ACTIVE_PIN_ID, activeId)
            } else {
                remove(KEY_ACTIVE_PIN_ID)
            }
            apply()
        }
    }

    fun setZoneRadiusKm(radiusKm: Float) {
        val rounded = ((radiusKm.coerceIn(0.0f, 30.0f) * 10f).roundToInt() / 10f)
        prefs?.edit()?.putFloat(KEY_ZONE_RADIUS_KM, rounded)?.apply()
        _uiState.update {
            it.copy(zoneRadiusKm = rounded)
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
    }

    fun setGpsBufferMeters(bufferMeters: Int) {
        val clamped = bufferMeters.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_GPS_BUFFER_METERS, clamped)?.apply()
        _uiState.update {
            it.copy(
                gpsBufferMeters = clamped,
                statusBannerMessageTh = "ตั้งค่าเผื่อสัญญาณ GPS (Buffer) เป็น $clamped เมตร"
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
    }

    fun setAlertVibrationEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ALERT_VIBRATION, enabled)?.apply()
        _uiState.update {
            it.copy(
                alertVibrationEnabled = enabled,
                statusBannerMessageTh = if (enabled) "เปิดการสั่นแจ้งเตือนโซนแล้ว" else "ปิดการสั่นแจ้งเตือนโซนแล้ว"
            )
        }
        syncZoneConfigToBus()
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ALERT_SOUND, enabled)?.apply()
        _uiState.update {
            it.copy(
                alertSoundEnabled = enabled,
                statusBannerMessageTh = if (enabled) "เปิดเสียงแจ้งเตือนโซนแล้ว" else "ปิดเสียงแจ้งเตือนโซนแล้ว"
            )
        }
        syncZoneConfigToBus()
    }

    fun setAlertTone(tone: ZoneAlertTone, playPreview: Boolean = true) {
        prefs?.edit()?.putString(KEY_ALERT_TONE, tone.name)?.apply()
        _uiState.update {
            it.copy(
                alertTone = tone,
                statusBannerMessageTh = "เลือกเสียงเตือน: ${tone.titleTh}"
            )
        }
        syncZoneConfigToBus()
        if (playPreview) {
            TacticalSoundEngine.playZoneAlertTone(
                soundEnabled = true,
                tone = tone,
                volumePercent = _uiState.value.alertVolumePercent.coerceAtLeast(40)
            )
        }
    }

    fun setAlertVolumePercent(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_ALERT_VOLUME, clamped)?.apply()
        _uiState.update {
            it.copy(alertVolumePercent = clamped)
        }
        syncZoneConfigToBus()
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        prefs?.edit()?.putString(KEY_MEASUREMENT_UNIT, unit.name)?.apply()
        _uiState.update {
            it.copy(
                measurementUnit = unit,
                statusBannerMessageTh = "เปลี่ยนหน่วยวัดเป็น ${unit.titleTh}"
            )
        }
    }

    fun setUpdateFrequency(context: Context, frequency: UpdateFrequency) {
        prefs?.edit()?.putString(KEY_UPDATE_FREQUENCY, frequency.name)?.apply()
        _uiState.update {
            it.copy(
                updateFrequency = frequency,
                statusBannerMessageTh = "ตั้งความถี่การอัปเดตเป็น ${frequency.titleTh}"
            )
        }
        if (_uiState.value.isLockEnabled) {
            startGpsLockEngine(context)
        }
    }

    fun setCoordinateFormat(format: CoordinateFormat) {
        prefs?.edit()?.putString(KEY_COORDINATE_FORMAT, format.name)?.apply()
        _uiState.update {
            it.copy(
                coordinateFormat = format,
                statusBannerMessageTh = "เปลี่ยนรูปแบบพิกัดเป็น ${format.titleTh}"
            )
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, enabled)?.apply()
        _uiState.update {
            it.copy(
                keepScreenOn = enabled,
                statusBannerMessageTh = if (enabled) "เปิดโหมดหน้าจอค้างไว้เสมอ" else "ปิดโหมดหน้าจอค้างไว้เสมอ"
            )
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessageTh = null) }
    }

    fun onHardwareSensorFixReceived(location: Location) {
        _uiState.update { it.copy(isLockEnabled = true) }
        applyHardwareLocation(location)
    }

    override fun onCleared() {
        super.onCleared()
        stopViewModelRepeatAlertLoop()
        periodicMaintenanceJob?.cancel()
        try {
            locationService?.stopLocationUpdates()
        } catch (_: Throwable) {}
    }

    companion object {
        private const val PREFS_NAME = "crimson_fixpoint_prefs"
        private const val KEY_LOCK_ENABLED = "key_lock_enabled"
        private const val KEY_MEASUREMENT_UNIT = "key_measurement_unit"
        private const val KEY_UPDATE_FREQUENCY = "key_update_frequency"
        private const val KEY_COORDINATE_FORMAT = "key_coordinate_format"
        private const val KEY_KEEP_SCREEN_ON = "key_keep_screen_on"
        private const val KEY_SAVED_LOCATIONS = "key_saved_locations"
        private const val KEY_ACTIVE_PIN_ID = "key_active_pin_id"
        private const val KEY_ZONE_RADIUS_KM = "key_zone_radius_km"
        private const val KEY_GPS_BUFFER_METERS = "key_gps_buffer_meters"
        private const val KEY_ALERT_VIBRATION = "key_alert_vibration"
        private const val KEY_ALERT_SOUND = "key_alert_sound"
        private const val KEY_ALERT_TONE = "key_alert_tone"
        private const val KEY_ALERT_VOLUME = "key_alert_volume"
        // v1.0.1 keys
        private const val KEY_DELIVERY_STOPS = "key_delivery_stops"
        private const val KEY_BREAKEVEN_DATE = "key_breakeven_date"
        private const val KEY_CAR_INSTALLMENT = "key_car_installment"
        private const val KEY_DAILY_FUEL_COST = "key_daily_fuel_cost"
        private const val KEY_CUSTOM_EXPENSES = "key_custom_expenses"
        private const val KEY_JOB_INCOMES = "key_job_incomes"
        private const val KEY_FINANCIAL_HISTORY = "key_financial_history"
        private const val KEY_FUEL_TANK_LITERS = "key_fuel_tank_liters"
        private const val KEY_FUEL_KM_PER_LITER = "key_fuel_km_per_liter"
        private const val KEY_FUEL_DIST_DRIVEN_KM = "key_fuel_dist_driven_km"
        private const val KEY_FUEL_LOW_WARN_LITERS = "key_fuel_low_warn_liters"
        private const val KEY_SAVED_POIS = "key_saved_pois"
        private const val KEY_OVERPASS_CACHE_ITEMS = "key_overpass_cache_items"
        private const val KEY_OVERPASS_CACHE_TS = "key_overpass_cache_ts"
        private const val KEY_OVERPASS_LAST_LAT = "key_overpass_last_lat"
        private const val KEY_OVERPASS_LAST_LNG = "key_overpass_last_lng"
        private const val KEY_OVERPASS_RADIUS_KM = "key_overpass_radius_km"
        private const val KEY_RAIN_ALERT_ENABLED = "key_rain_alert_enabled"
        private const val KEY_RAIN_LEAD_OPTION = "key_rain_lead_option"
        private const val KEY_RAIN_PROB_THRESHOLD = "key_rain_prob_threshold"
    }
}
