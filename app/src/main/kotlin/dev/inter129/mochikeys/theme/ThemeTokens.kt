package dev.inter129.mochikeys.theme

/** 치수(dp)·색·타이밍 토큰. 값은 iOS 27 시뮬레이터(iPhone 17, 402pt) 실측. */
data class ThemeTokens(
    val rowHeight: Float = 57f,
    val gapX: Float = 7f,
    val gapY: Float = 15f,
    val sidePadding: Float = 3.5f,
    val topPadding: Float = 19.5f,
    val bottomPadding: Float = 66f, // 지구본·받아쓰기 영역 자리
    val keyRadius: Float = 8.5f,
    val background: Int,
    val charKey: Int,
    val funcKey: Int,
    val keyText: Int,
    val keyShadow: Int,
    val pressedKey: Int,
    val hintText: Int = 0xFF9A9BA1.toInt(),
    val accent: Int = 0xFF007AFF.toInt(),
    val panelRadius: Float = 26f,
    val charFontSize: Float = 25f,
    val funcFontSize: Float = 18.5f,
    val longPressMs: Long = 450,
    val repeatStartMs: Long = 500,
    val repeatIntervalMs: Long = 100,
) {
    companion object {
        val light = ThemeTokens(
            background = 0xFFE0E1E8.toInt(), charKey = 0xFFFFFFFF.toInt(), funcKey = 0xFFFFFFFF.toInt(), pressedKey = 0xFFC4C6CE.toInt(),
            keyText = 0xFF000000.toInt(), keyShadow = 0x00000000,
        )
        val dark = ThemeTokens(
            background = 0xFF202123.toInt(), charKey = 0xFF444445.toInt(), funcKey = 0xFF444445.toInt(), pressedKey = 0xFF6E6E70.toInt(),
            keyText = 0xFFFFFFFF.toInt(), keyShadow = 0x00000000,
        )
    }
}
