package dev.altayeb.anon

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * One finished glide, matched against dictionary words letter by letter. Coordinates are in key units
 * (1 = one key across, one row down) so the numbers below hold on any screen and for any key height.
 *
 * A word fits when its letters come near the path in order, it starts at the start and ends at the end,
 * and the straight line through its keys is about as long as what the finger drew.
 * Letters with no key on this layout (apostrophes, accents) are skipped at no cost.
 */
class Glide(path: FloatArray, centers: Map<Char, List<FloatArray>>) {

    private val chars = centers.keys.sorted().toCharArray()
    private val cx = FloatArray(chars.size) { centers.getValue(chars[it])[0][0] }
    private val cy = FloatArray(chars.size) { centers.getValue(chars[it])[0][1] }
    private val n: Int
    private val dist: Array<FloatArray>   // dist[char][point]: to the closest key that types this char
    private val length: Float

    // One entry per depth, so the trie walk can branch without allocating.
    private val pos = IntArray(MAX_DEPTH + 2)
    private val cost = FloatArray(MAX_DEPTH + 2)
    private val letters = IntArray(MAX_DEPTH + 2)
    private val line = FloatArray(MAX_DEPTH + 2)
    private val last = IntArray(MAX_DEPTH + 2)

    init {
        val pts = resample(path, POINTS)
        n = pts.size / 2
        var total = 0f
        for (i in 1 until n) total += hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
        length = total
        dist = Array(chars.size) { c ->
            val spots = centers.getValue(chars[c])
            FloatArray(n) { i -> spots.minOf { hypot(it[0] - pts[2 * i], it[1] - pts[2 * i + 1]) } }
        }
        reset()
    }

    fun reset() {
        pos[0] = 0; cost[0] = 0f; letters[0] = 0; line[0] = 0f; last[0] = -1
    }

    /** Extends the word at [depth] by [ch]. False = no word through this letter can fit. */
    fun push(depth: Int, ch: Char): Boolean {
        val d = depth + 1
        val c = java.util.Arrays.binarySearch(chars, ch)
        if (c < 0) { // no key for it: same state one level deeper
            pos[d] = pos[depth]; cost[d] = cost[depth]; letters[d] = letters[depth]
            line[d] = line[depth]; last[d] = last[depth]
            return true
        }
        val row = dist[c]
        var j = pos[depth]
        if (letters[depth] == 0) {
            if (row[0] > END_RADIUS) return false // has to start where the finger came down
        } else {
            while (j < n && row[j] > RADIUS) j++
            if (j == n) return false
        }
        while (j + 1 < n && row[j + 1] <= row[j]) j++ // slide to where the path is closest to this key
        val prev = last[depth]
        val seg = if (prev < 0) 0f else hypot(cx[c] - cx[prev], cy[c] - cy[prev])
        val total = line[depth] + seg
        if (total > length * 1.5f + 1.5f) return false // the word needs far more travel than was drawn
        pos[d] = j; cost[d] = cost[depth] + row[j]; letters[d] = letters[depth] + 1
        line[d] = total; last[d] = c
        return true
    }

    /** The word ending at [depth] has to finish where the finger lifted. */
    fun fits(depth: Int): Boolean = letters[depth] > 0 && last[depth] >= 0 && dist[last[depth]][n - 1] <= END_RADIUS

    /** Shape score of the word at [depth], higher is better; the caller adds its frequency. */
    fun shape(depth: Int): Double {
        val avg = cost[depth] / letters[depth]
        val ratio = ln((line[depth] + 1.0) / (length + 1.0))
        return -NEAR_WEIGHT * avg * avg - LENGTH_WEIGHT * abs(ratio)
    }

    /** Whole-word version of [push] for words outside the trie (learned ones). Null = does not fit. */
    fun shapeOf(key: String): Double? {
        if (key.length > MAX_DEPTH) return null
        reset()
        for (i in key.indices) if (!push(i, key[i])) return null
        return if (fits(key.length)) shape(key.length) else null
    }

    companion object {
        const val MAX_DEPTH = 32
        const val FREQ_WEIGHT = 0.55
        private const val POINTS = 64
        private const val RADIUS = 1.05f
        private const val END_RADIUS = 0.75f // fingers start and lift on purpose, so these are held tighter
        private const val NEAR_WEIGHT = 9.0
        private const val LENGTH_WEIGHT = 5.0

        private fun hypot(x: Float, y: Float) = sqrt(x * x + y * y)

        /** [count] points equally spaced along the path, so finger speed does not matter. */
        fun resample(path: FloatArray, count: Int): FloatArray {
            val m = path.size / 2
            if (m < 2) return path.copyOf()
            var total = 0f
            for (i in 1 until m) total += hypot(path[2 * i] - path[2 * i - 2], path[2 * i + 1] - path[2 * i - 1])
            if (total <= 0f) return floatArrayOf(path[0], path[1])
            val step = total / (count - 1)
            val out = FloatArray(count * 2)
            out[0] = path[0]; out[1] = path[1]
            var done = 1
            var walked = 0f
            for (i in 1 until m) {
                val dx = path[2 * i] - path[2 * i - 2]
                val dy = path[2 * i + 1] - path[2 * i - 1]
                val seg = hypot(dx, dy)
                while (seg > 0f && done < count - 1 && step * done <= walked + seg) {
                    val t = (step * done - walked) / seg
                    out[2 * done] = path[2 * i - 2] + dx * t
                    out[2 * done + 1] = path[2 * i - 1] + dy * t
                    done++
                }
                walked += seg
            }
            while (done < count) { out[2 * done] = path[2 * m - 2]; out[2 * done + 1] = path[2 * m - 1]; done++ }
            return out
        }
    }
}
