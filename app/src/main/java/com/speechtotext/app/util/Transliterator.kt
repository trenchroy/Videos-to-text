package com.speechtotext.app.util

object Transliterator {

    // Common Hindi words mapped directly to popular, natural Hinglish spellings
    private val commonWords = mapOf(
        "मैं" to "main",
        "हूँ" to "hoon",
        "हूं" to "hoon",
        "है" to "hai",
        "हैं" to "hain",
        "हो" to "ho",
        "था" to "tha",
        "थी" to "thi",
        "थे" to "the",
        "घर" to "ghar",
        "जा" to "ja",
        "रहा" to "raha",
        "रही" to "rahi",
        "रहे" to "rahe",
        "और" to "aur",
        "क्या" to "kya",
        "क्यों" to "kyon",
        "क्यूं" to "kyon",
        "कैसे" to "kaise",
        "नहीं" to "nahi",
        "नही" to "nahi",
        "आप" to "aap",
        "हम" to "hum",
        "तुम" to "tum",
        "वह" to "woh",
        "वो" to "woh",
        "यह" to "yeh",
        "ये" to "ye",
        "अच्छा" to "achha",
        "बहुत" to "bahut",
        "करना" to "karna",
        "कर" to "kar",
        "किया" to "kiya",
        "दिया" to "diya",
        "लिया" to "liya",
        "कहाँ" to "kahan",
        "यहाँ" to "yahan",
        "वहाँ" to "wahan",
        "जहाँ" to "jahan",
        "बात" to "baat",
        "दिन" to "din",
        "काम" to "kaam",
        "लोग" to "log",
        "कोई" to "koi",
        "कुछ" to "kuch",
        "सब" to "sab",
        "समय" to "samay",
        "दोस्त" to "dost",
        "भाई" to "bhai",
        "नमस्ते" to "namaste",
        "धन्यवाद" to "dhanyavaad",
        "शुक्रिया" to "shukriya"
    )

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )

    private val matras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee",
        'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
    )

    private val consonants: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u095C' to "r", '\u095D' to "rh", '\u095E' to "z",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g"
    )

    fun toHinglish(text: String): String {
        if (text.isBlank()) return text

        // Tokenize by word boundaries while preserving punctuation and whitespace
        val tokens = text.split("(?<=\\s)|(?=\\s)|(?<=\\p{Punct})|(?=\\p{Punct})".toRegex())
        val result = StringBuilder()

        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty() || trimmed.all { it.isWhitespace() || !isDevanagari(it) }) {
                result.append(token)
                continue
            }

            // Check dictionary first
            val lowerMatch = commonWords[trimmed]
            if (lowerMatch != null) {
                result.append(lowerMatch)
                continue
            }

            // Otherwise, rule-based transliteration
            result.append(transliterateDevanagariWord(trimmed))
        }

        return result.toString()
    }

    private fun isDevanagari(c: Char): Boolean = c in '\u0900'..'\u097F'

    private fun transliterateDevanagariWord(word: String): String {
        val sb = StringBuilder()
        val len = word.length
        var i = 0

        while (i < len) {
            val c = word[i]

            // 1. Independent vowels
            if (independentVowels.containsKey(c)) {
                sb.append(independentVowels[c])
                i++
                continue
            }

            // 2. Anusvara / Chandrabindu
            if (c == 'ं' || c == 'ँ') {
                sb.append("n")
                i++
                continue
            }

            // 3. Visarga
            if (c == 'ः') {
                sb.append("h")
                i++
                continue
            }

            // 4. Halant (virama)
            if (c == '्') {
                i++
                continue
            }

            // 5. Consonants
            if (consonants.containsKey(c)) {
                var roman = consonants[c] ?: ""
                val nextChar = if (i + 1 < len) word[i + 1] else null

                // Check for nukta on next char
                var actualNext = nextChar
                var step = 1
                if (nextChar == '़') {
                    when (c) {
                        'क' -> roman = "q"
                        'ख' -> roman = "kh"
                        'ग' -> roman = "g"
                        'ज' -> roman = "z"
                        'ड' -> roman = "r"
                        'ढ' -> roman = "rh"
                        'फ' -> roman = "f"
                    }
                    step = 2
                    actualNext = if (i + 2 < len) word[i + 2] else null
                }

                sb.append(roman)

                if (actualNext != null) {
                    if (actualNext == '्') {
                        // Halant cancels inherent vowel 'a'
                        i += step + 1
                        continue
                    } else if (matras.containsKey(actualNext)) {
                        // Explicit matra vowel replaces inherent 'a'
                        var matraRoman = matras[actualNext] ?: ""
                        // Natural Hinglish: word-ending 'ी' often written as 'i' (e.g., koshish, nahi)
                        if (actualNext == 'ी' && (i + step + 1 >= len)) {
                            matraRoman = "i"
                        }
                        sb.append(matraRoman)
                        i += step + 1
                        continue
                    } else if (consonants.containsKey(actualNext) || independentVowels.containsKey(actualNext)) {
                        // Next is another consonant -> append inherent 'a'
                        // Unless penultimate consonant before word-ending consonant (Hindi schwa deletion)
                        val isPenultimate = (i + step + 1 < len && (i + step + 2 >= len || (i + step + 2 < len && word[i + step + 2] == '्')))
                        if (isPenultimate && len > 3 && sb.length > 2) {
                            // Apply subtle schwa deletion for natural sound
                            // e.g., karnaa -> karrna vs karna
                        } else {
                            sb.append("a")
                        }
                    }
                } else {
                    // Last character of the word:
                    // In Hindi, word-final short 'a' is dropped (schwa deletion) e.g., ghar, not ghara
                    // No inherent 'a' added at word end
                }

                i += step
                continue
            }

            // Fallback for unrecognized chars
            sb.append(c)
            i++
        }

        return sb.toString()
    }
}
