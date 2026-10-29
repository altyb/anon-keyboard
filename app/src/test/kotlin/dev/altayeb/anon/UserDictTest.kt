package dev.altayeb.anon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserDictTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dict(name: String = "d.bin") = UserDict(File(tmp.root, name))

    /** Distinct lowercase word for index [i]: "q" + base-26 letters. */
    private fun w(i: Int): String {
        var n = i
        val sb = StringBuilder("q")
        do {
            sb.append('a' + n % 26)
            n /= 26
        } while (n > 0)
        return sb.toString()
    }

    @Test fun wordsAndPairsStayBounded() {
        val d = dict()
        for (i in 0 until 20_000) {
            d.learn(w(i), w(i + 1))
            assertTrue(d.size() <= UserDict.MAX_WORDS)
            assertTrue(d.pairCount() <= UserDict.MAX_PAIRS)
        }
    }

    @Test fun unusedWordFadesAway() {
        val d = dict()
        d.learn("zebra", null)
        for (i in 0 until 4_000) {
            if (i % 100 == 0) d.learn("keep", null)
            d.learn(w(i), null)
        }
        assertEquals(0, d.count("zebra"))
        assertTrue(d.complete("zeb", 5).isEmpty())
        assertTrue(d.count("keep") > 0)
    }

    @Test fun saveLoadRoundTrip() {
        val f = File(tmp.root, "d.bin")
        val d = UserDict(f)
        d.learn("Hello", null)
        d.learn("hello", null)
        d.learn("world", "hello")
        d.learn("world", "hello")
        d.save()

        val e = UserDict(f)
        e.load()
        assertEquals(2, e.count("hello"))
        assertEquals(2, e.count("world"))
        assertEquals(listOf("world"), e.next("hello", 5))
        assertEquals(listOf("hello"), e.complete("he", 5))
    }

    @Test fun missingOrGarbageFileLoadsEmpty() {
        val missing = dict("nope.bin")
        missing.load()
        assertEquals(0, missing.size())

        val f = File(tmp.root, "garbage.bin")
        f.writeBytes(ByteArray(100) { (it * 7).toByte() })
        val d = UserDict(f)
        d.load()
        assertEquals(0, d.size())
        assertTrue(d.complete("a", 5).isEmpty())
    }

    @Test fun truncatedFileLoadsEmpty() {
        val f = File(tmp.root, "d.bin")
        val d = UserDict(f)
        d.learn("alpha", "beta")
        d.learn("gamma", null)
        d.save()
        f.writeBytes(f.readBytes().copyOf(20))

        val e = UserDict(f)
        e.load()
        assertEquals(0, e.size())
    }

    @Test fun contactsBoostAndCap() {
        val d = dict()
        d.setContacts(listOf("Ahmed Ali"))
        assertEquals(20, d.count("ahmed"))
        assertEquals(20, d.count("Ali"))
        assertEquals(listOf("Ahmed"), d.complete("ahm", 5))

        d.learn("ahmad", null)
        assertEquals(listOf("Ahmed", "ahmad"), d.complete("ahm", 5))

        d.setContacts((0 until 3_000).map { w(it) })
        assertEquals(UserDict.MAX_CONTACTS, d.contactCount())
        assertEquals(0, d.count("ahmed"))
    }

    @Test fun completeBestFirst() {
        val d = dict()
        repeat(3) { d.learn("apple", null) }
        repeat(5) { d.learn("apricot", null) }
        d.learn("application", null)
        d.learn("banana", null)
        assertEquals(listOf("apricot", "apple", "application"), d.complete("ap", 3))
        assertEquals(listOf("apricot", "apple"), d.complete("ap", 2))
        assertEquals(listOf("apricot"), d.complete("apr", 5))
    }

    @Test fun nextBestFirst() {
        val d = dict()
        repeat(3) { d.learn("you", "ana") }
        repeat(2) { d.learn("me", "ana") }
        d.learn("tea", "ana")
        assertEquals(listOf("you", "me"), d.next("ana", 2))
        assertEquals(listOf("you", "me", "tea"), d.next("ana", 10))
        assertTrue(d.next("nobody", 3).isEmpty())
    }

    @Test fun forgetRemovesWordAndPairs() {
        val d = dict()
        d.learn("hello", "world")
        d.learn("world", "hello")
        d.forget("hello")
        assertEquals(0, d.count("hello"))
        assertTrue(d.next("world", 5).isEmpty())
        assertTrue(d.next("hello", 5).isEmpty())
        assertEquals(1, d.count("world"))
    }

    @Test fun arabicCompletion() {
        val d = dict()
        d.learn("أحمد", null)
        assertEquals(listOf("أحمد"), d.complete("احم", 5))
        assertEquals(listOf("أحمد"), d.complete("أحم", 5))
    }

    @Test fun rejectsInvalidWords() {
        val d = dict()
        d.learn("a", null)
        d.learn("abc1", null)
        d.learn("1234", null)
        d.learn("x".repeat(33), null)
        assertEquals(0, d.size())
    }

    @Test fun displayKeepsCasedSpellingUnlessLowercaseArrives() {
        val d = dict()
        d.learn("Hello", null)
        d.learn("hello", null)
        d.learn("HELLO", null)
        assertEquals(listOf("hello"), d.complete("he", 5))
    }
}
