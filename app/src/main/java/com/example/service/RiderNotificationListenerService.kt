package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.integration.DeliveryAdapterRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class RawNotificationJobSignal(
    val packageName: String,
    val title: String,
    val body: String,
    val postedAtMillis: Long
)

object JobRadarRuntimeBus {
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _incomingSignals = MutableSharedFlow<RawNotificationJobSignal>(extraBufferCapacity = 32)
    val incomingSignals: SharedFlow<RawNotificationJobSignal> = _incomingSignals.asSharedFlow()

    fun setListenerConnected(connected: Boolean) {
        _isListenerConnected.value = connected
    }

    fun emitSignal(signal: RawNotificationJobSignal) {
        _incomingSignals.tryEmit(signal)
    }
}

/**
 * Passive, read-only NotificationListenerService for delivery job notifications.
 * Strictly respects Android OS Notification Access consent and never interacts with or dismisses third-party notifications.
 */
class RiderNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        JobRadarRuntimeBus.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        JobRadarRuntimeBus.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return
        if (DeliveryAdapterRegistry.resolveForPackage(pkg) == null) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val mergedBody = listOf(text, bigText)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")

        if (title.isBlank() && mergedBody.isBlank()) return

        JobRadarRuntimeBus.emitSignal(
            RawNotificationJobSignal(
                packageName = pkg,
                title = title,
                body = mergedBody,
                postedAtMillis = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        )
    }
}
