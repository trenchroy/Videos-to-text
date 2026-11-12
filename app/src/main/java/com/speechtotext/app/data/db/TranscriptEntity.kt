package com.speechtotext.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.speechtotext.app.domain.Transcript
import com.speechtotext.app.domain.WordTiming
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val durationSeconds: Double,
    val languageCode: String,
    val languageName: String,
    val fullText: String,
    val wordsJson: String,
    val sourceUri: String?,
    val sourceFileName: String?,
    val isPinned: Boolean = false,
    val isRtl: Boolean = false,
    val originalLanguageCode: String? = null,
    val targetLanguageCode: String? = null,
    val originalLanguageName: String? = null,
    val targetLanguageName: String? = null,
    val translatedText: String? = null
) {
    fun toDomain(): Transcript {
        val words = try {
            if (wordsJson.isNotBlank()) {
                Json.decodeFromString<List<WordTiming>>(wordsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        return Transcript(
            id = id,
            title = title,
            createdAt = createdAt,
            durationSeconds = durationSeconds,
            languageCode = languageCode,
            languageName = languageName,
            fullText = fullText,
            words = words,
            sourceUri = sourceUri,
            sourceFileName = sourceFileName,
            isPinned = isPinned,
            isRtl = isRtl,
            originalLanguageCode = originalLanguageCode,
            targetLanguageCode = targetLanguageCode,
            originalLanguageName = originalLanguageName,
            targetLanguageName = targetLanguageName,
            translatedText = translatedText
        )
    }

    companion object {
        fun fromDomain(transcript: Transcript): TranscriptEntity {
            val json = try {
                Json.encodeToString(transcript.words)
            } catch (e: Exception) {
                "[]"
            }
            return TranscriptEntity(
                id = transcript.id,
                title = transcript.title,
                createdAt = transcript.createdAt,
                durationSeconds = transcript.durationSeconds,
                languageCode = transcript.languageCode,
                languageName = transcript.languageName,
                fullText = transcript.fullText,
                wordsJson = json,
                sourceUri = transcript.sourceUri,
                sourceFileName = transcript.sourceFileName,
                isPinned = transcript.isPinned,
                isRtl = transcript.isRtl,
                originalLanguageCode = transcript.originalLanguageCode,
                targetLanguageCode = transcript.targetLanguageCode,
                originalLanguageName = transcript.originalLanguageName,
                targetLanguageName = transcript.targetLanguageName,
                translatedText = transcript.translatedText
            )
        }
    }
}
