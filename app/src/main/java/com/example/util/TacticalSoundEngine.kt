package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.ZoneAlertTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Audio & Haptic alert synthesizer for Crimson FixPoint:
 * - Supports 3 selectable Out-of-Zone Alert Tones with configurable volume (0-100%)
 * - Supports hardware vibration patterns (Out-of-zone warning pulse & Return-to-zone single short pulse)
 */
object TacticalSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playLockChime(isMuted: Boolean, volumePercent: Int = 80) {
        if (isMuted || volumePercent <= 0) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 587.33, durationMs = 75, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 880.0, durationMs = 85, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1174.66, durationMs = 170, waveform = Waveform.SINE)
                ),
                volumeScale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
            )
        }
    }

    fun playSRankMatchBeep(isMuted: Boolean, volumePercent: Int = 80) {
        if (isMuted || volumePercent <= 0) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 1046.50, durationMs = 95, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 0.0, durationMs = 45, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1318.51, durationMs = 140, waveform = Waveform.SINE)
                ),
                volumeScale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
            )
        }
    }

    fun playReturnToZoneChime(soundEnabled: Boolean, volumePercent: Int) {
        if (!soundEnabled || volumePercent <= 0) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 783.99, durationMs = 90, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1046.50, durationMs = 150, waveform = Waveform.SINE)
                ),
                volumeScale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
            )
        }
    }

    fun playZoneAlertTone(
        soundEnabled: Boolean,
        tone: ZoneAlertTone,
        volumePercent: Int
    ) {
        if (!soundEnabled || volumePercent <= 0) return
        val scale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
        scope.launch {
            val sequence = when (tone) {
                ZoneAlertTone.SIREN_DUAL -> listOf(
                    ToneSegment(freqHz = 880.0, durationMs = 180, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 622.25, durationMs = 180, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 880.0, durationMs = 180, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 622.25, durationMs = 200, waveform = Waveform.SQUARE)
                )
                ZoneAlertTone.HIGH_PULSE -> listOf(
                    ToneSegment(freqHz = 1396.91, durationMs = 120, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 0.0, durationMs = 55, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1396.91, durationMs = 120, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 0.0, durationMs = 55, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1760.0, durationMs = 170, waveform = Waveform.SINE)
                )
                ZoneAlertTone.SONAR_DEEP -> listOf(
                    ToneSegment(freqHz = 330.0, durationMs = 160, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 0.0, durationMs = 50, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 220.0, durationMs = 260, waveform = Waveform.SQUARE)
                )
            }
            playToneSequence(sequence, scale)
        }
    }

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
     * Triggers vibration and warning chime when estimated fuel drops to the low-fuel threshold.
     */
    fun triggerLowFuelWarning(context: Context, vibrationEnabled: Boolean, soundEnabled: Boolean, volumePercent: Int) {
        if (vibrationEnabled) {
            runCatching {
                val vibrator = getVibrator(context) ?: return@runCatching
                if (vibrator.hasVibrator()) {
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
        }
        if (soundEnabled && volumePercent > 0) {
            scope.launch {
                playToneSequence(
                    tones = listOf(
                        ToneSegment(freqHz = 740.0, durationMs = 130, waveform = Waveform.SQUARE),
                        ToneSegment(freqHz = 0.0, durationMs = 60, waveform = Waveform.SQUARE),
                        ToneSegment(freqHz = 554.37, durationMs = 190, waveform = Waveform.SQUARE)
                    ),
                    volumeScale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
                )
            }
        }
    }

    /**
     * Triggers vibration and alert tone when rain is approaching.
     */
    fun triggerRainApproachingWarning(context: Context, vibrationEnabled: Boolean, soundEnabled: Boolean, volumePercent: Int) {
        if (vibrationEnabled) {
            runCatching {
                val vibrator = getVibrator(context) ?: return@runCatching
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 280L, 120L, 280L, 120L, 380L),
                                intArrayOf(0, 200, 0, 220, 0, 255),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0L, 280L, 120L, 280L, 120L, 380L), -1)
                    }
                }
            }
        }
        if (soundEnabled && volumePercent > 0) {
            scope.launch {
                playToneSequence(
                    tones = listOf(
                        ToneSegment(freqHz = 659.25, durationMs = 120, waveform = Waveform.SINE),
                        ToneSegment(freqHz = 880.0, durationMs = 120, waveform = Waveform.SINE),
                        ToneSegment(freqHz = 1046.50, durationMs = 200, waveform = Waveform.SINE)
                    ),
                    volumeScale = (volumePercent / 100.0).coerceIn(0.0, 1.0)
                )
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

    private enum class Waveform { SINE, SQUARE }

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val waveform: Waveform
    )

    private fun playToneSequence(tones: List<ToneSegment>, volumeScale: Double) {
        try {
            val sampleRate = 24000
            val totalDurationMs = tones.sumOf { it.durationMs }
            val totalSamples = (sampleRate * totalDurationMs) / 1000
            if (totalSamples <= 0) return

            val pcmBuffer = ShortArray(totalSamples)
            var offset = 0

            for (segment in tones) {
                val segSamples = (sampleRate * segment.durationMs) / 1000
                for (i in 0 until segSamples) {
                    if (offset + i >= totalSamples) break
                    if (segment.freqHz <= 1.0) {
                        pcmBuffer[offset + i] = 0
                    } else {
                        val env = when {
                            i < segSamples * 0.1 -> i / (segSamples * 0.1)
                            i > segSamples * 0.8 -> (segSamples - i) / (segSamples * 0.2)
                            else -> 1.0
                        }.coerceIn(0.0, 1.0)

                        val angle = 2.0 * PI * i * segment.freqHz / sampleRate
                        val rawWave = when (segment.waveform) {
                            Waveform.SINE -> sin(angle)
                            Waveform.SQUARE -> if (sin(angle) >= 0.0) 0.78 else -0.78
                        }
                        pcmBuffer[offset + i] =
                            (rawWave * env * Short.MAX_VALUE * 0.65 * volumeScale).toInt().toShort()
                    }
                }
                offset += segSamples
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            audioTrack.play()
            Thread.sleep(totalDurationMs.toLong() + 60L)
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Throwable) {
            // Audio device unavailable in headless/CI tests; ignore safely.
        }
    }
}
