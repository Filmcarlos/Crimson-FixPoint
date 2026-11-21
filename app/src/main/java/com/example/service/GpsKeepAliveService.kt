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
import com.example.model.GeoMath
import com.example.model.SatelliteBarItem
import com.example.model.SavedLocationItem
import com.example.model.ZoneMonitorStatus
import com.example.util.TacticalSoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

data class ServiceZoneConfig(
    val activePin: SavedLocationItem? = null,
    val radiusKm: Float = 5.0f,
    val gpsBufferMeters: Int = 20,
    val alertVibrationEnabled: Boolean = true,
    val isAlertMuted: Boolean = false
)

/**
 * Shared real-time bridge between [GpsKeepAliveService] and [com.example.viewmodel.FixPointViewModel]
 * so background GPS fixes, satellite status, and out-of-zone alerts stay synchronized even when the screen turns off.
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

    private val _zoneConfig = MutableStateFlow(ServiceZoneConfig())
    val zoneConfig: StateFlow<ServiceZoneConfig> = _zoneConfig.asStateFlow()

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

    fun updateZoneConfig(config: ServiceZoneConfig) {
        _zoneConfig.value = config
    }

    fun setAlertMuted(muted: Boolean) {
        _zoneConfig.update { it.copy(isAlertMuted = muted) }
    }
}

/**
 * Foreground Service that locks and maintains continuous GPS hardware connection
 * and monitors the active Work-Zone radius even when the screen is off or user switches apps.
 */
class GpsKeepAliveService : Service() {
    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var repeatAlertJob: Job? = null

