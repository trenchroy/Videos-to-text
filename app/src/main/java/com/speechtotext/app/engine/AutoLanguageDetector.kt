package com.speechtotext.app.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

sealed class DetectionResult {
    data class Success(
        val topLanguage: Language,
        val confidencePercent: Int,
        val isLowConfidence: Boolean,
        val secondChoice: Language? = null,
        val secondChoicePercent: Int = 0,
        val recognizedSample: String = ""
    ) : DetectionResult()

    data class Error(val message: String) : DetectionResult()
    data class NotEnoughLanguages(val message: String = "Auto detect ke liye kam se kam 2 bhasha download karo") : DetectionResult()
}

object AutoLanguageDetector {

    private const val TAG = "AutoLanguageDetector"

    // 18 seconds of 16kHz 16-bit mono PCM = 18 * 16000 * 2 bytes = 576,000 bytes
    private const val TARGET_SAMPLE_BYTES = 18 * 16000 * 2
    // Minimum 2 seconds of speech required to attempt recognition
    private const val MIN_SPEECH_BYTES = 2 * 16000 * 2
    // Voice Activity Detection amplitude threshold (samples above this are considered active voice)
    private const val VAD_AMPLITUDE_THRESHOLD = 400

    suspend fun detectLanguage(
        context: Context,
        mediaUri: Uri,
        downloadedLanguages: List<Language>,
        modelManager: ModelManager,
        recentlyUsedCodes: List<String> = emptyList(),
        onStatusUpdate: ((String) -> Unit)? = null
    ): DetectionResult = withContext(Dispatchers.IO) {
        if (downloadedLanguages.size < 2) {
            return@withContext DetectionResult.NotEnoughLanguages("Auto detect ke liye kam se kam 2 bhasha download karo")
        }

        onStatusUpdate?.invoke("Audio sample extract ho raha hai...")

        // Step 1: Extract 15-20 seconds of active speech PCM (skipping silence with VAD and filters)
        val speechPcmStream = ByteArrayOutputStream()
        var scannedSeconds = 0.0

        val decodeSuccess = AudioDecoder.decodeToPcmChunks(
            context = context,
            uri = mediaUri
        ) { chunk, length, progress ->
            scannedSeconds = progress.processedSeconds

            // Apply audio cleanup filter (DC offset removal & noise gating)
            AudioNormalizer.normalize(chunk, length)

            // VAD filter: check if chunk has voice activity
            var idx = 0
            var speechInChunk = 0
            while (idx < length - 1) {
                val low = chunk[idx].toInt() and 0xFF
                val high = chunk[idx + 1].toInt()
                val sample = (high shl 8) or low
                if (abs(sample) > VAD_AMPLITUDE_THRESHOLD) {
                    speechInChunk++
                }
                idx += 2
            }

            // Only append audio if it contains audible speech/voice
            if (speechInChunk > (length / 8)) {
                speechPcmStream.write(chunk, 0, length)
            }

            // Stop once we have reached 18s of active speech or scanned 3 minutes of media
            if (speechPcmStream.size() >= TARGET_SAMPLE_BYTES || scannedSeconds > 180.0) {
                false // stop extraction
            } else {
                true
            }
        }

        val speechPcm = speechPcmStream.toByteArray()
        if (speechPcm.size < MIN_SPEECH_BYTES) {
            return@withContext DetectionResult.Error("Audio sample me koi aawaz ya bol nahi mila (No speech found in sample).")
        }

        // Step 2: Select up to 4 models to test, prioritizing recently used languages first
        val sortedLanguages = downloadedLanguages.sortedBy { lang ->
            val recentIdx = recentlyUsedCodes.indexOf(lang.code)
            if (recentIdx >= 0) recentIdx else 99
        }
        val candidateLanguages = sortedLanguages.take(4)

        onStatusUpdate?.invoke("${candidateLanguages.size} bhasha models me speech check ho rahi hai...")

        val modelScores = mutableListOf<Triple<Language, Double, String>>()

        val languageIdentifier = try {
            LanguageIdentification.getClient()
        } catch (e: Exception) {
            null
        }

        // Step 3: Run sample through each Vosk model and score
        for (language in candidateLanguages) {
            val modelDir = modelManager.getModelDirectory(language.modelName) ?: continue
            onStatusUpdate?.invoke("${language.nativeName} test ho raha hai...")

            var engine: VoskEngine? = null
            try {
                engine = VoskEngine(modelDir, 16000f)
                val recognizedWords = mutableListOf<WordTiming>()
                val recognizedTextParts = mutableListOf<String>()

                // Feed sample in 16KB chunks
                val chunkSize = 16384
                var offset = 0
                while (offset < speechPcm.size) {
                    val len = (speechPcm.size - offset).coerceAtMost(chunkSize)
                    val isBoundary = engine.acceptWaveForm(speechPcm, len)
                    if (isBoundary) {
                        val res = engine.getResult()
                        if (res.text.isNotBlank()) {
                            recognizedWords.addAll(res.words)
                            recognizedTextParts.add(res.text)
                        }
                    }
                    offset += len
                }

                val finalRes = engine.getFinalResult()
                if (finalRes.text.isNotBlank()) {
                    recognizedWords.addAll(finalRes.words)
                    recognizedTextParts.add(finalRes.text)
                }

                val fullText = recognizedTextParts.joinToString(" ").trim()
                val wordCount = recognizedWords.size
                val avgConfidence = if (wordCount > 0) {
                    recognizedWords.map { w: WordTiming -> w.confidence }.average()
                } else 0.0

                var mlKitConfirmationBonus = 0.0
                if (fullText.isNotBlank() && languageIdentifier != null) {
                    try {
                        val mlKitTarget = TranslatorEngine.mapVoskToMlKitTag(language.code)
                        val identified = Tasks.await(languageIdentifier.identifyPossibleLanguages(fullText))
                        for (idLang in identified) {
                            if (idLang.languageTag.equals(mlKitTarget, ignoreCase = true) ||
                                (mlKitTarget == "zh" && idLang.languageTag.startsWith("zh")) ||
                                (mlKitTarget == "hi" && idLang.languageTag.startsWith("hi")) ||
                                (mlKitTarget == "en" && idLang.languageTag.startsWith("en"))
                            ) {
                                mlKitConfirmationBonus += idLang.confidence * 40.0
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "ML Kit language-id check failed", e)
                    }
                }

                // Weighted combination score
                val totalScore = (wordCount * 8.0) + (avgConfidence * 50.0) + mlKitConfirmationBonus
                modelScores.add(Triple(language, totalScore, fullText))

            } catch (e: Exception) {
                Log.e(TAG, "Failed testing model for ${language.code}", e)
            } finally {
                try { engine?.close() } catch (ignored: Exception) {}
            }
        }

        try { languageIdentifier?.close() } catch (ignored: Exception) {}

        if (modelScores.isEmpty() || modelScores.all { it.second <= 0.0 }) {
            return@withContext DetectionResult.Error("Bhasha detect nahi ho payi (Detection failed). Kripya bhasha khud select karein.")
        }

        // Rank by score descending
        val sortedScores = modelScores.sortedByDescending { it.second }
        val topMatch = sortedScores.first()
        val secondMatch = sortedScores.getOrNull(1)

        val totalSum = sortedScores.sumOf { it.second }.coerceAtLeast(1.0)
        val rawConfidence = ((topMatch.second / totalSum) * 100).toInt().coerceIn(40, 96)

        // If top confidence is below 60% or close to second match, mark as low confidence
        val isLowConfidence = rawConfidence < 60 || (secondMatch != null && (topMatch.second - secondMatch.second) < (topMatch.second * 0.25))

        val secondPercent = if (secondMatch != null) {
            ((secondMatch.second / totalSum) * 100).toInt().coerceIn(15, 50)
        } else 0

        DetectionResult.Success(
            topLanguage = topMatch.first,
            confidencePercent = rawConfidence,
            isLowConfidence = isLowConfidence,
            secondChoice = secondMatch?.first,
            secondChoicePercent = secondPercent,
            recognizedSample = topMatch.third
        )
    }
}
