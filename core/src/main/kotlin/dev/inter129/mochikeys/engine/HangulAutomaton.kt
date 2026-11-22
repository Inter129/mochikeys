package dev.inter129.mochikeys.engine

/** 두벌식 한글 오토마타. 입력은 호환 자모, 조합 중 음절은 [composing]. */
class HangulAutomaton {
    private var cho: Char? = null
    private var jung: Char? = null
    private var jong: Char? = null

    val composing: String
        get() {
            val c = cho; val j = jung
            return when {
                c != null && j != null -> syllable(c, j, jong).toString()
                c != null -> c.toString()
                j != null -> j.toString()
                else -> ""
            }
        }
    val isEmpty get() = cho == null && jung == null

    fun reset() { cho = null; jung = null; jong = null }

    /** 자모 하나를 넣고, 이번 입력으로 확정된 문자열을 돌려준다. */
    fun input(ch: Char): String {
        var out = ""
        fun commit() { out += composing; reset() }
        if (ch in JUNG) {
            val jo = jong
            when {
                jo != null -> { // 받침이 다음 음절 초성으로 이동
                    val split = JONG_SPLIT[jo]
                    val c0 = cho!!; val j0 = jung!!
                    if (split != null) { out += syllable(c0, j0, split.first); cho = split.second }
                    else { out += syllable(c0, j0, null); cho = jo }
                    jung = ch; jong = null
                }
                jung != null -> {
                    val comp = JUNG_COMB[jung!! to ch]
                    if (comp != null) jung = comp else { commit(); jung = ch }
                }
                else -> jung = ch
            }
        } else if (ch in CHO) {
            when {
                cho != null && jung != null && jong == null -> if (ch in JONG) jong = ch else { commit(); cho = ch }
                cho != null && jung != null -> {
                    val comp = JONG_COMB[jong!! to ch]
                    if (comp != null) jong = comp else { commit(); cho = ch }
                }
                else -> { if (!isEmpty) commit(); cho = ch }
            }
        } else { commit(); out += ch }
        return out
    }

    /** 조합 중이면 자모 하나를 지우고 true. */
    fun backspace(): Boolean {
        val jo = jong; val ju = jung
        when {
            jo != null -> jong = JONG_SPLIT[jo]?.first
            ju != null -> jung = JUNG_SPLIT[ju]
            cho != null -> cho = null
            else -> return false
        }
        return true
    }

    companion object {
        const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        const val JONG = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        private val JUNG_COMB = mapOf(
            ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ',
            ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ', ('ㅜ' to 'ㅣ') to 'ㅟ', ('ㅡ' to 'ㅣ') to 'ㅢ',
        )
        private val JUNG_SPLIT = JUNG_COMB.entries.associate { it.value to it.key.first }
        private val JONG_COMB = mapOf(
            ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ', ('ㄹ' to 'ㄱ') to 'ㄺ',
            ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ', ('ㄹ' to 'ㅅ') to 'ㄽ', ('ㄹ' to 'ㅌ') to 'ㄾ',
            ('ㄹ' to 'ㅍ') to 'ㄿ', ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ',
        )
        private val JONG_SPLIT = JONG_COMB.entries.associate { it.value to it.key }
        val SHIFT = mapOf("ㅂ" to "ㅃ", "ㅈ" to "ㅉ", "ㄷ" to "ㄸ", "ㄱ" to "ㄲ", "ㅅ" to "ㅆ", "ㅐ" to "ㅒ", "ㅔ" to "ㅖ")

        fun syllable(c: Char, j: Char, jo: Char?): Char =
            (0xAC00 + (CHO.indexOf(c) * 21 + JUNG.indexOf(j)) * 28 + (if (jo == null) 0 else JONG.indexOf(jo) + 1)).toChar()
    }
}
