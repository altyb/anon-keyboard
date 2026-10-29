package dev.altayeb.anon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val words = listOf(
        "the\t1000000", "they\t300000", "then\t200000", "hello\t50000", "help\t40000",
        "i\t100000", "am\t10000", "me\t5000",
    )

    private fun engine(): Pair<Engine, UserDict> {
        val f = tmp.newFile()
        Trie.build(words.asSequence(), sequenceOf("i am\t40"), f)
        val user = UserDict(File(tmp.root, "user.json"))
        user.load()
        val engine = Engine(Trie.open(f), user)
        val row = listOf('q', 'w', 'e', 'r', 't', 'y').map { Key(it.toString(), it.code) }
        val row2 = listOf('a', 's', 'd', 'f', 'g').map { Key(it.toString(), it.code) }
        engine.setLayout(Layout("en", listOf(row, row2), false))
        return engine to user
    }

    @Test fun autocorrectTehToThe() {
        val (e, _) = engine()
        val s = e.suggest("teh", true, false)
        assertEquals("the", s.words[0])
        assertTrue(s.autoCorrect)
        assertFalse(e.suggest("teh", false, false).autoCorrect)
    }

    @Test fun completionHel() {
        val (e, _) = engine()
        val s = e.suggest("hel", false, false)
        assertEquals(listOf("hello", "help"), s.words)
        assertFalse(s.autoCorrect)
    }

    @Test fun nextWordFromBigrams() {
        val (e, _) = engine()
        val s = e.suggest("hello i ", false, true)
        assertEquals(listOf("am"), s.words)
        assertFalse(s.autoCorrect)
        assertEquals(Suggestions.EMPTY, e.suggest("hello i ", false, false))
    }

    @Test fun learnedPairComesFirst() {
        val (e, user) = engine()
        user.learn("me", "the") // UserDict ignores 1-letter prev words, so use a longer one
        assertEquals(listOf("me"), e.suggest("the ", false, true).words)
    }

    @Test fun capitalization() {
        val (e, _) = engine()
        assertEquals("Hello", e.suggest("Hel", false, false).words[0])
        assertEquals("HELLO", e.suggest("HEL", false, false).words[0])
        assertEquals("hello", e.suggest("hel", false, false).words[0])
    }

    @Test fun emptyWhenNothingToSuggest() {
        val (e, _) = engine()
        assertEquals(Suggestions.EMPTY, e.suggest("", true, true))
        assertEquals(Suggestions.EMPTY, e.suggest("hello ", false, false))
        assertEquals(Suggestions.EMPTY, e.suggest("hello.", false, true))
    }

    @Test fun previousWordReranksCompletion() {
        // "they" is more common than "then", but "and then" is the known pair.
        val f = tmp.newFile()
        Trie.build(words.asSequence() + sequenceOf("and\t90000"), sequenceOf("and then\t50"), f)
        val e = Engine(Trie.open(f), UserDict(File(tmp.root, "u2")).also { it.load() })
        assertEquals("they", e.suggest("the", false, false).words.first { it != "the" })
        assertEquals("then", e.suggest("and the", false, false).words.first { it != "the" })
    }

    @Test fun learnedWordBeatsFuzzyGuess() {
        // "yey" is one typo from hey / yes / yet / key (real frequencies); a word typed twice must still show up.
        val f = tmp.newFile()
        Trie.build(sequenceOf("hey\t1149292", "yes\t1634050", "yet\t257947", "key\t160000", "yep\t300000"), emptySequence(), f)
        val user = UserDict(File(tmp.root, "u3")).also { it.load() }
        val e = Engine(Trie.open(f), user)
        assertTrue("yeyet" !in e.suggest("yey", false, false).words)
        user.learn("yeyet", null); user.learn("yeyet", null)
        assertTrue("yeyet" in e.suggest("yey", false, false).words)
    }
}
