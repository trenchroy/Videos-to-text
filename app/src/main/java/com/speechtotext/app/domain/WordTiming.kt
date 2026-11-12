package com.speechtotext.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class WordTiming(
    val word: String,
    val start: Double,
    val end: Double,
    val confidence: Double = 1.0
) {
    val duration: Double
        get() = (end - start).coerceAtLeast(0.0)
}
