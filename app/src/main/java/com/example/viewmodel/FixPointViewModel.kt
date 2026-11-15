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
import com.example.model.GeoMath
import com.example.model.GpsLockState
import com.example.model.LiveGnssTelemetry
import com.example.model.MapTapActionMode
import com.example.model.MeasurementUnit
import com.example.model.SatelliteBarItem
import com.example.model.SavedLocationItem
import com.example.model.UpdateFrequency
import com.example.model.ZoneAlertTone
import com.example.model.ZoneMonitorStatus
import com.example.service.GpsKeepAliveService
import com.example.service.GpsServiceRuntimeBus
import com.example.service.LocationService
import com.example.service.ServiceZoneConfig
import com.example.service.SmoothedLocationUpdate
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
                zoneStatus = if (resolvedActiveId != null) ZoneMonitorStatus.WAITING_FOR_GPS else ZoneMonitorStatus.NO_ACTIVE_PIN
            )
        }
        syncZoneConfigToBus()

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
        recalculateZoneEvaluation(triggerAlertsOnTransition = true)
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
            recalculateZoneEvaluation(triggerAlertsOnTransition = true)
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

    /**
     * Evaluates the real GPS position against the active pin, radius (0-30 km), and GPS buffer (0-100 m).
     */
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
                isAlertMuted = s.isZoneAlertMuted
            )
        )
    }

    /**
     * Method 1: Pin or Move using current real GPS coordinates.
     */
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

    /**
     * Method 2: Pin or Move by tapping directly on the map.
     */
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

    /**
     * Method 3: Pin or Move by pasting/typing coordinates ("ละติจูด, ลองจิจูด").
     */
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

    /**
     * Adjusts the Work-Zone radius between 0.0 and 30.0 km (rounded to 1 decimal place).
     */
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
    }
}
