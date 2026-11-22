package dev.inter129.mochikeys

import dev.inter129.mochikeys.layout.*
import kotlin.test.*

class FlickTest {
    @Test fun directions() {
        assertEquals(0, flickIndex(3f, -4f, 20f))
        assertEquals(1, flickIndex(-40f, 5f, 20f))
        assertEquals(2, flickIndex(5f, -40f, 20f))
        assertEquals(3, flickIndex(40f, -5f, 20f))
        assertEquals(4, flickIndex(-5f, 40f, 20f))
    }
    @Test fun kanaLayoutHasTallReturnAndNoSpacer() {
        val m = Metrics(402f, 57f, 7f, 15f, 3.5f, 19.5f)
        val r = KeyGeometry.layout(Layouts.kana12, m)
        assertEquals(19, r.size)
        val ret = r.first { it.key.action == KeyAction.Return }
        assertEquals(2 * 57f - 15f, ret.visual.height, 0.01f)
        val a = r.first { it.key.label == "あ" }.key.action as KeyAction.Kana
        assertEquals('1', a.codes[0]); assertEquals("う", a.labels[2])
    }
}
