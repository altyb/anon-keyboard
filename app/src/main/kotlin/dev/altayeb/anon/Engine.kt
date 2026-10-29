package dev.altayeb.anon

import java.util.Locale
import kotlin.math.ln

/** Result for the suggestion strip. [words][0] is the best. [autoCorrect]: space should replace the typed word with words[0]. */
data class Suggestions(val words: List<String>, val autoCorrect: Boolean) {
    companion object { val EMPTY = Suggestions(emptyList(), false) }
}

/**
 * Suggestions + autocorrect + next word for one language. Worker thread only, single-threaded, no locks.
 * Must not allocate much per call; called on every key press.
 */
class Engine(private val trie: Trie, private val user: UserDict) {

    /** Sorted (a shl 16 | b) pairs, both directions; binary-searched so lookups never box. */
    private var nearPairs = IntArray(0)
    private val nearFn = NearKeys { a, b -> isNear(a, b) }

    /** Set the layout so autocorrect knows which keys are neighbours. */
    fun setLayout(layout: Layout) {
        val neighbours = HashMap<Char, HashSet<Char>>()
        val grid = layout.rows.map { row -> row.map { charOf(it) } }
        for (r in grid.indices) for (i in grid[r].indices) {
            val a = grid[r][i] ?: continue
            val set = neighbours.getOrPut(a) { HashSet() }
            grid[r].getOrNull(i - 1)?.let { set.add(it) }
            grid[r].getOrNull(i + 1)?.let { set.add(it) }
            for (rr in intArrayOf(r - 1, r + 1)) {
                val other = grid.getOrNull(rr) ?: continue
                for (ii in i - 1..i + 1) other.getOrNull(ii)?.let { set.add(it) }
            }
            set.remove(a)
        }
        val pairs = ArrayList<Int>()
        for ((a, set) in neighbours) for (b in set) { pairs.add(a.code shl 16 or b.code); pairs.add(b.code shl 16 or a.code) }
        nearPairs = pairs.distinct().sorted().toIntArray()
    }

    /**
     * [before] = text before the cursor (last ~64 chars). If it ends inside a word → completions/corrections
     * for that word; if it ends with a space → next-word predictions for the previous word.
     */
    fun suggest(before: String, autocorrect: Boolean, predictNext: Boolean): Suggestions {
        val end = before.length
        var start = end
        while (start > 0 && isWordChar(before[start - 1])) start--
        val cur = before.substring(start)

        if (cur.isEmpty()) {
            if (!predictNext || end == 0 || !before[end - 1].isWhitespace()) return Suggestions.EMPTY
            var p = end
            while (p > 0 && before[p - 1].isWhitespace()) p--
            var q = p
            while (q > 0 && isWordChar(before[q - 1])) q--
            return if (q == p) Suggestions.EMPTY else nextWords(before.substring(q, p))
        }

        val typedKey = Normalize.key(cur)
        val d = when {
            cur.length <= 2 -> 0
            cur.length <= 4 -> 1
            else -> 2
        }
        val cands = ArrayList<Cand>(16)
        for (w in trie.complete(cur, 5)) addCand(cands, w, trieBase(w), false, typedKey)
        // A word the user typed before must be able to beat a fuzzy guess at a common word ("yey" → "hey").
        for (w in user.complete(cur, 3)) {
            addCand(cands, w, maxOf(trieBase(w), LEARNED_FLOOR + 2.0 * ln(user.count(w) + 1.0)), false, typedKey)
        }
        for (sw in trie.fuzzy(cur, d, 3, nearFn)) {
            addCand(cands, sw.word, sw.score + 1.5 * ln(user.count(sw.word) + 1.0), true, typedKey)
        }
        // Context: words that usually follow the previous word get a boost, and join the list if they fit the prefix.
        val prev = previousWord(before, start)
        if (prev.isNotEmpty()) {
            val follow = user.next(prev, 8) + trie.next(prev, 8)
            for ((rank, w) in follow.withIndex()) {
                val key = Normalize.key(w)
                if (!key.startsWith(typedKey)) continue
                val bonus = CONTEXT_BONUS - 0.2 * rank
                val i = cands.indexOfFirst { it.key == key }
                if (i >= 0) cands[i] = cands[i].let { Cand(it.word, it.key, it.score + bonus, it.fromFuzzy) }
                else addCand(cands, w, trieBase(w) + bonus, false, typedKey)
            }
        }
        if (cands.isEmpty()) return Suggestions.EMPTY

        cands.sortByDescending { it.score }
        val top = cands.take(3)
        val best = top[0]
        // Never "fix" a word glued to digits (h3llo, 2fa codes, IDs): it is not a typo.
        val glued = start > 0 && before[start - 1].isDigit()
        val auto = autocorrect && !glued && cur.length >= 2 && trie.frequency(cur) < 0 && user.count(cur) == 0 &&
            best.fromFuzzy && (top.size == 1 || best.score - top[1].score >= 1.0)
        return Suggestions(top.map { applyCase(it.word, cur) }, auto)
    }

