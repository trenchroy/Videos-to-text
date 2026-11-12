package com.speechtotext.app

import com.speechtotext.app.domain.WordTiming
import com.speechtotext.app.util.CaptionGrouper
import com.speechtotext.app.util.TextCleaner
import com.speechtotext.app.util.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechToTextUnitTest {

    @Test
    fun testHinglishTransliteration() {
        val hindi = "मैं घर जा रहा हूँ"
        val hinglish = Transliterator.toHinglish(hindi)
        assertEquals("main ghar ja raha hoon", hinglish)
    }

    @Test
    fun testCaptionGrouper() {
        val words = listOf(
            WordTiming("Hello", 0.0, 0.4),
            WordTiming("world", 0.5, 0.9),
            WordTiming("this", 1.0, 1.4),
            WordTiming("is", 1.5, 1.8),
            WordTiming("test", 1.9, 2.3)
        )
        val lines = CaptionGrouper.groupCaptions(words, maxWordsPerLine = 2)
        assertEquals(3, lines.size)
        assertEquals("Hello world", lines[0].text)
        assertEquals(0.0, lines[0].start, 0.01)
        assertEquals(0.9, lines[0].end, 0.01)
    }

    @Test
    fun testTextCleaner() {
        val input = "umm this is matlab like very good"
        val cleaned = TextCleaner.cleanTranscriptText(input)
        assertTrue(cleaned.contains("This is very good."))
    }
}
