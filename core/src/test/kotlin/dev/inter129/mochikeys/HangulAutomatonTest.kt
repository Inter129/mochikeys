package dev.inter129.mochikeys

import dev.inter129.mochikeys.engine.HangulAutomaton
import kotlin.test.*

class HangulAutomatonTest {
    private fun type(s: String, a: HangulAutomaton = HangulAutomaton()): String {
        val sb = StringBuilder(); for (c in s) sb.append(a.input(c)); return sb.toString() + a.composing
    }
    @Test fun basic() = assertEquals("안녕하세요", type("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
    @Test fun jongMovesToNextSyllable() = assertEquals("가나", type("ㄱㅏㄴㅏ"))
    @Test fun compoundJongSplits() = assertEquals("갑시", type("ㄱㅏㅂㅅㅣ"))
    @Test fun compoundJongStays() = assertEquals("값", type("ㄱㅏㅂㅅ"))
    @Test fun compoundJongThenConsonant() = assertEquals("닭도", type("ㄷㅏㄹㄱㄷㅗ"))
    @Test fun compoundVowel() = assertEquals("과의원", type("ㄱㅗㅏㅇㅡㅣㅇㅜㅓㄴ"))
    @Test fun doubleConsonantNotJong() = assertEquals("아빠", type("ㅇㅏㅃㅏ"))
    @Test fun ssJong() = assertEquals("있다", type("ㅇㅣㅆㄷㅏ"))
    @Test fun loneJamo() = assertEquals("ㅋㅋㅋ", type("ㅋㅋㅋ"))
    @Test fun loneVowels() = assertEquals("ㅠㅠ", type("ㅠㅠ"))
    @Test fun backspaceByJamo() {
        val a = HangulAutomaton(); type("ㄷㅏㄹㄱ", a)
        val seen = ArrayList<String>()
        while (a.backspace()) seen += a.composing
        assertEquals(listOf("달", "다", "ㄷ", ""), seen)
    }
    @Test fun backspaceCompoundVowel() {
        val a = HangulAutomaton(); type("ㄱㅗㅏ", a); a.backspace(); assertEquals("고", a.composing)
    }
    @Test fun nonHangulCommits() = assertEquals("한 ", type("ㅎㅏㄴ "))
}
