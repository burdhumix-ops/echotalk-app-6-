package co.znxdnd.echotalk.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * AudioSignalProcessor: Digital Signal Processing (DSP) utilities for computing RMS energy,
 * soft noise-gating, and logarithmic dB-scaled waveform visualization bars from 16-bit PCM audio.
 */
object AudioSignalProcessor {

    private const val MIN_DB_FLOOR = -52.0
    private const val NOISE_GATE_RMS_THRESHOLD = 180.0

    /**
     * Computes Root Mean Square (RMS) loudness in decibels relative to full scale (dBFS).
     */
    fun calculateRmsDb(samples: ShortArray): Double {
        if (samples.isEmpty()) return -96.0
        var sumSq = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / samples.size)
        if (rms <= 1.0) return -96.0
        return 20.0 * log10(rms / Short.MAX_VALUE.toDouble())
    }

    /**
     * Compresses raw PCM audio samples into [barCount] perceptually normalized bar heights (0f..1f)
     * with a soft noise gate so ambient silence stays visually quiet.
     */
    fun extractNormalizedWaveform(samples: ShortArray, barCount: Int = 24): List<Float> {
        if (barCount <= 0) return emptyList()
        if (samples.isEmpty()) return List(barCount) { 0.08f }

        val chunkSize = (samples.size / barCount).coerceAtLeast(1)
        val result = ArrayList<Float>(barCount)

        for (bar in 0 until barCount) {
            val start = (bar * chunkSize).coerceAtMost(samples.lastIndex)
            val end = if (bar == barCount - 1) samples.size else ((bar + 1) * chunkSize).coerceAtMost(samples.size)
            var sumSq = 0.0
            var count = 0
            for (i in start until end) {
                val sample = abs(samples[i].toDouble())
                sumSq += sample * sample
                count++
            }
            val rms = if (count > 0) sqrt(sumSq / count) else 0.0
            val level = if (rms < NOISE_GATE_RMS_THRESHOLD) {
                0.08f
            } else {
                val db = 20.0 * log10(rms / Short.MAX_VALUE.toDouble())
                val normalized = ((db - MIN_DB_FLOOR) / -MIN_DB_FLOOR).toFloat()
                normalized.coerceIn(0.10f, 1.0f)
            }
            result.add(level)
        }
        return result
    }
}
