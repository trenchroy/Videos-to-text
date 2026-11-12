package com.speechtotext.app.engine

import kotlin.math.abs
import kotlin.math.max

object AudioNormalizer {

    // Noise gate threshold: samples below ~250 amplitude are considered ambient silence
    private const val NOISE_GATE_THRESHOLD = 250

    /**
     * Optimizes 16-bit PCM audio:
     * 1. DC offset removal to eliminate mic drift
     * 2. Intelligent noise gating for silence
     * 3. Adaptive automatic gain control (AGC) with soft limiting to maximize speech clarity
     */
    fun normalize(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
        if (length < 4) return

        val numSamples = length / 2

        // Step 1: DC offset calculation and peak detection
        var dcSum = 0L
        var peak = 0
        var activeSamplesCount = 0

        var idx = 0
        while (idx < length - 1) {
            val low = pcmBytes[idx].toInt() and 0xFF
            val high = pcmBytes[idx + 1].toInt()
            val sample = (high shl 8) or low
            dcSum += sample
            val absSample = abs(sample)
            if (absSample > NOISE_GATE_THRESHOLD) {
                activeSamplesCount++
            }
            if (absSample > peak) {
                peak = absSample
            }
            idx += 2
        }

        val dcOffset = (dcSum / numSamples).toInt()

        // If completely silent or noise-only, gentle noise suppression
        if (peak <= NOISE_GATE_THRESHOLD || activeSamplesCount < (numSamples / 20)) {
            // Apply slight attenuation to background room tone
            idx = 0
            while (idx < length - 1) {
                val low = pcmBytes[idx].toInt() and 0xFF
                val high = pcmBytes[idx + 1].toInt()
                val sample = ((high shl 8) or low) - dcOffset
                val attenuated = (sample * 0.5f).toInt().coerceIn(-32768, 32767).toShort()
                pcmBytes[idx] = (attenuated.toInt() and 0xFF).toByte()
                pcmBytes[idx + 1] = ((attenuated.toInt() shr 8) and 0xFF).toByte()
                idx += 2
            }
            return
        }

        // Target peak: ~27000 (clear speech without distortion)
        val targetPeak = 27000.0f
        val gain = if (peak > 0) {
            (targetPeak / peak).coerceIn(1.0f, 6.0f)
        } else 1.0f

        // Step 2: Apply DC offset removal, gain and soft limiting
        idx = 0
        while (idx < length - 1) {
            val low = pcmBytes[idx].toInt() and 0xFF
            val high = pcmBytes[idx + 1].toInt()
            val sample = ((high shl 8) or low) - dcOffset

            val absSample = abs(sample)
            val effectiveGain = if (absSample < NOISE_GATE_THRESHOLD) {
                gain * (absSample.toFloat() / NOISE_GATE_THRESHOLD) // Smooth gate ramp down
            } else {
                gain
            }

            var amplified = sample * effectiveGain

            // Soft limiter to prevent clipping
            if (amplified > 32000f) {
                amplified = 32000f + (amplified - 32000f) * 0.2f
            } else if (amplified < -32000f) {
                amplified = -32000f + (amplified + 32000f) * 0.2f
            }

            val finalSample = amplified.toInt().coerceIn(-32768, 32767).toShort()
            pcmBytes[idx] = (finalSample.toInt() and 0xFF).toByte()
            pcmBytes[idx + 1] = ((finalSample.toInt() shr 8) and 0xFF).toByte()
            idx += 2
        }
    }
}
