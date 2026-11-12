package com.speechtotext.app.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

enum class ChineseDisplayMode(val label: String) {
    HANZI("Chinese (Hanzi)"),
    PINYIN("Pinyin (Roman)"),
    BOTH("Dono dikhao")
}

object PinyinConverter {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    fun toPinyin(chineseText: String): String {
        if (chineseText.isBlank()) return chineseText
        val sb = StringBuilder()
        var lastWasHanzi = false

        for (c in chineseText) {
            val isHanzi = isChineseCharacter(c)
            if (isHanzi) {
                try {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format)
                    if (!pinyinArray.isNullOrEmpty()) {
                        if (sb.isNotEmpty() && lastWasHanzi) {
                            sb.append(" ")
                        }
                        sb.append(pinyinArray[0])
                        lastWasHanzi = true
                    } else {
                        sb.append(c)
                        lastWasHanzi = false
                    }
                } catch (e: Exception) {
                    sb.append(c)
                    lastWasHanzi = false
                }
            } else {
                sb.append(c)
                lastWasHanzi = false
            }
        }
        return sb.toString()
    }

    fun formatChineseOutput(hanziText: String, mode: ChineseDisplayMode): String {
        return when (mode) {
            ChineseDisplayMode.HANZI -> hanziText
            ChineseDisplayMode.PINYIN -> toPinyin(hanziText)
            ChineseDisplayMode.BOTH -> {
                val pinyin = toPinyin(hanziText)
                "$hanziText\n($pinyin)"
            }
        }
    }

    private fun isChineseCharacter(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
