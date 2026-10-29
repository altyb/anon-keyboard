package dev.altayeb.anon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EdgeCasesEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun engine(): Engine {
        val f = tmp.newFile()
        Trie.build(
            listOf("the\t1000", "hello\t500", "help\t400", "i\t100", "am\t10", "مرحبا\t50").asSequence(),
            sequenceOf("i am\t4"), f,
        )
        val user = UserDict(File(tmp.root, "user.bin")).also { it.load() }
        return Engine(Trie.open(f), user)
    }

    @Test fun degenerateInputsReturnSaneSuggestions() {
        val e = engine()
        val inputs = listOf(
            "", "   ", "!!!???", "a".repeat(64), "مَرْحَبًا", "hello مرحبا",
            "12345", "😀😀", "https://x.com/a?b=c",
        )
        for (s in inputs) {
            val r = e.suggest(s, true, true)
            assertTrue("input '$s' gave ${r.words.size} words", r.words.size <= 3)
        }
    }

    @Test fun digitInTypedWordDoesNotAutocorrect() {
        assertFalse(engine().suggest("h3llo", true, false).autoCorrect)
    }

    @Test
    fun digitInTypedWordDoesNotAutocorrectSuffix() {
        assertFalse(engine().suggest("h3ello", true, false).autoCorrect)
    }
}
