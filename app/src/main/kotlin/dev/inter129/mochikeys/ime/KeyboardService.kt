package dev.inter129.mochikeys.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.inter129.mochikeys.layout.KeyAction
import dev.inter129.mochikeys.theme.ThemeTokens
import dev.inter129.mochikeys.view.KeyboardView

/**
 * IME 윈도우는 화면 전체 높이로 두고, 실제 키보드 영역만 콘텐츠·터치 영역으로 신고한다.
 * 그래서 맨 윗줄 키 팝업이 키보드 위로 튀어나와도 잘리지 않는다.
 */
class KeyboardService : InputMethodService() {
    private lateinit var root: FrameLayout
    private lateinit var keyboard: KeyboardView
    private var shift = false
    private var capsLock = false
    private lateinit var prefs: dev.inter129.mochikeys.settings.Prefs
    private var builtRowHeight = 57
    private var lang = "en"
    private val hangul = dev.inter129.mochikeys.engine.HangulAutomaton()

    /** 조합 중 글자를 밑줄 없이 표시(iOS처럼). Spannable을 넘기면 기본 밑줄 스팬이 붙지 않는다. */
    private fun showComposing() {
        val ic = currentInputConnection ?: return
        if (hangul.isEmpty) ic.finishComposingText() else ic.setComposingText(android.text.SpannableString(hangul.composing), 1)
    }
    private fun flushHangul() { if (!hangul.isEmpty) { hangul.reset(); currentInputConnection?.finishComposingText() } }

    override fun onUpdateSelection(oss: Int, ose: Int, nss: Int, nse: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(oss, ose, nss, nse, cs, ce)
        // 사용자가 커서를 옮기면 조합을 끝낸다
        if (!hangul.isEmpty && (nss != nse || (ce != -1 && nse != ce))) flushHangul()
        if (::keyboard.isInitialized) updateReturnLabel()
    }
    override fun onFinishInputView(finishingInput: Boolean) { flushHangul(); flushJa(); super.onFinishInputView(finishingInput) }

