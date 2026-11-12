package com.speechtotext.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsStore(private val context: Context) {

    companion object {
        val KEY_DEFAULT_LANG = stringPreferencesKey("default_language")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val KEY_MAX_WORDS_PER_LINE = intPreferencesKey("max_words_per_line")
        val KEY_CHINESE_CHARS_PER_LINE = intPreferencesKey("chinese_chars_per_line")
        val KEY_DEFAULT_PRESET = stringPreferencesKey("default_preset")
        val KEY_LAST_ORIGINAL_LANG = stringPreferencesKey("last_original_lang")
        val KEY_LAST_TARGET_LANG = stringPreferencesKey("last_target_lang")
        val KEY_RECENT_LANGUAGES = stringPreferencesKey("recent_languages")
    }

    val lastOriginalLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_ORIGINAL_LANG] ?: "auto"
    }

    val lastTargetLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_TARGET_LANG] ?: "hinglish"
    }

    val recentLanguages: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_LANGUAGES]?.split(",")?.filter { it.isNotBlank() } ?: listOf("hi", "en-us", "cn", "ja")
    }

    val defaultLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_LANG] ?: "hi"
    }

    val wifiOnlyDownloads: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY] ?: false
    }

    val maxWordsPerLine: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_WORDS_PER_LINE] ?: 0 // 0 = Auto
    }

    val chineseCharsPerLine: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHINESE_CHARS_PER_LINE] ?: 0 // 0 = Auto
    }

    val defaultPreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PRESET] ?: "REELS_BOLD"
    }

    suspend fun setDefaultLanguage(langCode: String) {
        context.dataStore.edit { it[KEY_DEFAULT_LANG] = langCode }
    }

    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setMaxWordsPerLine(count: Int) {
        context.dataStore.edit { it[KEY_MAX_WORDS_PER_LINE] = count }
    }

    suspend fun setChineseCharsPerLine(count: Int) {
        context.dataStore.edit { it[KEY_CHINESE_CHARS_PER_LINE] = count }
    }

    suspend fun setDefaultPreset(preset: String) {
        context.dataStore.edit { it[KEY_DEFAULT_PRESET] = preset }
    }

    suspend fun setLastOriginalLanguage(code: String) {
        context.dataStore.edit { it[KEY_LAST_ORIGINAL_LANG] = code }
    }

    suspend fun setLastTargetLanguage(code: String) {
        context.dataStore.edit { it[KEY_LAST_TARGET_LANG] = code }
    }

    suspend fun recordLanguageUsage(code: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT_LANGUAGES]?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            existing.remove(code)
            existing.add(0, code)
            val trimmed = existing.take(6).joinToString(",")
            prefs[KEY_RECENT_LANGUAGES] = trimmed
        }
    }
}
