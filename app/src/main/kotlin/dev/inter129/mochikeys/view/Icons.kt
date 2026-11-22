package dev.inter129.mochikeys.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.inter129.mochikeys.layout.KeyAction

/**
 * 기능 키 아이콘을 벡터로 직접 그린다(SF Symbols 느낌의 둥근 선, Apple 에셋 미사용).
 * 좌표는 아이콘 중심 기준 dp 단위.
 */
object Icons {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; strokeJoin = Paint.Join.ROUND }

    /** 그렸으면 true. */
    fun draw(c: Canvas, a: KeyAction, cx: Float, cy: Float, dp: Float, color: Int, shiftOn: Boolean, caps: Boolean): Boolean {
        stroke.color = color; fill.color = color
        stroke.strokeWidth = 1.65f * dp
        when (a) {
            KeyAction.Shift -> shift(c, cx, cy, dp, shiftOn, caps)
            KeyAction.Backspace -> backspace(c, cx, cy, dp)
            KeyAction.Globe -> globe(c, cx, cy, dp)
            KeyAction.Mic -> mic(c, cx, cy, dp)
            KeyAction.SwitchPage("emoji") -> smiley(c, cx, cy, dp)
            else -> return false
        }
        return true
    }

    private fun shift(c: Canvas, cx: Float, cy: Float, d: Float, on: Boolean, caps: Boolean) {
        val oy = if (caps) -2.5f else 0f
        val p = Path().apply {
            moveTo(cx, cy + (-9f + oy) * d)            // 꼭짓점
            lineTo(cx + 10f * d, cy + (0.5f + oy) * d)
            lineTo(cx + 4.6f * d, cy + (0.5f + oy) * d)
            lineTo(cx + 4.6f * d, cy + (8f + oy) * d)
            lineTo(cx - 4.6f * d, cy + (8f + oy) * d)
            lineTo(cx - 4.6f * d, cy + (0.5f + oy) * d)
            lineTo(cx - 10f * d, cy + (0.5f + oy) * d)
            close()
        }
        c.drawPath(p, if (on) fill.apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = 1.7f * d } else stroke)
        fill.style = Paint.Style.FILL
        if (caps) { // Caps Lock: 아래 막대
            c.drawRoundRect(cx - 4.6f * d, cy + 8.5f * d, cx + 4.6f * d, cy + 10.5f * d, d, d, fill)
        }
    }

    private fun backspace(c: Canvas, cx: Float, cy: Float, d: Float) {
        val l = cx - 10.5f * d; val r = cx + 10f * d; val t = cy - 8.3f * d; val b = cy + 8.3f * d
        val p = Path().apply {
            moveTo(l, cy)
            lineTo(l + 6.5f * d, t); lineTo(r - 2.5f * d, t)
            quadTo(r, t, r, t + 2.5f * d); lineTo(r, b - 2.5f * d)
            quadTo(r, b, r - 2.5f * d, b); lineTo(l + 6.5f * d, b)
            close()
        }
        c.drawPath(p, stroke)
        val x0 = cx + 2f * d; val s = 3.3f * d
        c.drawLine(x0 - s, cy - s, x0 + s, cy + s, stroke)
        c.drawLine(x0 - s, cy + s, x0 + s, cy - s, stroke)
    }

    /** 이름으로 그리는 아이콘: next(→), rewind(↺), search(돋보기), go(화살표). */
    fun drawNamed(c: Canvas, name: String, cx: Float, cy: Float, d: Float, color: Int) {
        stroke.color = color; fill.color = color; stroke.strokeWidth = 1.7f * d
        when (name) {
            "next", "go" -> {
                val w = if (name == "go") 9.5f else 10.5f
                if (name == "go") stroke.strokeWidth = 2.1f * d
                c.drawLine(cx - w * d, cy, cx + w * d, cy, stroke)
                c.drawLine(cx + w * d, cy, cx + (w - 5.5f) * d, cy - 5.5f * d, stroke)
                c.drawLine(cx + w * d, cy, cx + (w - 5.5f) * d, cy + 5.5f * d, stroke)
            }
            "rewind" -> { // 반시계 방향 화살표: 꼭대기에서 시작해 시계 방향으로 300° 도는 원호 + 꼭대기에서 왼쪽을 가리키는 촉
                val r = 8.4f * d; val oy = 1.2f * d
                c.drawArc(cx - r, cy - r + oy, cx + r, cy + r + oy, -95f, 300f, false, stroke)
                val tx = cx - 0.087f * r; val ty = cy + oy - 0.996f * r   // 촉 끝(원호의 시작점)
                val head = Path().apply {
                    moveTo(tx + 4.8f * d, ty - 4.4f * d); lineTo(tx, ty); lineTo(tx + 4.8f * d, ty + 4.4f * d)
                }
                c.drawPath(head, stroke)
            }
            "check" -> {
                stroke.strokeWidth = 2.2f * d
                c.drawLine(cx - 8f * d, cy + 0.5f * d, cx - 2.5f * d, cy + 6.5f * d, stroke)
                c.drawLine(cx - 2.5f * d, cy + 6.5f * d, cx + 8.5f * d, cy - 7f * d, stroke)
            }
            "search" -> {
                stroke.strokeWidth = 2.1f * d; val r = 6.6f * d
                c.drawCircle(cx - 2.2f * d, cy - 2.2f * d, r, stroke)
                c.drawLine(cx + 2.7f * d, cy + 2.7f * d, cx + 9f * d, cy + 9f * d, stroke)
            }
        }
    }

    private fun mic(c: Canvas, cx: Float, cy: Float, d: Float) {
        stroke.strokeWidth = 1.7f * d
        c.drawRoundRect(cx - 4.2f * d, cy - 13f * d, cx + 4.2f * d, cy + 3f * d, 4.2f * d, 4.2f * d, stroke)   // 캡슐
        c.drawArc(cx - 8.2f * d, cy - 9f * d, cx + 8.2f * d, cy + 7.4f * d, 0f, 180f, false, stroke)          // 받침 호
        c.drawLine(cx, cy + 7.4f * d, cx, cy + 12.5f * d, stroke)                                             // 기둥
        c.drawLine(cx - 4.5f * d, cy + 12.5f * d, cx + 4.5f * d, cy + 12.5f * d, stroke)                      // 바닥
    }

    private fun globe(c: Canvas, cx: Float, cy: Float, d: Float) {
        val r = 12.6f * d
        stroke.strokeWidth = 1.6f * d
        c.drawCircle(cx, cy, r, stroke)
        c.drawOval(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r, stroke)
        c.drawLine(cx, cy - r, cx, cy + r, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke)
        val y = r * 0.55f; val x = r * 0.84f
        c.drawLine(cx - x, cy - y, cx + x, cy - y, stroke)
        c.drawLine(cx - x, cy + y, cx + x, cy + y, stroke)
    }

    /** 활짝 웃는 얼굴: 굵은 원, 세로로 살짝 긴 눈, 윗줄(치아)은 비우고 아래만 채운 입. */
    private fun smiley(c: Canvas, cx: Float, cy: Float, d: Float) {
        stroke.strokeWidth = 2f * d
        c.drawCircle(cx, cy, 10.06f * d, stroke)
        for (sx in floatArrayOf(-2.9f, 2.9f))
            c.drawOval(cx + (sx - 1.15f) * d, cy - 4.39f * d, cx + (sx + 1.15f) * d, cy - 1.28f * d, fill)
        val mouth = Path().apply {
            moveTo(cx - 6.10f * d, cy + 1.39f * d)
            lineTo(cx + 6.10f * d, cy + 1.39f * d)
            cubicTo(cx + 6.10f * d, cy + 5.24f * d, cx + 3.32f * d, cy + 7.49f * d, cx, cy + 7.49f * d)
            cubicTo(cx - 3.32f * d, cy + 7.49f * d, cx - 6.10f * d, cy + 5.24f * d, cx - 6.10f * d, cy + 1.39f * d)
            close()
        }
        stroke.strokeWidth = 1.3f * d
        c.drawPath(mouth, stroke)
        c.save(); c.clipRect(cx - 7 * d, cy + 3.42f * d, cx + 7 * d, cy + 8 * d); c.drawPath(mouth, fill); c.restore()
    }
}
