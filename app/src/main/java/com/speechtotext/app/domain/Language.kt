package com.speechtotext.app.domain

import kotlinx.serialization.Serializable

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED
}

@Serializable
data class VoskModelInfo(
    val name: String,
    val lang: String,
    val lang_text: String? = null,
    val size: Long = 0L,
    val size_text: String? = null,
    val type: String = "small",
    val url: String = "",
    val obsolete: String = "false",
    val version: String = ""
)

data class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val modelName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sizeText: String,
    val status: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val isPinned: Boolean = false,
    val isRtl: Boolean = false
) {
    val isDownloaded: Boolean
        get() = status == ModelDownloadStatus.READY

    val isHindi: Boolean
        get() = code.startsWith("hi", ignoreCase = true)

    val isChinese: Boolean
        get() = code.startsWith("cn", ignoreCase = true) || code.startsWith("zh", ignoreCase = true)

    val isJapanese: Boolean
        get() = code.startsWith("ja", ignoreCase = true)
}
