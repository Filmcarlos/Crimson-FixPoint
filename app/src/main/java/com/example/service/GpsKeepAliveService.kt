package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max

/** Foreground GPS monitoring for an explicitly enabled rider FixPoint lock. */
class GpsKeepAliveService : Service() {
    private lateinit var locationManager: LocationManager
    private var satellitesInFix = 0
    private var accuracyMeters = 0f
    private var lastNotificationAt = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            accuracyMeters = location.accuracy
            updateNotification()
        }
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellitesInFix = (0 until status.satelliteCount).count { status.usedInFix(it) }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssStatusCallback(gnssCallback, null)
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalArgumentException) {
            // GPS provider may be unavailable on devices without GNSS hardware.
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::locationManager.isInitialized) {
            runCatching { locationManager.removeUpdates(locationListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
        super.onDestroy()
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationAt < 1000L) return
        lastNotificationAt = now
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val accuracyText = if (accuracyMeters > 0f) "±${accuracyMeters.toInt()} m" else "กำลังค้นหาพิกัด"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Crimson FixPoint • GPS Keep-Alive")
            .setContentText("Accuracy: $accuracyText • Satellites in fix: $satellitesInFix")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Foreground GPS monitoring is active.\n" +
                    "Accuracy: $accuracyText\n" +
                    "Satellites used in fix: $satellitesInFix\n" +
                    "Coordinate smoothing reduces marker jitter but cannot improve GPS accuracy."
            ))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Crimson GPS keep-alive", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.service.GpsKeepAliveService.STOP"
        private const val CHANNEL_ID = "crimson_fixpoint_gps"
        private const val NOTIFICATION_ID = 4101
    }
}
