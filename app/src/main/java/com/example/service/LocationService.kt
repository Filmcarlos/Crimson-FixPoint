package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.GeoMath
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
    val kalmanGain: Float,
    val driftSuppressedMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val timestampMillis: Long
)

/**
 * 2D Kalman Filter for GPS coordinate stabilization and anti-drift smoothing.
 * Models position uncertainty (variance) over time and blends noisy GNSS measurements
 * using optimal Kalman Gain K = P / (P + R).
 */
class GpsKalmanFilter(
    private var processNoiseMetersPerSec: Float = 2.2f
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
        val safeAccuracy = max(accuracyMeters, 1.5f)

        if (!isInitialized || variance < 0f) {
            estimatedLat = measuredLat
            estimatedLng = measuredLng
            variance = safeAccuracy * safeAccuracy
            lastTimestampMillis = timestampMillis
            lastKalmanGain = 1.0f
            isInitialized = true
            return Pair(estimatedLat, estimatedLng)
        }

        // 1. Time update (Prediction step): increase variance based on elapsed time and process noise Q
        val dtMillis = (timestampMillis - lastTimestampMillis).coerceAtLeast(0L)
        if (dtMillis > 0L) {
            val dtSeconds = dtMillis / 1000.0f
            variance += dtSeconds * processNoiseMetersPerSec * processNoiseMetersPerSec
            lastTimestampMillis = timestampMillis
        }

        // 2. Measurement update (Correction step): compute Kalman Gain K = P / (P + R)
        val measurementVariance = safeAccuracy * safeAccuracy
        val kalmanGain = (variance / (variance + measurementVariance)).coerceIn(0.02f, 0.98f)
        lastKalmanGain = kalmanGain

        // Update state estimate
        estimatedLat += kalmanGain * (measuredLat - estimatedLat)
        estimatedLng += kalmanGain * (measuredLng - estimatedLng)

        // Update error covariance P = (1 - K) * P
        variance = (1.0f - kalmanGain) * variance

        return Pair(estimatedLat, estimatedLng)
    }
}

/**
 * LocationService handling Google Play Services [FusedLocationProviderClient] for continuous,
 * high-accuracy GPS tracking combined with [GpsKalmanFilter] anti-drift stabilization.
 */
class LocationService(
    context: Context,
    processNoiseMetersPerSec: Float = 2.0f
) {
    private val appContext: Context = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val kalmanFilter = GpsKalmanFilter(processNoiseMetersPerSec)

    private val _latestLocation = MutableStateFlow<SmoothedLocationUpdate?>(null)
    val latestLocation: StateFlow<SmoothedLocationUpdate?> = _latestLocation.asStateFlow()

    private var activeCallback: LocationCallback? = null

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

    fun setKalmanProcessNoise(qMetersPerSec: Float) {
        kalmanFilter.setProcessNoise(qMetersPerSec)
    }

    fun resetKalmanFilter() {
        kalmanFilter.reset()
    }

    /**
     * Starts continuous high-accuracy location updates via [FusedLocationProviderClient]
     * and invokes [onUpdate] with each Kalman-smoothed GPS fix.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        intervalMillis: Long = 1000L,
        minUpdateIntervalMillis: Long = 500L,
        onUpdate: (SmoothedLocationUpdate) -> Unit = {}
    ) {
        if (!hasLocationPermission()) return
        stopLocationUpdates()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                val smoothedUpdate = processRawLocation(location)
                _latestLocation.value = smoothedUpdate
                onUpdate(smoothedUpdate)
            }
        }

        activeCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null && _latestLocation.value == null) {
                    val initialUpdate = processRawLocation(lastLoc)
                    _latestLocation.value = initialUpdate
                    onUpdate(initialUpdate)
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        } catch (_: Throwable) {
            // Google Play Services unavailable on device/test environment
        }
    }

    /**
     * Cold [Flow] streaming continuous high-accuracy Kalman-smoothed GPS updates.
     */
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
        val cb = activeCallback ?: return
        try {
            fusedLocationClient.removeLocationUpdates(cb)
        } catch (_: Throwable) {}
        activeCallback = null
    }

    private fun processRawLocation(location: Location): SmoothedLocationUpdate {
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
            accuracyMeters = location.accuracy.coerceIn(1.5f, 30.0f),
            kalmanGain = kalmanFilter.lastKalmanGain,
            driftSuppressedMeters = driftMeters,
            speedMps = location.speed,
            bearingDeg = location.bearing,
            timestampMillis = ts
        )
    }
}
