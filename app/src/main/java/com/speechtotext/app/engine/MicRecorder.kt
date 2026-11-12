package com.speechtotext.app.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.speechtotext.app.domain.Transcript
import com.speechtotext.app.domain.WordTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max

enum class MicRecordState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

class MicRecorder(
    private val modelDirectory: File,
    private val languageCode: String,
    private val languageName: String,
    private val isRtl: Boolean = false
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var engine: VoskEngine? = null

    private val _recordState = MutableStateFlow(MicRecordState.IDLE)
    val recordState: StateFlow<MicRecordState> = _recordState.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0.0)
    val durationSeconds: StateFlow<Double> = _durationSeconds.asStateFlow()

    private val _livePartialText = MutableStateFlow("")
    val livePartialText: StateFlow<String> = _livePartialText.asStateFlow()

    private val _accumulatedText = MutableStateFlow("")
    val accumulatedText: StateFlow<String> = _accumulatedText.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val wordsList = mutableListOf<WordTiming>()

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_recordState.value == MicRecordState.RECORDING) return true

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return false
            }

            engine = VoskEngine(modelDirectory, sampleRate.toFloat())
            audioRecord?.startRecording()
            _recordState.value = MicRecordState.RECORDING

            startAudioLoop(bufferSize)
            startTimer()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return false
        }
    }

    private fun startAudioLoop(bufferSize: Int) {
        recordingJob = scope.launch {
            val audioBuffer = ByteArray(bufferSize)

            while (isActive && _recordState.value != MicRecordState.STOPPED) {
                if (_recordState.value == MicRecordState.PAUSED) {
                    delay(50)
                    continue
                }

                val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                if (readBytes > 0) {
                    // Compute amplitude
                    var maxPeak = 0
                    var i = 0
                    while (i < readBytes - 1) {
                        val sample = ((audioBuffer[i + 1].toInt() shl 8) or (audioBuffer[i].toInt() and 0xFF)).toShort()
                        maxPeak = max(maxPeak, abs(sample.toInt()))
                        i += 2
                    }
                    _amplitude.value = (maxPeak / 32767.0f).coerceIn(0f, 1f)

                    // Normalize
                    AudioNormalizer.normalize(audioBuffer, readBytes)

                    val isPhraseBoundary = engine?.acceptWaveForm(audioBuffer, readBytes) ?: false
                    if (isPhraseBoundary) {
                        val chunk = engine?.getResult()
                        if (chunk != null && chunk.text.isNotBlank()) {
                            wordsList.addAll(chunk.words)
                            val current = _accumulatedText.value
                            _accumulatedText.value = if (current.isBlank()) chunk.text else "$current ${chunk.text}"
                            _livePartialText.value = ""
                        }
                    } else {
                        val partial = engine?.getPartialText() ?: ""
                        _livePartialText.value = partial
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (isActive && _recordState.value != MicRecordState.STOPPED) {
                if (_recordState.value == MicRecordState.RECORDING) {
                    _durationSeconds.value += 0.1
                }
                delay(100)
            }
        }
    }

    fun pauseRecording() {
        if (_recordState.value == MicRecordState.RECORDING) {
            _recordState.value = MicRecordState.PAUSED
        }
    }

    fun resumeRecording() {
        if (_recordState.value == MicRecordState.PAUSED) {
            _recordState.value = MicRecordState.RECORDING
        }
    }

    fun stopRecording(): Transcript {
        _recordState.value = MicRecordState.STOPPED
        recordingJob?.cancel()
        timerJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        // Flush final recognizer result
        val finalChunk = engine?.getFinalResult()
        if (finalChunk != null && finalChunk.text.isNotBlank()) {
            wordsList.addAll(finalChunk.words)
            val current = _accumulatedText.value
            _accumulatedText.value = if (current.isBlank()) finalChunk.text else "$current ${finalChunk.text}"
        }

        engine?.close()
        engine = null

        val full = _accumulatedText.value.trim()
        val totalSecs = _durationSeconds.value

        return Transcript(
            title = "Mic Recording (${languageName})",
            durationSeconds = totalSecs,
            languageCode = languageCode,
            languageName = languageName,
            fullText = full,
            words = ArrayList(wordsList),
            isRtl = isRtl
        )
    }
}
