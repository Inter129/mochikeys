package dev.inter129.mochikeys.view

import android.content.Context
import android.graphics.Paint

/** 이모지 검색. assets/emoji_index.tsv(유니코드 CLDR 이름·검색어, 영어·한국어·일본어)를 한 번 읽어 둔다. */
object EmojiSearch {
    private var index: List<Pair<String, List<String>>>? = null
    private val probe = Paint()

    private fun load(context: Context): List<Pair<String, List<String>>> = index ?: context.assets.open("emoji_index.tsv").bufferedReader().useLines { lines ->
        lines.mapNotNull { l -> val i = l.indexOf('\t'); if (i < 0) null else l.substring(0, i) to l.substring(i + 1).split('|') }
            .filter { probe.hasGlyph(it.first) }.toList()
    }.also { index = it }

    /** 검색어로 시작하는 낱말이 있는 이모지를 먼저, 그다음 검색어를 포함하는 이모지. */
    fun search(context: Context, query: String, limit: Int = 60): List<String> {
        val q = query.trim().lowercase(); if (q.isEmpty()) return emptyList()
        // 히라가나로 친 검색어는 가타카나로도 찾는다(일본어 이름은 ネコ처럼 가타카나가 많다)
        val kata = String(CharArray(q.length) { val c = q[it]; if (c in 'ぁ'..'ゖ') c + 0x60 else c })
        val qs = if (kata == q) listOf(q) else listOf(q, kata)
        val first = ArrayList<String>(); val second = ArrayList<String>()
        for ((e, keys) in load(context)) {
            when {
                keys.any { k -> qs.any { q -> k.startsWith(q) || k.split(' ').any { it.startsWith(q) } } } -> first += e
                keys.any { k -> qs.any { k.contains(it) } } -> second += e
            }
        }
        return (first + second).take(limit)
    }
}
