package dev.altayeb.anon

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.PriorityQueue
import kotlin.math.ln

/**
 * Read-only dictionary, memory-mapped from a binary file. Safe to use from one worker thread.
 *
 * File format (big-endian):
 *  header (16 bytes): magic, nodeCount, blobOff, 0
 *  nodeCount records of 26 bytes, BFS order, root = 0. Children of a node are contiguous and sorted by char:
 *    char(2) freq(4, 0 = not a word) maxFreq(4, subtree max) childCount(4) firstChild(4)
 *    displayOff(4, absolute, -1 none) nextOff(4, absolute bigram block, -1 none)
 *  blob: string = len(2) + UTF-8 bytes; bigram block = count(1) + count strings, best first.
 */
class Trie private constructor(private val buf: MappedByteBuffer) {

    // Scratch for fuzzy(); the Trie is single-threaded, so per-instance buffers are safe.
    private val rows = Array(MAX_WORD + 2) { FloatArray(MAX_WORD + 2) } // rows[depth][j]
    private val qBuf = CharArray(MAX_WORD + 1)
    private val pBuf = CharArray(MAX_WORD + 2)
    private var fzM = 0
    private var fzMax = 0f
    private var fzK = 0
    private var fzNear: NearKeys = NO_NEAR

    /** Frequency of an exact word, or -1 if absent. */
    fun frequency(word: String): Int {
        val n = find(Normalize.key(word))
        if (n < 0) return -1
        val f = freqAt(n)
        return if (f > 0) f else -1
    }

    /** Up to [k] completions of [prefix] (prefix itself included if it is a word), best first. */
    fun complete(prefix: String, k: Int): List<String> {
        if (k <= 0) return emptyList()
        val start = find(Normalize.key(prefix))
        if (start < 0) return emptyList()
        // Best-first: a node item is keyed by its subtree max, a word item by its own freq.
        val pq = PriorityQueue<Item>(16, Comparator { a, b -> b.prio.compareTo(a.prio) })
        pq.add(Item(start, maxAt(start), false))
        val out = ArrayList<String>(k)
        while (out.size < k) {
            val item = pq.poll() ?: break
            if (item.isWord) {
                out.add(readString(displayAt(item.node)))
                continue
            }
            val n = item.node
            if (freqAt(n) > 0) pq.add(Item(n, freqAt(n), true))
            val first = firstChildAt(n)
            for (c in first until first + childCountAt(n)) {
                if (maxAt(c) > 0) pq.add(Item(c, maxAt(c), false))
            }
        }
        return out
    }

    /**
     * Words within edit distance ≤ [maxDistance] of [word], best first, up to [k].
     * [nearKeys] returns true when two chars are neighbouring keys (cheaper substitution).
     */
    fun fuzzy(word: String, maxDistance: Int, k: Int, nearKeys: NearKeys): List<ScoredWord> {
        if (k <= 0 || word.length > MAX_WORD) return emptyList()
        val q = Normalize.key(word)
        if (q.length > MAX_WORD) return emptyList()
        fzM = q.length
        for (j in 0 until fzM) qBuf[j] = q[j]
        for (j in 0..fzM) rows[0][j] = j.toFloat()
        fzMax = maxDistance.toFloat()
        fzK = k
        fzNear = nearKeys
        val heap = PriorityQueue<ScoredWord>(k + 1, Comparator { a, b -> a.score.compareTo(b.score) })
        walk(0, 0, heap)
        fzNear = NO_NEAR
        return heap.sortedByDescending { it.score }
    }

