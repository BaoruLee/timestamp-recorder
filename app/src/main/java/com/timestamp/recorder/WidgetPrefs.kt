package com.timestamp.recorder

import android.content.Context

/** 小组件公共配置：圆角档位 + 单事件小组件绑定存储 */
object WidgetPrefs {
    const val PREFS = "tsr_widget"
    const val KEY_CORNER = "widget_corner"          // 圆角档位：8 / 16 / 24 / 32
    const val CORNER_DEFAULT = 16

    // 单事件小组件的绑定：single_<widgetId> -> eventId
    private fun singleKey(widgetId: Int) = "single_$widgetId"

    fun corner(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CORNER, CORNER_DEFAULT)

    fun setCorner(context: Context, corner: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CORNER, corner).apply()
    }

    fun saveSingleBind(context: Context, widgetId: Int, eventId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(singleKey(widgetId), eventId).apply()
    }

    fun singleBind(context: Context, widgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(singleKey(widgetId), -1L)

    fun removeSingleBind(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(singleKey(widgetId)).apply()
    }

    /** 圆角档位对应的按钮背景资源 */
    fun cornerRes(corner: Int): Int = when (corner) {
        8 -> R.drawable.bg_corner_r8
        24 -> R.drawable.bg_corner_r24
        32 -> R.drawable.bg_corner_r32
        else -> R.drawable.bg_corner_r16
    }

    /** 圆角档位对应的小组件容器背景资源 */
    fun widgetBgRes(corner: Int): Int = when (corner) {
        8 -> R.drawable.widget_bg_r8
        24 -> R.drawable.widget_bg_r24
        32 -> R.drawable.widget_bg_r32
        else -> R.drawable.widget_bg_r16
    }
}
