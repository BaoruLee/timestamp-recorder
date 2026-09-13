package com.timestamp.recorder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** 「全部事件」小组件列表数据源：每个事件一行彩色圆角按钮 */
class WidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return Factory(applicationContext, intent)
    }

    class Factory(private val context: Context, intent: Intent) : RemoteViewsFactory {

        private val repo = EventRepository(context)
        private var events: List<TimestampEvent> = emptyList()

        override fun onCreate() {
            events = repo.getEvents()
        }

        override fun onDataSetChanged() {
            events = repo.getEvents()
        }

        override fun onDestroy() {}

        override fun getCount(): Int = events.size

        override fun getViewAt(position: Int): RemoteViews {
            val ev = events[position]
            val rv = RemoteViews(context.packageName, R.layout.item_widget_event)
            rv.setTextViewText(R.id.tvName, ev.name)
            val last = repo.lastRecord(ev.id)
            rv.setTextViewText(R.id.tvInfo, if (last != null) TimeFormat.hm(last) else "")

            // 圆角背景 + 事件色 tint（颜色即分类）
            val corner = WidgetPrefs.corner(context)
            rv.setInt(R.id.rowRoot, "setBackgroundResource", WidgetPrefs.cornerRes(corner))
            rv.setColorStateList(R.id.rowRoot, "setBackgroundTintList", ColorStateList.valueOf(ev.color))

            val pi = PendingIntent.getBroadcast(
                context,
                ev.id.toInt(),
                Intent(context, TimestampWidgetProvider::class.java).apply {
                    action = TimestampWidgetProvider.ACTION_RECORD
                    putExtra(TimestampWidgetProvider.EXTRA_EVENT_ID, ev.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.rowRoot, pi)
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = events.getOrNull(position)?.id ?: 0L

        override fun hasStableIds(): Boolean = true
    }
}