    override fun onCreateInputView(): View {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        prefs = dev.inter129.mochikeys.settings.Prefs(this)
        builtRowHeight = prefs.rowHeight
        val tokens = (if (dark) ThemeTokens.dark else ThemeTokens.light).copy(rowHeight = builtRowHeight.toFloat())
        keyboard = KeyboardView(this, tokens) { onKey(it) }
        // 내비게이션 바 영역까지 키보드 배경색으로 채운다(검은 띠 방지). 버튼 색은 테마에 맞춘다.
        window?.window?.let { w ->
            w.navigationBarColor = tokens.background
            w.isNavigationBarContrastEnforced = false
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !dark
        }
        keyboard.onLanguagePicked = { setLanguage(it) }
        keyboard.onEmojiSearchToggle = { on ->
            if (on) { flushHangul(); flushJa() }
            emojiQuery = ""; emojiHangul.reset(); keyboard.setEmojiQuery(""); updateReturnLabel()
        }
        keyboard.onEmojiPicked = { currentInputConnection?.commitText(it, 1) }
        keyboard.onCursorMove = { d ->
            if (d == 0) { flushHangul(); flushJa() }
            else sendDownUpKeyEvents(if (d < 0) android.view.KeyEvent.KEYCODE_DPAD_LEFT else android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        keyboard.onCandidate = { id ->
            if (lang == "ja" && id < 0) { currentInputConnection?.commitText(kaomoji[-id - 1], 1); jaCands = emptyList(); keyboard.setCandidates(jaCands, -1); updateReturnLabel() }
            else if (lang == "ja") applyJa(ja().submitCandidate(id))
        }
        root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // target 35+: IME 윈도우도 edge-to-edge. 하단 시스템 바만큼 키보드를 올린다.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            // 제스처 내비게이션에서 시스템은 키보드 윈도우 맨 아래에 '키보드 내리기'·'키보드 전환' 버튼 띠를 그린다.
            // 이 띠는 기기·버전에 따라 내비게이션 바가 아니라 캡션 바나 탭 가능 영역 인셋으로 신고되므로,
            // 관련 인셋 가운데 가장 큰 값만큼 비워서 지구본·마이크와 겹치지 않게 한다.
            val parts = listOf(
                WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.Type.captionBar(),
                WindowInsetsCompat.Type.tappableElement(), WindowInsetsCompat.Type.mandatorySystemGestures(),
            ).map { insets.getInsets(it).bottom }
            keyboard.bottomInset = parts.max()
            android.util.Log.i("MochiKeys", "bottom insets nav/caption/tappable/gestures=$parts")
            keyboard.requestLayout()
            insets
        }
        return root
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onDestroy() { if (::keyboard.isInitialized) keyboard.clicks.release(); super.onDestroy() }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!::keyboard.isInitialized || keyboard.height == 0) {
            outInsets.contentTopInsets = 0; outInsets.visibleTopInsets = 0
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
            return
        }
        val top = keyboard.keyboardTop
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, keyboard.width, keyboard.height)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 설정 반영. 키 높이가 바뀌었으면 화면을 새로 만든다.
        if (prefs.rowHeight != builtRowHeight) { setInputView(onCreateInputView()); keyboard.lang = lang }
        keyboard.clicks.enabled = prefs.sound; keyboard.clicks.volume = prefs.soundVolume / 100f
        keyboard.isHapticFeedbackEnabled = prefs.haptic
        keyboard.enabledLangs = prefs.languages
        keyboard.showMic = voiceImeId() != null
        if (lang !in prefs.languages) { lang = prefs.languages.first(); keyboard.lang = lang }
        keyboard.setPage("abc")
        val act = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) 0 else info.imeOptions and EditorInfo.IME_MASK_ACTION
        baseAction = act > EditorInfo.IME_ACTION_NONE
        baseReturnLabel = when (act) {
            EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"; EditorInfo.IME_ACTION_SEND -> "send"
            EditorInfo.IME_ACTION_NEXT -> "next"; EditorInfo.IME_ACTION_DONE -> "done"; else -> "return"
        }
        baseReturnIcon = when (act) { EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"; else -> null }
        updateReturnLabel()
        updateAutoShift()
    }

    private fun setLanguage(l: String) {
        flushHangul(); flushJa()
        lang = l; keyboard.lang = l; keyboard.setPage("abc"); updateAutoShift(); updateReturnLabel()
    }

    // ── 일본어(Mozc) ──
    private var mozc: dev.inter129.mochikeys.mozc.MozcEngine? = null
    private var jaPreedit = ""
    private var jaCands: List<Pair<Int, String>> = emptyList()
    private var jaSel = -1
    private var baseReturnLabel = "return"
    private var baseAction = false
    private var baseReturnIcon: String? = null
    private val kaomoji = listOf("(^_^)", "(^^)", "(>_<)", "(T_T)", "(^o^)", "m(_ _)m", "(´・ω・`)", "(≧▽≦)", "(；´Д｀)", "＼(^o^)／", "(-_-)", "(*^^*)", "(^_-)☆", "(；ω；)", "orz")
    private fun ja(): dev.inter129.mochikeys.mozc.MozcEngine = mozc ?: dev.inter129.mochikeys.mozc.MozcEngine.open(this).also { mozc = it }
    // ── 이모지 검색어 입력 ──
    private val kanaMarks = setOf("゛", "゜", "小", "゛゜")
    private val kanaPlain = "かきくけこさしすせそたちつてとはひふへほう"
    private val kanaDaku = "がぎぐげござじずぜぞだぢづでどばびぶべぼゔ"
    private val kanaHan = mapOf('は' to 'ぱ', 'ひ' to 'ぴ', 'ふ' to 'ぷ', 'へ' to 'ぺ', 'ほ' to 'ぽ')
    private val kanaSmall = mapOf('あ' to 'ぁ', 'い' to 'ぃ', 'う' to 'ぅ', 'え' to 'ぇ', 'お' to 'ぉ', 'つ' to 'っ', 'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ', 'わ' to 'ゎ')
    /** 가나 한 글자에 탁점·반탁점·작은 글자를 적용한다. "゛゜"(키 탭)는 가능한 모양을 차례로 돈다. */
    private fun kanaMark(c: Char, mark: String): Char? {
        val base = kanaDaku.indexOf(c).takeIf { it >= 0 }?.let { kanaPlain[it] }
            ?: kanaHan.entries.firstOrNull { it.value == c }?.key ?: kanaSmall.entries.firstOrNull { it.value == c }?.key ?: c
        val daku = kanaPlain.indexOf(base).takeIf { it >= 0 }?.let { kanaDaku[it] }
        return when (mark) {
            "゛" -> daku; "゜" -> kanaHan[base]; "小" -> kanaSmall[base]
            else -> listOfNotNull(base, daku, kanaHan[base], kanaSmall[base]).let { v -> if (v.size < 2) null else v[(v.indexOf(c) + 1) % v.size] }
        }
    }
    private var emojiQuery = ""
    private val emojiHangul = dev.inter129.mochikeys.engine.HangulAutomaton()
    /** 검색 중에는 글자를 편집기가 아니라 검색어에 넣는다. 처리했으면 true. */
    private fun onKeySearch(action: KeyAction): Boolean {
        fun flush() { emojiQuery += emojiHangul.composing; emojiHangul.reset() }
        when (action) {
            is KeyAction.Text -> {
                val t = if (shift || capsLock) dev.inter129.mochikeys.layout.Layouts.shifted(action.text) else action.text
                if (t in kanaMarks) { // 濁点·半濁点·小: 마지막 가나를 바꾼다
                    val last = emojiQuery.lastOrNull() ?: return true
                    kanaMark(last, t)?.let { emojiQuery = emojiQuery.dropLast(1) + it }
                } else
                if (t.length == 1 && (t[0] in dev.inter129.mochikeys.engine.HangulAutomaton.CHO || t[0] in dev.inter129.mochikeys.engine.HangulAutomaton.JUNG)) emojiQuery += emojiHangul.input(t[0])
                else { flush(); emojiQuery += t }
                if (shift && !capsLock) { shift = false; keyboard.shifted = false }
            }
            KeyAction.Backspace -> if (!emojiHangul.backspace()) emojiQuery = emojiQuery.dropLast(1)
            KeyAction.Space -> { flush(); emojiQuery += " " }
            KeyAction.Return -> { keyboard.exitEmojiSearch(); return true }
            is KeyAction.SwitchPage -> { if (action.page == "emoji") { keyboard.exitEmojiSearch(); return true }; return false }
            is KeyAction.Code, KeyAction.NextChar, KeyAction.Rewind, KeyAction.Mic -> return true
            else -> return false
        }
        keyboard.setEmojiQuery(emojiQuery + emojiHangul.composing)
        return true
    }

    private fun updateReturnLabel() {
        if (keyboard.emojiSearch) { // 검색 중 리턴 키는 파란 ✓ (검색 닫기)
            keyboard.jaComposing = true; keyboard.spaceLabel = "空白"   // 검색 중에는 ゛゜小 키를 그대로 보여 준다
            keyboard.actionReturn = true; keyboard.returnDisabled = false; keyboard.returnIcon = "check"; return
        }
        val composing = lang == "ja" && jaPreedit.isNotEmpty()
        keyboard.returnLabel = if (lang != "ja") baseReturnLabel else if (composing) "確定" else if (baseAction) baseReturnLabel else "改行"
        keyboard.spaceLabel = if (composing) "次候補" else "空白"
        keyboard.actionReturn = baseAction && !composing   // 조합 중 確定은 iOS처럼 흰 키
        keyboard.jaComposing = composing
        keyboard.returnIcon = if (composing) null else baseReturnIcon
        val ic = currentInputConnection
        keyboard.returnDisabled = baseAction && !composing && hangul.isEmpty && ic != null &&
            ic.getTextBeforeCursor(1, 0).isNullOrEmpty() && ic.getTextAfterCursor(1, 0).isNullOrEmpty()
    }
    /** 엔진 상태를 편집기와 후보 바에 반영한다. 조합 중 글자는 밑줄 대신 옅은 배경으로 표시. */
    private fun applyJa(s: dev.inter129.mochikeys.mozc.MozcEngine.State) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (s.committed.isNotEmpty()) ic.commitText(s.committed, 1)
        jaPreedit = s.preedit
        if (jaPreedit.isEmpty()) ic.finishComposingText()
        else ic.setComposingText(android.text.SpannableString(jaPreedit).apply {
            setSpan(android.text.style.BackgroundColorSpan(0x33007AFF), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or android.text.Spanned.SPAN_COMPOSING)
        }, 1)
        ic.endBatchEdit()
        jaCands = if (jaPreedit.isEmpty()) emptyList() else s.candidates
        jaSel = -1
        keyboard.setCandidates(jaCands, jaSel); updateReturnLabel()
    }
    private fun flushJa() {
        if (jaPreedit.isEmpty()) return
        runCatching { applyJa(ja().command(org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand.CommandType.SUBMIT)) }
    }
    /** 일본어 모드의 키 처리. 처리했으면 true. */
    private fun onKeyJa(action: KeyAction): Boolean {
        val C = org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand.CommandType.SUBMIT.declaringJavaClass
        val composing = jaPreedit.isNotEmpty()
        when (action) {
            is KeyAction.Code -> if (!composing && action.code.toChar() in "*[`]") { // ^_^ 키: 얼굴 문자 후보
                jaCands = kaomoji.mapIndexed { i, s -> -(i + 1) to s }; jaSel = -1; keyboard.setCandidates(jaCands, -1)
            } else applyJa(ja().sendKeyCode(action.code))
            KeyAction.NextChar -> if (composing) applyJa(ja().command(java.lang.Enum.valueOf(C, "STOP_KEY_TOGGLING"))) else return true
            KeyAction.Rewind -> if (composing) applyJa(ja().command(java.lang.Enum.valueOf(C, "UNDO_OR_REWIND"))) else return true
            KeyAction.Backspace -> if (composing) applyJa(ja().sendSpecial(org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent.SpecialKey.BACKSPACE)) else return false
            KeyAction.Space -> if (composing && jaCands.isNotEmpty()) { // 次候補
                jaSel = (jaSel + 1) % jaCands.size; keyboard.setCandidates(jaCands, jaSel)
            } else if (composing) return true else { currentInputConnection?.commitText("　", 1) }
            KeyAction.Return -> if (composing) {
                if (jaSel >= 0) applyJa(ja().submitCandidate(jaCands[jaSel].first)) else applyJa(ja().command(java.lang.Enum.valueOf(C, "SUBMIT")))
            } else return false
            is KeyAction.Text, is KeyAction.SwitchPage -> { flushJa(); return false }
            else -> return false
        }
        return true
    }

    /** 켜져 있는 음성 입력 키보드(예: Google 음성 입력)의 ID. 없으면 null. */
    private fun voiceImeId(): String? {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.enabledInputMethodList.firstOrNull { info ->
            info.packageName != packageName && (0 until info.subtypeCount).any { info.getSubtypeAt(it).mode == "voice" }
        }?.id
    }

    private fun updateAutoShift() {
        if (capsLock) return
        if (!prefs.autoCap) { shift = false; keyboard.shifted = false; return }
        if (lang != "en") { shift = false; keyboard.shifted = false; return }
        val caps = currentInputConnection?.getCursorCapsMode(currentInputEditorInfo?.inputType ?: 0) ?: 0
        shift = caps != 0
        keyboard.shifted = shift
    }

    private fun onKey(action: KeyAction) {
        val ic = currentInputConnection ?: return
        if (keyboard.emojiSearch && onKeySearch(action)) return
        if (lang == "ja" && onKeyJa(action)) return
        when (action) {
            is KeyAction.Text -> {
                val t = if (shift || capsLock) dev.inter129.mochikeys.layout.Layouts.shifted(action.text) else action.text
                if (lang == "ko" && t.length == 1 && (t[0] in dev.inter129.mochikeys.engine.HangulAutomaton.CHO || t[0] in dev.inter129.mochikeys.engine.HangulAutomaton.JUNG)) {
                    ic.beginBatchEdit()
                    val done = hangul.input(t[0])
                    if (done.isNotEmpty()) ic.commitText(done, 1)
                    showComposing(); ic.endBatchEdit()
                } else { flushHangul(); ic.commitText(t, 1) }
                if (shift && !capsLock) { shift = false; keyboard.shifted = false }
                updateAutoShift()
            }
            KeyAction.Space -> {
                flushHangul()
                val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                if (prefs.doubleSpace && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
                } else ic.commitText(" ", 1)
                updateAutoShift()
            }
            KeyAction.Backspace -> {
                if (hangul.backspace()) { showComposing(); return }
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingTextInCodePoints(1, 0)
                updateAutoShift()
            }
            KeyAction.Return -> {
                flushHangul()
                val ei = currentInputEditorInfo
                val act = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                if (act > EditorInfo.IME_ACTION_NONE && (ei!!.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) ic.performEditorAction(act)
                else ic.commitText("\n", 1)
                updateAutoShift()
            }
            KeyAction.Shift -> {
                if (keyboard.consumeDoubleTapShift()) { capsLock = true; shift = true }
                else if (capsLock) { capsLock = false; shift = false }
                else shift = !shift
                keyboard.shifted = shift; keyboard.capsLock = capsLock
            }
            KeyAction.Globe -> prefs.languages.let { l -> setLanguage(l[(l.indexOf(lang) + 1) % l.size]) }
            is KeyAction.SwitchPage -> keyboard.setPage(action.page)
            KeyAction.Mic -> voiceImeId()?.let { flushHangul(); flushJa(); switchInputMethod(it) }
            else -> {}
        }
    }
}
