package com.speechtotext.app.util

import com.speechtotext.app.domain.WordTiming
import java.util.Locale

object TextCleaner {

    private val fillerWords = setOf(
        "umm", "um", "uh", "uhh", "mmm", "mm", "er", "ah",
        "matlab", "yani", "like", "you know"
    )

    private val fillerWordsHindi = setOf(
        "मतलब", "उम्म", "अह", "अरे"
    )

    fun cleanTranscriptText(text: String, isChinese: Boolean = false): String {
        if (text.isBlank()) return text

        var cleaned = text

        // 1. Remove filler words (case-insensitive)
        for (filler in fillerWords) {
            val pattern = "(?i)\\b$filler\\b[ ,.]*".toRegex()
            cleaned = cleaned.replace(pattern, " ")
        }
        for (filler in fillerWordsHindi) {
            val pattern = "(?i)\\b$filler\\b[ ,.]*".toRegex()
            cleaned = cleaned.replace(pattern, " ")
        }

        // 2. Normalize whitespace
        cleaned = cleaned.replace("\\s+".toRegex(), " ").trim()

        if (cleaned.isEmpty()) return ""

        // 3. Basic punctuation & sentence capitalization
        cleaned = if (isChinese) {
            cleanChinesePunctuation(cleaned)
        } else {
            cleanWesternPunctuation(cleaned)
        }

        return cleaned
    }

    private fun cleanWesternPunctuation(text: String): String {
        // Capitalize first letter of every sentence
        val sentences = text.split("(?<=[.?!])\\s+".toRegex())
        val capitalized = sentences.joinToString(" ") { sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isNotEmpty()) {
                val firstChar = trimmed[0].uppercaseChar()
                val rest = trimmed.substring(1)
                "$firstChar$rest"
            } else {
                ""
            }
        }

        // Ensure closing period if none exists
        val finalResult = capitalized.trim()
        return if (finalResult.isNotEmpty() && !finalResult.endsWith(".") && !finalResult.endsWith("?") && !finalResult.endsWith("!")) {
            "$finalResult."
        } else {
            finalResult
        }
    }

    private fun cleanChinesePunctuation(text: String): String {
        // Add Chinese full stop if ending without punctuation
        var res = text.replace(" ", "") // standard Chinese doesn't use inter-word spaces
        if (res.isNotEmpty() && !res.endsWith("。") && !res.endsWith("？") && !res.endsWith("！") && !res.endsWith("，")) {
            res += "。"
        }
        return res
    }

    /**
     * Filters out word timings corresponding to filler words
     */
    fun cleanWordTimings(words: List<WordTiming>): List<WordTiming> {
        return words.filter { wordTiming ->
            val wordClean = wordTiming.word.trim().lowercase(Locale.ROOT).replace("[^a-zA-Z0-9\u0900-\u097F]".toRegex(), "")
            wordClean.isNotEmpty() && !fillerWords.contains(wordClean) && !fillerWordsHindi.contains(wordClean)
        }
    }
}
