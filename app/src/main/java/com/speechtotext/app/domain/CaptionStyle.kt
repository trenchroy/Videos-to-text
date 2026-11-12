package com.speechtotext.app.domain

import androidx.compose.ui.graphics.Color

enum class CaptionPosition {
    TOP,
    MIDDLE,
    BOTTOM
}

enum class CaptionPreset(val label: String) {
    REELS_BOLD("Reels Bold"),
    MINIMAL_WHITE("Minimal White"),
    YELLOW_HIGHLIGHT("Yellow Highlight"),
    NEON("Neon Glow"),
    CLASSIC_SUBTITLE("Classic Subtitle")
}

data class CaptionStyle(
    val preset: CaptionPreset = CaptionPreset.REELS_BOLD,
    val fontSizeSp: Float = 24f,
    val textColor: Color = Color.White,
    val outlineColor: Color = Color.Black,
    val backgroundColor: Color = Color(0x99000000),
    val hasBackgroundBox: Boolean = true,
    val activeHighlightColor: Color = Color(0xFFFFD54F),
    val position: CaptionPosition = CaptionPosition.BOTTOM,
    val isAllUppercase: Boolean = false
) {
    companion object {
        fun fromPreset(preset: CaptionPreset): CaptionStyle = when (preset) {
            CaptionPreset.REELS_BOLD -> CaptionStyle(
                preset = preset,
                fontSizeSp = 26f,
                textColor = Color.White,
                outlineColor = Color.Black,
                backgroundColor = Color.Transparent,
                hasBackgroundBox = false,
                activeHighlightColor = Color(0xFF00E5FF),
                position = CaptionPosition.BOTTOM,
                isAllUppercase = true
            )
            CaptionPreset.MINIMAL_WHITE -> CaptionStyle(
                preset = preset,
                fontSizeSp = 20f,
                textColor = Color(0xFFF8FAFC),
                outlineColor = Color(0x66000000),
                backgroundColor = Color.Transparent,
                hasBackgroundBox = false,
                activeHighlightColor = Color(0xFF38BDF8),
                position = CaptionPosition.BOTTOM,
                isAllUppercase = false
            )
            CaptionPreset.YELLOW_HIGHLIGHT -> CaptionStyle(
                preset = preset,
                fontSizeSp = 24f,
                textColor = Color.White,
                outlineColor = Color.Black,
                backgroundColor = Color(0xAA000000),
                hasBackgroundBox = true,
                activeHighlightColor = Color(0xFFFFD600),
                position = CaptionPosition.BOTTOM,
                isAllUppercase = false
            )
            CaptionPreset.NEON -> CaptionStyle(
                preset = preset,
                fontSizeSp = 24f,
                textColor = Color(0xFF00E5FF),
                outlineColor = Color(0xFF0F172A),
                backgroundColor = Color(0xCC090D16),
                hasBackgroundBox = true,
                activeHighlightColor = Color(0xFFFF4081),
                position = CaptionPosition.BOTTOM,
                isAllUppercase = false
            )
            CaptionPreset.CLASSIC_SUBTITLE -> CaptionStyle(
                preset = preset,
                fontSizeSp = 19f,
                textColor = Color(0xFFFFFF00),
                outlineColor = Color.Black,
                backgroundColor = Color(0x99000000),
                hasBackgroundBox = true,
                activeHighlightColor = Color.White,
                position = CaptionPosition.BOTTOM,
                isAllUppercase = false
            )
        }
    }
}
