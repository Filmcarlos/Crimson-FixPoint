package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.GeoMath
import com.example.model.SatelliteBarItem
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max

/**
 * Data model representing a high-accuracy GPS update processed through the Kalman Filter.
 */
data class SmoothedLocationUpdate(
    val rawLat: Double,
    val rawLng: Double,
    val smoothedLat: Double,
    val smoothedLng: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val kalmanGain: Float,
    val driftSuppressedMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
    val timestampMillis: Long
)

/**
 * 2D Kalman Filter for GPS coordinate stabilization and anti-drift smoothing.
 */
class GpsKalmanFilter(
    private var processNoiseMetersPerSec: Float = 2.0f
) {
    private var isInitialized: Boolean = false
    private var estimatedLat: Double = 0.0
    private var estimatedLng: Double = 0.0
    private var variance: Float = -1f
    private var lastTimestampMillis: Long = 0L
    var lastKalmanGain: Float = 0.85f
        private set

    fun setProcessNoise(qMetersPerSec: Float) {
        processNoiseMetersPerSec = qMetersPerSec.coerceIn(0.3f, 15.0f)
    }

    fun reset() {
        isInitialized = false
        variance = -1f
        lastTimestampMillis = 0L
    }

    fun process(
        measuredLat: Double,
        measuredLng: Double,
        accuracyMeters: Float,
        timestampMillis: Long
    ): Pair<Double, Double> {
        val safeAccuracy = max(accuracyMeters, 1.0f)

        if (!isInitialized || variance < 0f) {
            estimatedLat = measuredLat
            estimatedLng = measuredLng
            variance = safeAccuracy * safeAccuracy
            lastTimestampMillis = timestampMillis
            lastKalmanGain = 1.0f
            isInitialized = true
            return Pair(estimatedLat, estimatedLng)
        }

        val dtMillis = (timestampMillis - lastTimestampMillis).coerceAtLeast(0L)
        if (dtMillis > 0L) {
            val dtSeconds = dtMillis / 1000.0f
            variance += dtSeconds * processNoiseMetersPerSec * processNoiseMetersPerSec
            lastTimestampMillis = timestampMillis
        }

        val measurementVariance = safeAccuracy * safeAccuracy
        val kalmanGain = (variance / (variance + measurementVariance)).coerceIn(0.02f, 0.98f)
        lastKalmanGain = kalmanGain

        estimatedLat += kalmanGain * (measuredLat - estimatedLat)
        estimatedLng += kalmanGain * (measuredLng - estimatedLng)
        variance = (1.0f - kalmanGain) * variance

        return Pair(estimatedLat, estimatedLng)
    }
}

/**
 * LocationService handling [FusedLocationProviderClient], hardware [LocationManager],
 * [GnssStatus.Callback] satellite telemetry, and hardware [SensorManager] compass azimuth.
 */
