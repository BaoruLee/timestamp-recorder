package com.timestamp.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/** 自绘柱状图：用于各事件记录数 / 近期每日记录趋势。零三方依赖。 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val items = mutableListOf<BarItem>()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val density = context.resources.displayMetrics.density

    init {
        val onSurfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
        val onSurface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, 0
        )
        labelPaint.color = onSurfaceVariant
        labelPaint.textSize = 11f * density
        valuePaint.color = onSurface
        valuePaint.textSize = 12f * density
    }

    fun setData(data: List<BarItem>) {
        items.clear()
        items.addAll(data)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (200f * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (items.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                context.getString(R.string.stats_empty_hint),
                w / 2f, h / 2f, labelPaint
            )
            return
        }
        val padX = 14f * density
        val topPad = 22f * density
        val bottomPad = 26f * density
        val usable = h - topPad - bottomPad
        val n = items.size
        val slot = (w - 2 * padX) / n
        val barW = (slot * 0.6f).coerceAtLeast(6f * density)
        val max = items.maxOf { it.value }.coerceAtLeast(1)
        // 柱子多的时候（如近 14 天）逐条画标签会糊成一片，隔条画并让文案自适应宽度
        val labelStep = if (slot < 16f * density) 2 else 1
        val labelMaxW = slot - 2f * density

        items.forEachIndexed { i, item ->
            val cx = padX + slot * i + slot / 2f
            val barH = if (item.value == 0) 2f * density else (item.value.toFloat() / max) * usable
            val left = cx - barW / 2f
            val top = h - bottomPad - barH
            val bottom = h - bottomPad
            barPaint.color = item.color
            canvas.drawRoundRect(RectF(left, top, left + barW, bottom), barW / 2f, barW / 2f, barPaint)

            if (item.value > 0) {
                valuePaint.textAlign = Paint.Align.CENTER
                canvas.drawText(item.value.toString(), cx, top - 6f * density, valuePaint)
            }
            if (i % labelStep == 0) {
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(fit(item.label, labelMaxW), cx, h - 8f * density, labelPaint)
            }
        }
    }

    /** 按可用宽度裁剪标签，放不下就省略尾部，避免相邻标签互相压字 */
    private fun fit(s: String, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (labelPaint.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && labelPaint.measureText(t + "…") > maxW) t = t.dropLast(1)
        return if (t.isEmpty()) "" else t + "…"
    }
}

data class BarItem(val label: String, val value: Int, val color: Int)