    private var satellitesInFix = 0
    private var satellitesVisible = 0
    private var currentLocation: Location? = null
    private var lastNotificationAt = 0L
    private var updateIntervalMillis: Long = 1000L
    private var lastEvaluatedZoneStatus: ZoneMonitorStatus = ZoneMonitorStatus.NO_ACTIVE_PIN

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            GpsServiceRuntimeBus.postLocation(location)
            evaluateZoneInService(location)
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
        createNotificationChannels()
        acquireKeepAliveWakeLock()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRepeatAlertJob()
                GpsServiceRuntimeBus.setRunning(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MUTE_ALERT -> {
                GpsServiceRuntimeBus.setAlertMuted(true)
                stopRepeatAlertJob()
                updateNotification(force = true)
                return START_STICKY
            }
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
            stopRepeatAlertJob()
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
        stopRepeatAlertJob()
        serviceScope.cancel()
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

    private fun evaluateZoneInService(location: Location) {
        val cfg = GpsServiceRuntimeBus.zoneConfig.value
        val pin = cfg.activePin
        if (pin == null) {
            lastEvaluatedZoneStatus = ZoneMonitorStatus.NO_ACTIVE_PIN
            stopRepeatAlertJob()
            return
        }

        val distanceMeters = GeoMath.haversineMeters(
            location.latitude,
            location.longitude,
            pin.latitude,
            pin.longitude
        )
        val thresholdMeters = (cfg.radiusKm * 1000.0) + cfg.gpsBufferMeters
        val isOutside = distanceMeters > thresholdMeters
        val excessMeters = max(0.0, distanceMeters - thresholdMeters)

        if (isOutside) {
            if (lastEvaluatedZoneStatus != ZoneMonitorStatus.OUTSIDE_ZONE) {
                lastEvaluatedZoneStatus = ZoneMonitorStatus.OUTSIDE_ZONE
                GpsServiceRuntimeBus.setAlertMuted(false)
                triggerOutOfZoneAlertNow(excessMeters, distanceMeters)
                startRepeatOutOfZoneLoop()
            } else if (cfg.isAlertMuted) {
                stopRepeatAlertJob()
            }
        } else {
            val wasOutside = lastEvaluatedZoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE
            lastEvaluatedZoneStatus = ZoneMonitorStatus.INSIDE_ZONE
            stopRepeatAlertJob()
            if (wasOutside) {
                GpsServiceRuntimeBus.setAlertMuted(false)
                TacticalSoundEngine.vibrateReturnToZoneSingleShort(
                    applicationContext,
                    cfg.alertVibrationEnabled
                )
                postZoneTransitionAlertNotification(
                    title = "กลับเข้าโซนแล้ว",
                    body = "ตำแหน่งปัจจุบันกลับเข้าสู่โซน \"${pin.name}\" (ระยะห่าง ${GeoMath.formatShortZoneDistanceTh(distanceMeters)})",
                    isOutside = false
                )
            }
        }
    }

    private fun triggerOutOfZoneAlertNow(excessMeters: Double, totalDistanceMeters: Double) {
        val cfg = GpsServiceRuntimeBus.zoneConfig.value
        val pin = cfg.activePin ?: return
        if (!cfg.isAlertMuted) {
            TacticalSoundEngine.vibrateOutOfZone(applicationContext, cfg.alertVibrationEnabled)
        }
        postZoneTransitionAlertNotification(
            title = "ออกนอกโซนรับงานแล้ว",
            body = "หมุด \"${pin.name}\" • เกินรัศมี ${GeoMath.formatShortZoneDistanceTh(excessMeters)} (ระยะห่างรวม ${GeoMath.formatShortZoneDistanceTh(totalDistanceMeters)})",
            isOutside = true
        )
    }

    private fun startRepeatOutOfZoneLoop() {
        stopRepeatAlertJob()
        repeatAlertJob = serviceScope.launch {
            while (isActive) {
                delay(10_000L)
                val cfg = GpsServiceRuntimeBus.zoneConfig.value
                val loc = currentLocation
                val pin = cfg.activePin
                if (loc == null || pin == null || cfg.isAlertMuted) {
                    break
                }
                val distanceMeters = GeoMath.haversineMeters(
                    loc.latitude,
                    loc.longitude,
                    pin.latitude,
                    pin.longitude
                )
                val thresholdMeters = (cfg.radiusKm * 1000.0) + cfg.gpsBufferMeters
                if (distanceMeters <= thresholdMeters) {
                    break
                }
                TacticalSoundEngine.vibrateOutOfZone(applicationContext, cfg.alertVibrationEnabled)
            }
        }
    }

    private fun stopRepeatAlertJob() {
        repeatAlertJob?.cancel()
        repeatAlertJob = null
    }

    private fun postZoneTransitionAlertNotification(
        title: String,
        body: String,
        isOutside: Boolean
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            200,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isOutside) {
            val muteIntent = Intent(this, GpsKeepAliveService::class.java).apply {
                action = ACTION_MUTE_ALERT
            }
            val pendingMute = PendingIntent.getService(
                this,
                201,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                "หยุดเตือนชั่วคราว",
                pendingMute
            )
        }

        nm.notify(ALERT_NOTIFICATION_ID, builder.setSilent(true).build())
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
        val cfg = GpsServiceRuntimeBus.zoneConfig.value
        val pin = cfg.activePin

        val statusTitle = if (loc != null && pin != null) {
            val distM = GeoMath.haversineMeters(loc.latitude, loc.longitude, pin.latitude, pin.longitude)
            val thresholdM = (cfg.radiusKm * 1000.0) + cfg.gpsBufferMeters
            if (distM > thresholdM) {
                val excess = max(0.0, distM - thresholdM)
                "ออกนอกโซนรับงานแล้ว • เกินรัศมี ${GeoMath.formatShortZoneDistanceTh(excess)}"
            } else {
                "อยู่ในโซนรับงาน (${pin.name}) • ห่างหมุด ${GeoMath.formatShortZoneDistanceTh(distM)}"
            }
        } else if (loc != null) {
            "ล็อกสัญญาณ GPS แล้ว • ความแม่นยำ ±${String.format(Locale.US, "%.1f", loc.accuracy)} ม."
        } else {
            "กำลังค้นหาและตรึงสัญญาณดาวเทียม GPS..."
        }

        val coordSummary = if (loc != null) {
            String.format(
                Locale.US,
                "พิกัด: %.6f, %.6f • ดาวเทียม %d/%d ดวง",
                loc.latitude,
                loc.longitude,
                satellitesInFix,
                satellitesVisible
            )
        } else {
            "ดาวเทียมที่ตรวจพบ: $satellitesInFix/$satellitesVisible ดวง • ทำงานเบื้องหลังต่อเนื่อง"
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

        if (lastEvaluatedZoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE && !cfg.isAlertMuted) {
            val muteIntent = Intent(this, GpsKeepAliveService::class.java).apply {
                action = ACTION_MUTE_ALERT
            }
            val pendingMute = PendingIntent.getService(
                this,
                102,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                "หยุดเตือนชั่วคราว",
                pendingMute
            )
        }

        return builder.setSilent(true).build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ระบบล็อกสัญญาณและตรวจโซนเบื้องหลัง",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "แสดงสถานะการทำงานของระบบตรึงสัญญาณพิกัดและตรวจรัศมีโซนรับงานต่อเนื่อง"
                setSound(null, null)
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "การแจ้งเตือนออกนอกโซนรับงาน",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "แจ้งเตือนทันทีเมื่อตำแหน่งจริงออกนอกรัศมีโซนรับงานหรือกลับเข้าโซน"
                setSound(null, null)
            }
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.service.GpsKeepAliveService.STOP"
        const val ACTION_MUTE_ALERT = "com.example.service.GpsKeepAliveService.MUTE_ALERT"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        private const val CHANNEL_ID = "crimson_fixpoint_gps"
        private const val ALERT_CHANNEL_ID = "crimson_fixpoint_zone_alert"
        private const val NOTIFICATION_ID = 4101
        private const val ALERT_NOTIFICATION_ID = 4102
    }
}