class LocationService(
    context: Context,
    processNoiseMetersPerSec: Float = 2.0f
) {
    private val appContext: Context = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val kalmanFilter = GpsKalmanFilter(processNoiseMetersPerSec)

    private val _latestLocation = MutableStateFlow<SmoothedLocationUpdate?>(null)
    val latestLocation: StateFlow<SmoothedLocationUpdate?> = _latestLocation.asStateFlow()

    private val _compassAzimuthDeg = MutableStateFlow<Float?>(null)
    val compassAzimuthDeg: StateFlow<Float?> = _compassAzimuthDeg.asStateFlow()

    private val _gnssSatellites = MutableStateFlow<Triple<Int, Int, List<SatelliteBarItem>>>(
        Triple(0, 0, emptyList())
    )
    val gnssSatellites: StateFlow<Triple<Int, Int, List<SatelliteBarItem>>> =
        _gnssSatellites.asStateFlow()

    private var activeFusedCallback: LocationCallback? = null
    private var activeLocationListener: LocationListener? = null
    private var activeGnssCallback: GnssStatus.Callback? = null
    private var activeSensorListener: SensorEventListener? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun resetKalmanFilter() {
        kalmanFilter.reset()
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        intervalMillis: Long = 1000L,
        minUpdateIntervalMillis: Long = 500L,
        onUpdate: (SmoothedLocationUpdate) -> Unit = {}
    ) {
        if (!hasLocationPermission()) return
        stopLocationUpdates()

        // 1. FusedLocationProviderClient
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                val smoothedUpdate = processRawLocation(location)
                _latestLocation.value = smoothedUpdate
                onUpdate(smoothedUpdate)
            }
        }
        activeFusedCallback = fusedCallback

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                fusedCallback,
                Looper.getMainLooper()
            )
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null && _latestLocation.value == null) {
                    val initialUpdate = processRawLocation(lastLoc)
                    _latestLocation.value = initialUpdate
                    onUpdate(initialUpdate)
                }
            }
        } catch (_: Throwable) {}

        // 2. Hardware LocationManager + GnssStatus for direct satellite counts & SNR
        try {
            val mgr = locationManager
            if (mgr != null) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val smoothedUpdate = processRawLocation(location)
                        _latestLocation.value = smoothedUpdate
                        onUpdate(smoothedUpdate)
                    }

                    @Deprecated("Deprecated in Android API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                activeLocationListener = listener

                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    if (mgr.isProviderEnabled(provider)) {
                        mgr.requestLocationUpdates(
                            provider,
                            intervalMillis,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    }
                    val lastKnown = mgr.getLastKnownLocation(provider)
                    if (lastKnown != null && _latestLocation.value == null) {
                        val initialUpdate = processRawLocation(lastKnown)
                        _latestLocation.value = initialUpdate
                        onUpdate(initialUpdate)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val gnssCb = object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            val total = status.satelliteCount
                            var used = 0
                            val bars = mutableListOf<SatelliteBarItem>()
                            for (i in 0 until total) {
                                val isUsed = status.usedInFix(i)
                                if (isUsed) used++
                                val label = when (status.getConstellationType(i)) {
                                    GnssStatus.CONSTELLATION_GPS -> "GPS"
                                    GnssStatus.CONSTELLATION_GLONASS -> "GLO"
                                    GnssStatus.CONSTELLATION_GALILEO -> "GAL"
                                    GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
                                    GnssStatus.CONSTELLATION_QZSS -> "QZS"
                                    else -> "GNSS"
                                }
                                bars.add(
                                    SatelliteBarItem(
                                        svid = status.getSvid(i),
                                        constellationLabel = label,
                                        cn0DbHz = status.getCn0DbHz(i),
                                        usedInFix = isUsed
                                    )
                                )
                            }
                            _gnssSatellites.value = Triple(
                                used,
                                total,
                                bars.sortedByDescending { it.cn0DbHz }.take(12)
                            )
                        }
                    }
                    activeGnssCallback = gnssCb
                    mgr.registerGnssStatusCallback(gnssCb, null)
                }
            }
        } catch (_: Throwable) {}

        // 3. Hardware Compass / Rotation Vector Sensor for real-time heading
        startCompassSensor()
    }

    private fun startCompassSensor() {
        try {
            val sm = sensorManager ?: return
            val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationSensor != null) {
                val listener = object : SensorEventListener {
                    private val rotationMatrix = FloatArray(9)
                    private val orientationAngles = FloatArray(3)

                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            val azimuthRad = orientationAngles[0].toDouble()
                            val azimuthDeg = ((Math.toDegrees(azimuthRad) + 360.0) % 360.0).toFloat()
                            _compassAzimuthDeg.value = azimuthDeg
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                activeSensorListener = listener
                sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdatesFlow(
        intervalMillis: Long = 1000L,
        minUpdateIntervalMillis: Long = 500L
    ): Flow<SmoothedLocationUpdate> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val smoothedUpdate = processRawLocation(location)
                _latestLocation.value = smoothedUpdate
                trySend(smoothedUpdate)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (_: Throwable) {}
        }
    }

    fun stopLocationUpdates() {
        activeFusedCallback?.let {
            runCatching { fusedLocationClient.removeLocationUpdates(it) }
        }
        activeFusedCallback = null

        activeLocationListener?.let {
            runCatching { locationManager?.removeUpdates(it) }
        }
        activeLocationListener = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activeGnssCallback?.let {
                runCatching { locationManager?.unregisterGnssStatusCallback(it) }
            }
        }
        activeGnssCallback = null

        activeSensorListener?.let {
            runCatching { sensorManager?.unregisterListener(it) }
        }
        activeSensorListener = null
    }

    fun processRawLocation(location: Location): SmoothedLocationUpdate {
        val ts = if (location.time > 0L) location.time else System.currentTimeMillis()
        val (smoothedLat, smoothedLng) = kalmanFilter.process(
            measuredLat = location.latitude,
            measuredLng = location.longitude,
            accuracyMeters = location.accuracy,
            timestampMillis = ts
        )
        val driftMeters = (GeoMath.haversineKm(
            location.latitude,
            location.longitude,
            smoothedLat,
            smoothedLng
        ) * 1000.0).toFloat()

        return SmoothedLocationUpdate(
            rawLat = location.latitude,
            rawLng = location.longitude,
            smoothedLat = smoothedLat,
            smoothedLng = smoothedLng,
            accuracyMeters = location.accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            kalmanGain = kalmanFilter.lastKalmanGain,
            driftSuppressedMeters = driftMeters,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            bearingDeg = if (location.hasBearing()) location.bearing else null,
            timestampMillis = ts
        )
    }
}
