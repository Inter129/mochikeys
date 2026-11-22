package dev.inter129.mochikeys.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import dev.inter129.mochikeys.layout.KeyAction
import dev.inter129.mochikeys.theme.ThemeTokens
import kotlin.math.ceil

/**
 * 아이폰식 이모지 패널: 5행 격자를 세로 방향으로 채우고 가로로 넘긴다. 위에는 분류 제목,
 * 아래에는 문자판 복귀·분류 바로가기·백스페이스. KeyboardView의 키 영역 안에 그려진다.
 * 이모지 그림은 기기의 시스템 이모지 글꼴을 쓴다.
 */
class EmojiPanel(
    private val host: View,
    private val t: ThemeTokens,
    private val onEmoji: (String) -> Unit,
    private val onAbc: () -> Unit,
    private val onBackspace: () -> Unit,
) {
    private val dp = ThemeTokens.scaledDensity(host.resources)
    private val prefs = host.context.getSharedPreferences("emoji", Context.MODE_PRIVATE)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 29f * dp }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scroller = OverScroller(host.context)
    private var left = 0f; private var top = 0f; private var width = 0f
    private var scrollX = 0f
    var abcLabel = "ABC"

    private class Section(val title: String, val icon: String, val items: List<String>, var x: Float = 0f, var w: Float = 0f)
    private var sections: List<Section> = emptyList()

    private val rows = 5
    private val cell get() = 44f * dp
    private val rowH get() = 35f * dp
    private val headerH get() = 26f * dp
    private val gridH get() = rows * rowH
    private val barH get() = 36f * dp
    private val contentW get() = (sections.lastOrNull()?.let { it.x + it.w } ?: 0f) + 8 * dp
    private val maxScroll get() = (contentW - width).coerceAtLeast(0f)

    /** 패널을 열 때마다 호출: 최근 사용 목록을 반영해 구획을 다시 만든다. */
    fun open(left: Float, top: Float, width: Float) {
        this.left = left; this.top = top; this.width = width
        val recent = prefs.getString("recent", "")!!.split(" ").filter { it.isNotEmpty() }
        val all = ArrayList<Section>()
        if (recent.isNotEmpty()) all += Section("FREQUENTLY USED", "🕘", recent.take(30))
        for ((title, icon, s) in EmojiData.categories) all += Section(title, icon, s.split(" ").filter { emojiPaint.hasGlyph(it) })
        var x = 8 * dp
        // 구획 폭은 제목이 옆 구획과 겹치지 않도록 제목 길이보다 좁아지지 않게 한다
        textPaint.textSize = 12.5f * dp; textPaint.isFakeBoldText = true
        for (s in all) { s.x = x; s.w = maxOf(ceil(s.items.size / rows.toFloat()) * cell, textPaint.measureText(s.title) + 12 * dp); x += s.w + 14 * dp }
        textPaint.isFakeBoldText = false
        sections = all; scrollX = 0f; scroller.forceFinished(true)
    }

    fun draw(c: Canvas) {
        c.save(); c.clipRect(left, top, left + width, top + headerH + gridH)
        textPaint.textAlign = Paint.Align.LEFT; textPaint.textSize = 12.5f * dp; textPaint.color = t.hintText
        textPaint.isFakeBoldText = true
        for (s in sections) {
            val sx = left + s.x - scrollX
            if (sx > left + width || sx + s.w < left) continue
            c.drawText(s.title, sx + 6 * dp, top + 18 * dp, textPaint)
            s.items.forEachIndexed { i, e ->
                val cx = sx + (i / rows) * cell + cell / 2
                if (cx < left - cell || cx > left + width + cell) return@forEachIndexed
                val cy = top + headerH + (i % rows) * rowH + rowH / 2
                c.drawText(e, cx, cy - (emojiPaint.ascent() + emojiPaint.descent()) / 2, emojiPaint)
            }
        }
        c.restore()
        // 아래 바: 문자판 복귀 · 분류 · 백스페이스
        val by = top + headerH + gridH + barH / 2
        textPaint.isFakeBoldText = false; textPaint.textSize = 16f * dp; textPaint.color = t.keyText; textPaint.textAlign = Paint.Align.CENTER
        c.drawText(abcLabel, left + 34 * dp, by - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        val cur = currentSection()
        val slot = catSlot
        textPaint.textSize = 17f * dp
        sections.forEachIndexed { i, s ->
            val cx = left + 68 * dp + slot * (i + 0.5f)
            if (i == cur) { textPaint.color = t.pressedKey; c.drawCircle(cx, by, 15 * dp, textPaint) }
            textPaint.color = t.keyText; textPaint.alpha = if (i == cur) 255 else 130
            c.drawText(s.icon, cx, by - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            textPaint.alpha = 255
        }
        Icons.draw(c, KeyAction.Backspace, left + width - 34 * dp, by, dp, t.keyText, false, false)
    }

    private val catSlot get() = (width - 68 * dp - 68 * dp) / sections.size.coerceAtLeast(1)
    private fun currentSection(): Int {
        val probe = scrollX + width * 0.3f
        return sections.indexOfLast { it.x <= probe }.coerceAtLeast(0)
    }

    fun computeScroll() {
        if (scroller.computeScrollOffset()) { scrollX = scroller.currX.toFloat(); host.postInvalidateOnAnimation() }
    }

    private val detector = GestureDetector(host.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            scrollX = (scrollX + dx).coerceIn(0f, maxScroll); host.invalidate(); return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            scroller.fling(scrollX.toInt(), 0, -vx.toInt(), 0, 0, maxScroll.toInt(), 0, 0); host.postInvalidateOnAnimation(); return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean { tap(e.x, e.y); return true }
    })

    fun onTouch(e: MotionEvent): Boolean = detector.onTouchEvent(e)

    private fun tap(x: Float, y: Float) {
        val gy = y - top - headerH
        if (gy >= gridH) { // 아래 바
            when {
                x < left + 68 * dp -> onAbc()
                x > left + width - 68 * dp -> onBackspace()
                else -> {
                    val i = ((x - left - 68 * dp) / catSlot).toInt().coerceIn(0, sections.lastIndex)
                    scroller.startScroll(scrollX.toInt(), 0, ((sections[i].x - 8 * dp).coerceAtMost(maxScroll) - scrollX).toInt(), 0, 250)
                    host.postInvalidateOnAnimation()
                }
            }
            host.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        if (gy < 0) return
        val row = (gy / rowH).toInt().coerceIn(0, rows - 1)
        val cx = x - left + scrollX
        for (s in sections) {
            if (cx < s.x || cx >= s.x + s.w) continue
            val idx = ((cx - s.x) / cell).toInt() * rows + row
            val e = s.items.getOrNull(idx) ?: return
            host.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onEmoji(e)
            val recent = (listOf(e) + prefs.getString("recent", "")!!.split(" ").filter { it.isNotEmpty() && it != e }).take(30)
            prefs.edit().putString("recent", recent.joinToString(" ")).apply()
            return
        }
    }
}

object EmojiData {
    val categories = listOf(
        Triple("SMILEYS & PEOPLE", "😀", "😀 😃 😄 😁 😆 🥹 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫣 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👻 💀 👽 🤖 💩 👋 🤚 ✋ 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 👏 🙌 🫶 👐 🤝 🙏 💪 👀 👶 🧒 👦 👧 🧑 👨 👩 🧓 💁 🙅 🙆 🙋 🤦 🤷"),
        Triple("ANIMALS & NATURE", "🐻", "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🦈 🐊 🐘 🦒 🐪 🐎 🐖 🐑 🐕 🐈 🐓 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌛 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️ ⛅ ☁️ 🌧 ❄️ ☃️ 💧 🌊"),
        Triple("FOOD & DRINK", "🍔", "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥒 🌶 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾"),
        Triple("ACTIVITY", "⚽", "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🎱 🏓 🏸 🏒 🥊 🥋 ⛳ 🎣 🎿 ⛷ 🏂 🏋️ 🤸 🏌️ 🏄 🏊 🚴 🏆 🥇 🥈 🥉 🏅 🎖 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟ 🎯 🎳 🎮 🎰 🧩"),
        Triple("TRAVEL & PLACES", "🚗", "🚗 🚕 🚙 🚌 🚎 🏎 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍 🚲 🛴 🚨 🚃 🚄 🚅 🚈 🚂 🚆 🚇 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🛳 ⛴ 🚢 ⚓ ⛽ 🚧 🚦 🗺 🗿 🗽 🗼 🏰 🏯 🏟 🎡 🎢 🎠 ⛱ 🏖 🏝 🏜 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🏠 🏡 🏢 🏬 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 ⛩ 🌅 🌄 🌠 🎇 🎆 🌇 🌆 🏙 🌃 🌌 🌉"),
        Triple("OBJECTS", "💡", "⌚ 📱 💻 ⌨️ 🖥 🖨 🖱 💽 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙 ⏰ ⏳ 📡 🔋 🔌 💡 🔦 🕯 🧯 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 🛠 ⛏ 🔩 ⚙️ 🧲 🔫 💣 🔪 🛡 🚬 ⚰️ 🔮 💈 🔭 🔬 💊 💉 🧬 🌡 🧹 🧻 🚽 🚿 🛁 🧼 🔑 🗝 🚪 🛋 🛏 🧸 🖼 🛍 🛒 🎁 🎈 🎀 🎊 🎉 ✉️ 📩 📦 📜 📄 📊 📈 📉 📅 🗑 📁 📰 📓 📕 📚 📖 🔖 📎 📐 📏 📌 ✂️ 🖊 🖌 ✏️ 🔍 🔒 🔓"),
        Triple("SYMBOLS", "💟", "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☯️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ ☢️ 📴 📳 🆚 💮 ㊙️ ㊗️ 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🔞 ❗ ❕ ❓ ❔ ‼️ ⁉️ ⚠️ 🔱 ⚜️ 🔰 ♻️ ✅ 💹 ❇️ ✳️ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 ♿ 🅿️ 🈳 ▶️ ⏸ ⏹ ⏺ ⏭ ⏮ ⏩ ⏪ 🔀 🔁 🔂 ➕ ➖ ➗ ✖️ ♾ 💲 ™️ ©️ ®️ ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔺 🔻 🔶 🔷 🔔 🔕 🎵 🎶 💬 💭"),
        Triple("FLAGS", "🏳️", "🏳️ 🏴 🏁 🚩 🏳️‍🌈 🇰🇷 🇯🇵 🇺🇸 🇬🇧 🇨🇳 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇨🇦 🇦🇺 🇧🇷 🇮🇳 🇷🇺 🇲🇽 🇹🇼 🇭🇰 🇸🇬 🇹🇭 🇻🇳 🇵🇭 🇮🇩 🇲🇾 🇳🇱 🇸🇪 🇨🇭 🇹🇷 🇺🇦 🇵🇱 🇦🇷 🇳🇿 🇪🇺 🇺🇳"),
    )
}
