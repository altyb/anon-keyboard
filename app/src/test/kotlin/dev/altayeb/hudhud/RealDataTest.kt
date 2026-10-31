package dev.altayeb.hudhud

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Builds the shipped dictionaries the way the phone does and checks next-word prediction works. */
class RealDataTest {
    @get:Rule val tmp = TemporaryFolder()

    private val assets = File("src/main/assets")

    private fun engine(lang: String): Pair<Engine, Long> {
        val out = tmp.newFile()
        val t0 = System.currentTimeMillis()
        File(assets, "$lang.tsv").useLines { w ->
            File(assets, "$lang.bigrams").useLines { b -> Trie.build(w, b, out) }
        }
        val ms = System.currentTimeMillis() - t0
        return Engine(Trie.open(out), UserDict(File(tmp.root, "u-$lang")).also { it.load() }) to ms
    }

    @Test fun englishPredictsNextWord() {
        val (e, ms) = engine("en")
        val next = e.suggest("how ", false, true).words
        assertTrue("how → $next", next.any { it in setOf("to", "many", "much") })
        assertTrue("thank → ${e.suggest("thank ", false, true).words}", "you" in e.suggest("thank ", false, true).words)
        assertTrue("build took $ms ms", ms < 15_000)
    }

    @Test fun arabicPredictsNextWord() {
        val (e, _) = engine("ar")
        val next = e.suggest("شكرا ", false, true).words
        assertTrue("شكرا → $next", next.any { it in setOf("على", "لك", "جزيلا") })
    }
}
