package dev.inter129.mochikeys.layout

/**
 * 레이아웃을 픽셀 사각형으로 펼친다.
 * 터치 영역은 키보드 전체를 빈틈없이 나눈다: 키 사이 간격은 양쪽 키가 반씩 나눠 갖고,
 * 행 양끝 키는 들여쓰기 여백과 화면 끝까지 확장한다(데드존 없음).
 */
data class Metrics(
    val width: Float,
    val rowHeight: Float,
    val gapX: Float,
    val gapY: Float,
    val sidePadding: Float,
    val topPadding: Float,
)

object KeyGeometry {
    fun layout(kb: KeyboardLayout, m: Metrics): List<KeyRect> {
        val out = ArrayList<KeyRect>()
        if (kb.rows.isEmpty()) return out
        // 가장 긴 행 기준으로 단위 폭 결정(iOS처럼 모든 행의 문자 키 폭이 같다).
        val unitsMax = kb.rows.maxOf { r -> r.keys.sumOf { it.width.toDouble() }.toFloat() + 2 * r.inset }
        val usable = m.width - 2 * m.sidePadding
        val unit = usable / unitsMax
        kb.rows.forEachIndexed { ri, row ->
            val top = m.topPadding + ri * m.rowHeight
            val bottom = top + m.rowHeight
            val rowUnits = row.keys.sumOf { it.width.toDouble() }.toFloat()
            // 행이 가장 긴 행보다 짧으면 가운데 정렬(inset 포함)
            var x = m.sidePadding + (usable - rowUnits * unit) / 2
            row.keys.forEachIndexed { ki, key ->
                val w = key.width * unit
                var vl = x + m.gapX / 2; var vr = x + w - m.gapX / 2
                key.visW?.let { vw ->
                    val pw = vw * unit - m.gapX
                    when (ki) { 0 -> vr = vl + pw; row.keys.lastIndex -> vl = vr - pw; else -> { val c = (vl + vr) / 2; vl = c - pw / 2; vr = c + pw / 2 } }
                }
                val spanBottom = bottom + (key.rowSpan - 1) * m.rowHeight
                val visual = Rect(vl, top + m.gapY / 2, vr, spanBottom - m.gapY / 2)
                val hitL = if (ki == 0) 0f else x
                val hitR = if (ki == row.keys.lastIndex) m.width else x + w
                val hitT = if (ri == 0) 0f else top
                if (key.action != KeyAction.None) out += KeyRect(key, visual, Rect(hitL, hitT, hitR, spanBottom))
                x += w
            }
        }
        return out
    }
}

object HitTester {
    /** 터치 영역 안의 키, 없으면(키보드 밖) 시각 중심이 가장 가까운 키. */
    fun find(rects: List<KeyRect>, x: Float, y: Float): KeyRect? =
        rects.firstOrNull { (x to y) in it.hit }
            ?: rects.minByOrNull { val dx = it.visual.centerX - x; val dy = it.visual.centerY - y; dx * dx + dy * dy }
}
