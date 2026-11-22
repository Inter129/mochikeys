package dev.inter129.mochikeys.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dev.inter129.mochikeys.sound.ClickSynth

/**
 * 클릭음 재생. 지연을 줄이려고 소리마다 미리 채워 둔 정적 AudioTrack(저지연 모드)을 몇 개씩 돌려 쓴다.
 * 폰이 무음·진동 모드면 소리를 내지 않는다(아이폰의 무음 스위치와 같은 동작).
 */
class ClickPlayer(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val voices = 3
    private val tracks = HashMap<ClickSynth.Kind, Array<AudioTrack>>()
    private val next = HashMap<ClickSynth.Kind, Int>()
    /** 기본은 꺼짐. 설정("settings" 환경설정의 sound)에서 켠다. */
    var enabled = false
    var volume = 0.55f

    init {
        for (k in ClickSynth.Kind.values()) {
            val pcm = ClickSynth.render(k)
            tracks[k] = Array(voices) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(ClickSynth.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
                    .also { it.write(pcm, 0, pcm.size) }
            }
            next[k] = 0
        }
    }

    fun play(kind: ClickSynth.Kind) {
        if (!enabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val i = next[kind]!!; next[kind] = (i + 1) % voices
        val t = tracks[kind]!![i]
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData(); t.setVolume(volume); t.play()
        }
    }

    fun release() { tracks.values.forEach { a -> a.forEach { it.release() } }; tracks.clear() }
}
