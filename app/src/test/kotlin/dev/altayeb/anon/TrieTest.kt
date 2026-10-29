package dev.altayeb.anon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrieTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun trie(words: List<String>, bigrams: List<String> = emptyList()): Trie {
        val f = tmp.newFile()
        Trie.build(words.asSequence(), bigrams.asSequence(), f)
        return Trie.open(f)
    }

    @Test fun completeOrder() {
        val t = trie(listOf("hello\t100", "help\t500", "helper\t50", "hell\t10", "world\t999"))
        assertEquals(listOf("help", "hello", "helper"), t.complete("hel", 3))
        assertEquals(listOf("help", "hello", "helper", "hell"), t.complete("hel", 10))
    }

    @Test fun prefixItselfCounts() {
        val t = trie(listOf("hello\t100", "helper\t50", "hell\t10"))
        assertEquals(listOf("hello", "hell"), t.complete("hell", 3))
    }

    @Test fun frequency() {
        val t = trie(listOf("hello\t100", "help\t500"))
        assertEquals(100, t.frequency("hello"))
        assertEquals(100, t.frequency("HELLO"))
        assertEquals(-1, t.frequency("hel"))
        assertEquals(-1, t.frequency("nope"))
    }

    @Test fun duplicateSpellingsSumAndDisplayIsMostFrequent() {
        val t = trie(listOf("Hello\t10", "hello\t30", "HELLO\t5"))
        assertEquals(45, t.frequency("hello"))
        assertEquals(listOf("hello"), t.complete("he", 1))
    }

    @Test fun buildSkipsBadLines() {
        val t = trie(listOf("123\t5", "don't\t7", "x".repeat(33) + "\t5", "ok\t3"))
        assertEquals(-1, t.frequency("123"))
        assertEquals(7, t.frequency("don't"))
        assertEquals(-1, t.frequency("x".repeat(33)))
    }

    @Test fun fuzzyHeloFindsHello() {
        val t = trie(listOf("hello\t100", "world\t900", "held\t20"))
        assertEquals(listOf("hello", "held"), t.fuzzy("helo", 1, 3) { _, _ -> false }.map { it.word })
    }

    @Test fun nearKeysSubstitutionRanksFirst() {
        val t = trie(listOf("cat\t10", "cqt\t10"))
        val near = NearKeys { a, b -> setOf(a, b) == setOf('w', 'a') }
        assertEquals(listOf("cat", "cqt"), t.fuzzy("cwt", 1, 2, near).map { it.word })
    }

    @Test fun arabicCompleteAndFuzzy() {
        val t = trie(listOf("أحمد\t500", "احمر\t10"))
        assertEquals(listOf("أحمد", "احمر"), t.complete("احم", 2))
        assertEquals("أحمد", t.fuzzy("احمذ", 1, 3) { _, _ -> false }.first().word)
        assertEquals(500, t.frequency("احمد"))
    }

    @Test fun nextUsesBigrams() {
        val t = trie(
            listOf("i\t100", "am\t10", "you\t50", "the\t80", "cat\t9"),
            listOf("i am\t40", "i you\t9", "the cat\t5"),
        )
        assertEquals(listOf("am", "you"), t.next("i", 2))
        assertEquals(listOf("am"), t.next("I", 1))
        assertEquals(emptyList<String>(), t.next("cat", 3))
        assertEquals(emptyList<String>(), t.next("zzz", 3))
    }

    @Test fun realListBuildOpenAndPerf() {
        val src = File("src/main/assets/en.tsv")
        val out = File(tmp.root, "en.bin")
        val t0 = System.nanoTime()
        src.useLines { Trie.build(it, emptySequence(), out) }
        val buildMs = (System.nanoTime() - t0) / 1_000_000
        println("PERF build_ms=$buildMs size_bytes=${out.length()}")
        assertTrue("build took $buildMs ms", buildMs < 5000) // one-time on first run; loose so a busy machine does not flake

        val t = Trie.open(out)
        val prefixes = listOf("th", "wh", "ab", "hel", "wor", "qu", "ex", "tr", "sh", "in")
        val typos = listOf("teh", "hte", "wrod", "recieve", "helo", "thsi", "adn", "exmaple", "tlephone", "becuase")
        fun call(i: Int) {
            val w = typos[i % typos.size]
            t.complete(prefixes[i % prefixes.size], 3)
            t.fuzzy(w, if (w.length <= 4) 1 else 2, 3) { _, _ -> false }
        }
        repeat(200) { call(it) }
        val calls = 1000
        val t1 = System.nanoTime()
        repeat(calls) { call(it) }
        val avgMs = (System.nanoTime() - t1) / 1_000_000.0 / calls
        println("PERF avg_ms_per_complete_plus_fuzzy=$avgMs")
        assertTrue("avg $avgMs ms", avgMs < 2.0)
    }
}
