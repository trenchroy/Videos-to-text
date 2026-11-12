package com.speechtotext.app.engine

import android.content.Context
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.ModelDownloadStatus
import com.speechtotext.app.domain.VoskModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ModelListRepository(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cacheFile = File(context.filesDir, "model_list_cache.json")

    /**
     * Fetches official model list from https://alphacephei.com/vosk/models/model-list.json.
     * Falls back to disk cache, then to built-in offline list.
     */
    suspend fun getAvailableLanguages(): List<Language> = withContext(Dispatchers.IO) {
        val models = fetchRemoteModelList() ?: readCacheModelList() ?: getFallbackModelList()

        val smallModels = models.filter {
            it.type.equals("small", ignoreCase = true) &&
                    !it.obsolete.equals("true", ignoreCase = true)
        }

        val languages = smallModels.map { model ->
            mapToLanguage(model)
        }

        // Sort: Pinned first (hi, en-in, en-us, cn), then alphabetical by englishName
        languages.sortedWith(
            compareByDescending<Language> { it.isPinned }
                .thenBy { it.englishName }
        )
    }

    private fun fetchRemoteModelList(): List<VoskModelInfo>? {
        return try {
            val request = Request.Builder()
                .url("https://alphacephei.com/vosk/models/model-list.json")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    cacheFile.writeText(body)
                    json.decodeFromString<List<VoskModelInfo>>(body)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readCacheModelList(): List<VoskModelInfo>? {
        return try {
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString<List<VoskModelInfo>>(content)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Built-in offline fallback list for Hindi, English (India), English (US), Chinese, etc.
     */
    private fun getFallbackModelList(): List<VoskModelInfo> {
        return listOf(
            VoskModelInfo(
                name = "vosk-model-small-hi-0.22",
                lang = "hi",
                lang_text = "Hindi",
                size = 44040192L,
                size_text = "42M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-en-in-0.4",
                lang = "en-in",
                lang_text = "English (Indian)",
                size = 37748736L,
                size_text = "36M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-en-us-0.15",
                lang = "en-us",
                lang_text = "English (US)",
                size = 41943040L,
                size_text = "40M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-cn-0.22",
                lang = "cn",
                lang_text = "Chinese",
                size = 44040192L,
                size_text = "42M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-es-0.42",
                lang = "es",
                lang_text = "Spanish",
                size = 40894464L,
                size_text = "39M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-fr-0.22",
                lang = "fr",
                lang_text = "French",
                size = 42991616L,
                size_text = "41M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-de-0.15",
                lang = "de",
                lang_text = "German",
                size = 47185920L,
                size_text = "45M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-ru-0.22",
                lang = "ru",
                lang_text = "Russian",
                size = 47185920L,
                size_text = "45M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-ja-0.22",
                lang = "ja",
                lang_text = "Japanese",
                size = 50331648L,
                size_text = "48M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-fa-0.4",
                lang = "fa",
                lang_text = "Persian",
                size = 41943040L,
                size_text = "40M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.4.zip"
            ),
            VoskModelInfo(
                name = "vosk-model-small-ar-0.22",
                lang = "ar",
                lang_text = "Arabic",
                size = 44040192L,
                size_text = "42M",
                type = "small",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-ar-0.22.zip"
            )
        )
    }

    private fun mapToLanguage(model: VoskModelInfo): Language {
        val langCode = model.lang.lowercase()
        val isPinned = langCode in listOf("hi", "en-in", "en-us", "cn", "zh")

        val (native, english) = getLanguageNames(langCode, model.lang_text)
        val isRtl = langCode in listOf("ar", "fa", "ur", "he")

        val sizeText = model.size_text ?: if (model.size > 0) "${model.size / (1024 * 1024)}M" else "40M"

        return Language(
            code = langCode,
            nativeName = native,
            englishName = english,
            modelName = model.name,
            downloadUrl = model.url,
            sizeBytes = model.size,
            sizeText = sizeText,
            status = ModelDownloadStatus.NOT_DOWNLOADED,
            isPinned = isPinned,
            isRtl = isRtl
        )
    }

    private fun getLanguageNames(langCode: String, fallbackText: String?): Pair<String, String> {
        return when (langCode) {
            "hi" -> Pair("हिन्दी", "Hindi")
            "en-in" -> Pair("English (India)", "English (India)")
            "en-us", "en" -> Pair("English (US)", "English (US)")
            "cn", "zh" -> Pair("中文", "Chinese")
            "es" -> Pair("Español", "Spanish")
            "fr" -> Pair("Français", "French")
            "de" -> Pair("Deutsch", "German")
            "ru" -> Pair("Русский", "Russian")
            "ja" -> Pair("日本語", "Japanese")
            "ko" -> Pair("한국어", "Korean")
            "ar" -> Pair("العربية", "Arabic")
            "fa" -> Pair("فارسی", "Persian")
            "it" -> Pair("Italiano", "Italian")
            "pt" -> Pair("Português", "Portuguese")
            "tr" -> Pair("Türkçe", "Turkish")
            "nl" -> Pair("Nederlands", "Dutch")
            "pl" -> Pair("Polski", "Polish")
            "uk" -> Pair("Українська", "Ukrainian")
            "vi" -> Pair("Tiếng Việt", "Vietnamese")
            "sv" -> Pair("Svenska", "Swedish")
            else -> {
                val display = fallbackText ?: langCode.uppercase()
                Pair(display, display)
            }
        }
    }
}
