package com.example.viewmodel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MlKitOrderOcrEngine
import com.example.ai.OcrScanOutcome
import com.example.ai.OrderOcrParser
import com.example.ai.RiderIntelligenceEngine
import com.example.data.local.StoredRiderPreferences
import com.example.data.repository.FixPointRepository
import com.example.integration.DeliveryAdapterRegistry
import com.example.integration.JobRadarEngine
import com.example.model.AnalyzedOrderRecord
import com.example.model.AppScreen
import com.example.model.CloudSyncState
import com.example.model.CoordinateFormat
import com.example.model.CustomExpenseItem
import com.example.model.DailyBreakEvenState
import com.example.model.DailyWorkMetrics
import com.example.model.DeliveryPlatform
import com.example.model.DetectedJobOffer
import com.example.model.EarningsGoals
import com.example.model.EarningsHistoryFilter
import com.example.model.EarningsPeriod
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.FuelEstimateState
import com.example.model.GeoMath
import com.example.model.GpsLockState
import com.example.model.JobIncomeEntry
import com.example.model.JobOfferSource
import com.example.model.JobOfferStatus
import com.example.model.JobRankingMode
import com.example.model.LiveGnssTelemetry
import com.example.model.MapLayerFilters
import com.example.model.MapTapActionMode
import com.example.model.MeasurementUnit
import com.example.model.OrderAnalysisResult
import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft
import com.example.model.PinCategory
import com.example.model.PlatformConnectionStatus
import com.example.model.PrivacyPreferences
import com.example.model.RiderProfileConfig
import com.example.model.SatelliteBarItem
import com.example.model.SavedLocationItem
import com.example.model.ScreenReaderState
import com.example.model.SecondaryScreen
import com.example.model.SmartEarningsCalculator
import com.example.model.SmartEarningsState
import com.example.model.StopPoiItem
import com.example.model.UpdateFrequency
import com.example.model.WorkSessionRecord
import com.example.model.ZoneMonitorStatus
import com.example.service.GpsKeepAliveService
import com.example.service.GpsServiceRuntimeBus
import com.example.service.JobRadarRuntimeBus
import com.example.service.LocationService
import com.example.service.RawNotificationJobSignal
import com.example.service.ScreenReaderCaptureService
import com.example.service.ScreenReaderRuntimeBus
import com.example.service.ServiceZoneConfig
import com.example.service.SmoothedLocationUpdate
import com.example.util.NetworkClients
import com.example.util.OverpassFuelResult
import com.example.util.TacticalSoundEngine
import com.example.worker.DailyMaintenanceWorker
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
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class FixPointUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val secondaryScreen: SecondaryScreen? = null,
    // Multi-Platform Job Radar (Phase 4)
    val detectedJobOffers: List<DetectedJobOffer> = emptyList(),
    val jobRankingMode: JobRankingMode = JobRankingMode.SMART_BEST_JOB,
    val notificationAccessGranted: Boolean = false,
    val lineManEnabled: Boolean = true,
    val grabEnabled: Boolean = true,
    val shopeeFoodEnabled: Boolean = true,
    val radarClockTickMillis: Long = System.currentTimeMillis(),
    // Cloud Sync Readiness (Phase 6)
    val cloudSyncState: CloudSyncState = CloudSyncState(),
    // GPS Lock & Zone Monitoring (preserved & enhanced)
    val isLockEnabled: Boolean = false,
    val lockState: GpsLockState = GpsLockState.OFF,
    val telemetry: LiveGnssTelemetry = LiveGnssTelemetry(),
    val savedLocations: List<SavedLocationItem> = emptyList(),
    val activePinId: String? = null,
    val zoneRadiusKm: Float = 5.0f,
    val gpsBufferMeters: Int = 20,
    val mapTapActionMode: MapTapActionMode = MapTapActionMode.MOVE_ACTIVE_PIN,
    val newPinCategory: PinCategory = PinCategory.FIXPOINT,
    val mapLayerFilters: MapLayerFilters = MapLayerFilters(),
    val selectedMapPinId: String? = null,
    val zoneStatus: ZoneMonitorStatus = ZoneMonitorStatus.NO_ACTIVE_PIN,
    val currentDistanceFromPinMeters: Double? = null,
    val excessDistanceMeters: Double = 0.0,
    val isZoneAlertMuted: Boolean = false,
    val alertVibrationEnabled: Boolean = true,
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = true,
    // Rider Profile, Privacy & Fuel Intelligence
    val riderProfile: RiderProfileConfig = RiderProfileConfig(),
    val privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    val fuelState: FuelEstimateState = FuelEstimateState(),
    // Earnings & Break-Even (Room-backed)
    val breakEvenState: DailyBreakEvenState = DailyBreakEvenState(),
    val smartEarningsState: SmartEarningsState = SmartEarningsState(),
    // Work Sessions
    val workSessions: List<WorkSessionRecord> = emptyList(),
    val sessionClockTickMillis: Long = System.currentTimeMillis(),
    // AI Assistant, OCR & MediaProjection Screen Reader
    val analyzedOrders: List<AnalyzedOrderRecord> = emptyList(),
    val currentOrderDraft: ParsedOrderDraft = ParsedOrderDraft(),
    val currentOrderAnalysis: OrderAnalysisResult? = null,
    val isRunningOcr: Boolean = false,
    val ocrErrorMessageTh: String? = null,
    val screenReaderState: ScreenReaderState = ScreenReaderState.IDLE,
    val showScreenReaderConsentDialog: Boolean = false,
    // Overpass Optional Fuel Discovery
    val nearbyOverpassFuelPois: List<StopPoiItem> = emptyList(),
    val overpassSearchRadiusKm: Int = 5,
    val isSearchingOverpass: Boolean = false,
    val overpassErrorOrStatusTh: String? = null,
    // System Permissions & Feedback
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

    val selectedMapPin: SavedLocationItem?
        get() = savedLocations.firstOrNull { it.id == selectedMapPinId }

    val activeWorkSession: WorkSessionRecord?
        get() = workSessions.firstOrNull { it.isActive }

    fun isPlatformAdapterEnabled(platform: DeliveryPlatform): Boolean = when (platform) {
        DeliveryPlatform.LINE_MAN -> lineManEnabled
        DeliveryPlatform.GRAB -> grabEnabled
        DeliveryPlatform.SHOPEE_FOOD -> shopeeFoodEnabled
        DeliveryPlatform.OTHER -> true
    }

    val rankedActiveJobOffers: List<DetectedJobOffer>
        get() = JobRadarEngine.rankOffers(
            offers = detectedJobOffers.filter { isPlatformAdapterEnabled(it.platform) },
            mode = jobRankingMode,
            nowMillis = radarClockTickMillis
        )

    val platformConnectionStatuses: List<PlatformConnectionStatus>
        get() = JobRadarEngine.evaluatePlatformStatuses(
            notificationAccessGranted = notificationAccessGranted,
            screenReaderState = screenReaderState,
            offers = detectedJobOffers,
            adapterEnabledMap = mapOf(
                DeliveryPlatform.LINE_MAN to lineManEnabled,
                DeliveryPlatform.GRAB to grabEnabled,
                DeliveryPlatform.SHOPEE_FOOD to shopeeFoodEnabled
            )
        )
}

class FixPointViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FixPointUiState())
    val uiState: StateFlow<FixPointUiState> = _uiState.asStateFlow()

    private var repository: FixPointRepository? = null
    private var locationService: LocationService? = null
    private var appContextRef: Context? = null
    private var isInitializedWithContext = false

    private var viewModelRepeatAlertJob: Job? = null
    private var sessionTickerJob: Job? = null
    private var radarTickerJob: Job? = null
    private var routeNextOcrToRadar: Boolean = false
    private var lastGpsTrackLat: Double? = null
    private var lastGpsTrackLng: Double? = null

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
        viewModelScope.launch {
            ScreenReaderRuntimeBus.state.collectLatest { srState ->
                _uiState.update { it.copy(screenReaderState = srState) }
            }
        }
        viewModelScope.launch {
            ScreenReaderRuntimeBus.statusMessageTh.collectLatest { msg ->
                if (msg != null) {
                    _uiState.update { it.copy(statusBannerMessageTh = msg) }
                    ScreenReaderRuntimeBus.clearMessage()
                }
            }
        }
        viewModelScope.launch {
            ScreenReaderRuntimeBus.capturedBitmaps.collect { bitmap ->
                processOrderBitmap(bitmap, OrderInputSource.SCREEN_READER)
            }
        }
        viewModelScope.launch {
            JobRadarRuntimeBus.incomingSignals.collect { signal ->
                handleIncomingNotificationSignal(signal)
            }
        }
        viewModelScope.launch {
            JobRadarRuntimeBus.isListenerConnected.collectLatest { connected ->
                if (connected) {
                    _uiState.update { it.copy(notificationAccessGranted = true) }
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

        val repo = FixPointRepository(appContext)
        repository = repo
        DailyMaintenanceWorker.schedule(appContext)

        val service = LocationService(appContext)
        locationService = service

        // One-time safe migration from legacy v1.0.1 SharedPreferences into Room + DataStore
        viewModelScope.launch {
            val migrated = runCatching { repo.migrateLegacySharedPrefsIfNeeded() }.getOrDefault(false)
            val snap = runCatching { repo.preferencesStore.getSnapshot() }.getOrNull()
            if (snap != null) {
                applyStoredPreferences(snap)
                if (snap.lockEnabled && _uiState.value.hasAnyLocationPermission) {
                    startGpsLockEngine(appContext)
                }
            }
            if (migrated) {
                _uiState.update {
                    it.copy(statusBannerMessageTh = "โอนย้ายข้อมูลเดิมสู่ฐานข้อมูล Room 2.0 เรียบร้อยแล้ว")
                }
            }
        }

        // Reactive Room & DataStore observers
        viewModelScope.launch {
            repo.preferencesFlow.collectLatest { prefs ->
                applyStoredPreferences(prefs)
            }
        }
        viewModelScope.launch {
            repo.savedPinsFlow.collectLatest { pins ->
                _uiState.update { state ->
                    val resolvedActiveId = state.activePinId?.takeIf { id -> pins.any { it.id == id } }
                        ?: pins.firstOrNull()?.id
                    state.copy(
                        savedLocations = pins,
                        activePinId = resolvedActiveId,
                        zoneStatus = when {
                            resolvedActiveId == null -> ZoneMonitorStatus.NO_ACTIVE_PIN
                            state.telemetry.latitude == null -> ZoneMonitorStatus.WAITING_FOR_GPS
                            else -> state.zoneStatus
                        }
                    )
                }
                syncZoneConfigToBus()
                recalculateZoneEvaluation(triggerAlertsOnTransition = false)
            }
        }
        viewModelScope.launch {
            repo.transactionsFlow.collectLatest { txns ->
                val todayKey = DailyBreakEvenState.todayDateKey()
                val todayTxns = txns.filter { it.dateKey == todayKey }
                val todayFuel = todayTxns
                    .filter { it.type == EarningsRecordType.EXPENSE && it.category.contains("น้ำมัน") }
                    .sumOf { it.amountBaht }
                val todayOtherExp = todayTxns
                    .filter { it.type == EarningsRecordType.EXPENSE && !it.category.contains("น้ำมัน") }
                    .map { CustomExpenseItem(id = it.id, title = it.title, amountBaht = it.amountBaht) }
                val todayJobs = todayTxns
                    .filter { it.type == EarningsRecordType.INCOME }
                    .map { JobIncomeEntry(id = it.id, label = it.title, amountBaht = it.amountBaht, timestampMillis = it.timestampMillis) }
                _uiState.update { state ->
                    state.copy(
                        smartEarningsState = state.smartEarningsState.copy(transactions = txns),
                        breakEvenState = state.breakEvenState.copy(
                            currentDateKey = todayKey,
                            fuelCostBaht = todayFuel,
                            customExpenses = todayOtherExp,
                            jobIncomes = todayJobs
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.dailyMetricsFlow.collectLatest { metricsMap ->
                _uiState.update { state ->
                    state.copy(
                        smartEarningsState = state.smartEarningsState.copy(dailyWorkMetrics = metricsMap)
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.workSessionsFlow.collectLatest { sessions ->
                _uiState.update { it.copy(workSessions = sessions) }
                if (sessions.any { it.isActive }) {
                    ensureSessionTickerRunning()
                } else {
                    sessionTickerJob?.cancel()
                    sessionTickerJob = null
                }
            }
        }
        viewModelScope.launch {
            repo.analyzedOrdersFlow.collectLatest { orders ->
                _uiState.update { it.copy(analyzedOrders = orders) }
                refreshCloudSyncReadiness()
            }
        }
        viewModelScope.launch {
            repo.jobOffersFlow.collectLatest { offers ->
                val expiredSynced = JobRadarEngine.expireOutdatedOffers(offers)
                _uiState.update {
                    it.copy(
                        detectedJobOffers = expiredSynced,
                        radarClockTickMillis = System.currentTimeMillis()
                    )
                }
                if (expiredSynced.any { it.status == JobOfferStatus.ACTIVE }) {
                    ensureRadarTickerRunning()
                } else {
                    radarTickerJob?.cancel()
                    radarTickerJob = null
                }
            }
        }

        // Hardware compass & GNSS satellites
        viewModelScope.launch {
            service.compassAzimuthDeg.collectLatest { azimuth ->
                if (_uiState.value.isLockEnabled && azimuth != null) {
                    _uiState.update { s ->
                        s.copy(telemetry = s.telemetry.copy(compassAzimuthDegrees = azimuth))
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
    }

    private fun applyStoredPreferences(prefs: StoredRiderPreferences) {
        _uiState.update { state ->
            val resolvedActiveId = prefs.activePinId
                ?.takeIf { id -> state.savedLocations.isEmpty() || state.savedLocations.any { it.id == id } }
                ?: state.activePinId
            state.copy(
                riderProfile = prefs.profile,
                privacyPreferences = prefs.privacy,
                fuelState = prefs.fuel.copy(lowFuelAlertTriggered = state.fuelState.lowFuelAlertTriggered),
                smartEarningsState = state.smartEarningsState.copy(goals = prefs.goals),
                activePinId = resolvedActiveId,
                zoneRadiusKm = prefs.zoneRadiusKm,
                gpsBufferMeters = prefs.gpsBufferMeters,
                alertVibrationEnabled = prefs.alertVibrationEnabled,
                measurementUnit = prefs.measurementUnit,
                updateFrequency = prefs.updateFrequency,
                coordinateFormat = prefs.coordinateFormat,
                keepScreenOn = prefs.keepScreenOn,
                jobRankingMode = prefs.jobRankingMode,
                lineManEnabled = prefs.lineManEnabled,
                grabEnabled = prefs.grabEnabled,
                shopeeFoodEnabled = prefs.shopeeFoodEnabled,
                cloudSyncState = state.cloudSyncState.copy(
                    isMembershipProEnabled = prefs.membershipProEnabled,
                    cloudSyncOptIn = prefs.cloudSyncOptIn,
                    lastLocalBackupMillis = prefs.lastLocalBackupMillis.takeIf { it > 0L }
                )
            )
        }
        syncZoneConfigToBus()
        refreshCloudSyncReadiness()
    }

    private fun ensureSessionTickerRunning() {
        if (sessionTickerJob?.isActive == true) return
        sessionTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                _uiState.update { it.copy(sessionClockTickMillis = System.currentTimeMillis()) }
            }
        }
    }

    private fun ensureRadarTickerRunning() {
        if (radarTickerJob?.isActive == true) return
        radarTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                val currentOffers = _uiState.value.detectedJobOffers
                val newlyExpired = currentOffers.filter {
                    it.status == JobOfferStatus.ACTIVE && now >= it.expiresAtMillis
                }
                if (newlyExpired.isNotEmpty()) {
                    val updated = JobRadarEngine.expireOutdatedOffers(currentOffers, now)
                    _uiState.update {
                        it.copy(detectedJobOffers = updated, radarClockTickMillis = now)
                    }
                    repository?.upsertJobOffers(
                        newlyExpired.map { it.copy(status = JobOfferStatus.EXPIRED) }
                    )
                } else {
                    _uiState.update { it.copy(radarClockTickMillis = now) }
                }
                if (_uiState.value.detectedJobOffers.none { it.status == JobOfferStatus.ACTIVE }) {
                    break
                }
            }
        }
    }

    // =========================================================================
    // Navigation & Permissions
    // =========================================================================

    fun selectScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen, secondaryScreen = null) }
    }

    fun openSecondaryScreen(secondary: SecondaryScreen?) {
        _uiState.update { it.copy(secondaryScreen = secondary) }
        if (secondary == SecondaryScreen.CLOUD_SYNC || secondary == SecondaryScreen.MEMBERSHIP) {
            refreshCloudSyncReadiness()
        }
    }

    fun navigateBack(): Boolean {
        val s = _uiState.value
        return when {
            s.secondaryScreen != null -> {
                _uiState.update { it.copy(secondaryScreen = null) }
                true
            }
            s.currentScreen != AppScreen.HOME -> {
                _uiState.update { it.copy(currentScreen = AppScreen.HOME) }
                true
            }
            else -> false
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
        val notifListenerGranted = JobRadarEngine.isNotificationListenerEnabled(appContext) ||
            JobRadarRuntimeBus.isListenerConnected.value

        _uiState.update {
            it.copy(
                hasFineLocationPermission = fine,
                hasCoarseLocationPermission = coarse,
                hasBackgroundLocationPermission = bg,
                hasNotificationPermission = notif,
                isIgnoringBatteryOptimizations = batteryExempt,
                notificationAccessGranted = notifListenerGranted,
                permissionDeniedMessageTh = if (fine || coarse) null else it.permissionDeniedMessageTh
            )
        }
    }

    fun onPermissionResult(context: Context, granted: Boolean, startLockIfGranted: Boolean) {
        refreshPermissionFlags(context)
        if (granted) {
            _uiState.update { it.copy(permissionDeniedMessageTh = null) }
            if (startLockIfGranted) startGpsLockEngine(context)
        } else {
            stopGpsLockEngine(context)
            _uiState.update {
                it.copy(
                    permissionDeniedMessageTh = "ไม่ได้รับสิทธิ์เข้าถึงตำแหน่ง กรุณาอนุญาตสิทธิ์ตำแหน่งแม่นยำเพื่อใช้งาน GPS และตรวจโซน"
                )
            }
        }
    }

    fun onBackgroundLocationResult(context: Context, granted: Boolean) {
        refreshPermissionFlags(context)
        _uiState.update {
            if (granted) {
                it.copy(statusBannerMessageTh = "อนุญาตสิทธิ์ตำแหน่งเบื้องหลังเรียบร้อยแล้ว")
            } else {
                it.copy(
                    permissionDeniedMessageTh = "ไม่ได้รับสิทธิ์ตำแหน่งเบื้องหลัง กรุณาเลือก 'อนุญาตตลอดเวลา' ในการตั้งค่า"
                )
            }
        }
    }

    fun onBatteryExemptionResult(context: Context) {
        refreshPermissionFlags(context)
        _uiState.update {
            it.copy(
                statusBannerMessageTh = if (it.isIgnoringBatteryOptimizations) {
                    "อนุญาตการข้ามประหยัดแบตเตอรี่เรียบร้อยแล้ว"
                } else {
                    "ยังไม่ได้ยกเว้นการประหยัดแบตเตอรี่ ระบบอาจถูกจำกัดเมื่อปิดหน้าจอ"
                }
            )
        }
    }

    // =========================================================================
    // Work Session Management (Start / Stop Shift)
    // =========================================================================

    fun toggleWorkSession(context: Context) {
        val active = _uiState.value.activeWorkSession
        if (active != null) {
            stopWorkSession()
        } else {
            startWorkSession(context)
        }
    }

    fun startWorkSession(context: Context? = null) {
        if (_uiState.value.activeWorkSession != null) return
        val now = System.currentTimeMillis()
        val todayKey = DailyBreakEvenState.todayDateKey(now)
        val session = WorkSessionRecord(
            id = "SES-$now-${UUID.randomUUID().toString().take(4)}",
            dateKey = todayKey,
            startTimeMillis = now,
            endTimeMillis = null,
            distanceKm = 0.0,
            completedOrders = 0,
            grossIncomeBaht = 0.0,
            fuelCostBaht = 0.0,
            isActive = true
        )
        _uiState.update {
            it.copy(
                workSessions = listOf(session) + it.workSessions,
                sessionClockTickMillis = now,
                statusBannerMessageTh = "เริ่มรอบวิ่งงานแล้ว ระบบพร้อมติดตามระยะทาง เวลา และรายได้"
            )
        }
        ensureSessionTickerRunning()
        viewModelScope.launch {
            repository?.upsertWorkSession(session)
        }
        if (context != null && _uiState.value.hasAnyLocationPermission && !_uiState.value.isLockEnabled) {
            startGpsLockEngine(context)
        }
    }

    fun stopWorkSession() {
        val active = _uiState.value.activeWorkSession ?: return
        val now = System.currentTimeMillis()
        val durationMins = active.durationMinutes(now).coerceAtLeast(1)
        val finished = active.copy(
            endTimeMillis = now,
            isActive = false
        )
        val updatedSessions = _uiState.value.workSessions.map {
            if (it.id == active.id) finished else it
        }
        val dayKey = active.dateKey
        val existingMetric = _uiState.value.smartEarningsState.dailyWorkMetrics[dayKey]
            ?: DailyWorkMetrics(dateKey = dayKey)
        val updatedMetric = existingMetric.copy(
            workMinutes = existingMetric.workMinutes + durationMins,
            gpsDistanceKm = max(existingMetric.gpsDistanceKm, existingMetric.gpsDistanceKm)
        )
        val updatedMetricsMap = _uiState.value.smartEarningsState.dailyWorkMetrics.toMutableMap().apply {
            put(dayKey, updatedMetric)
        }
        sessionTickerJob?.cancel()
        sessionTickerJob = null
        _uiState.update {
            it.copy(
                workSessions = updatedSessions,
                smartEarningsState = it.smartEarningsState.copy(dailyWorkMetrics = updatedMetricsMap),
                statusBannerMessageTh = String.format(
                    Locale.US,
                    "ปิดรอบงานแล้ว (%d นาที • %d ออเดอร์ • ฿%.0f)",
                    durationMins,
                    finished.completedOrders,
                    finished.grossIncomeBaht
                )
            )
        }
        viewModelScope.launch {
            repository?.upsertWorkSession(finished)
            repository?.upsertDailyMetric(updatedMetric)
        }
    }

    // =========================================================================
    // GPS Lock Engine & Zone Monitoring (Preserved 100%)
    // =========================================================================

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
                it.copy(permissionDeniedMessageTh = "จำเป็นต้องได้รับสิทธิ์ตำแหน่งก่อนเริ่มล็อกสัญญาณ GPS")
            }
            return
        }

        val freq = _uiState.value.updateFrequency
        syncZoneConfigToBus()

        _uiState.update { state ->
            val nextLockState = if (state.telemetry.hasFix) GpsLockState.LOCKED else GpsLockState.SEARCHING
            state.copy(
                isLockEnabled = true,
                lockState = nextLockState,
                permissionDeniedMessageTh = null,
                statusBannerMessageTh = "เริ่มทำงานระบบล็อกสัญญาณและตรวจโซนรับงานแล้ว"
            )
        }
        persistTelemetryPrefs()

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
        stopViewModelRepeatAlertLoop()
        lastGpsTrackLat = null
        lastGpsTrackLng = null

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
        persistTelemetryPrefs()
    }

    fun onHardwareSensorFixReceived(location: Location) {
        _uiState.update { it.copy(isLockEnabled = true) }
        applyHardwareLocation(location)
    }

    private fun applySmoothedLocation(update: SmoothedLocationUpdate) {
        _uiState.update { state ->
            if (!state.isLockEnabled) return@update state
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
        accumulateRealGpsDistance(lat, lng)
        refreshJobOfferDistancesWithGps(lat, lng)
    }

    private fun refreshJobOfferDistancesWithGps(lat: Double, lng: Double) {
        val current = _uiState.value.detectedJobOffers
        if (current.none { it.pickupLat != null && it.pickupLng != null }) return
        val updated = JobRadarEngine.refreshDistancesWithGps(current, lat, lng)
        _uiState.update { it.copy(detectedJobOffers = updated) }
    }

    private fun accumulateRealGpsDistance(lat: Double, lng: Double) {
        val prevLat = lastGpsTrackLat
        val prevLng = lastGpsTrackLng
        lastGpsTrackLat = lat
        lastGpsTrackLng = lng
        if (prevLat == null || prevLng == null) return

        val stepKm = GeoMath.haversineKm(prevLat, prevLng, lat, lng)
        if (stepKm in 0.002..5.0) {
            val state = _uiState.value
            val currentFuel = state.fuelState
            val newDistKm = currentFuel.distanceDrivenSinceRefuelKm + stepKm
            val updatedFuel = currentFuel.copy(distanceDrivenSinceRefuelKm = newDistKm)
            val todayKey = DailyBreakEvenState.todayDateKey()
            val currentDayMetric = state.smartEarningsState.dailyWorkMetrics[todayKey]
                ?: DailyWorkMetrics(dateKey = todayKey)
            val updatedDayMetric = currentDayMetric.copy(gpsDistanceKm = currentDayMetric.gpsDistanceKm + stepKm)
            val updatedMetricsMap = state.smartEarningsState.dailyWorkMetrics.toMutableMap().apply {
                put(todayKey, updatedDayMetric)
            }

            // Also update active work session distance if running
            val activeSession = state.activeWorkSession
            val updatedActiveSession = activeSession?.copy(
                distanceKm = activeSession.distanceKm + stepKm,
                fuelCostBaht = activeSession.fuelCostBaht +
                    (stepKm / currentFuel.consumptionKmPerLiter.coerceAtLeast(1f)) * state.riderProfile.fuelPricePerLiterBaht
            )
            val updatedSessions = if (updatedActiveSession != null) {
                state.workSessions.map { if (it.id == updatedActiveSession.id) updatedActiveSession else it }
            } else {
                state.workSessions
            }

            val shouldTriggerLowFuelAlert = state.privacyPreferences.lowFuelAlertEnabled &&
                updatedFuel.isLowFuel && !currentFuel.lowFuelAlertTriggered
            val finalFuel = if (shouldTriggerLowFuelAlert) {
                updatedFuel.copy(lowFuelAlertTriggered = true)
            } else {
                updatedFuel
            }

            _uiState.update {
                it.copy(
                    fuelState = finalFuel,
                    workSessions = updatedSessions,
                    smartEarningsState = it.smartEarningsState.copy(dailyWorkMetrics = updatedMetricsMap),
                    statusBannerMessageTh = if (shouldTriggerLowFuelAlert) {
                        String.format(
                            Locale.US,
                            "แจ้งเตือนน้ำมันใกล้หมด: เหลือ ~%.2f ลิตร (วิ่งได้อีก ~%.0f กม.)",
                            finalFuel.remainingLiters,
                            finalFuel.estimatedRemainingRangeKm
                        )
                    } else {
                        it.statusBannerMessageTh
                    }
                )
            }

            viewModelScope.launch {
                repository?.preferencesStore?.updateFuel(finalFuel)
                repository?.upsertDailyMetric(updatedDayMetric)
                if (updatedActiveSession != null) {
                    repository?.upsertWorkSession(updatedActiveSession)
                }
            }

            if (shouldTriggerLowFuelAlert) {
                appContextRef?.let { ctx ->
                    TacticalSoundEngine.triggerLowFuelWarning(ctx, _uiState.value.alertVibrationEnabled)
                }
            }
        }
    }

    private fun updateSatellitesInternal(used: Int, visible: Int, bars: List<SatelliteBarItem>) {
        val avgSnr = if (bars.isNotEmpty()) bars.map { it.cn0DbHz }.average().toFloat() else 0f
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
                statusBannerMessageTh = "หยุดการสั่นเตือนโซนชั่วคราวแล้ว (รีเซ็ตเมื่อกลับเข้าโซน)"
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
                isAlertMuted = s.isZoneAlertMuted
            )
        )
    }

    // =========================================================================
    // AI Rider Assistant: ML Kit OCR, Draft Editing, Analysis & Screen Reader
    // =========================================================================

    fun requestStartScreenReader(): Boolean {
        if (!_uiState.value.privacyPreferences.screenReaderConsentAccepted) {
            _uiState.update { it.copy(showScreenReaderConsentDialog = true) }
            return false
        }
        return true
    }

    fun dismissScreenReaderConsentDialog() {
        _uiState.update { it.copy(showScreenReaderConsentDialog = false) }
    }

    fun acceptScreenReaderConsent() {
        val updated = _uiState.value.privacyPreferences.copy(screenReaderConsentAccepted = true)
        _uiState.update {
            it.copy(
                privacyPreferences = updated,
                showScreenReaderConsentDialog = false
            )
        }
        viewModelScope.launch {
            repository?.preferencesStore?.updatePrivacy(updated)
        }
    }

    fun onMediaProjectionPermissionResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            ScreenReaderRuntimeBus.updateState(
                ScreenReaderState.PERMISSION_DENIED,
                "ไม่ได้รับสิทธิ์จับภาพหน้าจอ คุณยังสามารถนำเข้าภาพหน้าจอหรือกรอกข้อมูลเองได้"
            )
            _uiState.update {
                it.copy(
                    screenReaderState = ScreenReaderState.PERMISSION_DENIED,
                    ocrErrorMessageTh = "ไม่ได้รับสิทธิ์จับภาพหน้าจอ — ใช้ปุ่มนำเข้าภาพหน้าจอ (Photo Picker) หรือกรอกข้อมูลแทนได้"
                )
            }
            return
        }
        val appContext = context.applicationContext
        runCatching {
            val intent = Intent(appContext, ScreenReaderCaptureService::class.java).apply {
                action = ScreenReaderCaptureService.ACTION_START
                putExtra(ScreenReaderCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenReaderCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(appContext, intent)
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    screenReaderState = ScreenReaderState.ERROR,
                    ocrErrorMessageTh = "ไม่สามารถเริ่มบริการ Screen Reader ได้ (${err.localizedMessage ?: "ไม่รองรับ"})"
                )
            }
        }
    }

    fun triggerScreenReaderCaptureNow(context: Context) {
        if (_uiState.value.screenReaderState != ScreenReaderState.ACTIVE_READY) {
            _uiState.update {
                it.copy(statusBannerMessageTh = "กรุณาเปิดสิทธิ์ Screen Reader ก่อนกดจับภาพ")
            }
            return
        }
        runCatching {
            val intent = Intent(context.applicationContext, ScreenReaderCaptureService::class.java).apply {
                action = ScreenReaderCaptureService.ACTION_CAPTURE_NOW
            }
            context.applicationContext.startService(intent)
        }
    }

    fun stopScreenReader(context: Context) {
        runCatching {
            val intent = Intent(context.applicationContext, ScreenReaderCaptureService::class.java).apply {
                action = ScreenReaderCaptureService.ACTION_STOP
            }
            context.applicationContext.startService(intent)
        }
        ScreenReaderRuntimeBus.updateState(ScreenReaderState.IDLE)
        _uiState.update {
            it.copy(
                screenReaderState = ScreenReaderState.IDLE,
                statusBannerMessageTh = "ปิดการทำงาน Screen Reader เรียบร้อยแล้ว"
            )
        }
    }

    fun processOrderImageUri(
        context: Context,
        uri: Uri,
        source: OrderInputSource = OrderInputSource.SCREENSHOT_IMPORT,
        alsoSendToJobRadar: Boolean = false
    ) {
        routeNextOcrToRadar = alsoSendToJobRadar
        _uiState.update {
            it.copy(
                isRunningOcr = true,
                ocrErrorMessageTh = null,
                currentScreen = if (alsoSendToJobRadar) AppScreen.JOB_RADAR else AppScreen.AI_ASSISTANT,
                secondaryScreen = null
            )
        }
        viewModelScope.launch {
            val outcome = MlKitOrderOcrEngine.recognizeImageUri(context.applicationContext, uri, source)
            handleOcrOutcome(outcome)
        }
    }

    fun processOrderBitmap(
        bitmap: Bitmap,
        source: OrderInputSource = OrderInputSource.SCREEN_READER
    ) {
        _uiState.update { it.copy(isRunningOcr = true, ocrErrorMessageTh = null) }
        viewModelScope.launch {
            try {
                val outcome = MlKitOrderOcrEngine.recognizeBitmap(bitmap, source)
                handleOcrOutcome(outcome)
            } finally {
                if (_uiState.value.privacyPreferences.autoDeleteScreenshots && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun handleOcrOutcome(outcome: OcrScanOutcome) {
        val forwardToRadar = routeNextOcrToRadar
        routeNextOcrToRadar = false
        when (outcome) {
            is OcrScanOutcome.Success -> {
                val rawTextForRadar = outcome.draft.rawOcrText
                val cleanDraft = if (_uiState.value.privacyPreferences.storeRawOcrText) {
                    outcome.draft
                } else {
                    outcome.draft.copy(rawOcrText = "")
                }
                val analysis = RiderIntelligenceEngine.analyzeOrder(
                    draft = cleanDraft,
                    profile = _uiState.value.riderProfile,
                    fuelState = _uiState.value.fuelState
                )
                _uiState.update {
                    it.copy(
                        isRunningOcr = false,
                        currentOrderDraft = cleanDraft,
                        currentOrderAnalysis = analysis,
                        ocrErrorMessageTh = if (cleanDraft.missingFields.isNotEmpty()) {
                            "ตรวจพบข้อมูลบางส่วน กรุณาตรวจสอบหรือกรอกช่องที่ขาด (${cleanDraft.missingFields.joinToString(", ")})"
                        } else null,
                        statusBannerMessageTh = "อ่านข้อความ OCR เสร็จสิ้น โปรดตรวจสอบความถูกต้องก่อนบันทึก"
                    )
                }
                if (forwardToRadar && rawTextForRadar.isNotBlank()) {
                    ingestJobOfferText(
                        rawText = rawTextForRadar,
                        source = JobOfferSource.OCR_SCREEN_READER
                    )
                }
            }
            is OcrScanOutcome.ProtectedOrBlankScreen -> {
                _uiState.update {
                    it.copy(
                        isRunningOcr = false,
                        screenReaderState = ScreenReaderState.PROTECTED_SCREEN,
                        ocrErrorMessageTh = outcome.messageTh
                    )
                }
            }
            is OcrScanOutcome.Failure -> {
                _uiState.update {
                    it.copy(
                        isRunningOcr = false,
                        ocrErrorMessageTh = outcome.reasonTh
                    )
                }
            }
        }
    }

    fun parseManualOrPastedOrderText(rawText: String) {
        val draft = OrderOcrParser.parseOrderText(rawText, OrderInputSource.MANUAL_OR_TEXT)
        val analysis = RiderIntelligenceEngine.analyzeOrder(
            draft = draft,
            profile = _uiState.value.riderProfile,
            fuelState = _uiState.value.fuelState
        )
        _uiState.update {
            it.copy(
                currentOrderDraft = draft,
                currentOrderAnalysis = analysis,
                ocrErrorMessageTh = if (draft.payoutBaht == null && draft.distanceKm == null) {
                    "ไม่พบตัวเลขรายได้หรือระยะทางในข้อความ กรุณากรอกข้อมูลในช่องด้านล่าง"
                } else null
            )
        }
    }

    fun updateOrderDraftFields(
        payoutText: String? = null,
        distanceText: String? = null,
        deadMileageText: String? = null,
        minutesText: String? = null,
        pickupName: String? = null,
        dropoffName: String? = null
    ) {
        _uiState.update { state ->
            val cur = state.currentOrderDraft
            val nextPayout = if (payoutText != null) payoutText.trim().toDoubleOrNull()?.takeIf { it > 0.0 } else cur.payoutBaht
            val nextDist = if (distanceText != null) distanceText.trim().toDoubleOrNull()?.takeIf { it > 0.0 } else cur.distanceKm
            val nextDead = if (deadMileageText != null) deadMileageText.trim().toDoubleOrNull()?.takeIf { it >= 0.0 } else cur.deadMileageKm
            val nextMins = if (minutesText != null) minutesText.trim().toIntOrNull()?.takeIf { it > 0 } else cur.estimatedMinutes
            val nextPickup = if (pickupName != null) pickupName.trim().takeIf { it.isNotEmpty() } else cur.pickupName
            val nextDropoff = if (dropoffName != null) dropoffName.trim().takeIf { it.isNotEmpty() } else cur.dropoffName

            val missing = buildList {
                if (nextPayout == null) add("รายได้ (บาท)")
                if (nextDist == null) add("ระยะทาง (กม.)")
                if (nextMins == null) add("เวลาโดยประมาณ (นาที)")
            }

            val updatedDraft = cur.copy(
                payoutBaht = nextPayout,
                distanceKm = nextDist,
                deadMileageKm = nextDead,
                estimatedMinutes = nextMins,
                pickupName = nextPickup,
                dropoffName = nextDropoff,
                missingFields = missing,
                wasUserCorrected = true,
                overallConfidence = if (nextPayout != null && nextDist != null) {
                    if (nextMins != null) 1.0f else 0.85f
                } else 0.3f
            )
            val analysis = RiderIntelligenceEngine.analyzeOrder(
                draft = updatedDraft,
                profile = state.riderProfile,
                fuelState = state.fuelState
            )
            state.copy(
                currentOrderDraft = updatedDraft,
                currentOrderAnalysis = analysis,
                ocrErrorMessageTh = null
            )
        }
    }

    fun resetOrderDraft() {
        _uiState.update {
            it.copy(
                currentOrderDraft = ParsedOrderDraft(),
                currentOrderAnalysis = null,
                ocrErrorMessageTh = null
            )
        }
    }

    fun saveCurrentAnalyzedOrder(alsoLogToEarnings: Boolean): Boolean {
        val state = _uiState.value
        val draft = state.currentOrderDraft
        val analysis = state.currentOrderAnalysis
            ?: RiderIntelligenceEngine.analyzeOrder(draft, state.riderProfile, state.fuelState)
            ?: return false

        val now = System.currentTimeMillis()
        val todayKey = DailyBreakEvenState.todayDateKey(now)
        val pickup = draft.pickupName?.ifBlank { "จุดรับงาน" } ?: "จุดรับงาน"
        val dropoff = draft.dropoffName?.ifBlank { "จุดส่งลูกค้า" } ?: "จุดส่งลูกค้า"
        val orderRecord = AnalyzedOrderRecord(
            id = "ORD-$now-${UUID.randomUUID().toString().take(4)}",
            dateKey = todayKey,
            source = draft.source,
            pickupName = pickup,
            dropoffName = dropoff,
            payoutBaht = analysis.grossPayoutBaht,
            distanceKm = analysis.tripDistanceKm,
            deadMileageKm = analysis.deadMileageKm,
            estimatedMinutes = analysis.estimatedMinutes ?: 0,
            fuelCostBaht = analysis.fuelCostBaht,
            netProfitBaht = analysis.netProfitBaht,
            earningsPerKm = analysis.earningsPerKm,
            earningsPerHour = analysis.earningsPerHour ?: 0.0,
            smartScore = analysis.smartScore,
            grade = analysis.grade,
            confidenceScore = analysis.confidenceScore,
            wasUserCorrected = draft.wasUserCorrected,
            savedToEarnings = alsoLogToEarnings,
            latitude = state.telemetry.latitude ?: state.activePin?.latitude,
            longitude = state.telemetry.longitude ?: state.activePin?.longitude,
            createdAtMillis = now
        )

        val updatedOrders = listOf(orderRecord) + state.analyzedOrders
        _uiState.update {
            it.copy(
                analyzedOrders = updatedOrders,
                statusBannerMessageTh = if (alsoLogToEarnings) {
                    String.format(
                        Locale.US,
                        "บันทึกผลการวิเคราะห์ (เกรด %s) และเพิ่มรายได้ +฿%.0f แล้ว",
                        analysis.grade.label,
                        analysis.grossPayoutBaht
                    )
                } else {
                    "บันทึกผลการวิเคราะห์ออเดอร์ (เกรด ${analysis.grade.label}) เรียบร้อยแล้ว"
                }
            )
        }

        viewModelScope.launch {
            repository?.upsertAnalyzedOrder(orderRecord)
        }

        if (alsoLogToEarnings) {
            val title = buildString {
                append(draft.platformHint?.let { "[$it] " } ?: "")
                append(pickup)
                if (dropoff != "จุดส่งลูกค้า") append(" → $dropoff")
            }
            addEarningsTransaction(
                type = EarningsRecordType.INCOME,
                dateKey = todayKey,
                title = title,
                category = draft.platformHint ?: "รับงาน",
                amountBaht = analysis.grossPayoutBaht,
                distanceKm = analysis.totalDistanceKm,
                durationMinutes = analysis.estimatedMinutes ?: 0,
                timestampMillis = now
            )

            // Update active work session if running
            state.activeWorkSession?.let { active ->
                val updatedSession = active.copy(
                    completedOrders = active.completedOrders + 1,
                    grossIncomeBaht = active.grossIncomeBaht + analysis.grossPayoutBaht
                )
                _uiState.update { s ->
                    s.copy(workSessions = s.workSessions.map { if (it.id == active.id) updatedSession else it })
                }
                viewModelScope.launch { repository?.upsertWorkSession(updatedSession) }
            }

            // Enrich nearby saved pin's local historical stats (honest local history!)
            val lat = orderRecord.latitude
            val lng = orderRecord.longitude
            if (lat != null && lng != null) {
                val nearest = state.savedLocations.minByOrNull {
                    GeoMath.haversineKm(lat, lng, it.latitude, it.longitude)
                }
                if (nearest != null && GeoMath.haversineKm(lat, lng, nearest.latitude, nearest.longitude) <= 1.5) {
                    val newCount = nearest.historicalOrderCount + 1
                    val newAvg = if (nearest.historicalOrderCount > 0 && nearest.historicalAvgPayoutBaht > 0.0) {
                        ((nearest.historicalAvgPayoutBaht * nearest.historicalOrderCount) + analysis.grossPayoutBaht) / newCount
                    } else {
                        analysis.grossPayoutBaht
                    }
                    val updatedPin = nearest.copy(
                        historicalOrderCount = newCount,
                        historicalAvgPayoutBaht = newAvg
                    )
                    viewModelScope.launch { repository?.upsertPin(updatedPin) }
                }
            }
        }
        return true
    }

    fun deleteAnalyzedOrder(id: String) {
        _uiState.update {
            it.copy(
                analyzedOrders = it.analyzedOrders.filterNot { o -> o.id == id },
                statusBannerMessageTh = "ลบประวัติการวิเคราะห์ออเดอร์แล้ว"
            )
        }
        viewModelScope.launch { repository?.deleteAnalyzedOrder(id) }
    }

    fun clearAllAnalyzedOrders() {
        _uiState.update {
            it.copy(
                analyzedOrders = emptyList(),
                statusBannerMessageTh = "ล้างประวัติ OCR และออเดอร์ที่วิเคราะห์ทั้งหมดแล้ว"
            )
        }
        viewModelScope.launch { repository?.clearAnalyzedOrders() }
    }

    // =========================================================================
    // Smart Map, Pins, Hotzones, Restaurants & Zone Radius
    // =========================================================================

    fun setMapTapActionMode(mode: MapTapActionMode) {
        _uiState.update { it.copy(mapTapActionMode = mode) }
    }

    fun setNewPinCategory(category: PinCategory) {
        _uiState.update { it.copy(newPinCategory = category) }
    }

    fun updateMapLayerFilters(transform: (MapLayerFilters) -> MapLayerFilters) {
        _uiState.update { it.copy(mapLayerFilters = transform(it.mapLayerFilters)) }
    }

    fun selectMapPinForSheet(pinId: String?) {
        _uiState.update { it.copy(selectedMapPinId = pinId) }
    }

    fun saveCurrentLocation(
        customName: String,
        moveActivePinInstead: Boolean = false,
        category: PinCategory = _uiState.value.newPinCategory
    ): Boolean {
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
            "${category.labelTh} #${_uiState.value.savedLocations.size + 1}"
        }
        val newItem = SavedLocationItem(
            id = "LOC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            name = cleanName,
            latitude = lat,
            longitude = lng,
            accuracyMeters = currentTelemetry.accuracyMeters,
            altitudeMeters = currentTelemetry.altitudeMeters,
            timestampMillis = System.currentTimeMillis(),
            category = category
        )
        return insertPinAndActivate(newItem, "ปักหมุด \"$cleanName\" เรียบร้อยแล้ว")
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
            val cat = _uiState.value.newPinCategory
            val cleanName = customName.trim().ifBlank {
                "${cat.labelTh} #${_uiState.value.savedLocations.size + 1}"
            }
            val newItem = SavedLocationItem(
                id = "LOC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
                name = cleanName,
                latitude = clampedLat,
                longitude = clampedLng,
                accuracyMeters = null,
                altitudeMeters = null,
                timestampMillis = System.currentTimeMillis(),
                category = cat
            )
            insertPinAndActivate(newItem, "ปักหมุด \"$cleanName\" บนแผนที่แล้ว")
        }
    }

    fun pinOrMoveFromCoordinatesText(
        rawCoordinateText: String,
        customName: String,
        moveActivePinInstead: Boolean = false,
        category: PinCategory = _uiState.value.newPinCategory
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
            "${category.labelTh} #${_uiState.value.savedLocations.size + 1}"
        }
        val newItem = SavedLocationItem(
            id = "LOC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            name = cleanName,
            latitude = lat,
            longitude = lng,
            accuracyMeters = null,
            altitudeMeters = null,
            timestampMillis = System.currentTimeMillis(),
            category = category
        )
        return insertPinAndActivate(newItem, "ปักหมุด \"$cleanName\" จากพิกัดที่วางเรียบร้อยแล้ว")
    }

    private fun insertPinAndActivate(item: SavedLocationItem, messageTh: String): Boolean {
        val updatedList = listOf(item) + _uiState.value.savedLocations
        val nextActiveId = if (item.category == PinCategory.FIXPOINT || _uiState.value.activePinId == null) {
            item.id
        } else {
            _uiState.value.activePinId
        }
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = nextActiveId,
                selectedMapPinId = item.id,
                statusBannerMessageTh = messageTh
            )
        }
        viewModelScope.launch {
            repository?.upsertPin(item)
            repository?.preferencesStore?.updateZoneSettings(
                activePinId = nextActiveId,
                radiusKm = _uiState.value.zoneRadiusKm,
                bufferMeters = _uiState.value.gpsBufferMeters
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        return true
    }

    fun selectActivePin(id: String) {
        val target = _uiState.value.savedLocations.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                activePinId = id,
                isZoneAlertMuted = false,
                statusBannerMessageTh = "ตั้ง \"${target.name}\" เป็นหมุดโซนหลักแล้ว"
            )
        }
        viewModelScope.launch {
            repository?.preferencesStore?.updateZoneSettings(
                activePinId = id,
                radiusKm = _uiState.value.zoneRadiusKm,
                bufferMeters = _uiState.value.gpsBufferMeters
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
        val existing = currentList.firstOrNull { it.id == id } ?: return false

        val resolvedName = newName?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name
        val updatedPin = existing.copy(
            name = resolvedName,
            latitude = newLatitude,
            longitude = newLongitude,
            timestampMillis = System.currentTimeMillis()
        )
        val updatedList = currentList.map { if (it.id == id) updatedPin else it }
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = it.activePinId ?: id,
                statusBannerMessageTh = "อัปเดตตำแหน่งหมุด \"$resolvedName\" เรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch {
            repository?.upsertPin(updatedPin)
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
        return true
    }

    fun updatePinCategoryAndNotes(id: String, category: PinCategory, newName: String, notes: String) {
        val existing = _uiState.value.savedLocations.firstOrNull { it.id == id } ?: return
        val updated = existing.copy(
            name = newName.trim().ifBlank { existing.name },
            category = category,
            notes = notes.trim()
        )
        _uiState.update { state ->
            state.copy(
                savedLocations = state.savedLocations.map { if (it.id == id) updated else it },
                statusBannerMessageTh = "บันทึกข้อมูลหมุด \"${updated.name}\" แล้ว"
            )
        }
        viewModelScope.launch { repository?.upsertPin(updated) }
    }

    fun deleteSavedLocation(id: String) {
        val updatedList = _uiState.value.savedLocations.filterNot { it.id == id }
        val nextActiveId = if (_uiState.value.activePinId == id) updatedList.firstOrNull()?.id else _uiState.value.activePinId
        val nextSelected = if (_uiState.value.selectedMapPinId == id) null else _uiState.value.selectedMapPinId
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                activePinId = nextActiveId,
                selectedMapPinId = nextSelected,
                statusBannerMessageTh = "ลบหมุดที่บันทึกเรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch {
            repository?.deletePin(id)
            repository?.preferencesStore?.updateZoneSettings(
                activePinId = nextActiveId,
                radiusKm = _uiState.value.zoneRadiusKm,
                bufferMeters = _uiState.value.gpsBufferMeters
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = false)
    }

    fun setZoneRadiusKm(radiusKm: Float) {
        val rounded = ((radiusKm.coerceIn(0.0f, 30.0f) * 10f).roundToInt() / 10f)
        _uiState.update { it.copy(zoneRadiusKm = rounded) }
        viewModelScope.launch {
            repository?.preferencesStore?.updateZoneSettings(
                activePinId = _uiState.value.activePinId,
                radiusKm = rounded,
                bufferMeters = _uiState.value.gpsBufferMeters
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
    }

    fun setGpsBufferMeters(bufferMeters: Int) {
        val clamped = bufferMeters.coerceIn(0, 100)
        _uiState.update {
            it.copy(
                gpsBufferMeters = clamped,
                statusBannerMessageTh = "ตั้งค่าเผื่อสัญญาณ GPS เป็น $clamped เมตร"
            )
        }
        viewModelScope.launch {
            repository?.preferencesStore?.updateZoneSettings(
                activePinId = _uiState.value.activePinId,
                radiusKm = _uiState.value.zoneRadiusKm,
                bufferMeters = clamped
            )
        }
        syncZoneConfigToBus()
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
    }

    fun searchNearbyFuelStations(forceRefresh: Boolean = false) {
        val lat = _uiState.value.telemetry.latitude ?: _uiState.value.activePin?.latitude
        val lng = _uiState.value.telemetry.longitude ?: _uiState.value.activePin?.longitude
        if (lat == null || lng == null) {
            _uiState.update {
                it.copy(overpassErrorOrStatusTh = "กรุณาเปิด GPS หรือปักหมุดก่อนค้นหาปั๊มน้ำมันใกล้เคียง")
            }
            return
        }
        val radiusKm = _uiState.value.overpassSearchRadiusKm
        _uiState.update {
            it.copy(
                isSearchingOverpass = true,
                overpassErrorOrStatusTh = "กำลังค้นหาปั๊มน้ำมันในรัศมี $radiusKm กม. (OpenStreetMap)..."
            )
        }
        viewModelScope.launch {
            when (val res = NetworkClients.fetchNearbyFuelStationsOverpass(lat, lng, radiusKm)) {
                is OverpassFuelResult.Success -> {
                    val sorted = res.stations.sortedBy {
                        GeoMath.haversineMeters(lat, lng, it.latitude, it.longitude)
                    }
                    _uiState.update {
                        it.copy(
                            isSearchingOverpass = false,
                            nearbyOverpassFuelPois = sorted,
                            overpassErrorOrStatusTh = if (sorted.isEmpty()) {
                                "ไม่พบปั๊มน้ำมันในรัศมี $radiusKm กม."
                            } else {
                                "พบปั๊มน้ำมัน ${sorted.size} แห่ง"
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
    // Smart Earnings 2.0 & Break-Even (Room-backed)
    // =========================================================================

    fun selectEarningsPeriod(period: EarningsPeriod) {
        _uiState.update {
            it.copy(smartEarningsState = it.smartEarningsState.copy(selectedPeriod = period))
        }
    }

    fun shiftEarningsDate(delta: Int) {
        _uiState.update { state ->
            val cur = state.smartEarningsState
            val shifted = SmartEarningsCalculator.shiftDateKey(cur.selectedDateKey, cur.selectedPeriod, delta)
            state.copy(smartEarningsState = cur.copy(selectedDateKey = shifted))
        }
    }

    fun resetEarningsDateToToday() {
        val today = DailyBreakEvenState.todayDateKey()
        _uiState.update {
            it.copy(smartEarningsState = it.smartEarningsState.copy(selectedDateKey = today))
        }
    }

    fun selectEarningsHistoryFilter(filter: EarningsHistoryFilter) {
        _uiState.update {
            it.copy(smartEarningsState = it.smartEarningsState.copy(historyFilter = filter))
        }
    }

    fun updateEarningsGoals(dailyBaht: Double, weeklyBaht: Double, monthlyBaht: Double) {
        val goals = EarningsGoals(
            dailyGoalBaht = dailyBaht.coerceAtLeast(0.0),
            weeklyGoalBaht = weeklyBaht.coerceAtLeast(0.0),
            monthlyGoalBaht = monthlyBaht.coerceAtLeast(0.0)
        )
        _uiState.update {
            it.copy(
                smartEarningsState = it.smartEarningsState.copy(goals = goals),
                statusBannerMessageTh = "บันทึกเป้าหมายรายได้เรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch { repository?.preferencesStore?.updateGoals(goals) }
    }

    fun updateDailyWorkHours(dateKey: String, hours: Double) {
        if (!dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return
        val safeMinutes = (hours.coerceIn(0.0, 24.0) * 60.0).roundToInt()
        val currentMap = _uiState.value.smartEarningsState.dailyWorkMetrics
        val existing = currentMap[dateKey] ?: DailyWorkMetrics(dateKey = dateKey)
        val updatedMetric = existing.copy(workMinutes = safeMinutes)
        val updatedMap = currentMap.toMutableMap().apply { put(dateKey, updatedMetric) }
        _uiState.update {
            it.copy(
                smartEarningsState = it.smartEarningsState.copy(dailyWorkMetrics = updatedMap),
                statusBannerMessageTh = String.format(Locale.US, "บันทึกชั่วโมงทำงาน %.1f ชม. เรียบร้อยแล้ว", safeMinutes / 60.0)
            )
        }
        viewModelScope.launch { repository?.upsertDailyMetric(updatedMetric) }
    }

    fun updateFixedDailyCosts(fuelCostBaht: Double) {
        val safeFuel = fuelCostBaht.coerceAtLeast(0.0)
        val todayKey = DailyBreakEvenState.todayDateKey()
        val fuelTxnId = "DAILY-FUEL-$todayKey"
        val currentTxns = _uiState.value.smartEarningsState.transactions.filterNot { it.id == fuelTxnId }
        val fuelTxn = if (safeFuel > 0.0) {
            EarningsTransaction(
                id = fuelTxnId,
                type = EarningsRecordType.EXPENSE,
                dateKey = todayKey,
                title = "ค่าน้ำมันรายวัน",
                category = "น้ำมัน",
                amountBaht = safeFuel,
                timestampMillis = System.currentTimeMillis()
            )
        } else null
        val updatedTxns = if (fuelTxn != null) listOf(fuelTxn) + currentTxns else currentTxns
        _uiState.update {
            it.copy(
                breakEvenState = it.breakEvenState.copy(fuelCostBaht = safeFuel),
                smartEarningsState = it.smartEarningsState.copy(transactions = updatedTxns),
                statusBannerMessageTh = "บันทึกค่าน้ำมันรายวันเรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch {
            if (fuelTxn != null) repository?.upsertTransaction(fuelTxn)
            else repository?.deleteTransaction(fuelTxnId)
        }
    }

    fun addEarningsTransaction(
        type: EarningsRecordType,
        dateKey: String,
        title: String,
        category: String,
        amountBaht: Double,
        distanceKm: Double = 0.0,
        durationMinutes: Int = 0,
        timestampMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (amountBaht <= 0.0) return false
        val validDateKey = if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            dateKey
        } else {
            DailyBreakEvenState.todayDateKey()
        }
        val cleanTitle = title.trim().ifBlank {
            if (type == EarningsRecordType.INCOME) "รายได้รับงาน" else category.ifBlank { "ค่าใช้จ่าย" }
        }
        val cleanCategory = category.trim().ifBlank {
            if (type == EarningsRecordType.INCOME) "รับงาน" else "ทั่วไป"
        }
        val candidate = EarningsTransaction(
            id = "TXN-$timestampMillis-${UUID.randomUUID().toString().take(6)}",
            type = type,
            dateKey = validDateKey,
            title = cleanTitle,
            category = cleanCategory,
            amountBaht = amountBaht,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            durationMinutes = durationMinutes.coerceAtLeast(0),
            timestampMillis = timestampMillis
        )
        val existingTxns = _uiState.value.smartEarningsState.transactions
        if (EarningsTransaction.isDuplicateCandidate(existingTxns, candidate)) {
            _uiState.update { it.copy(statusBannerMessageTh = "พบรายการซ้ำ ระบบป้องกันการบันทึกซ้ำแล้ว") }
            return false
        }
        val updatedTxns = (listOf(candidate) + existingTxns).distinctBy { it.id }
        _uiState.update {
            it.copy(
                smartEarningsState = it.smartEarningsState.copy(transactions = updatedTxns),
                statusBannerMessageTh = if (type == EarningsRecordType.INCOME) {
                    String.format(Locale.US, "บันทึกรายได้ +฿%.0f เรียบร้อยแล้ว", amountBaht)
                } else {
                    String.format(Locale.US, "บันทึกค่าใช้จ่าย -฿%.0f เรียบร้อยแล้ว", amountBaht)
                }
            )
        }
        viewModelScope.launch { repository?.upsertTransaction(candidate) }
        return true
    }

    fun deleteEarningsTransaction(id: String) {
        val updatedTxns = _uiState.value.smartEarningsState.transactions.filterNot { it.id == id }
        _uiState.update {
            it.copy(
                smartEarningsState = it.smartEarningsState.copy(transactions = updatedTxns),
                statusBannerMessageTh = "ลบรายการเรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch { repository?.deleteTransaction(id) }
    }

    // =========================================================================
    // Profile, Vehicle, Fuel, Privacy, Preferences & Local Backup/Restore
    // =========================================================================

    fun updateRiderProfile(profile: RiderProfileConfig) {
        val sanitized = profile.copy(
            riderName = profile.riderName.trim().ifBlank { "Rider FixPoint" },
            vehicleModel = profile.vehicleModel.trim().ifBlank { "Motorcycle" },
            fuelPricePerLiterBaht = profile.fuelPricePerLiterBaht.coerceIn(0f, 200f),
            maintenanceBahtPerKm = profile.maintenanceBahtPerKm.coerceIn(0f, 50f),
            minAcceptableBahtPerKm = profile.minAcceptableBahtPerKm.coerceIn(1f, 500f),
            minAcceptableBahtPerHour = profile.minAcceptableBahtPerHour.coerceIn(20f, 5000f)
        )
        _uiState.update { state ->
            val updatedAnalysis = RiderIntelligenceEngine.analyzeOrder(
                draft = state.currentOrderDraft,
                profile = sanitized,
                fuelState = state.fuelState
            )
            state.copy(
                riderProfile = sanitized,
                currentOrderAnalysis = updatedAnalysis,
                statusBannerMessageTh = "บันทึกข้อมูลโปรไฟล์และเกณฑ์คำนวณเรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch { repository?.preferencesStore?.updateProfile(sanitized) }
    }

    fun updatePrivacyPreferences(transform: (PrivacyPreferences) -> PrivacyPreferences) {
        val updated = transform(_uiState.value.privacyPreferences)
        _uiState.update {
            it.copy(
                privacyPreferences = updated,
                statusBannerMessageTh = "อัปเดตการตั้งค่าความเป็นส่วนตัวแล้ว"
            )
        }
        viewModelScope.launch { repository?.preferencesStore?.updatePrivacy(updated) }
    }

    fun updateFuelSettings(
        tankCapacityLiters: Float,
        consumptionKmPerLiter: Float,
        lowFuelWarningLiters: Float
    ) {
        val safeCap = tankCapacityLiters.coerceIn(0.5f, 200f)
        val safeCons = consumptionKmPerLiter.coerceIn(1.0f, 120f)
        val safeWarn = lowFuelWarningLiters.coerceIn(0.1f, safeCap)
        val updated = _uiState.value.fuelState.copy(
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
        viewModelScope.launch { repository?.preferencesStore?.updateFuel(updated) }
    }

    fun markRefueledFullTank() {
        val updated = _uiState.value.fuelState.copy(
            distanceDrivenSinceRefuelKm = 0.0,
            lowFuelAlertTriggered = false
        )
        _uiState.update {
            it.copy(
                fuelState = updated,
                statusBannerMessageTh = "รีเซ็ตปริมาณน้ำมันเต็มถังเรียบร้อยแล้ว"
            )
        }
        viewModelScope.launch { repository?.preferencesStore?.updateFuel(updated) }
    }

    fun setAlertVibrationEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                alertVibrationEnabled = enabled,
                statusBannerMessageTh = if (enabled) "เปิดการสั่นแจ้งเตือนโซนแล้ว" else "ปิดการสั่นแจ้งเตือนโซนแล้ว"
            )
        }
        persistTelemetryPrefs()
        syncZoneConfigToBus()
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        _uiState.update {
            it.copy(
                measurementUnit = unit,
                statusBannerMessageTh = "เปลี่ยนหน่วยวัดเป็น ${unit.titleTh}"
            )
        }
        persistTelemetryPrefs()
    }

    fun setUpdateFrequency(context: Context, frequency: UpdateFrequency) {
        _uiState.update {
            it.copy(
                updateFrequency = frequency,
                statusBannerMessageTh = "ตั้งความถี่อัปเดต GPS เป็น ${frequency.titleTh}"
            )
        }
        persistTelemetryPrefs()
        if (_uiState.value.isLockEnabled) {
            startGpsLockEngine(context)
        }
    }

    fun setCoordinateFormat(format: CoordinateFormat) {
        _uiState.update {
            it.copy(
                coordinateFormat = format,
                statusBannerMessageTh = "เปลี่ยนรูปแบบพิกัดเป็น ${format.titleTh}"
            )
        }
        persistTelemetryPrefs()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiState.update {
            it.copy(
                keepScreenOn = enabled,
                statusBannerMessageTh = if (enabled) "เปิดโหมดหน้าจอค้างไว้เสมอ" else "ปิดโหมดหน้าจอค้างไว้เสมอ"
            )
        }
        persistTelemetryPrefs()
    }

    private fun persistTelemetryPrefs() {
        val s = _uiState.value
        viewModelScope.launch {
            repository?.preferencesStore?.updateTelemetryPreferences(
                alertVibrationEnabled = s.alertVibrationEnabled,
                measurementUnit = s.measurementUnit,
                updateFrequency = s.updateFrequency,
                coordinateFormat = s.coordinateFormat,
                keepScreenOn = s.keepScreenOn,
                lockEnabled = s.isLockEnabled
            )
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val repo = repository ?: FixPointRepository(context.applicationContext)
            runCatching {
                val json = repo.exportLocalBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("ไม่สามารถเปิดไฟล์ปลายทางได้")
            }.onSuccess {
                _uiState.update {
                    it.copy(statusBannerMessageTh = "สำรองข้อมูลทั้งหมดลงไฟล์ในเครื่องเรียบร้อยแล้ว")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusBannerMessageTh = "สำรองข้อมูลไม่สำเร็จ (${err.localizedMessage ?: "ข้อผิดพลาดไฟล์"})")
                }
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val repo = repository ?: FixPointRepository(context.applicationContext)
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: error("ไม่สามารถอ่านไฟล์สำรองได้")
                repo.importLocalBackupJson(raw).getOrThrow()
            }.onSuccess { count ->
                _uiState.update {
                    it.copy(statusBannerMessageTh = "กู้คืนข้อมูลจากไฟล์สำรองเรียบร้อยแล้ว ($count รายการ)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusBannerMessageTh = "กู้คืนข้อมูลไม่สำเร็จ (${err.localizedMessage ?: "รูปแบบไฟล์ไม่ถูกต้อง"})")
                }
            }
        }
    }

    // =========================================================================
    // Phase 4 & 6: Multi-Platform Job Radar & Cloud Sync Readiness
    // =========================================================================

    fun setJobRankingMode(mode: JobRankingMode) {
        _uiState.update {
            it.copy(
                jobRankingMode = mode,
                statusBannerMessageTh = "จัดอันดับ Job Radar: ${mode.labelTh}"
            )
        }
        val s = _uiState.value
        viewModelScope.launch {
            repository?.preferencesStore?.updateJobRadarPreferences(
                rankingMode = mode,
                lineManEnabled = s.lineManEnabled,
                grabEnabled = s.grabEnabled,
                shopeeFoodEnabled = s.shopeeFoodEnabled
            )
        }
    }

    fun togglePlatformAdapter(platform: DeliveryPlatform, enabled: Boolean) {
        _uiState.update { s ->
            when (platform) {
                DeliveryPlatform.LINE_MAN -> s.copy(lineManEnabled = enabled)
                DeliveryPlatform.GRAB -> s.copy(grabEnabled = enabled)
                DeliveryPlatform.SHOPEE_FOOD -> s.copy(shopeeFoodEnabled = enabled)
                DeliveryPlatform.OTHER -> s
            }
        }
        val s = _uiState.value
        viewModelScope.launch {
            repository?.preferencesStore?.updateJobRadarPreferences(
                rankingMode = s.jobRankingMode,
                lineManEnabled = s.lineManEnabled,
                grabEnabled = s.grabEnabled,
                shopeeFoodEnabled = s.shopeeFoodEnabled
            )
        }
    }

    fun handleIncomingNotificationSignal(signal: RawNotificationJobSignal) {
        val adapter = DeliveryAdapterRegistry.resolveForPackage(signal.packageName) ?: return
        if (!_uiState.value.isPlatformAdapterEnabled(adapter.platform)) return
        ingestJobOfferInternal(
            rawText = signal.body,
            title = signal.title,
            packageName = signal.packageName,
            source = JobOfferSource.NOTIFICATION_LISTENER,
            pickupLat = null,
            pickupLng = null,
            nowMillis = signal.postedAtMillis,
            silentIfUnparsed = true
        )
    }

    fun ingestJobOfferText(
        rawText: String,
        source: JobOfferSource = JobOfferSource.MANUAL_OR_SHARED,
        title: String = "",
        pickupLat: Double? = null,
        pickupLng: Double? = null
    ): Boolean {
        return ingestJobOfferInternal(
            rawText = rawText,
            title = title,
            packageName = null,
            source = source,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            nowMillis = System.currentTimeMillis(),
            silentIfUnparsed = false
        )
    }

    private fun ingestJobOfferInternal(
        rawText: String,
        title: String,
        packageName: String?,
        source: JobOfferSource,
        pickupLat: Double?,
        pickupLng: Double?,
        nowMillis: Long,
        silentIfUnparsed: Boolean
    ): Boolean {
        val state = _uiState.value
        val parsed = JobRadarEngine.parseToJobOffer(
            rawText = rawText,
            source = source,
            profile = state.riderProfile,
            fuelState = state.fuelState,
            riderLat = state.telemetry.latitude,
            riderLng = state.telemetry.longitude,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            packageName = packageName,
            title = title,
            nowMillis = nowMillis
        )
        if (parsed == null) {
            if (!silentIfUnparsed) {
                _uiState.update {
                    it.copy(statusBannerMessageTh = "ไม่พบข้อมูลค่าส่งหรือรายละเอียดงานที่เพียงพอในข้อความนี้")
                }
            }
            return false
        }

        val duplicate = JobRadarEngine.isDuplicateOffer(state.detectedJobOffers, parsed)
        if (duplicate != null) {
            val merged = duplicate.copy(
                isDuplicateMerged = true,
                expiresAtMillis = maxOf(duplicate.expiresAtMillis, parsed.expiresAtMillis),
                smartScore = maxOf(duplicate.smartScore, parsed.smartScore),
                confidenceScore = maxOf(duplicate.confidenceScore, parsed.confidenceScore)
            )
            _uiState.update { s ->
                s.copy(
                    detectedJobOffers = s.detectedJobOffers.map { if (it.id == duplicate.id) merged else it },
                    statusBannerMessageTh = "รวมสัญญาณงานซ้ำจาก ${parsed.platform.displayNameTh} แล้ว"
                )
            }
            viewModelScope.launch { repository?.upsertJobOffer(merged) }
            return true
        }

        _uiState.update { s ->
            s.copy(
                detectedJobOffers = listOf(parsed) + s.detectedJobOffers,
                radarClockTickMillis = nowMillis,
                statusBannerMessageTh = String.format(
                    Locale.US,
                    "ตรวจพบงานใหม่ %s ฿%.0f (เกรด %s)",
                    parsed.platform.displayNameTh,
                    parsed.payoutBaht,
                    parsed.grade.label
                )
            )
        }
        ensureRadarTickerRunning()
        viewModelScope.launch { repository?.upsertJobOffer(parsed) }
        return true
    }

    fun acceptAndLogJobOffer(offerId: String): Boolean {
        val state = _uiState.value
        val offer = state.detectedJobOffers.firstOrNull { it.id == offerId } ?: return false
        val updatedOffer = offer.copy(status = JobOfferStatus.ACCEPTED_LOGGED)
        val now = System.currentTimeMillis()
        val todayKey = DailyBreakEvenState.todayDateKey(now)

        _uiState.update { s ->
            s.copy(
                detectedJobOffers = s.detectedJobOffers.map { if (it.id == offerId) updatedOffer else it }
            )
        }
        viewModelScope.launch { repository?.upsertJobOffer(updatedOffer) }

        val orderRecord = AnalyzedOrderRecord(
            id = "ORD-RAD-${offer.id.takeLast(8)}-$now",
            dateKey = todayKey,
            source = when (offer.source) {
                JobOfferSource.NOTIFICATION_LISTENER -> OrderInputSource.MANUAL_OR_TEXT
                JobOfferSource.OCR_SCREEN_READER -> OrderInputSource.SCREENSHOT_IMPORT
                JobOfferSource.MANUAL_OR_SHARED -> OrderInputSource.MANUAL_OR_TEXT
            },
            pickupName = offer.pickupName,
            dropoffName = offer.dropoffName,
            payoutBaht = offer.payoutBaht,
            distanceKm = offer.tripDistanceKm,
            deadMileageKm = offer.deadMileageKm,
            estimatedMinutes = offer.estimatedMinutes ?: 0,
            fuelCostBaht = (offer.payoutBaht - offer.netProfitBaht).coerceAtLeast(0.0),
            netProfitBaht = offer.netProfitBaht,
            earningsPerKm = offer.earningsPerKm,
            earningsPerHour = offer.earningsPerHour ?: 0.0,
            smartScore = offer.smartScore,
            grade = offer.grade,
            confidenceScore = offer.confidenceScore,
            wasUserCorrected = false,
            savedToEarnings = true,
            latitude = state.telemetry.latitude ?: state.activePin?.latitude,
            longitude = state.telemetry.longitude ?: state.activePin?.longitude,
            createdAtMillis = now
        )
        _uiState.update { s ->
            s.copy(analyzedOrders = listOf(orderRecord) + s.analyzedOrders)
        }
        viewModelScope.launch { repository?.upsertAnalyzedOrder(orderRecord) }

        val title = "[${offer.platform.displayNameTh}] ${offer.pickupName}"
        addEarningsTransaction(
            type = EarningsRecordType.INCOME,
            dateKey = todayKey,
            title = title,
            category = offer.platform.displayNameTh,
            amountBaht = offer.payoutBaht,
            distanceKm = offer.tripDistanceKm + offer.deadMileageKm,
            durationMinutes = offer.estimatedMinutes ?: 0,
            timestampMillis = now
        )

        state.activeWorkSession?.let { active ->
            val updatedSession = active.copy(
                completedOrders = active.completedOrders + 1,
                grossIncomeBaht = active.grossIncomeBaht + offer.payoutBaht
            )
            _uiState.update { s ->
                s.copy(workSessions = s.workSessions.map { if (it.id == active.id) updatedSession else it })
            }
            viewModelScope.launch { repository?.upsertWorkSession(updatedSession) }
        }
        return true
    }

    fun dismissJobOffer(offerId: String) {
        val offer = _uiState.value.detectedJobOffers.firstOrNull { it.id == offerId } ?: return
        val updated = offer.copy(status = JobOfferStatus.DISMISSED)
        _uiState.update { s ->
            s.copy(
                detectedJobOffers = s.detectedJobOffers.map { if (it.id == offerId) updated else it },
                statusBannerMessageTh = "ข้ามรายการงานจาก ${offer.platform.displayNameTh} แล้ว"
            )
        }
        viewModelScope.launch { repository?.upsertJobOffer(updated) }
    }

    fun clearExpiredJobOffers() {
        val expired = _uiState.value.detectedJobOffers.filter { it.status == JobOfferStatus.EXPIRED }
        if (expired.isEmpty()) return
        _uiState.update { s ->
            s.copy(
                detectedJobOffers = s.detectedJobOffers.filterNot { it.status == JobOfferStatus.EXPIRED },
                statusBannerMessageTh = "ล้างรายการงานที่หมดเวลา ${expired.size} รายการแล้ว"
            )
        }
        viewModelScope.launch {
            expired.forEach { repository?.deleteJobOffer(it.id) }
        }
    }

    fun clearAllJobOffers() {
        _uiState.update {
            it.copy(
                detectedJobOffers = emptyList(),
                statusBannerMessageTh = "ล้างรายการเรดาร์งานทั้งหมดแล้ว"
            )
        }
        viewModelScope.launch { repository?.clearJobOffers() }
    }

    fun loadOfferIntoAiAnalyzer(offer: DetectedJobOffer) {
        val draft = ParsedOrderDraft(
            source = when (offer.source) {
                JobOfferSource.OCR_SCREEN_READER -> OrderInputSource.SCREENSHOT_IMPORT
                else -> OrderInputSource.MANUAL_OR_TEXT
            },
            rawOcrText = offer.rawSummary,
            payoutBaht = offer.payoutBaht,
            payoutConfidence = offer.confidenceScore,
            distanceKm = offer.tripDistanceKm.takeIf { it > 0.0 },
            distanceConfidence = if (offer.tripDistanceKm > 0.0) offer.confidenceScore else 0f,
            estimatedMinutes = offer.estimatedMinutes,
            durationConfidence = if (offer.estimatedMinutes != null) offer.confidenceScore else 0f,
            deadMileageKm = offer.effectiveDistanceToPickupKm(),
            pickupName = offer.pickupName,
            dropoffName = offer.dropoffName,
            platformHint = offer.platform.displayNameTh,
            overallConfidence = offer.confidenceScore,
            wasUserCorrected = false
        )
        val state = _uiState.value
        val analysis = RiderIntelligenceEngine.analyzeOrder(draft, state.riderProfile, state.fuelState)
        _uiState.update {
            it.copy(
                currentOrderDraft = draft,
                currentOrderAnalysis = analysis,
                currentScreen = AppScreen.AI_ASSISTANT,
                secondaryScreen = null,
                ocrErrorMessageTh = if (draft.distanceKm == null) {
                    "แจ้งเตือนไม่ได้ระบุระยะทางส่ง — กรุณากรอกระยะทาง (กม.) เพื่อคำนวณ Smart Score แม่นยำ"
                } else null
            )
        }
    }

    fun toggleCloudSyncOptIn(enabled: Boolean) {
        _uiState.update {
            it.copy(cloudSyncState = it.cloudSyncState.copy(cloudSyncOptIn = enabled))
        }
        viewModelScope.launch {
            val s = _uiState.value.cloudSyncState
            repository?.preferencesStore?.updateCloudSyncPreferences(
                cloudSyncOptIn = enabled,
                membershipProEnabled = s.isMembershipProEnabled
            )
            refreshCloudSyncReadiness()
        }
    }

    fun toggleMembershipProReady(enabled: Boolean) {
        _uiState.update {
            it.copy(cloudSyncState = it.cloudSyncState.copy(isMembershipProEnabled = enabled))
        }
        viewModelScope.launch {
            val s = _uiState.value.cloudSyncState
            repository?.preferencesStore?.updateCloudSyncPreferences(
                cloudSyncOptIn = s.cloudSyncOptIn,
                membershipProEnabled = enabled
            )
            refreshCloudSyncReadiness()
        }
    }

    fun refreshCloudSyncReadiness() {
        val repo = repository ?: return
        viewModelScope.launch {
            val readyState = runCatching { repo.cloudSyncManager.evaluateReadiness() }.getOrNull() ?: return@launch
            _uiState.update { it.copy(cloudSyncState = readyState) }
        }
    }

    fun triggerCloudSyncAttempt() {
        val repo = repository ?: return
        viewModelScope.launch {
            val res = repo.cloudSyncManager.requestCloudSync()
            refreshCloudSyncReadiness()
            val msg = res.exceptionOrNull()?.localizedMessage
                ?: "ซิงก์ข้อมูลคลาวด์สำเร็จ"
            _uiState.update {
                it.copy(
                    cloudSyncState = it.cloudSyncState.copy(lastCloudSyncAttemptMillis = System.currentTimeMillis()),
                    statusBannerMessageTh = msg
                )
            }
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessageTh = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopViewModelRepeatAlertLoop()
        sessionTickerJob?.cancel()
        radarTickerJob?.cancel()
        try {
            locationService?.stopLocationUpdates()
        } catch (_: Throwable) {}
    }
}
