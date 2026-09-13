package com.timestamp.recorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews

/**
 * 小组件类型二：单事件大按钮。
 * 创建时通过配置页绑定一个事件，桌面显示该事件色大按钮（圆角），点击即记录。
 */
class WidgetSingleProvider : AppWidgetProvider() {

    companion object {
        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val repo = EventRepository(context)
            val eventId = WidgetPrefs.singleBind(context, appWidgetId)
            val event = if (eventId > 0) repo.getEvent(eventId) else null

            val views = RemoteViews(context.packageName, R.layout.widget_single)
            if (event != null) {
                views.setTextViewText(R.id.tvName, event.name)
                val last = repo.lastRecord(event.id)
                views.setTextViewText(R.id.tvInfo, if (last != null) TimeFormat.full(last) else "")
            } else {
                // 绑定的事件已删除：提示重新配置
                views.setTextViewText(R.id.tvName, context.getString(R.string.widget_single_missing))
                views.setTextViewText(R.id.tvInfo, "")
            }

            // 圆角背景 + 事件色 tint（纯色 shape 被 tint 直接替换为事件色）
            val corner = WidgetPrefs.corner(context)
            views.setInt(R.id.rowRoot, "setBackgroundResource", WidgetPrefs.cornerRes(corner))
            if (event != null) {
                views.setColorStateList(R.id.rowRoot, "setBackgroundTintList", ColorStateList.valueOf(event.color))
            } else {
                views.setColorStateList(R.id.rowRoot, "setBackgroundTintList", ColorStateList.valueOf(0xFF546E7A.toInt()))
            }

            val clickIntent = Intent(context, TimestampWidgetProvider::class.java).apply {
                action = TimestampWidgetProvider.ACTION_RECORD
                putExtra(TimestampWidgetProvider.EXTRA_EVENT_ID, eventId)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                eventId.toInt(),
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.rowRoot, pi)
            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildRemoteViews(context, it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.removeSingleBind(context, it) }
    }
}
