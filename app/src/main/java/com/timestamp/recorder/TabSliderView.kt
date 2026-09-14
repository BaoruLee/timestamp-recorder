package com.timestamp.recorder

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 底部胶囊岛的「选中态滑块」。
 *
 * View 本身铺满整座岛（match_parent），但 [onDraw] 只画「当前选中 Tab」位置上的那块
 * 玻璃胶囊；位置由 [fraction]（0~1，ViewPager2 的 onPageScrolled 逐帧喂入）决定，
 * 所以天然跟手、可急停、可反向。
 *
 * ⚠️ 为什么不用「一个窄 View 平移 + 改 layoutParams.width」：几何全靠布局参数联动，
 *   在不同密度机型上差一档就会把整条岛铺满（真机实测踩过）；画出来的尺寸永远由
 *   onDraw 里的等分计算决定，与密度无关。
 */
class TabSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 0 = 停在第 0 个 Tab（事件），1 = 第 1 个（时间线）；拖动过程中是 0~1 的连续值 */
    var fraction = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val inset = resources.getDimensionPixelSize(R.dimen.space_1).toFloat()  // 胶囊上下左右各缩 4dp
        // 中缝禁区：加号玻璃圆钮（直径 fab_glass_size）居中占据岛中部，
        // 滑块胶囊与它之间留 4dp 呼吸缝 —— 静置时滑块不被圆钮遮挡（阴影/边缘完整）。
        val halfGap = resources.getDimensionPixelSize(R.dimen.fab_glass_size) / 2f +
                resources.getDimensionPixelSize(R.dimen.space_1)
        val midL = width / 2f - halfGap   // 左滑块右缘
        val midR = width / 2f + halfGap   // 右滑块左缘
        val leftEdge = inset
        val rightEdge = width - inset
        if (midL <= leftEdge || midR >= rightEdge || height <= 2 * inset) return
        // fraction 0→1：滑块从左 Tab 位置连续滑到右 Tab 位置（宽度恒定、中心贴 Tab 中心）；
        // 滑动中途滑块会从圆钮底下穿过（按钮在最上层，与桌面 tab 风格一致）
        val l = leftEdge + fraction * (midR - leftEdge)
        val r = midL + fraction * (rightEdge - midL)
        rect.set(l, inset, r, height - inset)

        fillPaint.style = Paint.Style.FILL
        // 白天在浅玻璃上「白上加白」看不出边界，深色用白微光；描边把轮廓交代清楚
        fillPaint.color = if (night) 0x2EFFFFFF.toInt() else 0x1F000000
        // 圆角 = 滑块高的一半（完整胶囊）——与外层岛（28dp 圆角完整胶囊）同一设计语言
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = resources.getDimensionPixelSize(R.dimen.space_1) / 4f
        strokePaint.color = if (night) 0x33FFFFFF else 0x2E5B6B8C
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }
}
