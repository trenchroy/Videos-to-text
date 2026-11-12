package com.speechtotext.app.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodeProgress(
    val percent: Int,
    val speedMultiplier: Float,
    val remainingSeconds: Long,
    val processedSeconds: Double
)

object AudioDecoder {

    const val TARGET_SAMPLE_RATE = 16000
    private const val BUFFER_TIMEOUT_US = 1000L
    private const val CHUNK_SIZE = 16384 // 16 KB chunks for high throughput and fast Kaldi processing

    /**
     * Decodes any audio/video file at [uri] to 16kHz mono 16-bit PCM at maximum hardware speed.
     * Streams chunks into [onChunk] callback with detailed progress and speed metrics.
     * Returns true on complete success, false if cancelled or error.
     */
    suspend fun decodeToPcmChunks(
        context: Context,
        uri: Uri,
        onChunk: suspend (chunk: ByteArray, length: Int, progress: DecodeProgress) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                return@withContext false
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                1L
            }
            val totalDurationSec = (durationUs / 1_000_000.0).coerceAtLeast(0.1)

            var inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false

            val chunkAccumulator = ByteArray(CHUNK_SIZE)
            var accumulatedBytes = 0

            val startTimeMs = System.currentTimeMillis()
            var lastProgressReportTime = 0L
            var currentProcessedSec = 0.0

            while (!isOutputEos && coroutineContext.isActive) {
                // 1. Feed input buffers
                if (!isInputEos) {
                    val inIndex = codec.dequeueInputBuffer(BUFFER_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEos = true
                            } else {
                                val presentationTime = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain all ready output buffers quickly
                var outputDrained = false
                while (!outputDrained && !isOutputEos && coroutineContext.isActive) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                    when {
                        outIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val rawBytes = ByteArray(bufferInfo.size)
                                outputBuffer.get(rawBytes)

                                // Convert to 16 kHz Mono 16-bit PCM
                                val resampledPcm = processPcmData(
                                    rawBytes,
                                    inputSampleRate,
                                    channelCount,
                                    TARGET_SAMPLE_RATE
                                )

                                // Adaptive Normalization
                                AudioNormalizer.normalize(resampledPcm)

                                currentProcessedSec = (bufferInfo.presentationTimeUs / 1_000_000.0).coerceIn(0.0, totalDurationSec)

                                // Speed and ETA calculation
                                val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                                val speed = (currentProcessedSec / elapsedSec).toFloat().coerceAtLeast(0.5f)
                                val remainingAudioSec = (totalDurationSec - currentProcessedSec).coerceAtLeast(0.0)
                                val remainingWallSec = (remainingAudioSec / speed).toLong()

                                val percent = ((currentProcessedSec / totalDurationSec) * 100).toInt().coerceIn(0, 99)

                                val progress = DecodeProgress(
                                    percent = percent,
                                    speedMultiplier = speed,
                                    remainingSeconds = remainingWallSec,
                                    processedSeconds = currentProcessedSec
                                )

                                // Stream in chunks of CHUNK_SIZE
                                var offset = 0
                                while (offset < resampledPcm.size) {
                                    val toCopy = (chunkAccumulator.size - accumulatedBytes).coerceAtMost(resampledPcm.size - offset)
                                    System.arraycopy(resampledPcm, offset, chunkAccumulator, accumulatedBytes, toCopy)
                                    accumulatedBytes += toCopy
                                    offset += toCopy

                                    if (accumulatedBytes >= chunkAccumulator.size) {
                                        val shouldContinue = onChunk(chunkAccumulator, accumulatedBytes, progress)
                                        if (!shouldContinue) {
                                            return@withContext false
                                        }
                                        accumulatedBytes = 0
                                    }
                                }
                            }

                            codec.releaseOutputBuffer(outIndex, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isOutputEos = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                inputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        else -> {
                            outputDrained = true
                        }
                    }
                }
            }

            // Flush remaining chunk
            if (accumulatedBytes > 0) {
                val finalProgress = DecodeProgress(
                    percent = 100,
                    speedMultiplier = 1.0f,
                    remainingSeconds = 0,
                    processedSeconds = totalDurationSec
                )
                onChunk(chunkAccumulator, accumulatedBytes, finalProgress)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    /**
     * Converts multi-channel audio to mono and resamples to target sample rate (16 kHz).
     */
    private fun processPcmData(
        srcBytes: ByteArray,
        srcSampleRate: Int,
        channels: Int,
        targetSampleRate: Int
    ): ByteArray {
        val srcShorts = ShortArray(srcBytes.size / 2)
        ByteBuffer.wrap(srcBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        // 1. Downmix to Mono
        val monoShorts: ShortArray = if (channels > 1) {
            val numFrames = srcShorts.size / channels
            val mono = ShortArray(numFrames)
            for (f in 0 until numFrames) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += srcShorts[f * channels + c]
                }
                mono[f] = (sum / channels).coerceIn(-32768, 32767).toShort()
            }
            mono
        } else {
            srcShorts
        }

        // 2. High-speed Resampling
        val resampledShorts = if (srcSampleRate != targetSampleRate && srcSampleRate > 0) {
            resampleLinearFast(monoShorts, srcSampleRate, targetSampleRate)
        } else {
            monoShorts
        }

        val outBytes = ByteArray(resampledShorts.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledShorts)
        return outBytes
    }

    /**
     * Fast linear resampling with fixed-point arithmetic optimization.
     */
    private fun resampleLinearFast(input: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val inLen = input.size
        val outLen = ((inLen.toLong() * outRate) / inRate).toInt().coerceAtLeast(1)
        val output = ShortArray(outLen)

        // Use 16-bit fixed-point math (shift by 16) for blazing speed
        val stepFixed = ((inLen.toLong() shl 16) / outLen).toInt()
        var posFixed = 0

        for (i in 0 until outLen) {
            val idx = posFixed shr 16
            val frac = posFixed and 0xFFFF

            if (idx >= inLen - 1) {
                output[i] = input[inLen - 1]
            } else {
                val s0 = input[idx].toInt()
                val s1 = input[idx + 1].toInt()
                val interpolated = s0 + (((s1 - s0) * frac) shr 16)
                output[i] = interpolated.coerceIn(-32768, 32767).toShort()
            }
            posFixed += stepFixed
        }

        return output
    }
}
