package com.speechtotext.app.ui.home

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.db.AppDatabase
import com.speechtotext.app.data.db.TranscriptEntity
import com.speechtotext.app.data.settings.SettingsStore
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.Transcript
import com.speechtotext.app.domain.WordTiming
import com.speechtotext.app.engine.AudioDecoder
import com.speechtotext.app.engine.AutoLanguageDetector
import com.speechtotext.app.engine.DetectionResult
import com.speechtotext.app.engine.ModelListRepository
import com.speechtotext.app.engine.ModelManager
import com.speechtotext.app.engine.TranscribeService
import com.speechtotext.app.engine.TargetLanguage
import com.speechtotext.app.engine.TranslatorEngine
import com.speechtotext.app.engine.VoskEngine
import com.speechtotext.app.util.CaptionGrouper
import com.speechtotext.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedMediaInfo(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val mimeType: String,
    val isVideo: Boolean
)

data class DetectionUiState(
    val isDetecting: Boolean = false,
    val detectedLanguage: Language? = null,
    val confidencePercent: Int = 0,
    val isLowConfidence: Boolean = false,
    val secondChoice: Language? = null,
    val secondChoicePercent: Int = 0,
    val statusText: String = "",
    val errorMessage: String? = null,
    val notEnoughLanguages: Boolean = false,
    val isConfirmed: Boolean = false
)