    private fun walk(node: Int, depth: Int, heap: PriorityQueue<ScoredWord>) {
        val row = rows[depth]
        if (depth > 0 && freqAt(node) > 0 && row[fzM] <= fzMax) {
            offer(heap, node, ln(freqAt(node) + 1.0) - 2.0 * row[fzM])
        }
        if (depth >= MAX_WORD) return
        val nd = depth + 1
        val first = firstChildAt(node)
        val prev = rows[depth]
        val cur = rows[nd]
        for (c in first until first + childCountAt(node)) {
            val ch = charAt(c)
            // First letter must be typed right or be a neighbouring key: prunes most of the trie at the root.
            if (depth == 0 && fzM > 0 && ch != qBuf[0] && !fzNear.near(ch, qBuf[0])) continue
            pBuf[nd] = ch
            cur[0] = nd.toFloat()
            var rowMin = cur[0]
            for (j in 1..fzM) {
                val qj = qBuf[j - 1]
                var v = minOf(prev[j] + 1f, cur[j - 1] + 1f)
                val sub = if (ch == qj) 0f else if (fzNear.near(ch, qj)) 0.5f else 1f
                v = minOf(v, prev[j - 1] + sub)
                if (nd > 1 && j > 1 && ch == qBuf[j - 2] && pBuf[nd - 1] == qj) {
                    v = minOf(v, rows[nd - 2][j - 2] + 1f) // transposition
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin <= fzMax) walk(c, nd, heap)
        }
    }

    private fun offer(heap: PriorityQueue<ScoredWord>, node: Int, score: Double) {
        if (heap.size < fzK) {
            heap.add(ScoredWord(readString(displayAt(node)), score))
        } else if (score > heap.peek().score) {
            heap.poll()
            heap.add(ScoredWord(readString(displayAt(node)), score))
        }
    }

    /** Up to [k] most likely next words after [word]. */
    fun next(word: String, k: Int): List<String> {
        if (k <= 0) return emptyList()
        val n = find(Normalize.key(word))
        if (n < 0) return emptyList()
        val off = bigramAt(n)
        if (off < 0) return emptyList()
        val count = buf.get(off).toInt() and 0xFF
        val out = ArrayList<String>(minOf(count, k))
        var p = off + 1
        for (i in 0 until count) {
            val len = buf.getShort(p).toInt() and 0xFFFF
            if (out.size < k) out.add(readString(p))
            p += 2 + len
        }
        return out
    }

    // ---- node access -------------------------------------------------------------------------

    private fun rec(i: Int): Int = HEADER + i * RECORD
    private fun charAt(i: Int): Char = buf.getChar(rec(i))
    private fun freqAt(i: Int): Int = buf.getInt(rec(i) + 2)
    private fun maxAt(i: Int): Int = buf.getInt(rec(i) + 6)
    private fun childCountAt(i: Int): Int = buf.getInt(rec(i) + 10)
    private fun firstChildAt(i: Int): Int = buf.getInt(rec(i) + 14)
    private fun displayAt(i: Int): Int = buf.getInt(rec(i) + 18)
    private fun bigramAt(i: Int): Int = buf.getInt(rec(i) + 22)

    private fun child(node: Int, c: Char): Int {
        var lo = firstChildAt(node)
        var hi = lo + childCountAt(node) - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val mc = charAt(mid)
            when {
                mc < c -> lo = mid + 1
                mc > c -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Node index for an already-normalized key, or -1. */
    private fun find(key: String): Int {
        var n = 0
        for (ch in key) {
            n = child(n, ch)
            if (n < 0) return -1
        }
        return n
    }

    private fun readString(off: Int): String {
        val len = buf.getShort(off).toInt() and 0xFFFF
        val bytes = ByteArray(len)
        for (i in 0 until len) bytes[i] = buf.get(off + 2 + i)
        return String(bytes, Charsets.UTF_8)
    }

    private class Item(val node: Int, val prio: Int, val isWord: Boolean)

    companion object {
        private const val MAGIC = 0x414E5431 // "ANT1"
        private const val HEADER = 16
        private const val RECORD = 26
        private const val MAX_WORD = 32
        private const val MAX_NEXT = 8

        /**
         * Build a dictionary file from a word list (one "word<TAB>count" or "word count" per line)
         * and optional bigrams ("word1 word2<TAB>count"). Written to a temp file then renamed.
         */
        fun build(words: Sequence<String>, bigrams: Sequence<String>, out: File) {
            val entries = HashMap<String, Entry>()
            for (line in words) {
                val (w, c) = parseLine(line) ?: continue
                if (!validWord(w)) continue
                val key = Normalize.key(w)
                if (key.length > MAX_WORD) continue
                val e = entries.getOrPut(key) { Entry() }
                e.total += c
                if (c > e.bestCount) {
                    e.bestCount = c
                    e.display = w
                }
            }

            val pairs = HashMap<String, HashMap<String, Entry>>()
            for (line in bigrams) {
                val (pair, c) = parseLine(line) ?: continue
                val sp = pair.indexOf(' ')
                if (sp <= 0) continue
                val w1 = pair.substring(0, sp)
                val w2 = pair.substring(sp + 1).trim()
                if (!validWord(w2)) continue
                val k1 = Normalize.key(w1)
                if (!entries.containsKey(k1)) continue
                val e = pairs.getOrPut(k1) { HashMap() }.getOrPut(Normalize.key(w2)) { Entry() }
                e.total += c
                if (e.display.isEmpty()) e.display = w2
            }

            val root = BNode(' ')
            for ((key, e) in entries) {
                var n = root
                for (ch in key) {
                    var child = n.kids.firstOrNull { it.c == ch }
                    if (child == null) {
                        child = BNode(ch)
                        n.kids.add(child)
                    }
                    n = child
                }
                n.freq = minOf(e.total, Int.MAX_VALUE.toLong()).toInt()
                n.display = e.display
                n.next = pairs[key]?.values?.sortedByDescending { it.total }?.take(MAX_NEXT)?.map { it.display }
            }

            // BFS: children of each node end up contiguous, in char order.
            val order = ArrayList<BNode>()
            order.add(root)
            var i = 0
            while (i < order.size) {
                val n = order[i++]
                n.kids.sortBy { it.c }
                n.first = order.size
                order.addAll(n.kids)
            }
            fillMax(root)

            // String/bigram blob, offsets relative to blob start for now.
            val blob = ByteArrayOutputStream()
            fun putStr(s: String): Int {
                val pos = blob.size()
                val b = s.toByteArray(Charsets.UTF_8)
                blob.write(b.size ushr 8)
                blob.write(b.size and 0xFF)
                blob.write(b)
                return pos
            }
            val displayRel = IntArray(order.size) { -1 }
            val nextRel = IntArray(order.size) { -1 }
            for ((idx, n) in order.withIndex()) {
                n.display?.let { displayRel[idx] = putStr(it) }
                n.next?.let { list ->
                    nextRel[idx] = blob.size()
                    blob.write(list.size)
                    for (s in list) putStr(s)
                }
            }
            val blobBytes = blob.toByteArray()
            val blobOff = HEADER + order.size * RECORD

            val total = blobOff + blobBytes.size + 4 // + CRC32 trailer
            val buf = ByteBuffer.allocate(total)
            buf.putInt(MAGIC).putInt(order.size).putInt(blobOff).putInt(total) // last = file size, for open() to check
            for ((idx, n) in order.withIndex()) {
                buf.putChar(n.c)
                    .putInt(n.freq)
                    .putInt(n.max)
                    .putInt(n.kids.size)
                    .putInt(n.first)
                    .putInt(if (displayRel[idx] >= 0) blobOff + displayRel[idx] else -1)
                    .putInt(if (nextRel[idx] >= 0) blobOff + nextRel[idx] else -1)
            }
            buf.put(blobBytes)
            buf.putInt(java.util.zip.CRC32().apply { update(buf.array(), 0, total - 4) }.value.toInt())

            val tmp = File(out.path + ".tmp")
            Files.write(tmp.toPath(), buf.array())
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        /** Maps [file]. Throws IOException if it is not a complete dictionary (empty, cut short, old format). */
        fun open(file: File): Trie {
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { ch ->
                val size = ch.size()
                if (size < HEADER + 4 || size > Int.MAX_VALUE) throw java.io.IOException("bad dictionary size $size: ${file.path}")
                val map = ch.map(FileChannel.MapMode.READ_ONLY, 0, size)
                val nodes = map.getInt(4)
                val ok = map.getInt(0) == MAGIC && nodes >= 0 && map.getInt(12).toLong() == size &&
                    map.getInt(8).toLong() == HEADER + nodes.toLong() * RECORD && map.getInt(8) <= size
                if (!ok) throw java.io.IOException("damaged dictionary: ${file.path}")
                // Any flipped byte anywhere (bad sector, partial copy) → rebuild instead of reading garbage offsets.
                val crc = java.util.zip.CRC32().apply { update(map.duplicate().limit(size.toInt() - 4) as java.nio.ByteBuffer) }
                if (crc.value.toInt() != map.getInt(size.toInt() - 4)) throw java.io.IOException("dictionary checksum mismatch: ${file.path}")
                return Trie(map)
            }
        }

        private fun fillMax(n: BNode): Int {
            var m = n.freq
            for (c in n.kids) m = maxOf(m, fillMax(c))
            n.max = m
            return m
        }
    }
}

data class ScoredWord(val word: String, val score: Double)

/** Primitive-parameter callback: a plain lambda would box both chars on every edit-distance cell. */
fun interface NearKeys { fun near(a: Char, b: Char): Boolean }

private val NO_NEAR = NearKeys { _, _ -> false }

private class Entry {
    var total = 0L
    var bestCount = -1L
    var display = ""
}

private class BNode(val c: Char) {
    var freq = 0
    var max = 0
    var first = 0
    var display: String? = null
    var next: List<String>? = null
    val kids = ArrayList<BNode>(2)
}

private fun validWord(w: String): Boolean = w.length in 1..32 && w.any { it.isLetter() }

/** "word<TAB>count" or "word count" (last space). Count is at least 1. */
private fun parseLine(line: String): Pair<String, Long>? {
    val tab = line.indexOf('\t')
    val sep = if (tab >= 0) tab else line.lastIndexOf(' ')
    if (sep <= 0) return null
    val w = line.substring(0, sep).trim()
    val c = line.substring(sep + 1).trim().toLongOrNull() ?: return null
    return w to maxOf(c, 1L)
}
