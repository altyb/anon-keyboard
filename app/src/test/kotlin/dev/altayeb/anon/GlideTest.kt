package dev.altayeb.anon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

/** Draws fake fingers through the real layouts and asks the real dictionaries what was meant. */
class GlideTest {
    @get:Rule val tmp = TemporaryFolder()

    private val assets = File("src/main/assets")

    private fun engine(lang: String): Engine {
        val out = tmp.newFile()
        File(assets, "$lang.tsv").useLines { w -> File(assets, "$lang.bigrams").useLines { b -> Trie.build(w, b, out) } }
        return Engine(Trie.open(out), UserDict(File(tmp.root, "u-$lang")).also { it.load() })
    }

    /** Key centres in key units, rows centred the way KeyboardView centres them. */
    private fun centers(layout: Layout): Map<Char, List<FloatArray>> {
        val out = HashMap<Char, MutableList<FloatArray>>()
        val widest = layout.rows.maxOf { r -> r.sumOf { it.width.toDouble() } }.toFloat()
        layout.rows.forEachIndexed { y, row ->
            var x = (widest - row.sumOf { it.width.toDouble() }.toFloat()) / 2
            for (k in row) {
                if (k.code > 0 && Character.isLetter(k.code)) {
                    val c = Normalize.key(k.code.toChar().toString()).single()
                    out.getOrPut(c) { ArrayList() }.add(floatArrayOf(x + k.width / 2, y.toFloat()))
                }
                x += k.width
            }
        }
        return out
    }

    /** A finger: straight strokes between keys, wobbling by [noise] key units, sampled unevenly. */
    private fun draw(word: String, keys: Map<Char, List<FloatArray>>, noise: Float, r: Random): FloatArray? {
        val spots = Normalize.key(word).mapNotNull { keys[it]?.get(0) }
        if (spots.size < 2) return null
        val out = ArrayList<Float>()
        val aim = spots.map { floatArrayOf(it[0] + r.nextGaussian().toFloat() * noise, it[1] + r.nextGaussian().toFloat() * noise) }
        for (i in 1 until aim.size) {
            val steps = 3 + r.nextInt(6)
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                out.add(aim[i - 1][0] + (aim[i][0] - aim[i - 1][0]) * t + r.nextGaussian().toFloat() * noise / 3)
                out.add(aim[i - 1][1] + (aim[i][1] - aim[i - 1][1]) * t + r.nextGaussian().toFloat() * noise / 3)
            }
        }
        out.add(aim.last()[0]); out.add(aim.last()[1])
        return out.toFloatArray()
    }

    private fun accuracy(lang: String, noise: Float): Triple<Double, Double, Long> {
        val e = engine(lang)
        val layout = Layouts.get(lang, false)
        val keys = centers(layout)
        val r = Random(7)
        val words = File(assets, "$lang.tsv").useLines { l ->
            l.map { it.split('\t', ' ')[0] }.filter { it.length in 3..12 && it.all(Char::isLetter) }.take(1000).toList()
        }
        var top1 = 0; var top3 = 0; var total = 0; var worst = 0L
        for (w in words) {
            val path = draw(w, keys, noise, r) ?: continue
            val t0 = System.nanoTime()
            val got = e.glide(Glide(path, keys), "").map(Normalize::key)
            worst = maxOf(worst, (System.nanoTime() - t0) / 1_000_000)
            total++
            val want = Normalize.key(w)
            if (got.firstOrNull() == want) top1++
            if (want in got) top3++
        }
        return Triple(top1.toDouble() / total, top3.toDouble() / total, worst)
    }

    @Test fun englishCommonWords() {
        val (t1, t3, ms) = accuracy("en", 0.22f)
        println("GLIDE en top1=%.3f top3=%.3f worst=%dms".format(t1, t3, ms))
        assertTrue("top1 $t1", t1 > 0.70)
        assertTrue("top3 $t3", t3 > 0.88)
    }

    @Test fun arabicCommonWords() {
        val (t1, t3, ms) = accuracy("ar", 0.22f)
        println("GLIDE ar top1=%.3f top3=%.3f worst=%dms".format(t1, t3, ms))
        assertTrue("top1 $t1", t1 > 0.65)
        assertTrue("top3 $t3", t3 > 0.85)
    }

    @Test fun cleanPathsFindTheWord() {
        val e = engine("en")
        val keys = centers(Layouts.get("en", false))
        for (w in listOf("hello", "people", "keyboard", "letter")) {
            val path = draw(w, keys, 0f, Random(1))!!
            assertTrue("$w → ${e.glide(Glide(path, keys), "")}", w in e.glide(Glide(path, keys), ""))
        }
    }

    @Test fun sameRowArabicWordsAreOffered() {
        val e = engine("ar")
        val keys = centers(Layouts.get("ar", false))
        for (w in listOf("سلام", "كتاب")) {
            val got = e.glide(Glide(draw(w, keys, 0f, Random(1))!!, keys), "").map(Normalize::key)
            println("GLIDE $w -> $got")
            assertTrue("$w → $got", Normalize.key(w) in got)
        }
    }

    @Test fun learnedWordCanBeGlided() {
        val out = tmp.newFile()
        File(assets, "en.tsv").useLines { w -> Trie.build(w, emptySequence(), out) }
        val user = UserDict(File(tmp.root, "u")).also { it.load() }
        repeat(3) { user.learn("altayeb", null) }
        val keys = centers(Layouts.get("en", false))
        val got = Engine(Trie.open(out), user).glide(Glide(draw("altayeb", keys, 0f, Random(1))!!, keys), "")
        assertTrue("$got", got.any { Normalize.key(it) == "altayeb" })
    }

    @Test fun contextPicksTheLikelyWord() {
        val e = engine("en")
        val keys = centers(Layouts.get("en", false))
        val path = draw("you", keys, 0f, Random(1))!!
        assertEquals("you", e.glide(Glide(path, keys), "thank").first())
    }

    @Test fun resampleKeepsEnds() {
        val p = Glide.resample(floatArrayOf(0f, 0f, 1f, 0f, 1f, 3f), 5)
        assertEquals(10, p.size)
        assertEquals(0f, p[0], 0f); assertEquals(1f, p[8], 0f); assertEquals(3f, p[9], 0.001f)
        assertEquals(2, Glide.resample(floatArrayOf(2f, 2f, 2f, 2f), 5).size) // finger never moved
    }
}

class EmojiSearchAndEditTest {
    private val index = EmojiIndex(java.io.File("src/main/assets/emoji-names.txt").readLines())

    @Test fun findsByEnglishAndArabic() {
        assertTrue("❤️" in index.search("heart") || "❤" in index.search("heart"))
        assertTrue("😂" in index.search("joy"))
        assertTrue("arabic → ${index.search("ضحك")}", index.search("ضحك").isNotEmpty())
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("zzzqqq").isEmpty())
    }

    @Test fun wordDeleteTakesSpacesThenWord() {
        assertEquals(5, wordDeleteLength("hello"))
        assertEquals(6, wordDeleteLength("one hello "))
        assertEquals(0, wordDeleteLength(""))
        assertEquals(2, wordDeleteLength("  "))
        assertEquals(5, wordDeleteLength("مرحبا ").let { it - 1 })
    }
}
