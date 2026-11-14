package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.model.SatelliteBarItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Shared real-time bridge between [GpsKeepAliveService] and the UI ViewModel
 * so background GPS fixes and satellite status stay synchronized even when the screen turns off.
 */
object GpsServiceRuntimeBus {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _lastServiceLocation = MutableStateFlow<Location?>(null)
    val lastServiceLocation: StateFlow<Location?> = _lastServiceLocation.asStateFlow()

    private val _satelliteSnapshot = MutableStateFlow(
        Triple(0, 0, emptyList<SatelliteBarItem>())
    )
    val satelliteSnapshot: StateFlow<Triple<Int, Int, List<SatelliteBarItem>>> =
        _satelliteSnapshot.asStateFlow()

    fun setRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _lastServiceLocation.value = null
            _satelliteSnapshot.value = Triple(0, 0, emptyList())
        }
    }

    fun postLocation(location: Location) {
        _lastServiceLocation.value = location
    }

    fun postSatellites(used: Int, visible: Int, bars: List<SatelliteBarItem>) {
        _satelliteSnapshot.value = Triple(used, visible, bars)
    }
}

/**
 * Foreground Service that locks and maintains continuous GPS hardware connection
 * with a partial WakeLock and real-time Thai status notification.
 */
class GpsKeepAliveService : Service() {
    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var satellitesInFix = 0
    private var satellitesVisible = 0
    private var currentLocation: Location? = null
    private var lastNotificationAt = 0L
    private var updateIntervalMillis: Long = 1000L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            GpsServiceRuntimeBus.postLocation(location)
            updateNotification(force = false)
        }

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var used = 0
            val bars = mutableListOf<SatelliteBarItem>()
            for (i in 0 until total) {
                val isUsed = status.usedInFix(i)
                if (isUsed) used++
                if (bars.size < 16) {
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
            }
            satellitesInFix = used
            satellitesVisible = total
            GpsServiceRuntimeBus.postSatellites(
                used = used,
                visible = total,
                bars = bars.sortedByDescending { it.cn0DbHz }.take(12)
            )
            updateNotification(force = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        acquireKeepAliveWakeLock()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            GpsServiceRuntimeBus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            GpsServiceRuntimeBus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        updateIntervalMillis = intent?.getLongExtra(EXTRA_INTERVAL_MS, 1000L) ?: 1000L

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            GpsServiceRuntimeBus.setRunning(true)
        } catch (_: SecurityException) {
            GpsServiceRuntimeBus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            GpsServiceRuntimeBus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            locationManager.removeUpdates(locationListener)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    updateIntervalMillis,
                    0f,
                    locationListener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    updateIntervalMillis,
                    0f,
                    locationListener
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && fineGranted) {
                locationManager.registerGnssStatusCallback(gnssCallback, null)
            }
        } catch (_: SecurityException) {
            GpsServiceRuntimeBus.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalArgumentException) {
            // Provider unavailable on device
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        GpsServiceRuntimeBus.setRunning(false)
        if (::locationManager.isInitialized) {
            runCatching { locationManager.removeUpdates(locationListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireKeepAliveWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CrimsonFixPoint::GpsKeepAliveWakeLock"
            )
            wl.setReferenceCounted(false)
            wl.acquire()
            wakeLock = wl
        } catch (_: Throwable) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < 1000L) return
        lastNotificationAt = now
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val loc = currentLocation
        val statusTitle = if (loc != null) {
            "ล็อกสัญญาณ GPS แล้ว • ความแม่นยำ ±${String.format(Locale.US, "%.1f", loc.accuracy)} ม."
        } else {
            "กำลังค้นหาและตรึงสัญญาณดาวเทียม GPS..."
        }
        val coordSummary = if (loc != null) {
            String.format(Locale.US, "พิกัด: %.6f, %.6f • ดาวเทียม %d/%d ดวง", loc.latitude, loc.longitude, satellitesInFix, satellitesVisible)
        } else {
            "ดาวเทียมที่ตรวจพบ: $satellitesInFix/$satellitesVisible ดวง • รักษาสถานะต่อเนื่อง"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GpsKeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(statusTitle)
            .setContentText(coordSummary)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$statusTitle\n$coordSummary"
                )
            )
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                "หยุดการล็อกสัญญาณ",
                pendingStop
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ระบบล็อกสัญญาณ GPS เบื้องหลัง",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "แสดงสถานะการทำงานของระบบตรึงสัญญาณพิกัดดาวเทียมต่อเนื่อง"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.service.GpsKeepAliveService.STOP"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        private const val CHANNEL_ID = "crimson_fixpoint_gps"
        private const val NOTIFICATION_ID = 4101
    }
}
