package dev.altayeb.hudhud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class EdgeCasesTrieTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun build(words: List<String>, bigrams: List<String> = emptyList()): Trie {
        val f = tmp.newFile()
        Trie.build(words.asSequence(), bigrams.asSequence(), f)
        return Trie.open(f)
    }

    private fun validFile(): File {
        val f = tmp.newFile()
        Trie.build(
            listOf("hello\t100", "help\t500", "world\t9", "i\t3", "am\t2").asSequence(),
            sequenceOf("i am\t4"), f,
        )
        return f
    }

    /** Open must reject with a clean exception, or return a Trie whose queries do not throw. */
    private fun assertRejectedOrUsable(f: File) {
        val t = try {
            Trie.open(f)
        } catch (e: IOException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        t.complete("", 100)
        t.complete("a", 3)
        t.frequency("a")
        t.fuzzy("he", 2, 5) { _, _ -> false }
        t.next("a", 3)
    }

    @Test fun emptyBuildIsUsable() {
        val t = build(emptyList())
        assertTrue(t.complete("a", 3).isEmpty())
        assertTrue(t.frequency("a") < 0)
        assertTrue(t.fuzzy("a", 2, 3) { _, _ -> false }.isEmpty())
        assertTrue(t.next("a", 3).isEmpty())
    }

    @Test fun junkLinesAreSkippedValidOnesKept() {
        val junk = listOf(
            "", "hello", "\t\t", "\t5", "word\t", "word\tabc", "neg\t-5",
            "x".repeat(500) + "\t5", "😀😀\t5", "🙂 smile\t3",
        )
        val t = build(listOf("world\t10", "مرحبا\t20", "hello مرحبا\t7") + junk)
        assertEquals(10, t.frequency("world"))
        assertEquals(20, t.frequency("مرحبا"))
        assertEquals(7, t.frequency("hello مرحبا"))
        assertEquals(1, t.frequency("neg")) // negative count clamps to 1
        assertEquals(-1, t.frequency("word"))
    }

    @Test(timeout = 2000) fun garbageBytesRejectedOrUsable() {
        val f = File(tmp.root, "garbage.bin")
        f.writeBytes(ByteArray(4096) { (it * 31 + 7).toByte() })
        assertRejectedOrUsable(f)
    }

    @Test(timeout = 2000)
    fun emptyFileRejectedCleanly() {
        val f = File(tmp.root, "empty.bin").apply { writeBytes(ByteArray(0)) }
        assertRejectedOrUsable(f)
    }

    @Test(timeout = 2000)
    fun halfCutFileRejectedOrUsable() {
        val f = validFile()
        f.writeBytes(f.readBytes().copyOf(f.length().toInt() / 2))
        assertRejectedOrUsable(f)
    }

    @Test fun oddQueriesDoNotCrash() {
        val t = build(listOf("hello\t100", "help\t50"))
        for (q in listOf("", " ", "😀😀", "a".repeat(1000), "hel lo")) {
            t.complete(q, 3)
            t.fuzzy(q, 2, 3) { _, _ -> false }
        }
        assertTrue(t.complete("hel", 0).isEmpty())
        assertTrue(t.fuzzy("hel", 1, 0) { _, _ -> false }.isEmpty())
    }
}
