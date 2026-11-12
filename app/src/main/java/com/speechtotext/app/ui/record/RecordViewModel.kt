package com.speechtotext.app.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.db.AppDatabase
import com.speechtotext.app.data.db.TranscriptEntity
import com.speechtotext.app.data.settings.SettingsStore
import com.speechtotext.app.domain.Language
import com.speechtotext.app.engine.MicRecordState
import com.speechtotext.app.engine.MicRecorder
import com.speechtotext.app.engine.ModelListRepository
import com.speechtotext.app.engine.ModelManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordUiState(
    val recordState: MicRecordState = MicRecordState.IDLE,
    val durationSeconds: Double = 0.0,
    val livePartialText: String = "",
    val accumulatedText: String = "",
    val amplitude: Float = 0f,
    val selectedLanguage: Language? = null,
    val downloadedLanguages: List<Language> = emptyList(),
    val errorMessage: String? = null
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    val modelManager = ModelManager(application)
    private val repository = ModelListRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Long>()
    val navigationEvent: SharedFlow<Long> = _navigationEvent.asSharedFlow()

    private var micRecorder: MicRecorder? = null

    init {
        loadLanguages()
    }

    fun loadLanguages() {
        viewModelScope.launch {
            try {
                val all = repository.getAvailableLanguages()
                modelManager.refreshStatuses()
                val downloaded = all.filter { modelManager.isModelReady(it.modelName) }
                val defaultLangCode = settingsStore.defaultLanguage.first()
                val preferred = downloaded.find { it.code == defaultLangCode }
                    ?: downloaded.firstOrNull()
                    ?: all.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    downloadedLanguages = downloaded,
                    selectedLanguage = preferred
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectLanguage(lang: Language) {
        if (_uiState.value.recordState == MicRecordState.IDLE) {
            _uiState.value = _uiState.value.copy(selectedLanguage = lang)
        }
    }

    fun startRecording(): Boolean {
        val lang = _uiState.value.selectedLanguage ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Pehle ek bhasha chunein")
            return false
        }
        val modelDir = modelManager.getModelDirectory(lang.modelName)
        if (modelDir == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "${lang.nativeName} model download nahi hai. Bhasha Manager se download karein."
            )
            return false
        }

        val recorder = MicRecorder(
            modelDirectory = modelDir,
            languageCode = lang.code,
            languageName = lang.nativeName,
            isRtl = lang.isRtl
        )
        micRecorder = recorder

        val success = recorder.startRecording()
        if (!success) {
            _uiState.value = _uiState.value.copy(errorMessage = "Microphone shuru nahi ho saka")
            return false
        }

        viewModelScope.launch {
            recorder.recordState.collect { state ->
                _uiState.value = _uiState.value.copy(recordState = state)
            }
        }
        viewModelScope.launch {
            recorder.durationSeconds.collect { dur ->
                _uiState.value = _uiState.value.copy(durationSeconds = dur)
            }
        }
        viewModelScope.launch {
            recorder.amplitude.collect { amp ->
                _uiState.value = _uiState.value.copy(amplitude = amp)
            }
        }
        viewModelScope.launch {
            recorder.livePartialText.collect { partial ->
                _uiState.value = _uiState.value.copy(livePartialText = partial)
            }
        }
        viewModelScope.launch {
            recorder.accumulatedText.collect { accum ->
                _uiState.value = _uiState.value.copy(accumulatedText = accum)
            }
        }

        return true
    }

    fun pauseRecording() {
        micRecorder?.pauseRecording()
    }

    fun resumeRecording() {
        micRecorder?.resumeRecording()
    }

    fun stopRecording() {
        val transcript = micRecorder?.stopRecording() ?: return
        if (transcript.fullText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                recordState = MicRecordState.IDLE,
                errorMessage = "Kuchh bola nahi gaya (No speech recognized)"
            )
            return
        }

        viewModelScope.launch {
            val entity = TranscriptEntity.fromDomain(transcript)
            val newId = database.transcriptDao().insert(entity)
            _uiState.value = _uiState.value.copy(
                recordState = MicRecordState.IDLE,
                durationSeconds = 0.0,
                livePartialText = "",
                accumulatedText = "",
                amplitude = 0f
            )
            _navigationEvent.emit(newId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        micRecorder?.stopRecording()
    }
}
