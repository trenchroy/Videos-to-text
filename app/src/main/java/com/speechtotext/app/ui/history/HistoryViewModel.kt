package com.speechtotext.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.db.AppDatabase
import com.speechtotext.app.domain.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val transcripts: List<Transcript> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val rawTranscripts = database.transcriptDao().getAllTranscripts()

    val uiState: StateFlow<HistoryUiState> = combine(
        rawTranscripts,
        _searchQuery
    ) { entities, query ->
        val domainList = entities.map { it.toDomain() }
        val filtered = if (query.isBlank()) {
            domainList
        } else {
            domainList.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.fullText.contains(query, ignoreCase = true) ||
                        it.languageName.contains(query, ignoreCase = true)
            }
        }
        HistoryUiState(
            transcripts = filtered,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState(isLoading = true))

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun renameTranscript(id: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptDao().updateTitle(id, newTitle.trim())
        }
    }

    fun togglePin(id: Long, currentPin: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptDao().updatePinStatus(id, !currentPin)
        }
    }

    fun deleteTranscript(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptDao().deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            database.transcriptDao().deleteAll()
        }
    }
}
