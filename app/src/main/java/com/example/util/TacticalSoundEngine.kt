package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic alert controller for Crimson FixPoint:
 * - Hardware vibration patterns for out-of-zone warning, return-to-zone confirmation, and low-fuel alert.
 */
object TacticalSoundEngine {

    /**
     * Triggers hardware vibration when exiting the zone (strong double pulse).
     */
    fun vibrateOutOfZone(context: Context, vibrationEnabled: Boolean) {
        if (!vibrationEnabled) return
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0L, 340L, 120L, 340L)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 340L, 120L, 340L), -1)
            }
        }
    }

    /**
     * Triggers a single short vibration when returning inside the zone ("เมื่อกลับเข้าโซน สั่นสั้น 1 ครั้ง").
     */
    fun vibrateReturnToZoneSingleShort(context: Context, vibrationEnabled: Boolean) {
        if (!vibrationEnabled) return
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180L)
            }
        }
    }

    /**
     * Triggers vibration when estimated fuel drops to the low-fuel threshold.
     */
    fun triggerLowFuelWarning(context: Context, vibrationEnabled: Boolean) {
        if (!vibrationEnabled) return
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 220L, 100L, 220L),
                        intArrayOf(0, 220, 0, 220),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 220L, 100L, 220L), -1)
            }
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
