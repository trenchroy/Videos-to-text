package com.speechtotext.app.engine

import kotlin.math.abs
import kotlin.math.max

object AudioNormalizer {

    /**
     * Normalizes a 16-bit PCM byte array in place to maximize clarity without clipping.
     * Audio format: 16-bit little-endian PCM.
     */
    fun normalize(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
        if (length < 2) return

        // 1. Find peak amplitude
        var peak = 0
        var i = 0
        while (i < length - 1) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = (high shl 8) or low
            peak = max(peak, abs(sample))
            i += 2
        }

        // If completely silent or already near max, return
        if (peak < 100 || peak >= 30000) return

        // Target peak is ~28000 (~-1.3 dBFS)
        val targetPeak = 28000.0f
        val gain = (targetPeak / peak).coerceAtMost(8.0f) // Limit max gain to +18dB to avoid blowing noise floor

        // 2. Apply gain to each sample
        i = 0
        while (i < length - 1) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            val amplified = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()

            pcmBytes[i] = (amplified.toInt() and 0xFF).toByte()
            pcmBytes[i + 1] = ((amplified.toInt() shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}
