package com.speechtotext.app.util

import com.speechtotext.app.domain.WordTiming

data class CaptionLine(
    val index: Int,
    val start: Double,
    val end: Double,
    val text: String,
    val words: List<WordTiming> = emptyList()
)

object CaptionGrouper {

    /**
     * Groups word-by-word timestamps into formatted caption lines.
     * @param words The list of word timings from Vosk.
     * @param maxWordsPerLine 0 for Auto, or 1, 2, 3, 5.
     * @param isCjk true for Chinese/Japanese.
     * @param cjkCharsPerLine 0 for Auto, or 4, 8, 12, 16.
     */
    fun groupCaptions(
        words: List<WordTiming>,
        maxWordsPerLine: Int = 0,
        isCjk: Boolean = false,
        cjkCharsPerLine: Int = 0
    ): List<CaptionLine> {
        if (words.isEmpty()) return emptyList()

        return if (isCjk) {
            groupCjk(words, cjkCharsPerLine)
        } else {
            groupWestern(words, maxWordsPerLine)
        }
    }

    private fun groupWestern(words: List<WordTiming>, maxWordsPerLine: Int): List<CaptionLine> {
        val lines = mutableListOf<CaptionLine>()
        val currentGroup = mutableListOf<WordTiming>()
        var lineIndex = 1

        val maxAllowed = if (maxWordsPerLine > 0) maxWordsPerLine else 4

        for (i in words.indices) {
            val word = words[i]
            val prevWord = currentGroup.lastOrNull()

            val isPause = prevWord != null && (word.start - prevWord.end) > 0.5
            val reachedMax = currentGroup.size >= maxAllowed

            if (currentGroup.isNotEmpty() && (isPause || reachedMax)) {
                lines.add(createLine(lineIndex++, currentGroup))
                currentGroup.clear()
            }

            currentGroup.add(word)
        }

        if (currentGroup.isNotEmpty()) {
            lines.add(createLine(lineIndex, currentGroup))
        }

        return lines
    }

    private fun groupCjk(words: List<WordTiming>, cjkCharsPerLine: Int): List<CaptionLine> {
        val lines = mutableListOf<CaptionLine>()
        val currentGroup = mutableListOf<WordTiming>()
        var currentLength = 0
        var lineIndex = 1

        val maxChars = if (cjkCharsPerLine > 0) cjkCharsPerLine else 8

        for (i in words.indices) {
            val word = words[i]
            val prevWord = currentGroup.lastOrNull()
            val wordLen = word.word.length

            val isPause = prevWord != null && (word.start - prevWord.end) > 0.6
            val reachedMax = currentLength + wordLen > maxChars

            if (currentGroup.isNotEmpty() && (isPause || reachedMax)) {
                lines.add(createLine(lineIndex++, currentGroup, isCjk = true))
                currentGroup.clear()
                currentLength = 0
            }

            currentGroup.add(word)
            currentLength += wordLen
        }

        if (currentGroup.isNotEmpty()) {
            lines.add(createLine(lineIndex, currentGroup, isCjk = true))
        }

        return lines
    }

    private fun createLine(index: Int, words: List<WordTiming>, isCjk: Boolean = false): CaptionLine {
        val start = words.first().start
        val end = words.last().end
        val text = if (isCjk) {
            words.joinToString("") { it.word }
        } else {
            words.joinToString(" ") { it.word }
        }
        return CaptionLine(
            index = index,
            start = start,
            end = end,
            text = text,
            words = ArrayList(words)
        )
    }

    fun toSrt(lines: List<CaptionLine>): String {
        val sb = StringBuilder()
        for (line in lines) {
            sb.append(line.index).append("\n")
            sb.append(TimeFormat.formatToSrtTime(line.start))
                .append(" --> ")
                .append(TimeFormat.formatToSrtTime(line.end))
                .append("\n")
            sb.append(line.text).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    fun toVtt(lines: List<CaptionLine>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        for (line in lines) {
            sb.append(line.index).append("\n")
            sb.append(TimeFormat.formatToVttTime(line.start))
                .append(" --> ")
                .append(TimeFormat.formatToVttTime(line.end))
                .append("\n")
            sb.append(line.text).append("\n\n")
        }
        return sb.toString().trimEnd()
    }
}
