package dev.altayeb.hudhud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Emoji picker shown in place of the keys. Tabs on top (recents first), grid below, ABC + delete at the bottom. */
class EmojiPanel(context: Context, private val panelHeight: Int) : View(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onEmojiBack()
        fun onEmojiDelete()
        fun onEmojiSearch()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val tabH = 44 * density
    private val bottomH = 48 * density
    private val rowH = 48 * density
    private val sideW = 80 * density
    private val slop = 8 * density

    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** Tab 0 = recents (snapshot, refreshed on open and on tab tap); tabs 1..8 = file categories. */
    private val icons: Array<String>
    private val grid: Array<Array<String>>
    private val toneable: Set<String>
    private val recentsView = ArrayList<String>()

    private var selectedTab = 1
    private var scrollY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var scrolling = false

    private val barPaint = Paint().apply { color = 0xFF1B1B1C.toInt() }
    private val linePaint = Paint().apply { color = 0xFF3A3C40.toInt(); strokeWidth = density }
    private val underlinePaint = Paint().apply { color = CYAN }
    private val tabIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20 * density; textAlign = Paint.Align.CENTER }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26 * density; textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN; textSize = 16 * density; textAlign = Paint.Align.CENTER }
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CYAN; style = Paint.Style.STROKE; strokeWidth = 2 * density; strokeCap = Paint.Cap.ROUND
    }
    private val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN; textSize = 20 * density; textAlign = Paint.Align.CENTER }

    /** Colours from the keyboard theme. */
    fun setTheme(t: KeyTheme) {
        setBackgroundColor(t.bg)
        barPaint.color = t.panel
        linePaint.color = t.card
        underlinePaint.color = t.accent
        labelPaint.color = t.accent
        deletePaint.color = t.accent
        searchPaint.color = t.accent
        invalidate()
    }

    init {
        setBackgroundColor(0xFF000000.toInt())
        // Line format: <icon>TAB<emoji> <emoji> ...
        val rows = context.assets.open("emoji.txt").bufferedReader().use { r ->
            r.lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        }
        icons = (listOf(RECENT_ICON) + rows.map { it[0] }).toTypedArray()
        // Newer emoji than this phone's font would show as empty boxes.
        grid = rows.map { row -> row[1].split(' ').filter { it.isNotEmpty() && emojiPaint.hasGlyph(it) }.toTypedArray() }.toTypedArray()
        toneable = context.assets.open("emoji-tones.txt").bufferedReader().use { it.readText() }.split(' ').toHashSet()
    }

    /** Call before showing: recents tab if there are any, else tab 1. */
    fun open() {
        loadRecents()
        tone = prefs.getInt("emojiTone", 0).coerceIn(0, 5)
        selectedTab = if (recentsView.isEmpty()) 1 else 0
        scrollY = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), panelHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tabW = w / TAB_COUNT
        val cellW = w / COLS
        val gridBottom = h - bottomH
        val count = itemCount(selectedTab)

        // Tab bar
        canvas.drawRect(0f, 0f, w, tabH, barPaint)
        for (t in 0 until TAB_COUNT) {
            canvas.drawText(icons[t], tabW * (t + 0.5f), baselineFor(tabH / 2, tabIconPaint), tabIconPaint)
        }
        canvas.drawRect(selectedTab * tabW, tabH - 2 * density, (selectedTab + 1) * tabW, tabH, underlinePaint)
        canvas.drawLine(0f, tabH, w, tabH, linePaint)

        // Grid: only visible rows, clipped to the grid area
        canvas.save()
        canvas.clipRect(0f, tabH, w, gridBottom)
        val rows = (count + COLS - 1) / COLS
        val first = (scrollY / rowH).toInt()
        val last = minOf(rows - 1, ((scrollY + gridBottom - tabH) / rowH).toInt())
        for (r in first..last) {
            val baseline = baselineFor(tabH + r * rowH - scrollY + rowH / 2, emojiPaint)
            for (c in 0 until COLS) {
                val i = r * COLS + c
                if (i >= count) break
                canvas.drawText(toned(itemAt(selectedTab, i)), c * cellW + cellW / 2, baseline, emojiPaint)
            }
        }
        canvas.restore()

        // Bottom bar: ABC | ⌫
        canvas.drawLine(0f, gridBottom, w, gridBottom, linePaint)
        canvas.drawRect(0f, gridBottom, w, h, barPaint)
        val mid = gridBottom + bottomH / 2
        canvas.drawText("ABC", sideW / 2, baselineFor(mid, labelPaint), labelPaint)
        canvas.drawText("⌫", w - sideW / 2, baselineFor(mid, deletePaint), deletePaint)
        val s = 7 * density // magnifier, drawn so it takes the theme colour
        canvas.drawCircle(w / 2 - s / 3, mid - s / 3, s, searchPaint)
        canvas.drawLine(w / 2 + s * 0.4f, mid + s * 0.4f, w / 2 + s * 1.2f, mid + s * 1.2f, searchPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                scrolling = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scrolling) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx * dx + dy * dy < slop * slop) return true
                    scrolling = true
                    lastY = downY
                }
                scrollY = (scrollY - (event.y - lastY)).coerceIn(0f, maxScroll())
                lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (!scrolling) tap(event.x, event.y)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val w = width.toFloat()
        val gridBottom = height - bottomH
        when {
            y < tabH -> {
                selectedTab = (x / (w / TAB_COUNT)).toInt().coerceIn(0, TAB_COUNT - 1)
                if (selectedTab == 0) loadRecents()
                scrollY = 0f
                invalidate()
            }
            y >= gridBottom -> when {
                x < sideW -> listener?.onEmojiBack()
                x > w - sideW -> listener?.onEmojiDelete()
                else -> listener?.onEmojiSearch()
            }
            else -> {
                val col = (x / (w / COLS)).toInt()
                val idx = ((y - tabH + scrollY) / rowH).toInt() * COLS + col
                if (col < COLS && idx < itemCount(selectedTab)) pick(toned(itemAt(selectedTab, idx)))
            }
        }
    }

    /** False in password fields / incognito: picks are typed but not remembered. */
    var keepRecents = true

    private fun pick(emoji: String) {
        listener?.onEmoji(emoji)
        if (keepRecents) remember(emoji)
    }

    private fun maxScroll(): Float =
        maxOf(0f, ((itemCount(selectedTab) + COLS - 1) / COLS) * rowH - (height - tabH - bottomH))

    private fun itemCount(tab: Int): Int = if (tab == 0) recentsView.size else grid[tab - 1].size

    private fun itemAt(tab: Int, i: Int): String = if (tab == 0) recentsView[i] else grid[tab - 1][i]

    /** 0 = default yellow, 1..5 = light to dark. Read when the panel opens. */
    private var tone = 0

    /** [emoji] in the chosen skin tone, when Unicode defines that form. */
    fun toned(emoji: String): String {
        if (tone == 0 || emoji !in toneable) return emoji
        val first = emoji.offsetByCodePoints(0, 1)
        return emoji.substring(0, first) + String(Character.toChars(0x1F3FA + tone)) + emoji.substring(first).replaceFirst("\uFE0F", "")
    }

    /** Vertical text baseline that centres the glyph on [cy]. */
    private fun baselineFor(cy: Float, p: Paint): Float = cy - (p.ascent() + p.descent()) / 2

    private fun loadRecents() {
        recentsView.clear()
        prefs.getString(KEY, "").orEmpty().split(' ').filterTo(recentsView) { it.isNotEmpty() }
    }

    /** Newest first, no duplicates, capped at MAX_RECENT. Visible recents grid is not reordered until re-opened. */
    fun remember(emoji: String) {
        val old = prefs.getString(KEY, "").orEmpty().split(' ').filter { it.isNotEmpty() && it != emoji }
        prefs.edit().putString(KEY, (listOf(emoji) + old).take(MAX_RECENT).joinToString(" ")).apply()
    }

    private companion object {
        const val COLS = 8
        const val TAB_COUNT = 9
        const val MAX_RECENT = 32
        const val KEY = "emoji_recent"
        const val RECENT_ICON = "🕘"
        const val CYAN = 0xFF33B5E5.toInt()
    }
}
