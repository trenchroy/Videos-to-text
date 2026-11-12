package com.speechtotext.app.engine

import com.speechtotext.app.domain.WordTiming
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class VoskEngine(modelDirectory: File, sampleRate: Float = 16000.0f) : AutoCloseable {

    private val model: Model = Model(modelDirectory.absolutePath)
    private val recognizer: Recognizer = Recognizer(model, sampleRate).apply {
        setWords(true)
    }

    /**
     * Accepts a 16kHz mono 16-bit PCM chunk.
     * Returns true if a full sentence/phrase boundary was detected, false otherwise.
     */
    fun acceptWaveForm(data: ByteArray, length: Int = data.size): Boolean {
        return recognizer.acceptWaveForm(data, length)
    }

    /**
     * Returns the intermediate partial transcript text.
     */
    fun getPartialText(): String {
        val jsonStr = recognizer.partialResult
        return try {
            val json = JSONObject(jsonStr)
            json.optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Parses the result accumulated so far when a phrase boundary was reached.
     */
    fun getResult(): RecognitionChunk {
        val jsonStr = recognizer.result
        return parseRecognitionJson(jsonStr)
    }

    /**
     * Flushes the final result at end of stream.
     */
    fun getFinalResult(): RecognitionChunk {
        val jsonStr = recognizer.finalResult
        return parseRecognitionJson(jsonStr)
    }

    private fun parseRecognitionJson(jsonStr: String): RecognitionChunk {
        if (jsonStr.isBlank()) return RecognitionChunk("", emptyList())
        return try {
            val json = JSONObject(jsonStr)
            val text = json.optString("text", "")
            val words = mutableListOf<WordTiming>()
            val resultArray = json.optJSONArray("result")
            if (resultArray != null) {
                for (i in 0 until resultArray.length()) {
                    val item = resultArray.getJSONObject(i)
                    val word = item.optString("word", "")
                    val start = item.optDouble("start", 0.0)
                    val end = item.optDouble("end", 0.0)
                    val conf = item.optDouble("conf", 1.0)
                    if (word.isNotBlank()) {
                        words.add(WordTiming(word = word, start = start, end = end, confidence = conf))
                    }
                }
            }
            RecognitionChunk(text, words)
        } catch (e: Exception) {
            RecognitionChunk("", emptyList())
        }
    }

    override fun close() {
        try {
            recognizer.close()
        } catch (ignored: Exception) {}
        try {
            model.close()
        } catch (ignored: Exception) {}
    }
}

data class RecognitionChunk(
    val text: String,
    val words: List<WordTiming>
)
