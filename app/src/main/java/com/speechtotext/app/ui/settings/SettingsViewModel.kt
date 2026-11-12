package com.speechtotext.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.settings.SettingsStore
import com.speechtotext.app.domain.Language
import com.speechtotext.app.engine.ModelListRepository
import com.speechtotext.app.engine.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultLanguageCode: String = "hi",
    val wifiOnlyDownloads: Boolean = false,
    val downloadedModels: List<Language> = emptyList(),
    val totalStorageUsedText: String = "0 MB",
    val availableLanguages: List<Language> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val modelManager = ModelManager(application)
    private val repository = ModelListRepository(application)
    private val settingsStore = SettingsStore(application)

    private val _availableLanguages = MutableStateFlow<List<Language>>(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.defaultLanguage,
        settingsStore.wifiOnlyDownloads,
        _availableLanguages,
        modelManager.downloadStatus
    ) { defLang, wifiOnly, allLangs, statuses ->
        val downloaded = allLangs.filter { modelManager.isModelReady(it.modelName) }
        val storageBytes = modelManager.getTotalStorageUsedBytes()
        val storageText = com.speechtotext.app.util.TimeFormat.formatFileSize(storageBytes)

        SettingsUiState(
            defaultLanguageCode = defLang,
            wifiOnlyDownloads = wifiOnly,
            downloadedModels = downloaded,
            totalStorageUsedText = storageText,
            availableLanguages = allLangs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                val langs = repository.getAvailableLanguages()
                _availableLanguages.value = langs
                modelManager.refreshStatuses()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDefaultLanguage(langCode: String) {
        viewModelScope.launch {
            settingsStore.setDefaultLanguage(langCode)
        }
    }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        viewModelScope.launch {
            settingsStore.setWifiOnlyDownloads(wifiOnly)
        }
    }

    fun deleteModel(modelName: String) {
        modelManager.deleteModel(modelName)
        loadData()
    }

    fun deleteAllModels() {
        modelManager.deleteAllModels()
        loadData()
    }
}
