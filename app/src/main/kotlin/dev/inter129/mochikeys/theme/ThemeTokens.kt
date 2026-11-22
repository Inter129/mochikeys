package dev.inter129.mochikeys.theme

/** 치수(dp)·색·타이밍 토큰. 값은 iOS 27 시뮬레이터(iPhone 17, 402pt) 실측. */
data class ThemeTokens(
    val rowHeight: Float = 57f,
    val gapX: Float = 6f,
    val gapY: Float = 11f,
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
        /**
         * 치수 배율이 곱해진 화면 밀도. 치수는 폭 402dp(iPhone 17) 화면에서 잰 값이라, 더 넓은 폰에서는
         * 높이·글자·간격도 폭에 비례해 키운다(안 그러면 키가 옆으로만 늘어나 납작해진다).
         * 가로 모드에서 과하게 커지지 않도록 짧은 변 기준으로 계산하고 0.9~1.3배로 제한한다.
         */
        fun scaledDensity(res: android.content.res.Resources): Float {
            val sw = res.configuration.smallestScreenWidthDp.takeIf { it > 0 } ?: 402
            return res.displayMetrics.density * (sw / 402f).coerceIn(0.9f, 1.3f)
        }
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
