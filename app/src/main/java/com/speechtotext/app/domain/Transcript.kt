package com.speechtotext.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class Transcript(
    val id: Long = 0L,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationSeconds: Double = 0.0,
    val languageCode: String = "en-us",
    val languageName: String = "English (US)",
    val fullText: String = "",
    val words: List<WordTiming> = emptyList(),
    val sourceUri: String? = null,
    val sourceFileName: String? = null,
    val isPinned: Boolean = false,
    val isRtl: Boolean = false,
    val originalLanguageCode: String? = null,
    val targetLanguageCode: String? = null,
    val originalLanguageName: String? = null,
    val targetLanguageName: String? = null,
    val translatedText: String? = null
) {
    val wordCount: Int
        get() = words.size.takeIf { it > 0 } ?: fullText.split("\\s+".toRegex()).count { it.isNotBlank() }
}
