package com.timestamp.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** 覆盖 alpha（保留 RGB） */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

/**
 * 时间线的染色竖线（自绘）。
 *
 * 就用**两色线性渐变**：从上一条的颜色匀匀地过渡到本条的颜色。相邻两行的接缝处
 * 上一条的收尾色 = 下一条的起始色，所以整条线读起来是连续的，没有色阶跳变。
 *
 * 三种形态：
 * - [Mode.SEGMENT]：一行记录 / 一个月标题 —— 上一条色 → 本色。
 * - [Mode.HEAD]：列表最上方的「起笔」—— 本色（半透明）→ 本色，向上不归零。
 * - [Mode.TAIL]：列表最下方的「收笔」—— 本色 → 本色（半透明），向下不归零；
 *   它同时负责把列表底部那段留白也染上色，所以拉到最底不会出现「一截没上色」。
 */
class TimelineLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { SEGMENT, HEAD, TAIL }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var prevColor: Int? = null
    private var ownColor: Int = Color.GRAY
    private var mode: Mode = Mode.SEGMENT

    fun setLine(prev: Int?, own: Int, mode: Mode = Mode.SEGMENT) {
        if (this.prevColor == prev && ownColor == own && this.mode == mode) return
        prevColor = prev
        ownColor = own
        this.mode = mode
        paint.shader = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        if (h <= 0f || width <= 0) return
        if (paint.shader == null) paint.shader = buildShader(h)
        canvas.drawRect(0f, 0f, width.toFloat(), h, paint)
    }

    /** 两色渐变：上 → 下 */
    private fun buildShader(h: Float): Shader {
        val top: Int
        val bottom: Int
        when (mode) {
            Mode.HEAD -> {
                top = withAlpha(ownColor, 0x80)   // 起笔：顶端就带颜色
                bottom = ownColor
            }
            Mode.TAIL -> {
                top = ownColor
                bottom = withAlpha(ownColor, 0x99) // 收笔：底端仍留 60% 颜色，不归零
            }
            Mode.SEGMENT -> {
                val prev = prevColor
                if (prev == null) {
                    top = withAlpha(ownColor, 0x66)
                    bottom = ownColor
                } else {
                    top = prev
                    bottom = ownColor
                }
            }
        }
        return LinearGradient(0f, 0f, 0f, h, intArrayOf(top, bottom), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
}
