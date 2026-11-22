package dev.inter129.mochikeys

import dev.inter129.mochikeys.sound.ClickSynth
import java.io.File
import kotlin.test.*

class ClickSynthTest {
    @Test fun rendersShortDistinctClicks() {
        val all = ClickSynth.Kind.values().associateWith { ClickSynth.render(it) }
        for ((k, pcm) in all) {
            assertTrue(pcm.size in 1000..3500, "$k length ${pcm.size}")
            assertTrue(pcm.maxOf { kotlin.math.abs(it.toInt()) } > 20000, "$k too quiet")
            assertEquals(0, pcm.last().toInt(), "$k must end at silence")
            // 에너지의 대부분이 앞 15ms 안에 있어야 한다(짧고 또렷한 클릭)
            val head = pcm.take(720).sumOf { it.toDouble() * it }; val total = pcm.sumOf { it.toDouble() * it }
            assertTrue(head / total > 0.85, "$k not percussive: ${head / total}")
        }
        assertFalse(all[ClickSynth.Kind.KEY]!!.contentEquals(all[ClickSynth.Kind.DELETE]!!))
        // 미리 듣기 파일
        val dir = File(System.getProperty("user.dir")).resolve("../docs/sounds").apply { mkdirs() }
        for (k in ClickSynth.Kind.values()) dir.resolve("${k.name.lowercase()}.wav").writeBytes(ClickSynth.wav(k))
    }
}
