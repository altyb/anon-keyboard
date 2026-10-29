package dev.altayeb.anon

import java.io.File

data class Clip(val id: Long, val text: String, val time: Long, val pinned: Boolean)

/** Clipboard history in RAM. Max [cap] items, unpinned ones expire after [ttlMs]. Main thread only. */
class ClipHistory(
    private val cap: Int = 30,
    private val ttlMs: Long = 3_600_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val list = ArrayList<Clip>() // newest first
    private var nextId = 1L

    /** Add a copied text. Ignored if [sensitive], blank, or same as newest. Text cut to 2 000 chars. */
    fun add(text: String, sensitive: Boolean) {
        if (sensitive || text.isBlank()) return
        val t = text.take(MAX_CHARS)
        if (list.firstOrNull()?.text == t) return
        list.add(0, Clip(nextId++, t, clock(), false))
        var unpinned = list.count { !it.pinned }
        val iter = list.listIterator(list.size)
        while (unpinned > cap && iter.hasPrevious()) {
            if (!iter.previous().pinned) {
                iter.remove()
                unpinned--
            }
        }
    }

    /** Newest first, expired removed. */
    fun items(): List<Clip> {
        val now = clock()
        list.removeAll { !it.pinned && now - it.time > ttlMs }
        return list.toList()
    }

    fun pin(id: Long, pinned: Boolean) {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(pinned = pinned)
    }

    fun remove(id: Long) {
        list.removeAll { it.id == id }
    }

    /** Remove everything, pinned too. */
    fun clearAll() {
        list.clear()
    }

    /** Remove every unpinned clip. */
    fun clearUnpinned() {
        list.removeAll { !it.pinned }
    }

    /** Newest clip if it was copied less than [maxAgeMs] ago, else null (for the paste chip in the strip). */
    fun fresh(maxAgeMs: Long): Clip? = list.firstOrNull()?.takeIf { clock() - it.time <= maxAgeMs }

    /** Pinned clips survive restarts; unpinned ones stay in RAM only. One clip per line, newlines escaped. */
    fun savePinned(file: File) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(list.filter { it.pinned }.joinToString("\n") { it.text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") })
        if (!tmp.renameTo(file)) throw java.io.IOException("could not replace ${file.path}")
    }

    fun loadPinned(file: File) {
        if (!file.exists()) return
        for (line in file.readLines().take(MAX_PINNED).asReversed()) {
            if (line.isEmpty()) continue
            val text = Regex("\\\\(.)").replace(line) { when (it.groupValues[1]) { "n" -> "\n"; "r" -> "\r"; else -> it.groupValues[1] } }
            if (list.none { it.text == text }) list.add(0, Clip(nextId++, text, clock(), true))
        }
    }

    companion object {
        private const val MAX_CHARS = 2_000
        private const val MAX_PINNED = 50
    }
}