data class HomeUiState(
    val selectedMedia: SelectedMediaInfo? = null,
    val isAutoDetect: Boolean = true,
    val selectedOriginalLanguage: Language? = null,
    val selectedTargetLanguage: TargetLanguage = TranslatorEngine.supportedTargetLanguages.first(),
    val downloadedLanguages: List<Language> = emptyList(),
    val availableTargetLanguages: List<TargetLanguage> = TranslatorEngine.supportedTargetLanguages,
    val detection: DetectionUiState = DetectionUiState(),
    val isSameLanguageWarning: Boolean = false,
    val pivotNotice: String? = null,
    val isTranscribing: Boolean = false,
    val progressPercent: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val remainingSeconds: Long = 0L,
    val processedSeconds: Double = 0.0,
    val statusMessage: String = "",
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val modelManager = ModelManager(application)
    private val repository = ModelListRepository(application)
    private val database = AppDatabase.getInstance(application)
    val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Long>()
    val navigationEvent: SharedFlow<Long> = _navigationEvent.asSharedFlow()

    private var transcribeJob: Job? = null
    private var detectJob: Job? = null
    private var allLanguagesList: List<Language> = emptyList()

    init {
        loadLanguagesAndSettings()
    }

    private fun loadLanguagesAndSettings() {
        viewModelScope.launch {
            try {
                val languages = repository.getAvailableLanguages()
                allLanguagesList = languages
                modelManager.refreshStatuses()
                val downloaded = allLanguagesList.filter { modelManager.isModelReady(it.modelName) }

                val lastOrig = settingsStore.lastOriginalLanguage.first()
                val lastTarget = settingsStore.lastTargetLanguage.first()

                val isAuto = lastOrig.equals("auto", ignoreCase = true)
                val origLang = if (!isAuto) {
                    downloaded.find { it.code.equals(lastOrig, ignoreCase = true) }
                        ?: allLanguagesList.find { it.code.equals(lastOrig, ignoreCase = true) }
                } else null

                val targetLang = TranslatorEngine.supportedTargetLanguages.find {
                    it.code.equals(lastTarget, ignoreCase = true)
                } ?: TranslatorEngine.supportedTargetLanguages.first()

                _uiState.value = _uiState.value.copy(
                    downloadedLanguages = downloaded,
                    isAutoDetect = isAuto,
                    selectedOriginalLanguage = origLang,
                    selectedTargetLanguage = targetLang
                )
                checkSameLanguageRule()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshDownloadedLanguages() {
        modelManager.refreshStatuses()
        val downloaded = allLanguagesList.filter { modelManager.isModelReady(it.modelName) }
        _uiState.value = _uiState.value.copy(downloadedLanguages = downloaded)
        checkSameLanguageRule()
    }

    fun selectOriginalAuto() {
        viewModelScope.launch {
            settingsStore.setLastOriginalLanguage("auto")
        }
        _uiState.value = _uiState.value.copy(
            isAutoDetect = true,
            selectedOriginalLanguage = null,
            detection = DetectionUiState(),
            errorMessage = null
        )
        checkSameLanguageRule()
    }

    fun selectOriginalLanguage(language: Language) {
        viewModelScope.launch {
            settingsStore.setLastOriginalLanguage(language.code)
            settingsStore.recordLanguageUsage(language.code)
        }
        _uiState.value = _uiState.value.copy(
            isAutoDetect = false,
            selectedOriginalLanguage = language,
            detection = DetectionUiState(),
            errorMessage = null
        )
        checkSameLanguageRule()
    }

    fun selectTargetLanguage(targetLanguage: TargetLanguage) {
        viewModelScope.launch {
            settingsStore.setLastTargetLanguage(targetLanguage.code)
        }
        _uiState.value = _uiState.value.copy(
            selectedTargetLanguage = targetLanguage,
            errorMessage = null
        )
        checkSameLanguageRule()
    }

    fun swapLanguages() {
        // Disabled when original is Auto
        if (_uiState.value.isAutoDetect) return

        val currentOrig = _uiState.value.selectedOriginalLanguage ?: return
        val currentTarget = _uiState.value.selectedTargetLanguage

        // Can we make target the original? Must have downloaded Vosk model
        val targetMatchingVosk = _uiState.value.downloadedLanguages.find {
            it.code.equals(currentTarget.code, ignoreCase = true) ||
            TranslatorEngine.mapVoskToMlKitTag(it.code).equals(currentTarget.code, ignoreCase = true)
        }

        if (targetMatchingVosk == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "${currentTarget.displayName} ka Vosk offline model downloaded nahi hai, isliye ise Original language me swap nahi kiya ja sakta."
            )
            return
        }

        // New target becomes current original
        val newTargetTag = TranslatorEngine.mapVoskToMlKitTag(currentOrig.code)
        val newTarget = TranslatorEngine.supportedTargetLanguages.find {
            it.code.equals(newTargetTag, ignoreCase = true) || it.code.equals(currentOrig.code, ignoreCase = true)
        } ?: TranslatorEngine.supportedTargetLanguages.first()

        selectOriginalLanguage(targetMatchingVosk)
        selectTargetLanguage(newTarget)
    }

    private fun checkSameLanguageRule() {
        val orig = _uiState.value.selectedOriginalLanguage
        val target = _uiState.value.selectedTargetLanguage
        if (orig != null) {
            val origMlTag = TranslatorEngine.mapVoskToMlKitTag(orig.code)
            val isSame = origMlTag.equals(target.code, ignoreCase = true) ||
                (orig.code.startsWith("hi") && target.code == "hi") ||
                (orig.code.startsWith("en") && target.code == "en")
            _uiState.value = _uiState.value.copy(isSameLanguageWarning = isSame)
        } else {
            _uiState.value = _uiState.value.copy(isSameLanguageWarning = false)
        }
    }

    fun confirmDetectedLanguage(language: Language) {
        viewModelScope.launch {
            settingsStore.recordLanguageUsage(language.code)
        }
        _uiState.value = _uiState.value.copy(
            selectedOriginalLanguage = language,
            detection = _uiState.value.detection.copy(
                detectedLanguage = language,
                isConfirmed = true,
                isLowConfidence = false
            )
        )
        checkSameLanguageRule()
    }

    fun changeDetectedLanguageManually() {
        _uiState.value = _uiState.value.copy(
            isAutoDetect = false,
            detection = DetectionUiState()
        )
    }

    fun onMediaSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "audio_file"
                var sizeBytes = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "audio_file"
                        if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }

                val retriever = MediaMetadataRetriever()
                var durationSec = 0.0
                var mimeType = "audio/*"
                var isVideo = false

                try {
                    retriever.setDataSource(context, uri)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durStr != null) {
                        durationSec = durStr.toDouble() / 1000.0
                    }
                    val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    if (mime != null) {
                        mimeType = mime
                        isVideo = mime.startsWith("video/")
                    }
                } catch (ignored: Exception) {
                } finally {
                    try { retriever.release() } catch (ignored: Exception) {}
                }

                _uiState.value = _uiState.value.copy(
                    selectedMedia = SelectedMediaInfo(
                        uri = uri,
                        fileName = fileName,
                        sizeBytes = sizeBytes,
                        durationSeconds = durationSec,
                        mimeType = mimeType,
                        isVideo = isVideo
                    ),
                    detection = DetectionUiState(),
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "File load nahi ho payi: ${e.localizedMessage}"
                )
            }
        }
    }

    fun runAutoDetectSample(onSuccessCallback: ((Language) -> Unit)? = null) {
        val media = _uiState.value.selectedMedia ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Kripya pehle video ya audio file chunein")
            return
        }

        val downloaded = _uiState.value.downloadedLanguages
        if (downloaded.size < 2) {
            _uiState.value = _uiState.value.copy(
                detection = _uiState.value.detection.copy(
                    notEnoughLanguages = true,
                    statusText = "Auto detect ke liye kam se kam 2 bhasha download karo"
                )
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            detection = DetectionUiState(isDetecting = true, statusText = "15-20s speech sample analyze ho raha hai...")
        )

        detectJob = viewModelScope.launch(Dispatchers.IO) {
            val recentCodes = settingsStore.recentLanguages.first()
            val result = AutoLanguageDetector.detectLanguage(
                context = context,
                mediaUri = media.uri,
                downloadedLanguages = downloaded,
                modelManager = modelManager,
                recentlyUsedCodes = recentCodes,
                onStatusUpdate = { status ->
                    _uiState.value = _uiState.value.copy(
                        detection = _uiState.value.detection.copy(statusText = status)
                    )
                }
            )

            when (result) {
                is DetectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedOriginalLanguage = if (!result.isLowConfidence) result.topLanguage else null,
                        detection = DetectionUiState(
                            isDetecting = false,
                            detectedLanguage = result.topLanguage,
                            confidencePercent = result.confidencePercent,
                            isLowConfidence = result.isLowConfidence,
                            secondChoice = result.secondChoice,
                            secondChoicePercent = result.secondChoicePercent,
                            statusText = "Detected: ${result.topLanguage.nativeName} (${result.confidencePercent}% sure)",
                            isConfirmed = !result.isLowConfidence
                        )
                    )
                    checkSameLanguageRule()
                    if (!result.isLowConfidence) {
                        onSuccessCallback?.invoke(result.topLanguage)
                    }
                }
                is DetectionResult.NotEnoughLanguages -> {
                    _uiState.value = _uiState.value.copy(
                        detection = DetectionUiState(
                            isDetecting = false,
                            notEnoughLanguages = true,
                            errorMessage = result.message
                        )
                    )
                }
                is DetectionResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        detection = DetectionUiState(
                            isDetecting = false,
                            errorMessage = result.message
                        )
                    )
                }
            }
        }
    }

    fun startTranscription() {
        val media = _uiState.value.selectedMedia ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Kripya pehle video ya audio file chunein")
            return
        }

        // If Auto Detect is active and language is not yet confirmed
        if (_uiState.value.isAutoDetect && _uiState.value.selectedOriginalLanguage == null) {
            runAutoDetectSample { detected ->
                proceedWithTranscription(media, detected)
            }
            return
        }

        val originalLang = _uiState.value.selectedOriginalLanguage ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Kripya original bhasha chunein ya Auto Detect chalayein")
            return
        }

        // Target rules check: Same language warning blocks translation
        if (_uiState.value.isSameLanguageWarning) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Original bhasha (${originalLang.nativeName}) aur Target bhasha (${_uiState.value.selectedTargetLanguage.displayName}) same hain! Kripya alag target bhasha chunein."
            )
            return
        }

        proceedWithTranscription(media, originalLang)
    }

    private fun proceedWithTranscription(media: SelectedMediaInfo, originalLang: Language) {
        val modelDir = modelManager.getModelDirectory(originalLang.modelName)
        if (modelDir == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "${originalLang.nativeName} (${originalLang.englishName}) model downloaded nahi hai. Pehle download karein."
            )
            return
        }

        val targetLang = _uiState.value.selectedTargetLanguage

        _uiState.value = _uiState.value.copy(
            isTranscribing = true,
            progressPercent = 0,
            speedMultiplier = 1.0f,
            remainingSeconds = 0L,
            processedSeconds = 0.0,
            pivotNotice = null,
            statusMessage = "Audio decode aur Vosk recognition shuru ho raha hai...",
            errorMessage = null
        )

        TranscribeService.start(context, "Transcription & Translation shuru ho raha hai...")

        transcribeJob = viewModelScope.launch(Dispatchers.IO) {
            var voskEngine: VoskEngine? = null
            try {
                voskEngine = VoskEngine(modelDir, 16000f)
                val fullWords = mutableListOf<WordTiming>()
                val fullTextParts = mutableListOf<String>()

                val success = AudioDecoder.decodeToPcmChunks(
                    context = context,
                    uri = media.uri
                ) { chunk, length, progress ->
                    if (!isActive || TranscribeService.isCancelled.value) {
                        return@decodeToPcmChunks false
                    }

                    val speedText = String.format(java.util.Locale.US, "%.1fx", progress.speedMultiplier)
                    val etaText = if (progress.remainingSeconds > 0) "${TimeFormat.formatSecondsToDisplay(progress.remainingSeconds.toDouble())} baaki" else "Pura hone wala hai..."
                    val status = "⚡ $speedText speed • $etaText (${progress.percent}%)"

                    _uiState.value = _uiState.value.copy(
                        progressPercent = progress.percent,
                        speedMultiplier = progress.speedMultiplier,
                        remainingSeconds = progress.remainingSeconds,
                        processedSeconds = progress.processedSeconds,
                        statusMessage = status
                    )
                    TranscribeService.updateProgress(context, progress.percent, status)

                    val isBoundary = voskEngine.acceptWaveForm(chunk, length)
                    if (isBoundary) {
                        val resultChunk = voskEngine.getResult()
                        if (resultChunk.text.isNotBlank()) {
                            fullWords.addAll(resultChunk.words)
                            fullTextParts.add(resultChunk.text)
                        }
                    }
                    true
                }

                if (!success || !isActive || TranscribeService.isCancelled.value) {
                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        statusMessage = "Process cancel ho gaya"
                    )
                    TranscribeService.stop(context)
                    return@launch
                }

                // Flush final recognition
                val finalChunk = voskEngine.getFinalResult()
                if (finalChunk.text.isNotBlank()) {
                    fullWords.addAll(finalChunk.words)
                    fullTextParts.add(finalChunk.text)
                }

                val originalFullText = fullTextParts.joinToString(" ").trim()
                if (originalFullText.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        errorMessage = "Audio me koi aawaz ya bol nahi mile (No speech found)"
                    )
                    TranscribeService.stop(context)
                    return@launch
                }

                // Now execute Translation if target is selected
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Offline ML Kit Translation chal raha hai..."
                )
                TranscribeService.updateProgress(context, 92, "Translating text offline...")

                var pivotNote: String? = null
                val translatedFullText = try {
                    TranslatorEngine.translate(
                        sourceVoskCode = originalLang.code,
                        targetCode = targetLang.code,
                        text = originalFullText,
                        onPivotMessage = { note ->
                            pivotNote = note
                            _uiState.value = _uiState.value.copy(pivotNotice = note)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Translation failed, falling back to original", e)
                    originalFullText
                }

                // Translate captions line by line to preserve word timestamps
                val captionLines = CaptionGrouper.groupCaptions(fullWords)
                val translatedWords = mutableListOf<WordTiming>()

                for (line in captionLines) {
                    val lineTrans = try {
                        TranslatorEngine.translate(
                            sourceVoskCode = originalLang.code,
                            targetCode = targetLang.code,
                            text = line.text
                        )
                    } catch (e: Exception) {
                        line.text
                    }

                    // Spread line timings across translated words
                    val wordsInLine = lineTrans.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (wordsInLine.isNotEmpty()) {
                        val duration = (line.end - line.start).coerceAtLeast(0.1)
                        val wordDuration = duration / wordsInLine.size
                        wordsInLine.forEachIndexed { idx, w ->
                            val s = line.start + (idx * wordDuration)
                            val e = (s + wordDuration).coerceAtMost(line.end)
                            translatedWords.add(WordTiming(word = w, start = s, end = e))
                        }
                    }
                }

                val finalWords = if (translatedWords.isNotEmpty()) translatedWords else fullWords

                // Auto-save to Room database
                val transcript = Transcript(
                    title = media.fileName.substringBeforeLast("."),
                    durationSeconds = media.durationSeconds,
                    languageCode = originalLang.code,
                    languageName = originalLang.nativeName,
                    fullText = translatedFullText,
                    words = finalWords,
                    sourceUri = media.uri.toString(),
                    sourceFileName = media.fileName,
                    isRtl = originalLang.isRtl || targetLang.code == "ar" || targetLang.code == "fa" || targetLang.code == "ur",
                    originalLanguageCode = originalLang.code,
                    targetLanguageCode = targetLang.code,
                    originalLanguageName = originalLang.nativeName,
                    targetLanguageName = targetLang.displayName,
                    translatedText = translatedFullText
                )

                val entity = TranscriptEntity.fromDomain(transcript)
                val newId = database.transcriptDao().insert(entity)

                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    progressPercent = 100,
                    statusMessage = "Transcription aur Translation pura ho gaya!"
                )
                TranscribeService.stop(context)

                // Record language usage for auto-detect ranking
                settingsStore.recordLanguageUsage(originalLang.code)

                _navigationEvent.emit(newId)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    errorMessage = "Error aaya: ${e.localizedMessage}"
                )
                TranscribeService.stop(context)
            } finally {
                try { voskEngine?.close() } catch (ignored: Exception) {}
            }
        }
    }

    fun cancelTranscription() {
        TranscribeService.cancelTranscription()
        transcribeJob?.cancel()
        detectJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isTranscribing = false,
            detection = _uiState.value.detection.copy(isDetecting = false),
            statusMessage = "Process cancel kar diya gaya"
        )
        TranscribeService.stop(context)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
