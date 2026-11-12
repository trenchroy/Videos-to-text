package com.speechtotext.app.ui.languages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.settings.SettingsStore
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.ModelDownloadStatus
import com.speechtotext.app.engine.ModelListRepository
import com.speechtotext.app.engine.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanguageUiState(
    val languages: List<Language> = emptyList(),
    val searchQuery: String = "",
    val downloadedOnly: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LanguageViewModel(application: Application) : AndroidViewModel(application) {

    val modelManager = ModelManager(application)
    private val repository = ModelListRepository(application)
    private val settingsStore = SettingsStore(application)

    private val _rawLanguages = MutableStateFlow<List<Language>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly = _downloadedOnly.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val uiState: StateFlow<LanguageUiState> = combine(
        _rawLanguages,
        _searchQuery,
        _downloadedOnly,
        modelManager.downloadStatus,
        modelManager.downloadProgress
    ) { raw, query, downloadedOnlyFilter, statuses, progresses ->
        val updatedList = raw.map { lang ->
            val status = statuses[lang.modelName] ?: ModelDownloadStatus.NOT_DOWNLOADED
            val progress = progresses[lang.modelName] ?: 0f
            lang.copy(status = status, downloadProgress = progress)
        }

        val filtered = updatedList.filter { lang ->
            val matchesQuery = query.isBlank() ||
                    lang.nativeName.contains(query, ignoreCase = true) ||
                    lang.englishName.contains(query, ignoreCase = true) ||
                    lang.code.contains(query, ignoreCase = true)
            val matchesFilter = !downloadedOnlyFilter || lang.isDownloaded
            matchesQuery && matchesFilter
        }

        LanguageUiState(
            languages = filtered,
            searchQuery = query,
            downloadedOnly = downloadedOnlyFilter,
            isLoading = _isLoading.value,
            errorMessage = _errorMessage.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageUiState())

    init {
        loadLanguages()
    }

    fun loadLanguages() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val list = repository.getAvailableLanguages()
                _rawLanguages.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Language list load nahi ho payi. Offline fallback use ho raha hai."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleDownloadedOnly(enabled: Boolean) {
        _downloadedOnly.value = enabled
    }

    fun downloadModel(language: Language) {
        viewModelScope.launch {
            val result = modelManager.downloadModel(language)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Model download fail ho gaya"
            }
        }
    }

    fun cancelDownload(language: Language) {
        modelManager.cancelDownload(language.modelName)
    }

    fun deleteModel(language: Language) {
        modelManager.deleteModel(language.modelName)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
