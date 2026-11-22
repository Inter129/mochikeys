package dev.inter129.mochikeys.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import dev.inter129.mochikeys.layout.*
import dev.inter129.mochikeys.theme.ThemeTokens

/**
 * M0 임시 표면: 키 그리기, 터치다운 팝업, 터치업 입력, 롤오버(새 손가락이 닿으면 이전 키 즉시 입력),
 * 누른 채 밀어 키 변경, 백스페이스 반복. M3에서 core TouchEngine + 3겹 레이어로 교체한다.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val t: ThemeTokens,
    private val onKey: (KeyAction) -> Unit,
) : View(context) {
    private val dp = resources.displayMetrics.density
    var bottomInset = 0
    var shifted = false; set(v) { field = v; invalidate() }
    var capsLock = false; set(v) { field = v; invalidate() }
    var showMic = false; set(v) { if (field != v) { field = v; relayout(); invalidate() } }
    var actionReturn = false; set(v) { field = v; invalidate() }
    var returnLabel = "return"; set(v) { field = v; invalidate() }
    var spaceLabel = "空白"; set(v) { field = v; invalidate() }
    /** 리턴 키 아이콘("search"·"go"). null이면 글자 라벨. */
    var returnIcon: String? = null; set(v) { field = v; invalidate() }
    /** 입력 칸이 비어 있어 검색·이동 키를 누를 수 없는 상태(iOS처럼 회색으로 꺼진다). */
    var returnDisabled = false; set(v) { if (field != v) { field = v; invalidate() } }
    var jaComposing = false; set(v) { if (field != v) { field = v; invalidate() } }
    private var page = "abc"
    var lang = "en"; set(v) { field = v; relayout(); requestLayout(); invalidate() }
    private var rects: List<KeyRect> = emptyList()
    private var lastShiftTap = 0L

    /** 키 줄 아래 영역: iOS 실측 66pt(지구본 중심 아래로 40pt). 시스템 내비게이션 바가 크면 그만큼 더 확보한다. */
    private val bottomAreaPx get() = maxOf(t.bottomPadding * dp, 50f * dp + bottomInset)
    val keyboardHeightPx get() = ((t.topPadding + 4 * t.rowHeight) * dp + bottomAreaPx + barPx).toInt()
    val keyboardTop get() = height - keyboardHeightPx

    private val pointers = LinkedHashMap<Int, KeyRect>()
    val clicks = dev.inter129.mochikeys.feedback.ClickPlayer(context)

    // ── 길게 눌러 변형 문자: 키 위에 가로 목록이 뜨고, 밀어서 고른 뒤 떼면 입력 ──
    private var varPending = -1
    private var varPid = -1
    private var varKr: KeyRect? = null
    private var varItems: List<String> = emptyList()
    private var varSel = 0
    private var varLeft = 0f
    private val varLong = Runnable {
        val kr = pointers[varPending] ?: return@Runnable
        if (kr.key.style != KeyStyle.CHARACTER || kr.key.variants.isEmpty()) return@Runnable
        val up = shifted || capsLock
        varItems = (listOf(kr.key.label) + kr.key.variants).map { if (up) Layouts.shifted(it) else it }
        val cw = kr.visual.width + t.gapX * dp
        varLeft = (kr.visual.left - t.gapX * dp / 2).coerceAtMost(width - varItems.size * cw - 4 * dp).coerceAtLeast(4 * dp)
        varPid = varPending; varKr = kr; varSel = ((kr.visual.centerX - varLeft) / cw).toInt().coerceIn(0, varItems.lastIndex)
        // 누른 키 바로 위 칸이 원래 글자가 되도록 순서를 맞춘다
        varItems = varItems.toMutableList().also { it.add(varSel, it.removeAt(0)) }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); invalidate()
    }
    private fun endVariants() { removeCallbacks(varLong); varPending = -1; varPid = -1; varKr = null }
    private fun drawVariants(c: Canvas) {
        val kr = varKr ?: return; val v = kr.visual
        val cw = v.width + t.gapX * dp; val r = t.keyRadius * dp * 1.3f
        val top = v.top - v.height - 14 * dp; val bottom = v.top - 6 * dp
        calloutShadow.setShadowLayer(10 * dp, 0f, 3 * dp, 0x45000000); calloutShadow.color = t.charKey
        c.drawRoundRect(varLeft, top, varLeft + varItems.size * cw, bottom, r, r, calloutShadow)
        // 누른 키와 이어지는 기둥(아이폰의 말풍선 모양)
        keyPaint.color = t.charKey
        c.drawRoundRect(v.left, bottom - r, v.right, v.bottom, t.keyRadius * dp, t.keyRadius * dp, calloutShadow)
        c.drawRoundRect(v.left, bottom - r, v.right, v.bottom, t.keyRadius * dp, t.keyRadius * dp, keyPaint)
        c.drawRect(varLeft + r, bottom - r, varLeft + varItems.size * cw - r, bottom, keyPaint)
        varItems.forEachIndexed { i, s ->
            val l = varLeft + i * cw
            if (i == varSel) { keyPaint.color = t.accent; c.drawRoundRect(l + 3 * dp, top + 3 * dp, l + cw - 3 * dp, bottom - 3 * dp, r * 0.75f, r * 0.75f, keyPaint) }
            textPaint.color = if (i == varSel) 0xFFFFFFFF.toInt() else t.keyText
            textPaint.textSize = (if (isJamo(s)) jamoSize else t.charFontSize * dp) * 1.1f
            c.drawText(s, l + cw / 2, (top + bottom) / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
    }

    // ── 스페이스 길게 눌러 커서 트랙패드: 키 글자가 사라지고, 좌우로 밀면 커서가 움직인다 ──
    var onCursorMove: ((Int) -> Unit)? = null   // 0 = 시작 알림, ±1 = 한 칸
    private var trackpad = false
    private var trackPid = -1
    private var trackAccum = 0f; private var trackLastX = 0f
    private val spaceLong = Runnable {
        if (pointers[trackPid]?.key?.action != KeyAction.Space) return@Runnable
        trackpad = true; trackAccum = 0f; trackLastX = downPos[trackPid]?.get(2) ?: 0f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onCursorMove?.invoke(0); invalidate()
    }
    private var repeatTask: Runnable? = null

    // 지구본 길게 누르기: 언어 목록이 뜨고, 손가락을 밀어 고른 뒤 떼면 전환된다(iOS 방식)
    var onLanguagePicked: ((String) -> Unit)? = null
    var enabledLangs: List<String> = listOf("en", "ko", "ja")
    private val languages get() = listOf("en" to "English (US)", "ko" to "한국어", "ja" to "日本語かな").filter { it.first in enabledLangs }
    private var langMenu = false
    private var langSel = -1
    private var langEntered = false   // 누른 채 목록 안으로 들어갔는지
    private val globeLong = Runnable {
        langMenu = true; langEntered = false; langSel = languages.indexOfFirst { it.first == lang }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); invalidate()
    }
    private fun langMenuRect(): RectF {
        val g = rects.first { it.key.action == KeyAction.Globe }.visual
        val h = languages.size * 46f * dp + 16 * dp
        return RectF(8 * dp, g.top - 10 * dp - h, 8 * dp + 220 * dp, g.top - 10 * dp)
    }
    private fun drawLangMenu(c: Canvas) {
        val m = langMenuRect(); val r = 24 * dp
        calloutShadow.setShadowLayer(14 * dp, 0f, 4 * dp, 0x55000000); calloutShadow.color = t.charKey
        c.drawRoundRect(m, r, r, calloutShadow)
        languages.forEachIndexed { i, (code, name) ->
            val top = m.top + 8 * dp + i * 46f * dp
            val sel = i == langSel
            if (sel) { keyPaint.color = t.pressedKey; keyPaint.alpha = 110; c.drawRoundRect(m.left + 8 * dp, top + dp, m.right - 8 * dp, top + 45 * dp, 22 * dp, 22 * dp, keyPaint); keyPaint.alpha = 255 }
            textPaint.textSize = 17f * dp; textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = t.keyText
            val base = top + 23 * dp - (textPaint.ascent() + textPaint.descent()) / 2
            c.drawText(name, m.left + 24 * dp, base, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.keyShadow }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.keyText; textAlign = Paint.Align.CENTER }
    private val calloutShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint().apply { color = t.background }

    init { isHapticFeedbackEnabled = true }

    private val emoji = EmojiPanel(this, t, { onKey(KeyAction.Text(it)) }, { onKey(KeyAction.SwitchPage("abc")) }, { onKey(KeyAction.Backspace) })
    private val emojiMode get() = page == "emoji"

    // ── 이모지 검색: 패널 위 검색 칸을 누르면 글자판이 뜨고, 친 글자는 검색어로 들어간다 ──
    var emojiSearch = false; private set
    var onEmojiSearchToggle: ((Boolean) -> Unit)? = null
    var onEmojiPicked: ((String) -> Unit)? = null
    private var emojiQuery = ""
    private var emojiResults: List<String> = emptyList()
    private var resScroll = 0f
    private var sbDownX = -1f; private var sbDownY = 0f; private var sbMoved = false
    private val fieldH get() = 46f * dp
    private val resultH get() = 46f * dp
    fun setEmojiQuery(q: String) { emojiQuery = q; emojiResults = EmojiSearch.search(context, q); resScroll = 0f; invalidate() }
    fun enterEmojiSearch() {
        emojiSearch = true; emojiQuery = ""; emojiResults = emptyList()
        setPage("abc"); onEmojiSearchToggle?.invoke(true)
    }
    fun exitEmojiSearch() { if (!emojiSearch) return; emojiSearch = false; setPage("emoji"); onEmojiSearchToggle?.invoke(false) }
    private fun drawEmojiSearchBar(c: Canvas) {
        val top = keyboardTop + 9 * dp; val l = 10 * dp; val r = width - 10 * dp
        keyPaint.color = t.pressedKey; keyPaint.alpha = 90
        c.drawRoundRect(l, top, r, top + 36 * dp, 12 * dp, 12 * dp, keyPaint); keyPaint.alpha = 255
        Icons.drawNamed(c, "search", l + 20 * dp, top + 18 * dp, dp * 0.72f, t.hintText)
        textPaint.textAlign = Paint.Align.LEFT; textPaint.textSize = 17f * dp
        textPaint.color = if (emojiQuery.isEmpty()) t.hintText else t.keyText
        val shown = if (emojiQuery.isEmpty()) "Search Emoji" else emojiQuery
        c.drawText(shown, l + 38 * dp, top + 18 * dp - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        if (emojiSearch) { // 커서와 닫기(✕)
            val cx0 = l + 38 * dp + (if (emojiQuery.isEmpty()) 0f else textPaint.measureText(emojiQuery)) + 1.5f * dp
            keyPaint.color = t.accent; c.drawRect(cx0, top + 8 * dp, cx0 + 2 * dp, top + 28 * dp, keyPaint)
            chevPaint.color = t.hintText; chevPaint.strokeWidth = 2 * dp
            val x = r - 20 * dp; val y = top + 18 * dp; val s = 5 * dp
            c.drawLine(x - s, y - s, x + s, y + s, chevPaint); c.drawLine(x - s, y + s, x + s, y - s, chevPaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        if (!emojiSearch) return
        val ry = keyboardTop + fieldH
        c.save(); c.clipRect(0f, ry, width.toFloat(), ry + resultH)
        if (emojiResults.isEmpty() && emojiQuery.isNotEmpty()) {
            textPaint.color = t.hintText; textPaint.textSize = 15f * dp
            c.drawText("No Emoji Found", width / 2f, ry + resultH / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
        textPaint.color = 0xFF000000.toInt(); textPaint.textSize = 30f * dp
        emojiResults.forEachIndexed { i, e ->
            val cx = 30 * dp + i * 48 * dp - resScroll
            if (cx > -40 * dp && cx < width + 40 * dp) c.drawText(e, cx, ry + resultH / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
        c.restore()
    }
    private fun searchBarTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sbDownX = e.x; sbDownY = e.y; sbMoved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - sbDownX
                if (emojiSearch && sbDownY > keyboardTop + fieldH && (sbMoved || kotlin.math.abs(dx) > 8 * dp)) {
                    sbMoved = true
                    val max = (emojiResults.size * 48 * dp + 12 * dp - width).coerceAtLeast(0f)
                    resScroll = (resScroll - dx).coerceIn(0f, max); sbDownX = e.x; invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!sbMoved) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (!emojiSearch) enterEmojiSearch()
                    else if (e.y <= keyboardTop + fieldH) { if (e.x > width - 60 * dp) exitEmojiSearch() }
                    else emojiResults.getOrNull(((e.x + resScroll - 6 * dp) / (48 * dp)).toInt())?.let { onEmojiPicked?.invoke(it) }
                }
                sbDownX = -1f
            }
            MotionEvent.ACTION_CANCEL -> sbDownX = -1f
        }
        return true
    }
    private val rowsBottomPx get() = keysTop + (t.topPadding + 4 * t.rowHeight) * dp
    override fun computeScroll() { if (emojiMode) emoji.computeScroll() }

    fun setPage(p: String) {
        page = p; pointers.clear(); stopRepeat(); relayout(); requestLayout()
        if (emojiMode) { emoji.abcLabel = if (lang == "ko") "한글" else "ABC"; emoji.open(0f, keysTop + 6 * dp, width.toFloat()) }
        invalidate()
    }
    fun consumeDoubleTapShift(): Boolean {
        val now = SystemClock.uptimeMillis(); val dbl = now - lastShiftTap < 300; lastShiftTap = now; return dbl
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = relayout()

    // ── 후보 바(일본어): 키 줄 위 한 줄. 옆으로 밀어 넘기고 눌러서 확정 ──
    var onCandidate: ((Int) -> Unit)? = null
    private var cands: List<Pair<Int, String>> = emptyList()
    private var candSel = -1
    private var candScroll = 0f
    private var candDownX = -1f; private var candMoved = false
    private val barPx get() = if (emojiSearch) fieldH + resultH else if (emojiMode) fieldH else if (lang == "ja") 44f * dp else 0f
    private val keysTop get() = keyboardTop + barPx
    fun setCandidates(list: List<Pair<Int, String>>, selected: Int) {
        if (list != cands) { candScroll = 0f; gridScroll = 0f }
        if (list.isEmpty()) candExpanded = false
        cands = list; candSel = selected; invalidate()
    }
    private fun candWidths(): List<Float> { textPaint.textSize = 19f * dp; return cands.map { textPaint.measureText(it.second) + 26 * dp } }
    private fun drawCandidates(c: Canvas) {
        if (emojiSearch || emojiMode) { drawEmojiSearchBar(c); return }
        if (barPx == 0f) return
        val top = keyboardTop + 8 * dp; val h = barPx - 4 * dp
        val ws = candWidths(); var x = 6 * dp - candScroll
        if (cands.isNotEmpty()) { // 오른쪽 끝: 구분선 + 펼치기(∨)/접기(∧)
            val cx = width - chevW / 2; val cy = top + h / 2; val d = if (candExpanded) -1 else 1
            chevPaint.color = t.hintText; chevPaint.strokeWidth = dp
            c.drawLine(width - chevW, top + 8 * dp, width - chevW, top + h - 8 * dp, chevPaint)
            chevPaint.color = t.keyText; chevPaint.strokeWidth = 2 * dp
            c.drawLine(cx - 7 * dp, cy - 3.5f * dp * d, cx, cy + 3.5f * dp * d, chevPaint); c.drawLine(cx, cy + 3.5f * dp * d, cx + 7 * dp, cy - 3.5f * dp * d, chevPaint)
        }
        if (candExpanded) return
        c.save(); c.clipRect(0f, top, width - chevW, top + h)
        cands.forEachIndexed { i, (_, text) ->
            val w = ws[i]
            if (x + w > 0 && x < width) {
                if (i == candSel) { keyPaint.color = t.charKey; c.drawRoundRect(x + 2 * dp, top + 3 * dp, x + w - 2 * dp, top + h - 3 * dp, 9 * dp, 9 * dp, keyPaint) }
                textPaint.color = t.keyText; textPaint.textSize = 19f * dp
                c.drawText(text, x + w / 2, top + h / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            x += w
        }
        c.restore()
    }
    // 펼친 후보 격자: 키 자리를 덮고, 후보를 줄바꿈하며 채운다. 위아래로 밀어 넘긴다.
    private val chevW get() = 46f * dp
    private val chevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var candExpanded = false
    private var gridScroll = 0f
    private var gridDownY = -1f; private var gridMoved = false
    private fun gridCells(): List<RectF> {
        val ws = candWidths(); val rowH = 46f * dp; val out = ArrayList<RectF>()
        var x = 6 * dp; var y = keysTop + 6 * dp
        for (w0 in ws) {
            val w = w0.coerceAtLeast(width / 5f - 4 * dp).coerceAtMost(width - 12 * dp)
            if (x + w > width - 6 * dp && x > 6 * dp) { x = 6 * dp; y += rowH }
            out += RectF(x, y, x + w, y + rowH); x += w
        }
        return out
    }
    private fun drawGrid(c: Canvas) {
        val cells = gridCells()
        c.save(); c.clipRect(0f, keysTop, width.toFloat(), rowsBottomPx)
        cells.forEachIndexed { i, r ->
            val top = r.top - gridScroll; if (top > rowsBottomPx || top + r.height() < keysTop) return@forEachIndexed
            if (i == candSel) { keyPaint.color = t.charKey; c.drawRoundRect(r.left + 2 * dp, top + 3 * dp, r.right - 2 * dp, top + r.height() - 3 * dp, 9 * dp, 9 * dp, keyPaint) }
            textPaint.color = t.keyText; textPaint.textSize = 19f * dp
            c.drawText(cands[i].second, r.centerX(), top + r.height() / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            chevPaint.color = t.hintText; chevPaint.strokeWidth = dp / 2
            c.drawLine(r.left, top + r.height(), r.right, top + r.height(), chevPaint)
        }
        c.restore()
    }
    private fun gridTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gridDownY = e.y; gridMoved = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - gridDownY
                if (gridMoved || kotlin.math.abs(dy) > 8 * dp) {
                    gridMoved = true
                    val max = ((gridCells().lastOrNull()?.bottom ?: 0f) - rowsBottomPx).coerceAtLeast(0f)
                    gridScroll = (gridScroll - dy).coerceIn(0f, max); gridDownY = e.y; invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!gridMoved) gridCells().indexOfFirst { e.x >= it.left && e.x < it.right && e.y + gridScroll >= it.top && e.y + gridScroll < it.bottom }.let { i ->
                    if (i >= 0) { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); candExpanded = false; onCandidate?.invoke(cands[i].first) }
                }
                gridDownY = -1f
            }
            MotionEvent.ACTION_CANCEL -> gridDownY = -1f
        }
        return true
    }

    private fun candTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { candDownX = e.x; candMoved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - candDownX
                if (candMoved || kotlin.math.abs(dx) > 8 * dp) {
                    candMoved = true
                    val max = (candWidths().sum() + 12 * dp - width).coerceAtLeast(0f)
                    candScroll = (candScroll - dx).coerceIn(0f, max); candDownX = e.x; invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!candMoved && e.x > width - chevW) { if (cands.isNotEmpty()) { candExpanded = !candExpanded; gridScroll = 0f; performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); invalidate() } }
                else if (!candMoved && !candExpanded) {
                    var x = 6 * dp - candScroll
                    candWidths().forEachIndexed { i, w -> if (e.x >= x && e.x < x + w) { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onCandidate?.invoke(cands[i].first) }; x += w }
                }
                candDownX = -1f
            }
            MotionEvent.ACTION_CANCEL -> candDownX = -1f
        }
        return true
    }

    // ── 플릭 ──
    private val downPos = HashMap<Int, FloatArray>()   // 포인터별 [시작x, 시작y, 현재x, 현재y]
    private val downTime = HashMap<Int, Long>()
    private fun flickDir(pid: Int, kr: KeyRect): Int {
        val p = downPos[pid] ?: return 0
        val n = (kr.key.action as KeyAction.Kana).codes.length
        val d = flickIndex(p[2] - p[0], p[3] - p[1], kr.visual.width * 0.33f)
        return if (d < n) d else 0
    }
    private fun fireKana(pid: Int, kr: KeyRect) {
        if (emojiSearch) { // 검색 중에는 변환 없이 가나를 바로 검색어에 넣는다
            val a = kr.key.action as KeyAction.Kana
            onKey(KeyAction.Text(a.labels.getOrNull(flickDir(pid, kr))?.ifEmpty { "゛゜" } ?: return)); return
        }
        fireKanaCode(pid, kr)
    }
    private fun fireKanaCode(pid: Int, kr: KeyRect) = onKey(KeyAction.Code((kr.key.action as KeyAction.Kana).codes[flickDir(pid, kr)].code))
    private fun drawFlickGuide(c: Canvas, pid: Int, kr: KeyRect) {
        val a = kr.key.action as KeyAction.Kana; val v = kr.visual
        val dir = flickDir(pid, kr)
        val held = SystemClock.uptimeMillis() - (downTime[pid] ?: 0L) >= 300
        // iOS 방식: 눌린 키는 회색, 방향 칸은 키에 딱 붙은 흰 칸. 빠르게 밀면 고른 칸만,
        // 누르고 있으면 네 칸이 십자로 뜨고 고른 칸이 회색으로 바뀐다.
        val dirs = if (held) listOf(1, 2, 3, 4) else if (dir != 0) listOf(dir) else emptyList()
        val r = t.keyRadius * dp; val gx = t.gapX * dp; val gy = t.gapY * dp
        fun cell(d: Int): RectF {
            val ox = when (d) { 1 -> -(v.width + gx); 3 -> v.width + gx; else -> 0f }
            val oy = when (d) { 2 -> -(v.height + gy); 4 -> v.height + gy; else -> 0f }
            // 가운데 키 쪽으로 틈만큼 늘려 이어 붙인다
            return RectF(v.left + ox - (if (d == 3) gx else 0f), v.top + oy - (if (d == 4) gy else 0f),
                         v.right + ox + (if (d == 1) gx else 0f), v.bottom + oy + (if (d == 2) gy else 0f))
        }
        calloutShadow.setShadowLayer(8 * dp, 0f, 2 * dp, 0x38000000); calloutShadow.color = t.charKey
        for (d in dirs) if (a.labels.getOrNull(d)?.isNotEmpty() == true) c.drawRoundRect(cell(d), r, r, calloutShadow)
        for (d in dirs) {
            val label = a.labels.getOrNull(d) ?: continue
            if (label.isEmpty()) continue
            val rc = cell(d)
            keyPaint.color = if (held && d == dir) t.pressedKey else t.charKey
            c.drawRoundRect(rc, r, r, keyPaint)
            textPaint.color = t.keyText; textPaint.textSize = 25f * dp
            val cx = v.centerX + when (d) { 1 -> -(v.width + gx); 3 -> v.width + gx; else -> 0f }
            val cy = v.centerY + when (d) { 2 -> -(v.height + gy); 4 -> v.height + gy; else -> 0f }
            c.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
        if (dirs.isNotEmpty()) { // 가운데 키를 칸들 위에 다시 그린다
            keyPaint.color = if (dir == 0) t.pressedKey else t.charKey
            c.drawRect(v.left, v.top, v.right, v.bottom, keyPaint)
            textPaint.color = t.keyText; textPaint.textSize = 25f * dp
            c.drawText(a.labels[0].ifEmpty { kr.key.label }, v.centerX, v.centerY - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
    }


    private fun relayout() {
        if (width == 0) return
        val m = Metrics(width.toFloat(), t.rowHeight * dp, t.gapX * dp, t.gapY * dp, t.sidePadding * dp, t.topPadding * dp)
        val top = keysTop
        rects = KeyGeometry.layout(Layouts.byPage(page, lang), m).map {
            KeyRect(it.key, it.visual.offset(top), it.hit.offset(top))
        }
        // 지구본(언어 전환): iOS처럼 키 줄 아래 왼쪽 영역에 배경 없이 둔다
        val rowsBottom = top + (t.topPadding + 4 * t.rowHeight) * dp
        val gx = 42f * dp; val gy = rowsBottom + 26f * dp
        rects = rects + KeyRect(
            Key("🌐", KeyAction.Globe, style = KeyStyle.FUNCTION),
            Rect(gx - 14 * dp, gy - 14 * dp, gx + 14 * dp, gy + 14 * dp),
            Rect(0f, rowsBottom, 96f * dp, height.toFloat()),
        )
        // 마이크(음성 입력): 음성 키보드가 설치돼 있을 때만 오른쪽 아래에 둔다
        if (showMic) {
            val mx = width - 42f * dp
            rects = rects + KeyRect(
                Key("🎤", KeyAction.Mic, style = KeyStyle.FUNCTION),
                Rect(mx - 14 * dp, gy - 14 * dp, mx + 14 * dp, gy + 14 * dp),
                Rect(width - 96f * dp, rowsBottom, width.toFloat(), height.toFloat()),
            )
        }
    }

    private fun Rect.offset(dy: Float) = Rect(left, top + dy, right, bottom + dy)

    override fun onDraw(c: Canvas) {
        val top = keyboardTop.toFloat()
        val pr = t.panelRadius * dp
        c.drawRoundRect(0f, top, width.toFloat(), height + pr, pr, pr, bgPaint)
        if (emojiMode) emoji.draw(c)
        val r = t.keyRadius * dp
        val flickActive = pointers.values.any { it.key.style == KeyStyle.FLICK }
        for (kr in rects) {
            if (candExpanded && kr.key.action != KeyAction.Globe) continue
            val pressed = pointers.values.any { it === kr }
            val v = kr.visual
            val isReturn = kr.key.action == KeyAction.Return
            keyPaint.color = when {
                kr.key.style == KeyStyle.CHARACTER -> t.charKey
                kr.key.style == KeyStyle.FLICK -> if (pressed) t.pressedKey else t.charKey
                pressed -> t.pressedKey
                isReturn && actionReturn && returnDisabled -> t.pressedKey
                isReturn && actionReturn -> t.accent
                else -> t.funcKey
            }
            textPaint.color = if (isReturn && actionReturn) 0xFFFFFFFF.toInt() else if (flickActive && !pressed) t.hintText else t.keyText
            if (kr.key.action == KeyAction.Globe || kr.key.action == KeyAction.Mic) {
                Icons.draw(c, kr.key.action, v.centerX, v.centerY, dp, if (pressed) t.pressedKey else t.keyText, false, false); continue
            }
            c.drawRoundRect(v.left, v.top + dp, v.right, v.bottom + dp, r, r, shadowPaint)
            c.drawRoundRect(v.left, v.top, v.right, v.bottom, r, r, keyPaint)
            if (trackpad) continue   // 트랙패드 중에는 글자·아이콘을 모두 숨긴다
            if (kr.key.action == KeyAction.NextChar || kr.key.action == KeyAction.Rewind) { // 조합 중이 아니면 흐리게
                Icons.drawNamed(c, if (kr.key.action == KeyAction.NextChar) "next" else "rewind", v.centerX, v.centerY, dp, if (jaComposing && !flickActive) t.keyText else t.hintText); continue
            }
            if (isReturn && returnIcon != null) {
                Icons.drawNamed(c, returnIcon!!, v.centerX, v.centerY, dp, if (!actionReturn) t.keyText else if (returnDisabled) t.hintText else 0xFFFFFFFF.toInt()); continue
            }
            if (kr.key.label == "゛゜小" && !jaComposing) { // 조합 전에는 얼굴 문자 키
                textPaint.textSize = 17f * dp; c.drawText("^_^", v.centerX, v.centerY + 5 * dp, textPaint); continue
            }
            if (Icons.draw(c, kr.key.action, v.centerX, v.centerY, dp, t.keyText, shifted || capsLock, capsLock)) continue
            val label = when {
                kr.key.style == KeyStyle.CHARACTER && (shifted || capsLock) -> Layouts.shifted(kr.key.label)
                isReturn -> returnLabel
                kr.key.action == KeyAction.Space && lang == "ja" -> spaceLabel
                else -> kr.key.label
            }
            textPaint.textSize = if (kr.key.style == KeyStyle.FLICK) (if (label.length > 1) 15f else 23f) * dp else if (lang == "ja" && kr.key.style != KeyStyle.CHARACTER) (if (label.length == 1) 24f else 15.5f) * dp else if (kr.key.style != KeyStyle.CHARACTER) t.funcFontSize * dp else if (isJamo(label)) jamoSize else t.charFontSize * dp
            if (label == "゛゜小") { // iOS처럼 위에 ゛゜, 아래에 小를 쌓아 그린다
                textPaint.textSize = 22f * dp
                c.drawText("゛", v.centerX - 4 * dp, v.centerY + 3 * dp, textPaint); c.drawText("゜", v.centerX + 13 * dp, v.centerY + 3 * dp, textPaint)
                textPaint.textSize = 13.5f * dp; c.drawText("小", v.centerX, v.centerY + 12 * dp, textPaint)
            } else if (!trackpad) c.drawText(label, v.centerX, v.centerY + baselineOffset(label), textPaint)
            if (label == "わ") { textPaint.textSize = 14f * dp; c.drawText("＿", v.centerX + 13 * dp, v.centerY + 9 * dp, textPaint) }
            if (kr.key.action == KeyAction.Space) { // 스페이스바 오른쪽 아래의 언어 표시
                textPaint.textSize = 11f * dp; textPaint.color = t.hintText; textPaint.textAlign = Paint.Align.RIGHT
                if (lang != "ja") c.drawText(if (lang == "ko") "한" else "EN", v.right - 10 * dp, v.bottom - 7 * dp, textPaint)
                textPaint.textAlign = Paint.Align.CENTER
            }
        }
        // 터치다운 팝업(문자 키): 키 위로 확장된 말풍선
        for ((pid, kr) in pointers) if (kr.key.style == KeyStyle.CHARACTER && pid != varPid) drawCallout(c, kr)
        if (varPid != -1) drawVariants(c)
        drawCandidates(c)
        if (candExpanded && !emojiSearch && !emojiMode) drawGrid(c)
        for ((pid, kr) in pointers) if (kr.key.style == KeyStyle.FLICK) drawFlickGuide(c, pid, kr)
        if (langMenu) drawLangMenu(c)
    }

    /**
     * iOS 실측: 소문자는 x-높이의 중앙, 대문자·숫자는 대문자 높이의 중앙이 키 중앙에 온다.
     * 한글 등은 글리프 경계 상자의 중앙을 맞춘다.
     */
    private val tb = android.graphics.Rect()
    private fun isJamo(s: String) = s.length == 1 && s[0] in 'ㄱ'..'ㅣ'
    /** 한글 자모 글자 크기: iOS 실측(자음 ㅂ 높이 10.3pt, 모음 ㅕ 높이 18.7pt)에 글리프 높이를 맞춘다. 글꼴마다 비율이 달라 둘의 평균을 쓴다. */
    private val jamoSize: Float by lazy {
        val p = Paint(textPaint).apply { textSize = 100f }
        fun h(ch: String): Float { p.getTextBounds(ch, 0, 1, tb); return tb.height().toFloat() }
        (10.33f * dp * 100f / h("ㅂ") + 18.67f * dp * 100f / h("ㅕ")) / 2
    }
    private fun baselineOffset(label: String): Float {
        if (label.isEmpty()) return 0f
        val ch = label[0]
        return when {
            ch.code < 0x250 && ch.isLowerCase() && label.length == 1 -> textPaint.textSize * 0.264f
            ch.code < 0x250 -> textPaint.textSize * 0.355f
            else -> { textPaint.getTextBounds(label, 0, label.length, tb); -(tb.top + tb.bottom) / 2f }
        }
    }

    private fun drawCallout(c: Canvas, kr: KeyRect) {
        val v = kr.visual
        val w = v.width * 1.5f; val h = v.height * 1.25f
        val cx = v.centerX.coerceIn(w / 2 + 2 * dp, width - w / 2 - 2 * dp)
        val bubble = RectF(cx - w / 2, v.top - h, cx + w / 2, v.top + 4 * dp)
        val r = t.keyRadius * dp * 1.3f
        val path = Path().apply {
            addRoundRect(bubble, r, r, Path.Direction.CW)
            addRoundRect(RectF(v.left, v.top - 2 * dp, v.right, v.bottom), t.keyRadius * dp, t.keyRadius * dp, Path.Direction.CW)
        }
        // 팝업 그림자: 아래로 살짝 번지는 옅은 테두리
        calloutShadow.setShadowLayer(6 * dp, 0f, 2 * dp, 0x40000000)
        calloutShadow.color = t.charKey
        c.drawPath(path, calloutShadow)
        keyPaint.color = t.charKey
        c.drawPath(path, keyPaint)
        textPaint.textSize = (if (isJamo(kr.key.label)) jamoSize else t.charFontSize * dp) * 1.45f
        val label = if (shifted || capsLock) Layouts.shifted(kr.key.label) else kr.key.label
        c.drawText(label, bubble.centerX(), bubble.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if ((emojiMode || emojiSearch) && pointers.isEmpty() && (sbDownX >= 0 || (e.actionMasked == MotionEvent.ACTION_DOWN && e.y < keysTop))) return searchBarTouch(e)
        if (emojiMode && (e.y < rowsBottomPx && pointers.isEmpty())) return emoji.onTouch(e)
        if (candExpanded && pointers.isEmpty() && (gridDownY >= 0 || (e.actionMasked == MotionEvent.ACTION_DOWN && e.y >= keysTop + 4 * dp && e.y < rowsBottomPx))) return gridTouch(e)
        if (barPx > 0 && !emojiSearch && !emojiMode && pointers.isEmpty() && (candDownX >= 0 || (e.actionMasked == MotionEvent.ACTION_DOWN && e.y < keysTop + 4 * dp))) return candTouch(e)
        if (langMenu && pointers.isEmpty()) { // 띄워 둔 언어 목록: 항목을 누르면 전환, 바깥을 누르면 닫힘
            val m = langMenuRect()
            val inside = e.x >= m.left && e.x <= m.right && e.y >= m.top && e.y <= m.bottom
            val i = if (inside) ((e.y - m.top - 8 * dp) / (46f * dp)).toInt().coerceIn(0, languages.lastIndex) else -1
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (inside && i != langSel) { langSel = i; invalidate() }
                MotionEvent.ACTION_UP -> { langMenu = false; if (inside) { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onLanguagePicked?.invoke(languages[i].first) }; invalidate() }
                MotionEvent.ACTION_CANCEL -> { langMenu = false; invalidate() }
            }
            return true
        }
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(e)
                // 롤오버: 이미 눌린 문자 키는 새 손가락이 닿는 순간 입력
                pointers.entries.toList().forEach { (pid, kr) ->
                    if (kr.key.style == KeyStyle.CHARACTER) { if (pid == varPid) { val s = varItems[varSel]; endVariants(); onKey(KeyAction.Text(s)) } else fire(kr); pointers.remove(pid) }
                    if (kr.key.style == KeyStyle.FLICK) { fireKana(pid, kr); pointers.remove(pid) }
                }
                val kr = HitTester.find(rects, e.getX(idx), e.getY(idx)) ?: return true
                pointers[id] = kr
                downPos[id] = floatArrayOf(e.getX(idx), e.getY(idx), e.getX(idx), e.getY(idx)); downTime[id] = SystemClock.uptimeMillis()
                if (kr.key.style == KeyStyle.FLICK) postInvalidateDelayed(310)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                clicks.play(when {
                    kr.key.action == KeyAction.Backspace -> dev.inter129.mochikeys.sound.ClickSynth.Kind.DELETE
                    kr.key.style == KeyStyle.CHARACTER || kr.key.style == KeyStyle.FLICK -> dev.inter129.mochikeys.sound.ClickSynth.Kind.KEY
                    else -> dev.inter129.mochikeys.sound.ClickSynth.Kind.MODIFIER
                })
                if (kr.key.action == KeyAction.Backspace) startRepeat(kr)
                if (kr.key.action == KeyAction.Globe) postDelayed(globeLong, t.longPressMs)
                if (varPid == -1) { removeCallbacks(varLong); if (kr.key.style == KeyStyle.CHARACTER && kr.key.variants.isNotEmpty()) { varPending = id; postDelayed(varLong, t.longPressMs) } }
                if (kr.key.action == KeyAction.Space && !trackpad) { trackPid = id; removeCallbacks(spaceLong); postDelayed(spaceLong, t.longPressMs) }
                if (kr.key.style != KeyStyle.CHARACTER && kr.key.action != KeyAction.Backspace && kr.key.style != KeyStyle.SPACE) {
                    // 기능 키는 터치다운에 반응(iOS: Shift·123은 누르는 즉시)
                    if (kr.key.action == KeyAction.Shift || kr.key.action is KeyAction.SwitchPage) { fire(kr); pointers[id] = kr }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (langMenu) {
                    val gi = pointers.entries.firstOrNull { it.value.key.action == KeyAction.Globe }?.key?.let { e.findPointerIndex(it) } ?: -1
                    if (gi >= 0) {
                        val m = langMenuRect(); val y = e.getY(gi)
                        val ns = if (y < m.top - 20 * dp || y > m.bottom + 4 * dp) (if (y > m.bottom) langSel else -1)
                                 else ((y - m.top - 8 * dp) / (46f * dp)).toInt().coerceIn(0, languages.lastIndex)
                        if (y >= m.top && y <= m.bottom) langEntered = true
                        if (ns != langSel) { langSel = ns; if (ns >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); invalidate() }
                    }
                }
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val cur = pointers[pid] ?: continue
                    downPos[pid]?.let { it[2] = e.getX(i); it[3] = e.getY(i) }
                    if (cur.key.style == KeyStyle.FLICK) { invalidate(); continue }
                    if (trackpad && pid == trackPid) {
                        trackAccum += e.getX(i) - trackLastX; trackLastX = e.getX(i)
                        val step = 9f * dp
                        while (trackAccum >= step) { trackAccum -= step; onCursorMove?.invoke(1) }
                        while (trackAccum <= -step) { trackAccum += step; onCursorMove?.invoke(-1) }
                        continue
                    }
                    if (cur.key.style != KeyStyle.CHARACTER) continue
                    if (pid == varPid) {
                        val cw = cur.visual.width + t.gapX * dp
                        val ns = ((e.getX(i) - varLeft) / cw).toInt().coerceIn(0, varItems.lastIndex)
                        if (ns != varSel) { varSel = ns; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); invalidate() }
                        continue
                    }
                    val kr = HitTester.find(rects, e.getX(i), e.getY(i)) ?: continue
                    if (kr !== cur && kr.key.style == KeyStyle.CHARACTER) {
                        pointers[pid] = kr; invalidate()
                        if (pid == varPending) { removeCallbacks(varLong); if (kr.key.variants.isNotEmpty()) postDelayed(varLong, t.longPressMs) }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointers.remove(id)?.let { kr ->
                    if (kr.key.action == KeyAction.Globe) {
                        removeCallbacks(globeLong)
                        if (langMenu) {
                            // 목록 안으로 밀고 들어가서 뗐으면 그 언어로 전환, 그냥 뗐으면 목록을 띄워 둔다(iOS와 같음)
                            if (langEntered) { langMenu = false; languages.getOrNull(langSel)?.let { onLanguagePicked?.invoke(it.first) } }
                            invalidate(); return true
                        }
                    }
                    if (id == varPid) { val s = varItems[varSel]; endVariants(); onKey(KeyAction.Text(s)); invalidate(); return true }
                    if (id == varPending) removeCallbacks(varLong)
                    if (id == trackPid) { removeCallbacks(spaceLong); trackPid = -1; if (trackpad) { trackpad = false; invalidate(); return true } }
                    val a = kr.key.action
                    if (a is KeyAction.Kana) { downPos[id]?.let { it[2] = e.getX(idx); it[3] = e.getY(idx) }; fireKana(id, kr); invalidate(); return true }
                    if (a != KeyAction.Shift && a !is KeyAction.SwitchPage && a != KeyAction.Backspace) fire(kr)
                    if (a == KeyAction.Backspace) stopRepeat()
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { endVariants(); removeCallbacks(spaceLong); trackpad = false; trackPid = -1; removeCallbacks(globeLong); langMenu = false; pointers.clear(); stopRepeat(); invalidate() }
        }
        return true
    }

    private fun fire(kr: KeyRect) { if (kr.key.action == KeyAction.Return && actionReturn && returnDisabled) return; onKey(kr.key.action) }

    private fun startRepeat(kr: KeyRect) {
        fire(kr)
        var interval = t.repeatIntervalMs
        val task = object : Runnable {
            override fun run() { fire(kr); interval = (interval * 0.92).toLong().coerceAtLeast(50); postDelayed(this, interval) }
        }
        repeatTask = task
        postDelayed(task, t.repeatStartMs)
    }

    private fun stopRepeat() { repeatTask?.let { removeCallbacks(it) }; repeatTask = null }
}
