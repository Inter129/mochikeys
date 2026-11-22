package dev.inter129.mochikeys

import dev.inter129.mochikeys.layout.*
import kotlin.test.*

class KeyGeometryTest {
    private val m = Metrics(width = 402f, rowHeight = 54f, gapX = 6f, gapY = 12f, sidePadding = 3f, topPadding = 8f)
    private val rects = KeyGeometry.layout(Layouts.qwerty, m)

    @Test fun noDeadZoneAcrossKeyboard() {
        var y = 0f
        while (y < 8f + 4 * 54f) {
            var x = 0f
            while (x < 402f) {
                assertNotNull(rects.firstOrNull { (x to y) in it.hit }, "dead zone at $x,$y")
                x += 1f
            }
            y += 1f
        }
    }

    @Test fun edgeOfSecondRowHitsA() {
        assertEquals("a", HitTester.find(rects, 2f, 8f + 54f + 20f)!!.key.label)
        assertEquals("l", HitTester.find(rects, 400f, 8f + 54f + 20f)!!.key.label)
    }

    @Test fun characterKeysShareWidth() {
        val q = rects.first { it.key.label == "q" }.visual.width
        val a = rects.first { it.key.label == "a" }.visual.width
        assertEquals(q, a, 0.01f)
    }
}
