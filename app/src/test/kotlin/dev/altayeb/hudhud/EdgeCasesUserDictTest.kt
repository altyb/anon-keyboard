package dev.altayeb.hudhud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EdgeCasesUserDictTest {
    @get:Rule val tmp = TemporaryFolder()

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

    @Test fun loadMissingFileThenUse() {
        val d = dict("missing.bin")
        d.load()
        d.learn("hello", null)
        assertEquals(1, d.count("hello"))
    }

    @Test fun loadEmptyFileThenUse() {
        val f = File(tmp.root, "empty.bin").apply { writeBytes(ByteArray(0)) }
        val d = UserDict(f)
        d.load()
        d.learn("hello", null)
        assertEquals(1, d.count("hello"))
    }

    @Test fun loadRandomBytesThenUse() {
        val f = File(tmp.root, "random.bin").apply { writeBytes(ByteArray(300) { (it * 13 + 5).toByte() }) }
        val d = UserDict(f)
        d.load()
        assertEquals(0, d.size())
        d.learn("hello", null)
        assertEquals(1, d.count("hello"))
    }

    @Test fun loadTruncatedWithValidMagicThenUse() {
        // Right magic + version, claims 3 words, but the data ends after the first key.
        val f = File(tmp.root, "trunc.bin")
        DataOutputStream(FileOutputStream(f)).use {
            it.writeInt(0x414E5544) // "ANUD"
            it.writeInt(1)
            it.writeInt(3)
            it.writeUTF("abc")
        }
        val d = UserDict(f)
        d.load()
        assertEquals(0, d.size())
        d.learn("hello", null)
        assertEquals(1, d.count("hello"))
    }

    @Test fun learnIgnoresEmptyLongDigitAndEmojiWords() {
        val d = dict()
        d.learn("", null)
        d.learn("   ", null)
        d.learn("a".repeat(1000), "b".repeat(1000))
        d.learn("abc123", null)
        d.learn("😀😀", null)
        assertEquals(0, d.size())
    }

    @Test fun learnEmojiWordWithLettersAndInvalidPrev() {
        val d = dict()
        d.learn("hi😀", "😀😀")
        assertEquals(1, d.count("hi😀"))
        assertEquals(0, d.pairCount())
    }

    @Test fun learnedCapsHoldAt6000Words() {
        val d = dict()
        for (i in 0 until 6_000) d.learn(w(i), w(i + 1))
        assertTrue("words ${d.size()}", d.size() <= UserDict.MAX_WORDS)
        assertTrue("pairs ${d.pairCount()}", d.pairCount() <= UserDict.MAX_PAIRS)
    }

    @Test fun saveLoadKeepsCounts() {
        val d = dict()
        repeat(3) { d.learn("Hello", null) }
        d.learn("world", "hello")
        d.save()

        val e = dict()
        e.load()
        assertEquals(3, e.count("hello"))
        assertEquals(listOf("world"), e.next("hello", 3))
    }

    @Test fun saveToReadOnlyDirDoesNotCorruptState() {
        val dir = File(tmp.root, "ro").apply { mkdirs(); setWritable(false) }
        try {
            val d = UserDict(File(dir, "d.bin"))
            d.learn("hello", null)
            try {
                d.save()
            } catch (e: IOException) {
                // expected when not running as root
            }
            assertEquals(1, d.count("hello"))
        } finally {
            dir.setWritable(true)
        }
    }
}
