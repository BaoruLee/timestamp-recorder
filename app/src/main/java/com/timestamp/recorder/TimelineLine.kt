package com.timestamp.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** 覆盖 alpha（保留 RGB）—— 时间线各处共用 */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

/**
 * 时间线的染色竖线（自绘）。
 *
 * 为什么不用「纯色背景 + GradientDrawable 拼渐变」：
 * 渐变停靠点需要一条**固定 dp 的过渡带**（约 22dp），而不是按整条行高平均分配 ——
 * 后者会让每一行都变成一段长长的晕染，看着很不自然。交给 View 自己按实测高度算，
 * 过渡带永远是那一条，行高怎么变都一致。
 *
 * 三种形态：
 * - [Mode.SEGMENT]：一行记录 / 一个月标题。顶部 22dp 内从「上一条的颜色」过渡到自己的颜色。
 * - [Mode.HEAD]：列表最上方的「起笔」——顶端就带自己的颜色（约 40% 透明度），向下渐实，
 *   这样时间线的上端**不是一点颜色都没有**。
 * - [Mode.TAIL]：列表最下方的「收笔」——自己的颜色向下渐隐到约 40%，同样不会突然消失。
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

    /** 过渡带高度（22dp）：手感与底栏圆角一个量级，够短，才不会糊成一片 */
    private val band = 22f * resources.displayMetrics.density

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

    private fun buildShader(h: Float): Shader = when (mode) {
        Mode.HEAD -> {
            // 起笔：顶端 40% 透明度就有颜色，向下两个刻度渐实
            val b = (band * 2f).coerceAtMost(h)
            LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(withAlpha(ownColor, 0x66), withAlpha(ownColor, 0xCC), ownColor, ownColor),
                floatArrayOf(0f, b / h * 0.5f, b / h, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Mode.TAIL -> {
            // 收笔：本体颜色向下渐隐，但不归零
            val b = (band * 2f).coerceAtMost(h)
            val t = 1f - b / h
            LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(ownColor, ownColor, withAlpha(ownColor, 0xCC), withAlpha(ownColor, 0x66)),
                floatArrayOf(0f, t, t + (1f - t) * 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Mode.SEGMENT -> {
            val prev = prevColor
            when {
                prev == null || prev == ownColor -> {
                    // 没有上一条（或同色）：整条就是本色，顶端稍作淡入避免生硬
                    val b = (band * 0.6f).coerceAtMost(h)
                    LinearGradient(
                        0f, 0f, 0f, h,
                        intArrayOf(withAlpha(ownColor, 0x00), ownColor),
                        floatArrayOf(0f, b / h),
                        Shader.TileMode.CLAMP
                    )
                }
                else -> {
                    // 5 段插值 + 缓入缓出：短距离内把色调换过去，且中途不发灰
                    val b = band.coerceAtMost(h)
                    val stops = 5
                    val colors = IntArray(stops)
                    val pos = FloatArray(stops)
                    for (i in 0 until stops) {
                        val t = i / (stops - 1f)
                        colors[i] = mixHsv(prev, ownColor, smooth(t))
                        pos[i] = (b / h) * t
                    }
                    colors[stops - 1] = ownColor
                    pos[stops - 1] = b / h
                    LinearGradient(0f, 0f, 0f, h, colors, pos, Shader.TileMode.CLAMP)
                }
            }
        }
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    /** HSV 最短色相路径插值：红→绿走黄、蓝→红走紫，不像 RGB 直插那样经过脏灰 */
    private fun mixHsv(a: Int, b: Int, t: Float): Int {
        val ha = FloatArray(3).also { Color.colorToHSV(a, it) }
        val hb = FloatArray(3).also { Color.colorToHSV(b, it) }
        var dh = hb[0] - ha[0]
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f
        val h = (ha[0] + dh * t + 360f) % 360f
        val s = ha[1] + (hb[1] - ha[1]) * t
        val v = ha[2] + (hb[2] - ha[2]) * t
        return Color.HSVToColor(255, floatArrayOf(h, s, v))
    }
}
