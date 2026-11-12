package com.speechtotext.app.engine

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.speechtotext.app.util.Transliterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TargetLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String = displayName
)

object TranslatorEngine {

    private const val TAG = "TranslatorEngine"

    // Custom code for Hinglish (Devanagari to Roman transliteration)
    const val CODE_HINGLISH = "hinglish"

    /**
     * Map Vosk language code (e.g. "hi", "en-us", "cn", "ja") to standard ML Kit BCP-47 tag ("hi", "en", "zh", "ja")
     */
    fun mapVoskToMlKitTag(voskCode: String): String {
        val lower = voskCode.lowercase().trim()
        return when {
            lower.startsWith("en") -> TranslateLanguage.ENGLISH
            lower.startsWith("hi") -> TranslateLanguage.HINDI
            lower.startsWith("cn") || lower.startsWith("zh") -> TranslateLanguage.CHINESE
            lower.startsWith("ja") -> TranslateLanguage.JAPANESE
            lower.startsWith("ru") -> TranslateLanguage.RUSSIAN
            lower.startsWith("es") -> TranslateLanguage.SPANISH
            lower.startsWith("fr") -> TranslateLanguage.FRENCH
            lower.startsWith("de") -> TranslateLanguage.GERMAN
            lower.startsWith("it") -> TranslateLanguage.ITALIAN
            lower.startsWith("pt") -> TranslateLanguage.PORTUGUESE
            lower.startsWith("ar") -> TranslateLanguage.ARABIC
            lower.startsWith("ko") -> TranslateLanguage.KOREAN
            lower.startsWith("tr") -> TranslateLanguage.TURKISH
            lower.startsWith("nl") -> TranslateLanguage.DUTCH
            lower.startsWith("pl") -> TranslateLanguage.POLISH
            lower.startsWith("vi") -> TranslateLanguage.VIETNAMESE
            lower.startsWith("uk") -> TranslateLanguage.UKRAINIAN
            lower.startsWith("fa") -> TranslateLanguage.PERSIAN
            lower.startsWith("ur") -> TranslateLanguage.URDU
            lower.startsWith("bn") -> TranslateLanguage.BENGALI
            lower.startsWith("mr") -> TranslateLanguage.MARATHI
            lower.startsWith("te") -> TranslateLanguage.TELUGU
            lower.startsWith("ta") -> TranslateLanguage.TAMIL
            lower.startsWith("gu") -> TranslateLanguage.GUJARATI
            lower.startsWith("kn") -> TranslateLanguage.KANNADA
            else -> {
                val prefix = lower.substringBefore("-").substringBefore("_")
                val matched = TranslateLanguage.fromLanguageTag(prefix)
                matched ?: TranslateLanguage.ENGLISH
            }
        }
    }

    /**
     * Complete list of target languages: ML Kit supported languages plus Hinglish (Roman)
     */
    val supportedTargetLanguages: List<TargetLanguage> by lazy {
        val list = mutableListOf<TargetLanguage>()

        // 1. Pinned top choices: Hinglish, Hindi, English
        list.add(TargetLanguage(CODE_HINGLISH, "Hinglish (Roman)", "Hinglish (Roman)"))
        list.add(TargetLanguage("hi", "Hindi (हिन्दी)", "हिन्दी"))
        list.add(TargetLanguage("en", "English", "English"))

        // Popular languages
        val popularCodes = listOf(
            "zh" to "Chinese (中文)",
            "es" to "Spanish (Español)",
            "ar" to "Arabic (العربية)",
            "fr" to "French (Français)",
            "de" to "German (Deutsch)",
            "ru" to "Russian (Русский)",
            "ja" to "Japanese (日本語)",
            "pt" to "Portuguese (Português)",
            "it" to "Italian (Italiano)",
            "ko" to "Korean (한국어)",
            "ur" to "Urdu (اردو)",
            "bn" to "Bengali (বাংলা)",
            "mr" to "Marathi (मराठी)",
            "ta" to "Tamil (தமிழ்)",
            "te" to "Telugu (తెలుగు)",
            "gu" to "Gujarati (ગુજરાતી)",
            "kn" to "Kannada (ಕನ್ನಡ)",
            "tr" to "Turkish (Türkçe)",
            "id" to "Indonesian (Bahasa Indonesia)",
            "vi" to "Vietnamese (Tiếng Việt)",
            "nl" to "Dutch (Nederlands)",
            "pl" to "Polish (Polski)",
            "th" to "Thai (ไทย)",
            "sv" to "Swedish (Svenska)",
            "fa" to "Persian (فارسی)",
            "uk" to "Ukrainian (Українська)"
        )

        for ((code, name) in popularCodes) {
            if (list.none { it.code == code }) {
                list.add(TargetLanguage(code, name, name))
            }
        }

        // Add remaining from ML Kit TranslateLanguage
        val allMlKit = TranslateLanguage.getAllLanguages()
        for (tag in allMlKit) {
            if (list.none { it.code.equals(tag, ignoreCase = true) }) {
                val locale = Locale.forLanguageTag(tag)
                val displayName = locale.getDisplayName(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
                val nativeName = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
                val label = if (displayName.isNotBlank()) "$displayName ($nativeName)" else tag.uppercase()
                list.add(TargetLanguage(tag, label, nativeName))
            }
        }

        list
    }

    /**
     * Translates text offline using ML Kit Translation.
     * If pair is not directly supported, automatically pivots through English.
     * If target is Hinglish, translates to Hindi then transliterates to Roman text.
     */
    suspend fun translate(
        sourceVoskCode: String,
        targetCode: String,
        text: String,
        onPivotMessage: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val sourceTag = mapVoskToMlKitTag(sourceVoskCode)

        // If target is Hinglish
        if (targetCode.equals(CODE_HINGLISH, ignoreCase = true)) {
            val hindiText = if (sourceTag == TranslateLanguage.HINDI) {
                text
            } else {
                translateInternal(sourceTag, TranslateLanguage.HINDI, text, onPivotMessage)
            }
            return@withContext Transliterator.toHinglish(hindiText)
        }

        val targetTag = TranslateLanguage.fromLanguageTag(targetCode) ?: TranslateLanguage.HINDI
        if (sourceTag == targetTag) {
            return@withContext text
        }

        translateInternal(sourceTag, targetTag, text, onPivotMessage)
    }

    private fun translateInternal(
        sourceTag: String,
        targetTag: String,
        text: String,
        onPivotMessage: ((String) -> Unit)?
    ): String {
        return try {
            // Attempt direct translation first
            directTranslate(sourceTag, targetTag, text)
        } catch (e: Exception) {
            Log.w(TAG, "Direct translation $sourceTag -> $targetTag failed, trying pivot through English", e)
            if (sourceTag != TranslateLanguage.ENGLISH && targetTag != TranslateLanguage.ENGLISH) {
                onPivotMessage?.invoke("Dono bhashao ke beech direct pair available nahi hai, isliye English ke zariye pivot translate kiya gaya.")
                val englishIntermediary = directTranslate(sourceTag, TranslateLanguage.ENGLISH, text)
                directTranslate(TranslateLanguage.ENGLISH, targetTag, englishIntermediary)
            } else {
                throw e
            }
        }
    }

    private fun directTranslate(sourceTag: String, targetTag: String, text: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceTag)
            .setTargetLanguage(targetTag)
            .build()
        val translator = Translation.getClient(options)
        return try {
            val conditions = DownloadConditions.Builder().build()
            Tasks.await(translator.downloadModelIfNeeded(conditions))
            Tasks.await(translator.translate(text))
        } finally {
            try { translator.close() } catch (ignored: Exception) {}
        }
    }
}
