package dev.altayeb.anon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipHistoryTest {

    private var now = 0L

    private fun history(cap: Int = 30, ttl: Long = 1_000) = ClipHistory(cap, ttl) { now }

    @Test fun capAppliesToUnpinnedOnly() {
        val c = history()
        for (i in 0 until 35) c.add("t$i", false)
        val items = c.items()
        assertEquals(30, items.size)
        assertEquals("t34", items.first().text)
        assertEquals("t5", items.last().text)
    }

    @Test fun ttlExpiresUnpinned() {
        val c = history(ttl = 1_000)
        c.add("old", false)
        now = 1_000
        assertEquals(1, c.items().size)
        now = 1_001
        assertTrue(c.items().isEmpty())
    }

    @Test fun pinnedSurvivesCapAndTtl() {
        val c = history(ttl = 1_000)
        c.add("keep", false)
        c.pin(c.items().first().id, true)
        for (i in 0 until 40) c.add("t$i", false)
        assertEquals(31, c.items().size)
        assertTrue(c.items().any { it.text == "keep" })
        now = 10_000
        assertEquals(listOf("keep"), c.items().map { it.text })
    }

    @Test fun sensitiveAndBlankIgnored() {
        val c = history()
        c.add("hunter2", true)
        c.add("   ", false)
        assertTrue(c.items().isEmpty())
    }

    @Test fun duplicateOfNewestIgnored() {
        val c = history()
        c.add("x", false)
        c.add("x", false)
        assertEquals(1, c.items().size)
        c.add("y", false)
        c.add("x", false)
        assertEquals(3, c.items().size)
    }

    @Test fun textCutTo2000Chars() {
        val c = history()
        c.add("a".repeat(2_500), false)
        assertEquals(2_000, c.items().first().text.length)
    }

    @Test fun pinAndRemoveById() {
        val c = history()
        c.add("a", false)
        c.add("b", false)
        val (b, a) = c.items()
        c.pin(a.id, true)
        assertTrue(c.items().last().pinned)
        c.pin(a.id, false)
        assertTrue(!c.items().last().pinned)
        c.remove(b.id)
        c.remove(999L)
        c.pin(999L, true)
        assertEquals(listOf("a"), c.items().map { it.text })
    }

    @Test fun clearUnpinnedKeepsPins() {
        val c = history()
        c.add("a", false); c.add("b", false)
        c.pin(c.items().first { it.text == "a" }.id, true)
        c.clearUnpinned()
        assertEquals(listOf("a"), c.items().map { it.text })
    }

    @Test fun freshOnlyWhenRecent() {
        val c = history(ttl = 100_000)
        c.add("x", false)
        now = 30_000
        assertEquals("x", c.fresh(60_000)?.text)
        now = 61_000
        assertEquals(null, c.fresh(60_000))
    }

    @Test fun pinnedSurviveSaveLoadWithNewlines() {
        val f = java.io.File.createTempFile("clips", ".txt").also { it.deleteOnExit() }
        val c = history()
        c.add("line1\r\nline2 \\ end", false); c.add("not pinned", false)
        c.pin(c.items().first { it.text.startsWith("line1") }.id, true)
        c.savePinned(f)
        val d = history().also { it.loadPinned(f) }
        assertEquals(listOf("line1\r\nline2 \\ end"), d.items().map { it.text })
        assertTrue(d.items().all { it.pinned })
    }
}
