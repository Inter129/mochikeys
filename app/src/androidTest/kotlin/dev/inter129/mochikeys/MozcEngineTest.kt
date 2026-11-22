package dev.inter129.mochikeys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.inter129.mochikeys.mozc.MozcEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MozcEngineTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** 12키 토글: 키 코드를 같은 키로 여러 번 → 행 안에서 순환. */
    private fun MozcEngine.kana(vararg taps: Pair<Char, Int>): MozcEngine.State {
        var s: MozcEngine.State? = null
        for ((k, n) in taps) repeat(n) { s = sendKeyCode(k.code) }.also { s = command(org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand.CommandType.STOP_KEY_TOGGLING) }
        return s!!
    }

    @Test fun dataLoaded() {
        MozcEngine.open(ctx)
        assertTrue(MozcEngine.dataVersion, MozcEngine.dataVersion.isNotEmpty())
    }

    @Test fun nihongoConvertsToKanji() {
        val m = MozcEngine.open(ctx)
        // に = な行(5) 2회, ほ = は行(6) 5회, ん = わ行(0) 3회, ご = か行(2) 5회 + 濁点(*)
        val s = m.kana('5' to 2, '6' to 5, '0' to 3, '2' to 5, '*' to 1)
        assertEquals("にほんご", s.preedit)
        assertTrue(s.candidates.joinToString(), s.candidates.any { it.second == "日本語" })
    }
}
