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
import kotlin.math.floor

object AudioDecoder {

    const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 5000L

    /**
     * Decodes any audio/video file at [uri] to 16kHz mono 16-bit PCM.
     * Streams chunks into [onChunk] callback.
     * Returns true on complete success, false if cancelled or error.
     */
    suspend fun decodeToPcmChunks(
        context: Context,
        uri: Uri,
        onChunk: suspend (chunk: ByteArray, length: Int, progressPercent: Int) -> Boolean
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

            val chunkAccumulator = ByteArray(4096)
            var accumulatedBytes = 0

            while (!isOutputEos && coroutineContext.isActive) {
                // 1. Feed input buffers
                if (!isInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
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

                // 2. Fetch output buffers
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            // Read decoded raw PCM
                            val rawBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(rawBytes)

                            // Convert to 16 kHz Mono 16-bit PCM
                            val resampledPcm = processPcmData(
                                rawBytes,
                                inputSampleRate,
                                channelCount,
                                TARGET_SAMPLE_RATE
                            )

                            // Normalize volume
                            AudioNormalizer.normalize(resampledPcm)

                            // Calculate progress percent
                            val progress = if (durationUs > 0) {
                                ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * 100).toInt().coerceIn(0, 99)
                            } else {
                                0
                            }

                            // Stream in ~4096 byte chunks
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
                }
            }

            // Flush remaining chunk
            if (accumulatedBytes > 0) {
                onChunk(chunkAccumulator, accumulatedBytes, 100)
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

        // 2. Resample if necessary
        val resampledShorts = if (srcSampleRate != targetSampleRate && srcSampleRate > 0) {
            resampleLinear(monoShorts, srcSampleRate, targetSampleRate)
        } else {
            monoShorts
        }

        // Convert back to ByteArray
        val outBytes = ByteArray(resampledShorts.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledShorts)
        return outBytes
    }

    /**
     * Linear interpolation resampling for clean offline audio conversion.
     */
    private fun resampleLinear(input: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outLength = (input.size / ratio).toInt().coerceAtLeast(1)
        val output = ShortArray(outLength)

        for (i in 0 until outLength) {
            val srcIndex = i * ratio
            val index0 = floor(srcIndex).toInt().coerceIn(0, input.size - 1)
            val index1 = (index0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcIndex - index0).toFloat()

            val s0 = input[index0].toFloat()
            val s1 = input[index1].toFloat()
            output[i] = (s0 + frac * (s1 - s0)).toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }
}
