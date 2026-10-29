package dev.altayeb.anon

import java.util.Locale

/** Lookup key for matching only (display keeps the original). Lowercase; Arabic: strip tashkeel, unify alef/yeh/teh-marbuta. */
object Normalize {
    fun key(word: String): String {
        val sb = StringBuilder(word.length)
        for (ch in word.lowercase(Locale.ROOT)) {
            when (ch) {
                in 'ً'..'ْ', 'ٰ', 'ـ' -> Unit // tashkeel, superscript alef, tatweel
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
