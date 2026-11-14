package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Multi-tone synthesizer for tactical rider audio alerts:
 * 1. Tactical Lock Chime (Ascending crystal lock sequence)
 * 2. S-Rank Short-Loop Match (High-Priority Double Beep)
 * 3. Out-of-Zone Warning Buzz (Low-Pitch Warning Buzz)
 */
object TacticalSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playLockChime(isMuted: Boolean) {
        if (isMuted) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 587.33, durationMs = 75, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 880.0, durationMs = 85, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1174.66, durationMs = 170, waveform = Waveform.SINE)
                )
            )
        }
    }

    fun playSRankMatchBeep(isMuted: Boolean) {
        if (isMuted) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 1046.50, durationMs = 95, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 0.0, durationMs = 45, waveform = Waveform.SINE),
                    ToneSegment(freqHz = 1318.51, durationMs = 140, waveform = Waveform.SINE)
                )
            )
        }
    }

    fun playOutOfZoneWarningBuzz(isMuted: Boolean) {
        if (isMuted) return
        scope.launch {
            playToneSequence(
                tones = listOf(
                    ToneSegment(freqHz = 215.0, durationMs = 130, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 0.0, durationMs = 40, waveform = Waveform.SQUARE),
                    ToneSegment(freqHz = 160.0, durationMs = 190, waveform = Waveform.SQUARE)
                )
            )
        }
    }

    private enum class Waveform { SINE, SQUARE }

    private data class ToneSegment(
        val freqHz: Double,
        val durationMs: Int,
        val waveform: Waveform
    )

    private fun playToneSequence(tones: List<ToneSegment>) {
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
                            Waveform.SQUARE -> if (sin(angle) >= 0.0) 0.75 else -0.75
                        }
                        pcmBuffer[offset + i] = (rawWave * env * Short.MAX_VALUE * 0.42).toInt().toShort()
                    }
                }
                offset += segSamples
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