    private class Cand(val word: String, val key: String, val score: Double, val fromFuzzy: Boolean)

    /** Word just before position [start] (skipping spaces), "" if none. */
    private fun previousWord(before: String, start: Int): String {
        var p = start
        while (p > 0 && before[p - 1].isWhitespace()) p--
        if (p == start) return "" // typed word is glued to punctuation or at the start
        var q = p
        while (q > 0 && isWordChar(before[q - 1])) q--
        return before.substring(q, p)
    }

    private fun trieBase(word: String): Double =
        ln(maxOf(trie.frequency(word), 0) + 1.0) + 1.5 * ln(user.count(word) + 1.0)

    /** Adds a candidate, +3 if it is the typed word, keeps the best-scored entry per key. */
    private fun addCand(out: MutableList<Cand>, word: String, base: Double, fuzzy: Boolean, typedKey: String) {
        val key = Normalize.key(word)
        val score = if (key == typedKey) base + 3.0 else base
        val i = out.indexOfFirst { it.key == key }
        when {
            i < 0 -> out.add(Cand(word, key, score, fuzzy))
            score > out[i].score -> out[i] = Cand(word, key, score, fuzzy)
        }
    }

    private fun nextWords(prev: String): Suggestions {
        val out = ArrayList<String>(3)
        val seen = HashSet<String>()
        fun take(list: List<String>) {
            for (w in list) {
                if (out.size >= 3) return
                if (seen.add(Normalize.key(w))) out.add(w)
            }
        }
        take(user.next(prev, 3))
        take(trie.next(prev, 3))
        return if (out.isEmpty()) Suggestions.EMPTY else Suggestions(out, false)
    }

    private fun applyCase(word: String, typed: String): String {
        if (word.isEmpty()) return word
        if (typed.length > 1 && typed.any { it.isUpperCase() } && typed.none { it.isLowerCase() }) {
            return word.uppercase(Locale.ROOT)
        }
        return if (typed[0].isUpperCase()) word[0].uppercaseChar().toString() + word.substring(1) else word
    }

    private fun isNear(a: Char, b: Char): Boolean =
        java.util.Arrays.binarySearch(nearPairs, a.code shl 16 or b.code) >= 0

    private fun charOf(key: Key): Char? {
        if (key.code <= 0 || key.code > 0xFFFF) return null
        val s = Normalize.key(key.code.toChar().toString())
        return if (s.length == 1) s[0] else null
    }

    private companion object {
        /** Score boost for a word that commonly follows the previous word (≈ 12× frequency). */
        const val CONTEXT_BONUS = 2.5
        /** Score floor for learned completions (≈ a very common dictionary word with one typo). */
        const val LEARNED_FLOOR = 11.0
    }

    private fun isWordChar(c: Char): Boolean =
        c.isLetter() || c == '\'' || c == '’' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
}
