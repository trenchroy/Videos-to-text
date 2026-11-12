package com.speechtotext.app.ui.result

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechtotext.app.data.db.AppDatabase
import com.speechtotext.app.data.db.TranscriptEntity
import com.speechtotext.app.domain.CaptionStyle
import com.speechtotext.app.domain.Transcript
import com.speechtotext.app.domain.WordTiming
import com.speechtotext.app.util.CaptionGrouper
import com.speechtotext.app.util.CaptionLine
import com.speechtotext.app.util.ChineseDisplayMode
import com.speechtotext.app.util.ExportFormat
import com.speechtotext.app.util.Exporters
import com.speechtotext.app.util.PinyinConverter
import com.speechtotext.app.util.TextCleaner
import com.speechtotext.app.util.Transliterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultUiState(
    val transcript: Transcript? = null,
    val captionLines: List<CaptionLine> = emptyList(),
    val maxWordsPerLine: Int = 0, // 0 = Auto, 1, 2, 3, 5
    val cjkCharsPerLine: Int = 0, // 0 = Auto, 4, 8, 12, 16
    val isHinglishMode: Boolean = false,
    val chineseMode: ChineseDisplayMode = ChineseDisplayMode.HANZI,
    val captionStyle: CaptionStyle = CaptionStyle(),
    val canUndoCleanup: Boolean = false,
    val isCleaned: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private var previousTranscriptState: Transcript? = null

    fun loadTranscript(transcriptId: Long) {
        viewModelScope.launch {
            val entity = database.transcriptDao().getTranscriptById(transcriptId)
            if (entity != null) {
                val transcript = entity.toDomain()
                val isCjk = transcript.languageCode.startsWith("cn") || transcript.languageCode.startsWith("zh") || transcript.languageCode.startsWith("ja")
                val lines = CaptionGrouper.groupCaptions(
                    words = transcript.words,
                    maxWordsPerLine = _uiState.value.maxWordsPerLine,
                    isCjk = isCjk,
                    cjkCharsPerLine = _uiState.value.cjkCharsPerLine
                )

                _uiState.value = _uiState.value.copy(
                    transcript = transcript,
                    captionLines = lines
                )
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Transcript nahi mila")
            }
        }
    }

    fun updateFullText(newText: String) {
        val current = _uiState.value.transcript ?: return
        val updated = current.copy(fullText = newText)
        _uiState.value = _uiState.value.copy(transcript = updated)
        saveToDatabase(updated)
    }

    fun updateWord(index: Int, newWordText: String, newStart: Double, newEnd: Double) {
        val current = _uiState.value.transcript ?: return
        if (index !in current.words.indices) return

        val mutableWords = current.words.toMutableList()
        mutableWords[index] = mutableWords[index].copy(
            word = newWordText,
            start = newStart,
            end = newEnd
        )

        val updated = current.copy(
            words = mutableWords,
            fullText = mutableWords.joinToString(" ") { it.word }
        )
        regroupCaptions(updated)
        saveToDatabase(updated)
    }

    fun setMaxWordsPerLine(count: Int) {
        _uiState.value = _uiState.value.copy(maxWordsPerLine = count)
        _uiState.value.transcript?.let { regroupCaptions(it) }
    }

    fun setCjkCharsPerLine(count: Int) {
        _uiState.value = _uiState.value.copy(cjkCharsPerLine = count)
        _uiState.value.transcript?.let { regroupCaptions(it) }
    }

    fun setHinglishMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isHinglishMode = enabled)
    }

    fun setChineseMode(mode: ChineseDisplayMode) {
        _uiState.value = _uiState.value.copy(chineseMode = mode)
    }

    fun updateCaptionStyle(style: CaptionStyle) {
        _uiState.value = _uiState.value.copy(captionStyle = style)
    }

    fun applyRuleBasedCleanup() {
        val current = _uiState.value.transcript ?: return
        previousTranscriptState = current

        val isChinese = current.languageCode.startsWith("cn") || current.languageCode.startsWith("zh")
        val cleanedText = TextCleaner.cleanTranscriptText(current.fullText, isChinese)
        val cleanedWords = TextCleaner.cleanWordTimings(current.words)

        val updated = current.copy(
            fullText = cleanedText,
            words = cleanedWords
        )

        _uiState.value = _uiState.value.copy(
            transcript = updated,
            isCleaned = true,
            canUndoCleanup = true,
            infoMessage = "Filler words hataye gaye aur punctuation fix kiya gaya!"
        )
        regroupCaptions(updated)
        saveToDatabase(updated)
    }

    fun undoCleanup() {
        val prev = previousTranscriptState ?: return
        _uiState.value = _uiState.value.copy(
            transcript = prev,
            isCleaned = false,
            canUndoCleanup = false,
            infoMessage = "Cleanup undo kiya gaya"
        )
        regroupCaptions(prev)
        saveToDatabase(prev)
    }

    private fun regroupCaptions(transcript: Transcript) {
        val isCjk = transcript.languageCode.startsWith("cn") || transcript.languageCode.startsWith("zh") || transcript.languageCode.startsWith("ja")
        val lines = CaptionGrouper.groupCaptions(
            words = transcript.words,
            maxWordsPerLine = _uiState.value.maxWordsPerLine,
            isCjk = isCjk,
            cjkCharsPerLine = _uiState.value.cjkCharsPerLine
        )
        _uiState.value = _uiState.value.copy(
            transcript = transcript,
            captionLines = lines
        )
    }

    fun getDisplayText(): String {
        val transcript = _uiState.value.transcript ?: return ""
        val text = transcript.fullText

        return when {
            transcript.languageCode.startsWith("hi") && _uiState.value.isHinglishMode -> {
                Transliterator.toHinglish(text)
            }
            (transcript.languageCode.startsWith("cn") || transcript.languageCode.startsWith("zh")) -> {
                PinyinConverter.formatChineseOutput(text, _uiState.value.chineseMode)
            }
            else -> text
        }
    }

    fun exportToUri(uri: Uri, format: ExportFormat) {
        val transcript = _uiState.value.transcript ?: return
        val lines = _uiState.value.captionLines
        viewModelScope.launch(Dispatchers.IO) {
            val success = Exporters.writeToUri(context, uri, format, transcript, lines)
            _uiState.value = _uiState.value.copy(
                infoMessage = if (success) "${format.label} export ho gaya!" else "Export fail ho gaya"
            )
        }
    }

    fun copyTranscript() {
        val text = getDisplayText()
        Exporters.copyToClipboard(context, text, "Transcript")
        _uiState.value = _uiState.value.copy(infoMessage = "Text clipboard me copy ho gaya!")
    }

    fun shareTranscript() {
        val text = getDisplayText()
        val title = _uiState.value.transcript?.title ?: "Transcript"
        Exporters.shareText(context, text, title)
    }

    private fun saveToDatabase(transcript: Transcript) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = TranscriptEntity.fromDomain(transcript)
            database.transcriptDao().update(entity)
        }
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
