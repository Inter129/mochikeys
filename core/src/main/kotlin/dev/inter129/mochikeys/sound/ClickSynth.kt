package dev.inter129.mochikeys.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 키 클릭음 합성(직접 만든 소리, Apple 음원 미사용).
 * 짧게 감쇠하는 사인파 두 개(몸통 + 딱 소리)에 아주 짧은 잡음 어택을 섞는다.
 */
object ClickSynth {
    const val SAMPLE_RATE = 48000
    /** 끝에서 음높이가 얼마나 올라가는지(0.35 = 35% 높아짐). */
    const val GLIDE = 0.35
    enum class Kind { KEY, DELETE, MODIFIER }

    private class Partial(val freq: Double, val tauMs: Double, val gain: Double)

    fun render(kind: Kind): ShortArray {
        val (parts, noiseGain, lenMs) = when (kind) {
            // 문자: 밝고 짧은 "톡"
            Kind.KEY -> Triple(listOf(Partial(2900.0, 2.0, 0.55), Partial(1230.0, 4.5, 0.45), Partial(5800.0, 0.8, 0.18)), 0.22, 28)
            // 삭제: 이전 문자 키 소리 그대로
            Kind.DELETE -> Triple(listOf(Partial(2500.0, 2.0, 0.55), Partial(1060.0, 4.5, 0.45), Partial(5000.0, 0.8, 0.18)), 0.22, 28)
            // 기능 키(스페이스·리턴·Shift): 더 낮고 둥근 "툭"
            Kind.MODIFIER -> Triple(listOf(Partial(2050.0, 2.6, 0.45), Partial(850.0, 6.0, 0.55), Partial(4200.0, 0.9, 0.10)), 0.16, 36)
        }
        val n = SAMPLE_RATE * lenMs / 1000
        val out = DoubleArray(n)
        val phase = DoubleArray(parts.size)
        var seed = 12345L + kind.ordinal
        var hp = 0.0; var prev = 0.0
        for (i in 0 until n) {
            val tMs = i * 1000.0 / SAMPLE_RATE
            var v = 0.0
            // 음높이가 끝으로 갈수록 올라간다(위로 미끄러지는 처프): 위상을 누적해 계산
            val rise = 1.0 + GLIDE * (i.toDouble() / n)
            parts.forEachIndexed { k, p ->
                phase[k] += 2 * PI * p.freq * rise / SAMPLE_RATE
                v += p.gain * exp(-tMs / p.tauMs) * sin(phase[k])
            }
            // 결정적 잡음(매번 같은 소리) → 1차 고역 통과 → 0.7ms 감쇠
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val white = ((seed ushr 33).toInt() / 1073741824.0) - 1.0
            hp = 0.85 * (hp + white - prev); prev = white
            v += noiseGain * exp(-tMs / 0.7) * hp
            out[i] = v
        }
        // 0.3ms 페이드인(팝 방지) + 끝 3ms 페이드아웃, 피크 정규화
        val fin = SAMPLE_RATE * 3 / 10000; val fout = SAMPLE_RATE * 3 / 1000
        for (i in 0 until fin) out[i] *= i.toDouble() / fin
        for (i in 0 until fout) out[n - 1 - i] *= i.toDouble() / fout
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(n) { (out[it] / peak * 0.8 * Short.MAX_VALUE).toInt().toShort() }
    }

    /** 미리 듣기용 16비트 모노 WAV. */
    fun wav(kind: Kind): ByteArray {
        val pcm = render(kind); val data = pcm.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(data)
        for (s in pcm) b.putShort(s)
        return b.array()
    }
}
