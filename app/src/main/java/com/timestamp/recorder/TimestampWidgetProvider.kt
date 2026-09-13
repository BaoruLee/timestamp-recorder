package com.timestamp.recorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * 小组件类型一：全部事件列表。
 * 显示所有事件的彩色按钮，点击对应事件立即记录。
 * 圆角档位来自设置页（WidgetPrefs.KEY_CORNER）。
 */
class TimestampWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_RECORD = "com.timestamp.recorder.ACTION_RECORD"
        const val EXTRA_EVENT_ID = "extra_event_id"

        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val corner = WidgetPrefs.corner(context)
            val views = RemoteViews(context.packageName, R.layout.widget_timestamp)
            // 容器背景：按圆角档位选择渐变圆角背景
            views.setInt(R.id.widgetRoot, "setBackgroundResource", WidgetPrefs.widgetBgRes(corner))

            val serviceIntent = Intent(context, WidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetList, serviceIntent)
            views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPi)
            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildRemoteViews(context, it)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RECORD) {
            WidgetRecordHelper.handleRecord(context, intent)
        }
    }
}

/** 小组件点击记录公共处理：记录 + 刷新两种类型全部实例 */
object WidgetRecordHelper {
    fun handleRecord(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(TimestampWidgetProvider.EXTRA_EVENT_ID, -1L)
        if (eventId > 0) {
            EventRepository(context).addRecord(eventId)
            refreshAll(context)
        }
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val allIds = manager.getAppWidgetIds(ComponentName(context, TimestampWidgetProvider::class.java))
        allIds.forEach { id ->
            manager.updateAppWidget(id, TimestampWidgetProvider.buildRemoteViews(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widgetList)
        }
        val singleIds = manager.getAppWidgetIds(ComponentName(context, WidgetSingleProvider::class.java))
        singleIds.forEach { id ->
            manager.updateAppWidget(id, WidgetSingleProvider.buildRemoteViews(context, id))
        }
    }
}
