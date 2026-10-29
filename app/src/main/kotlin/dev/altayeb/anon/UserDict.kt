package dev.altayeb.anon

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Learned words + word pairs + contact names. Bounded forever: caps, fading counts, LFU eviction.
 * Worker thread only. Saved atomically (temp file + rename).
 */
class UserDict(private val file: File) {

    private class Entry(var display: String, var count: Float)

    private val words = HashMap<String, Entry>()
    private val pairs = HashMap<String, HashMap<String, Entry>>()
    private var pairTotal = 0
    private val contacts = HashMap<String, String>()
    private var dirty = false
    private var learns = 0

    fun load() {
        clearLearned()
        if (file.exists()) {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { din ->
                    require(din.readInt() == MAGIC && din.readInt() == VERSION)
                    repeat(din.readInt()) {
                        val key = din.readUTF()
                        val display = din.readUTF()
                        words[key] = Entry(display, din.readFloat())
                    }
                    repeat(din.readInt()) {
                        val prev = din.readUTF()
                        val word = din.readUTF()
                        val display = din.readUTF()
                        val count = din.readFloat()
                        if (pairs.getOrPut(prev) { HashMap() }.put(word, Entry(display, count)) == null) pairTotal++
                    }
                }
            } catch (e: Exception) {
                clearLearned()
            }
        }
        dirty = false
    }

    /** Count one use of [word] (and the pair [prev] → [word] when prev is not null). */
    fun learn(word: String, prev: String?) {
        val w = word.trim()
        if (!valid(w)) return
        val wk = Normalize.key(w)
        val e = words.getOrPut(wk) { Entry(w, 0f) }
        e.count += 1f
        if (isLower(w)) e.display = w
        val p = prev?.trim()
        if (p != null && valid(p)) {
            val targets = pairs.getOrPut(Normalize.key(p)) { HashMap() }
            val pe = targets[wk] ?: Entry(w, 0f).also { targets[wk] = it; pairTotal++ }
            pe.count += 1f
            if (isLower(w)) pe.display = w
        }
        dirty = true
        if (++learns % FADE_EVERY == 0) fade()
        if (words.size > MAX_WORDS) evictWords()
        if (pairTotal > MAX_PAIRS) evictPairs()
    }

    /** Learned count for [word] (0 if unknown). Contacts count as a fixed boost. */
    fun count(word: String): Int {
        val k = Normalize.key(word.trim())
        val c = words[k]?.count?.toInt() ?: 0
        return if (contacts.containsKey(k)) maxOf(c, CONTACT_BOOST.toInt()) else c
    }

    /** Up to [k] learned words / contact names starting with [prefix], best first. */
    fun complete(prefix: String, k: Int): List<String> {
        val p = Normalize.key(prefix.trim())
        if (p.isEmpty() || k <= 0) return emptyList()
        val hits = ArrayList<Pair<String, Float>>()
        for ((key, e) in words) {
            if (!key.startsWith(p)) continue
            val score = if (contacts.containsKey(key)) maxOf(e.count, CONTACT_BOOST) else e.count
            hits += e.display to score
        }
        for ((key, name) in contacts) {
            if (!words.containsKey(key) && key.startsWith(p)) hits += name to CONTACT_BOOST
        }
        return hits.sortedByDescending { it.second }.map { it.first }.distinct().take(k)
    }

    /** Up to [k] learned next words after [prev], best first. */
    fun next(prev: String, k: Int): List<String> {
        if (k <= 0) return emptyList()
        val targets = pairs[Normalize.key(prev.trim())] ?: return emptyList()
        return targets.values.sortedByDescending { it.count }.take(k).map { it.display }
    }

    fun forget(word: String) {
        val k = Normalize.key(word.trim())
        words.remove(k)
        pairs.remove(k)?.let { pairTotal -= it.size }
        val iter = pairs.values.iterator()
        while (iter.hasNext()) {
            val targets = iter.next()
            if (targets.remove(k) != null) pairTotal--
            if (targets.isEmpty()) iter.remove()
        }
        dirty = true
    }

    /** Replace the contact-name set (capped). */
    /** Forget every learned word and pair, and delete the file. Contacts stay (they are re-read anyway). */
    fun clear() {
        words.clear(); pairs.clear(); pairTotal = 0; learns = 0; dirty = false
        file.delete()
    }

    fun setContacts(names: Collection<String>) {
        contacts.clear()
        for (name in names) {
            for (part in name.trim().split(WHITESPACE)) {
                if (contacts.size >= MAX_CONTACTS) return
                if (valid(part)) contacts[Normalize.key(part)] = part
            }
        }
    }

    /** Write to disk if changed. */
    fun save() {
        if (!dirty) return
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(words.size)
            for ((k, e) in words) {
                out.writeUTF(k)
                out.writeUTF(e.display)
                out.writeFloat(e.count)
            }
            out.writeInt(pairs.values.sumOf { it.size })
            for ((prev, targets) in pairs) {
                for ((word, e) in targets) {
                    out.writeUTF(prev)
                    out.writeUTF(word)
                    out.writeUTF(e.display)
                    out.writeFloat(e.count)
                }
            }
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        dirty = false
    }

    internal fun size(): Int = words.size
    internal fun pairCount(): Int = pairTotal
    internal fun contactCount(): Int = contacts.size

    private fun clearLearned() {
        words.clear()
        pairs.clear()
        pairTotal = 0
        learns = 0
    }

    private fun valid(w: String): Boolean =
        w.length in 2..32 && w.none { it.isDigit() } && w.any { it.isLetter() }

    private fun isLower(w: String): Boolean = w == w.lowercase(Locale.ROOT)

    private fun fade() {
        val f = FADE_FACTOR.toFloat()
        words.values.forEach { it.count *= f }
        words.values.removeAll { it.count < 0.5f }
        val iter = pairs.values.iterator()
        pairTotal = 0
        while (iter.hasNext()) {
            val targets = iter.next()
            targets.values.forEach { it.count *= f }
            targets.values.removeAll { it.count < 0.5f }
            if (targets.isEmpty()) iter.remove() else pairTotal += targets.size
        }
    }

    // ponytail: full sort per eviction, amortized over the 10% headroom it leaves
    private fun evictWords() {
        val drop = words.size - MAX_WORDS * 9 / 10
        words.entries.sortedBy { it.value.count }.take(drop).map { it.key }.forEach { words.remove(it) }
    }

    private fun evictPairs() {
        val all = ArrayList<Triple<String, String, Float>>(pairTotal)
        for ((prev, targets) in pairs) for ((word, e) in targets) all += Triple(prev, word, e.count)
        all.sortBy { it.third }
        for ((prev, word, _) in all.take(pairTotal - MAX_PAIRS * 9 / 10)) {
            val targets = pairs[prev] ?: continue
            if (targets.remove(word) != null) pairTotal--
            if (targets.isEmpty()) pairs.remove(prev)
        }
    }

    companion object {
        const val MAX_WORDS = 5_000
        const val MAX_PAIRS = 20_000
        const val MAX_CONTACTS = 2_000
        const val FADE_EVERY = 500      // learned words between fades
        const val FADE_FACTOR = 0.9

        private const val MAGIC = 0x414E5544 // "ANUD"
        private const val VERSION = 1
        private const val CONTACT_BOOST = 20f
        private val WHITESPACE = Regex("\\s+")
    }
}
