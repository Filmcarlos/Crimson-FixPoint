package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.ScreenReaderState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared local runtime bus between [ScreenReaderCaptureService] and ViewModel.
 * Never persists raw screenshots unless the user explicitly enables it.
 */
object ScreenReaderRuntimeBus {
    private val _state = MutableStateFlow(ScreenReaderState.IDLE)
    val state: StateFlow<ScreenReaderState> = _state.asStateFlow()

    private val _statusMessageTh = MutableStateFlow<String?>(null)
    val statusMessageTh: StateFlow<String?> = _statusMessageTh.asStateFlow()

    private val _capturedBitmaps = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
    val capturedBitmaps: SharedFlow<Bitmap> = _capturedBitmaps.asSharedFlow()

    fun updateState(newState: ScreenReaderState, messageTh: String? = null) {
        _state.value = newState
        if (messageTh != null) {
            _statusMessageTh.value = messageTh
        }
    }

    fun emitCapturedBitmap(bitmap: Bitmap) {
        _capturedBitmaps.tryEmit(bitmap)
    }

    fun clearMessage() {
        _statusMessageTh.value = null
    }
}

/**
 * User-controlled MediaProjection Foreground Service for Crimson FixPoint 2.0.
 *
 * Strict Safety & Privacy Design:
 * - Only starts after explicit user consent and Android MediaProjection permission grant.
 * - Shows a visible ongoing foreground notification with Stop & Capture Now controls.
 * - Captures a single screenshot strictly on explicit user command (never silently records).
 * - Never auto-clicks, accepts, rejects, or interacts with third-party delivery apps.
 * - Processes frames locally in memory and releases VirtualDisplay immediately after capture.
 */
class ScreenReaderCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            cleanupCaptureResources()
            ScreenReaderRuntimeBus.updateState(
                ScreenReaderState.IDLE,
                "หยุดการจับภาพหน้าจอแล้ว (MediaProjection สิ้นสุดการทำงาน)"
            )
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProjectionAndSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_NOW -> {
                captureSingleFrameOnDemand()
                return START_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    ScreenReaderRuntimeBus.updateState(
                        ScreenReaderState.PERMISSION_DENIED,
                        "ไม่ได้รับสิทธิ์การจับภาพหน้าจอจากระบบ"
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    val notification = buildOngoingNotification()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }

                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    val proj = mgr?.getMediaProjection(resultCode, resultData)
                    if (proj == null) {
                        ScreenReaderRuntimeBus.updateState(
                            ScreenReaderState.ERROR,
                            "ไม่สามารถเริ่ม MediaProjection ได้บนอุปกรณ์นี้"
                        )
                        stopSelf()
                        return START_NOT_STICKY
                    }

                    mediaProjection?.unregisterCallback(projectionCallback)
                    mediaProjection?.stop()
                    mediaProjection = proj
                    proj.registerCallback(projectionCallback, mainHandler)

                    ScreenReaderRuntimeBus.updateState(
                        ScreenReaderState.ACTIVE_READY,
                        "เปิด Screen Reader พร้อมใช้งานแล้ว — กด 'จับภาพตอนนี้' จากแถบแจ้งเตือนหรือในแอปเพื่ออ่านออเดอร์"
                    )
                } catch (e: Throwable) {
                    ScreenReaderRuntimeBus.updateState(
                        ScreenReaderState.ERROR,
                        "เริ่ม Screen Reader ไม่สำเร็จ (${e.localizedMessage ?: "ไม่รองรับ"})"
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun captureSingleFrameOnDemand() {
        val proj = mediaProjection
        if (proj == null) {
            ScreenReaderRuntimeBus.updateState(
                ScreenReaderState.IDLE,
                "กรุณาเปิดสิทธิ์ Screen Reader ก่อนกดจับภาพ"
            )
            return
        }

        ScreenReaderRuntimeBus.updateState(ScreenReaderState.CAPTURING)
        cleanupCaptureResources()

        try {
            val metrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)
            val width = metrics.widthPixels.coerceIn(360, 1440)
            val height = metrics.heightPixels.coerceIn(640, 3200)
            val densityDpi = metrics.densityDpi.coerceAtLeast(160)

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            var handled = false
            reader.setOnImageAvailableListener({ r ->
                if (handled) return@setOnImageAvailableListener
                val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                handled = true
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val fullBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    fullBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = if (fullBitmap.width != width) {
                        Bitmap.createBitmap(fullBitmap, 0, 0, width, height).also { fullBitmap.recycle() }
                    } else {
                        fullBitmap
                    }
                    ScreenReaderRuntimeBus.emitCapturedBitmap(cropped)
                    ScreenReaderRuntimeBus.updateState(ScreenReaderState.ACTIVE_READY)
                } catch (e: Throwable) {
                    ScreenReaderRuntimeBus.updateState(
                        ScreenReaderState.ERROR,
                        "จับภาพหน้าจอไม่สำเร็จ (${e.localizedMessage ?: "ข้อผิดพลาดหน่วยความจำ"})"
                    )
                } finally {
                    image.close()
                    cleanupCaptureResources()
                }
            }, mainHandler)

            virtualDisplay = proj.createVirtualDisplay(
                "CrimsonFixPointOrderReader",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            // Safety timeout if a protected or idle screen delivers no frame
            mainHandler.postDelayed({
                if (!handled) {
                    handled = true
                    cleanupCaptureResources()
                    ScreenReaderRuntimeBus.updateState(
                        ScreenReaderState.PROTECTED_SCREEN,
                        "ไม่ได้รับภาพจากหน้าจอ (อาจเป็นหน้าจอที่ถูกป้องกัน) — ลองนำเข้าภาพหน้าจอแทน"
                    )
                }
            }, 2500L)
        } catch (e: Throwable) {
            cleanupCaptureResources()
            ScreenReaderRuntimeBus.updateState(
                ScreenReaderState.ERROR,
                "ไม่สามารถสร้าง VirtualDisplay ได้ (${e.localizedMessage ?: "สิทธิ์หมดอายุ"})"
            )
        }
    }

    private fun cleanupCaptureResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
    }

    private fun stopProjectionAndSelf() {
        cleanupCaptureResources()
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        ScreenReaderRuntimeBus.updateState(
            ScreenReaderState.IDLE,
            "ปิดการทำงาน Screen Reader เรียบร้อยแล้ว"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cleanupCaptureResources()
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        ScreenReaderRuntimeBus.updateState(ScreenReaderState.IDLE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildOngoingNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            510,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val captureIntent = Intent(this, ScreenReaderCaptureService::class.java).apply {
            action = ACTION_CAPTURE_NOW
        }
        val pendingCapture = PendingIntent.getService(
            this,
            511,
            captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenReaderCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            512,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Crimson FixPoint Screen Reader เปิดอยู่")
            .setContentText("ประมวลผลเฉพาะในเครื่องเมื่อคุณกดจับภาพ • ไม่มีบันทึกอัตโนมัติ")
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_search, "จับภาพและอ่านออเดอร์", pendingCapture)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "หยุด", pendingStop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Reader (MediaProjection)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "แสดงสถานะเมื่อผู้ใช้เปิดโหมดอ่านหน้าจอออเดอร์แบบควบคุมเอง"
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.service.ScreenReaderCaptureService.START"
        const val ACTION_CAPTURE_NOW = "com.example.service.ScreenReaderCaptureService.CAPTURE_NOW"
        const val ACTION_STOP = "com.example.service.ScreenReaderCaptureService.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "crimson_fixpoint_screen_reader"
        private const val NOTIFICATION_ID = 4201
    }
}
