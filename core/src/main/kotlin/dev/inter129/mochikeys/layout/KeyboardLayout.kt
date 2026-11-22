package dev.inter129.mochikeys.layout

/** 키의 동작 종류. */
sealed interface KeyAction {
    data class Text(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Return : KeyAction
    data object Globe : KeyAction
    data class SwitchPage(val page: String) : KeyAction
    /** 일본어 12키. codes·labels 순서: 탭, 좌, 상, 우, 하. codes는 Mozc 토글·플릭 테이블의 키 코드. */
    data class Kana(val codes: String, val labels: List<String>) : KeyAction
    /** 엔진에 그대로 넘기는 키 코드(플릭 방향이 정해진 뒤). */
    data class Code(val code: Int) : KeyAction
    data object NextChar : KeyAction   // → : 같은 키 연타 끊기
    data object Rewind : KeyAction     // 逆順
    data object None : KeyAction       // 자리만 차지하는 빈칸
    data object Mic : KeyAction        // 음성 입력 키보드로 전환
}

enum class KeyStyle { CHARACTER, FUNCTION, SPACE, RETURN, FLICK }

/** 플릭 방향: 0 탭, 1 좌, 2 상, 3 우, 4 하. */
fun flickIndex(dx: Float, dy: Float, threshold: Float): Int = when {
    dx * dx + dy * dy < threshold * threshold -> 0
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx < 0) 1 else 3
    else -> if (dy < 0) 2 else 4
}

/** width는 한 행 안의 상대 가중치(문자 키 = 1). */
data class Key(
    val label: String,
    val action: KeyAction,
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.CHARACTER,
    val variants: List<String> = emptyList(),
    /** 보이는 폭(가중치 단위). 슬롯보다 좁게 그리는 키(Shift·⌫): 행의 바깥쪽 가장자리에 붙인다. */
    val visW: Float? = null,
    /** 아래 행까지 차지하는 키(일본어 판의 改行). */
    val rowSpan: Int = 1,
)

/** 행 양옆 여백(가중치 단위). 2열의 a/l처럼 들여쓴 행은 양끝 키가 여백까지 터치를 받는다. */
data class Row(val keys: List<Key>, val inset: Float = 0f)

data class KeyboardLayout(val id: String, val rows: List<Row>)

/** 픽셀 좌표의 키 사각형: visual은 그리는 영역, hit는 터치 영역(빈틈없이 타일링). */
data class KeyRect(val key: Key, val visual: Rect, val hit: Rect)

data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val width get() = right - left
    val height get() = bottom - top
    operator fun contains(p: Pair<Float, Float>) = p.first >= left && p.first < right && p.second >= top && p.second < bottom
}
