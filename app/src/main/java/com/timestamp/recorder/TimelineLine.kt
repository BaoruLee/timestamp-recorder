package com.timestamp.recorder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 时间线竖线的染色。
 *
 * 时间线上相邻记录往往属于不同事件（不同颜色），直接硬切会看着「一段一段」的，像被截断。
 * 这里把「上一条的颜色 → 本条的颜色」做成一条**竖向渐变**，中点用 **HSV 插值**
 * （而非 RGB 直插：RGB 直插下红→绿会经过脏灰，观感很糟）。
 * 于是整条时间线读起来是连绵过渡的一条时光，而不是若干个色块拼接。
 */
object TimelineLine {

    /** 覆盖 alpha（保留 RGB） */
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * 生成一段竖线渐变。
     * @param prev 上一条（更早那条）的颜色；null 表示没有上一条 → 顶部淡入
     * @param own  本条的颜色（记录 = 事件色；月份 = 该月首条记录的颜色）
     */
    fun gradient(prev: Int?, own: Int): GradientDrawable {
        val stops = when {
            // 顶端淡入：从全透明到本色，让线像是从屏幕上方「长」出来的
            prev == null -> intArrayOf(withAlpha(own, 0x00), withAlpha(own, 0x66), own)
            prev == own -> intArrayOf(own, own)
            else -> intArrayOf(prev, mixHsv(prev, own, 0.5f), own)
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, stops).apply {
            shape = GradientDrawable.RECTANGLE
        }
    }

    /**
     * HSV 空间按最短色相路径插值：红→绿走黄，蓝→红走紫，不会经过灰。
     * 明度/饱和度按线性插值。
     */
    private fun mixHsv(a: Int, b: Int, t: Float): Int {
        val ha = FloatArray(3).also { Color.colorToHSV(a, it) }
        val hb = FloatArray(3).also { Color.colorToHSV(b, it) }
        var dh = hb[0] - ha[0]
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f
        val h = (ha[0] + dh * t + 360f) % 360f
        val s = ha[1] + (hb[1] - ha[1]) * t
        val v = ha[2] + (hb[2] - ha[2]) * t
        val alpha = (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt()
        return Color.HSVToColor(alpha, floatArrayOf(h, s, v))
    }
}
