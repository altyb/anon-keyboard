package dev.altayeb.anon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Offline emoji lookup by English or Arabic name. Lines: emoji TAB english words TAB arabic words. */
class EmojiIndex(lines: List<String>) {
    private class Entry(val emoji: String, val words: List<String>)

    private val entries = lines.mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2) null
        else Entry(parts[0], parts.drop(1).joinToString(" ").split(' ').filter { it.isNotEmpty() }.map(Normalize::key))
    }

    /** Every word of [query] must start a name word; exact name words rank first, file order breaks ties. */
    fun search(query: String, limit: Int = 24): List<String> {
        val q = query.trim().split(' ').filter { it.isNotEmpty() }.map(Normalize::key)
        if (q.isEmpty()) return emptyList()
        return entries.filter { e -> q.all { t -> e.words.any { it.startsWith(t) } } }
            .sortedByDescending { e -> q.count { it in e.words } }
            .take(limit)
            .map { it.emoji }
    }
}

/** Replaces the suggestion strip during an emoji search: what was typed, and the emoji it found. */
class EmojiSearchBar(context: Context) : View(context) {

    interface Listener {
        fun onSearchPick(emoji: String)
        fun onSearchClose()
    }

    var listener: Listener? = null
    var query = ""
        private set
    var results: List<String> = emptyList()
        private set

    private val density = resources.displayMetrics.density
    private val lineH = 44 * density
    private val cell = 44 * density
    private val closeW = 44 * density

    private val queryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16 * density }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16 * density }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26 * density; textAlign = Paint.Align.CENTER }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18 * density; textAlign = Paint.Align.CENTER }
    private val hint = context.getString(R.string.emoji_search_hint)

    fun setTheme(t: KeyTheme) {
        setBackgroundColor(t.panel)
        queryPaint.color = t.label
        hintPaint.color = t.hint
        closePaint.color = t.accent
    }

    fun show(q: String, found: List<String>) {
        query = q
        results = found
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (lineH * 2).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val y = lineH / 2 - (queryPaint.ascent() + queryPaint.descent()) / 2
        val pad = 12 * density
        if (query.isEmpty()) canvas.drawText(hint, pad, y, hintPaint) else canvas.drawText(query, pad, y, queryPaint)
        canvas.drawText("✕", width - closeW / 2, lineH / 2 - (closePaint.ascent() + closePaint.descent()) / 2, closePaint)
        val base = lineH * 1.5f - (emojiPaint.ascent() + emojiPaint.descent()) / 2
        val fit = (width / cell).toInt()
        for (i in 0 until minOf(fit, results.size)) canvas.drawText(results[i], cell * (i + 0.5f), base, emojiPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        if (e.y < lineH) {
            if (e.x > width - closeW) listener?.onSearchClose()
        } else {
            results.getOrNull((e.x / cell).toInt())?.let { listener?.onSearchPick(it) }
        }
        return true
    }
}
