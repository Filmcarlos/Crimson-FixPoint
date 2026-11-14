package com.example.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppScreen
import com.example.model.CoordinateFormat
import com.example.model.GpsLockState
import com.example.model.LiveGnssTelemetry
import com.example.model.MeasurementUnit
import com.example.model.SatelliteBarItem
import com.example.model.SavedLocationItem
import com.example.model.UpdateFrequency
import com.example.service.GpsKeepAliveService
import com.example.service.GpsServiceRuntimeBus
import com.example.service.LocationService
import com.example.service.SmoothedLocationUpdate
import com.example.util.TacticalSoundEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FixPointUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val isLockEnabled: Boolean = false,
    val lockState: GpsLockState = GpsLockState.OFF,
    val telemetry: LiveGnssTelemetry = LiveGnssTelemetry(),
    val savedLocations: List<SavedLocationItem> = emptyList(),
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = true,
    val hasFineLocationPermission: Boolean = false,
    val hasCoarseLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val permissionDeniedMessageTh: String? = null,
    val statusBannerMessageTh: String? = null
) {
    val hasAnyLocationPermission: Boolean
        get() = hasFineLocationPermission || hasCoarseLocationPermission
}

class FixPointViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FixPointUiState())
    val uiState: StateFlow<FixPointUiState> = _uiState.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var locationService: LocationService? = null
    private var isInitializedWithContext = false

    init {
        // Observe background service location updates so screen-off updates stay synced
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
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
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
        val savedItemsJson = sharedPrefs.getString(KEY_SAVED_LOCATIONS, null)

        val unit = runCatching { MeasurementUnit.valueOf(unitName ?: "") }.getOrDefault(MeasurementUnit.METRIC)
        val freq = runCatching { UpdateFrequency.valueOf(freqName ?: "") }.getOrDefault(UpdateFrequency.STANDARD)
        val coordFmt = runCatching { CoordinateFormat.valueOf(coordName ?: "") }.getOrDefault(CoordinateFormat.DECIMAL)
        val savedItems = SavedLocationItem.deserializeList(savedItemsJson)

        _uiState.update { state ->
            state.copy(
                measurementUnit = unit,
                updateFrequency = freq,
                coordinateFormat = coordFmt,
                keepScreenOn = keepScreen,
                savedLocations = savedItems
            )
        }

        val service = LocationService(appContext)
        locationService = service

        // Collect live compass heading and GNSS satellite status from LocationService
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

        // Restore last remembered power state if location permission is granted
        if (savedLockEnabled && _uiState.value.hasAnyLocationPermission) {
            startGpsLockEngine(appContext)
        } else if (savedLockEnabled && !_uiState.value.hasAnyLocationPermission) {
            // Keep remembered preference as false until user grants permission
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

        _uiState.update {
            it.copy(
                hasFineLocationPermission = fine,
                hasCoarseLocationPermission = coarse,
                hasBackgroundLocationPermission = bg,
                hasNotificationPermission = notif,
                permissionDeniedMessageTh = if (fine || coarse) null else it.permissionDeniedMessageTh
            )
        }
    }

    fun selectScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
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
                    permissionDeniedMessageTh = "ไม่ได้รับสิทธิ์การเข้าถึงตำแหน่ง กรุณาอนุญาตสิทธิ์ตำแหน่งแม่นยำเพื่อเริ่มล็อกสัญญาณ GPS"
                )
            }
        }
    }

    fun toggleMainPowerLock(context: Context): Boolean {
        refreshPermissionFlags(context)
        val currentlyEnabled = _uiState.value.isLockEnabled
        if (currentlyEnabled) {
            stopGpsLockEngine(context)
            return true
        } else {
            if (!_uiState.value.hasAnyLocationPermission) {
                return false // Caller should launch runtime permission dialog
            }
            startGpsLockEngine(context)
            return true
        }
    }

    fun startGpsLockEngine(context: Context) {
        val appContext = context.applicationContext
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

        _uiState.update { state ->
            val nextLockState = if (state.telemetry.hasFix) GpsLockState.LOCKED else GpsLockState.SEARCHING
            state.copy(
                isLockEnabled = true,
                lockState = nextLockState,
                permissionDeniedMessageTh = null,
                statusBannerMessageTh = "เริ่มทำงานระบบล็อกสัญญาณ GPS เบื้องหลังแล้ว"
            )
        }

        TacticalSoundEngine.playLockChime(false)

        // 1. Start Foreground Service with configured update interval
        try {
            val serviceIntent = Intent(appContext, GpsKeepAliveService::class.java).apply {
                putExtra(GpsKeepAliveService.EXTRA_INTERVAL_MS, freq.intervalMillis)
            }
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (_: Throwable) {}

        // 2. Start LocationService (FusedLocationProviderClient + LocationManager + GnssStatus + Compass)
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
                statusBannerMessageTh = "หยุดการทำงานและปล่อยทรัพยากร GPS เรียบร้อยแล้ว"
            )
        }
    }

    private fun applySmoothedLocation(update: SmoothedLocationUpdate) {
        _uiState.update { state ->
            if (!state.isLockEnabled) return@update state
            val wasSearching = state.lockState != GpsLockState.LOCKED
            if (wasSearching) {
                TacticalSoundEngine.playSRankMatchBeep(false)
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
     * Saves the current real GPS coordinates with a user-defined name and timestamp.
     */
    fun saveCurrentLocation(customName: String): Boolean {
        val currentTelemetry = _uiState.value.telemetry
        val lat = currentTelemetry.latitude ?: return false
        val lng = currentTelemetry.longitude ?: return false

        val cleanName = customName.trim().ifBlank {
            "พิกัดที่บันทึก #${_uiState.value.savedLocations.size + 1}"
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
        prefs?.edit()?.putString(KEY_SAVED_LOCATIONS, SavedLocationItem.serializeList(updatedList))?.apply()

        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                statusBannerMessageTh = "บันทึกตำแหน่ง \"$cleanName\" เรียบร้อยแล้ว"
            )
        }
        return true
    }

    fun deleteSavedLocation(id: String) {
        val updatedList = _uiState.value.savedLocations.filterNot { it.id == id }
        prefs?.edit()?.putString(KEY_SAVED_LOCATIONS, SavedLocationItem.serializeList(updatedList))?.apply()
        _uiState.update {
            it.copy(
                savedLocations = updatedList,
                statusBannerMessageTh = "ลบรายการตำแหน่งที่บันทึกแล้ว"
            )
        }
    }

    fun clearAllSavedLocations() {
        prefs?.edit()?.remove(KEY_SAVED_LOCATIONS)?.apply()
        _uiState.update {
            it.copy(
                savedLocations = emptyList(),
                statusBannerMessageTh = "ล้างรายการตำแหน่งที่บันทึกทั้งหมดแล้ว"
            )
        }
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
    }
}
