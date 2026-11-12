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
import com.speechtotext.app.engine.ModelListRepository
import com.speechtotext.app.engine.ModelManager
import com.speechtotext.app.engine.TranscribeService
import com.speechtotext.app.engine.VoskEngine
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

data class HomeUiState(
    val selectedMedia: SelectedMediaInfo? = null,
    val selectedLanguage: Language? = null,
    val downloadedLanguages: List<Language> = emptyList(),
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
    private var allLanguagesList: List<Language> = emptyList()

    init {
        loadLanguages()
    }

    fun loadLanguages() {
        viewModelScope.launch {
            try {
                val languages = repository.getAvailableLanguages()
                allLanguagesList = languages
                refreshDownloadedLanguages()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshDownloadedLanguages() {
        modelManager.refreshStatuses()
        val downloaded = allLanguagesList.filter { modelManager.isModelReady(it.modelName) }
        val currentSelected = _uiState.value.selectedLanguage

        viewModelScope.launch {
            val defaultLangCode = settingsStore.defaultLanguage.first()
            val preferred = downloaded.find { it.code == defaultLangCode }
                ?: downloaded.firstOrNull()
                ?: allLanguagesList.find { it.code == defaultLangCode }
                ?: allLanguagesList.firstOrNull()

            _uiState.value = _uiState.value.copy(
                downloadedLanguages = downloaded,
                selectedLanguage = currentSelected ?: preferred
            )
        }
    }

    fun selectLanguage(language: Language) {
        viewModelScope.launch {
            settingsStore.setDefaultLanguage(language.code)
        }
        _uiState.value = _uiState.value.copy(
            selectedLanguage = language,
            errorMessage = null
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
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "File load nahi ho payi: ${e.localizedMessage}"
                )
            }
        }
    }

    fun startTranscription() {
        val media = _uiState.value.selectedMedia ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Kripya pehle video ya audio file chunein")
            return
        }

        val language = _uiState.value.selectedLanguage ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Kripya ek bhasha chunein")
            return
        }

        val modelDir = modelManager.getModelDirectory(language.modelName)
        if (modelDir == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "${language.nativeName} (${language.englishName}) model downloaded nahi hai. Pehle download karein."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isTranscribing = true,
            progressPercent = 0,
            speedMultiplier = 1.0f,
            remainingSeconds = 0L,
            processedSeconds = 0.0,
            statusMessage = "Audio decode aur Vosk speech recognition shuru ho raha hai...",
            errorMessage = null
        )

        TranscribeService.start(context, "Transcription shuru ho raha hai...")

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
                        statusMessage = "Transcription cancel ho gaya"
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

                val finalFullText = fullTextParts.joinToString(" ").trim()
                if (finalFullText.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isTranscribing = false,
                        errorMessage = "Audio me koi aawaz ya bol nahi mile (No speech found)"
                    )
                    TranscribeService.stop(context)
                    return@launch
                }

                // Auto-save to Room database
                val transcript = Transcript(
                    title = media.fileName.substringBeforeLast("."),
                    durationSeconds = media.durationSeconds,
                    languageCode = language.code,
                    languageName = language.nativeName,
                    fullText = finalFullText,
                    words = fullWords,
                    sourceUri = media.uri.toString(),
                    sourceFileName = media.fileName,
                    isRtl = language.isRtl
                )

                val entity = TranscriptEntity.fromDomain(transcript)
                val newId = database.transcriptDao().insert(entity)

                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    progressPercent = 100,
                    statusMessage = "Transcription pura ho gaya!"
                )
                TranscribeService.stop(context)

                _navigationEvent.emit(newId)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isTranscribing = false,
                    errorMessage = "Transcription me error aaya: ${e.localizedMessage}"
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
        _uiState.value = _uiState.value.copy(
            isTranscribing = false,
            statusMessage = "Process cancel kar diya gaya"
        )
        TranscribeService.stop(context)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
