package com.timestamp.recorder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.timestamp.recorder.databinding.ActivityWidgetConfigureBinding
import com.timestamp.recorder.databinding.ItemWidgetBindBinding

/**
 * 单事件小组件配置页：选择该小组件要绑定的目标事件。
 * 绑定后桌面显示该事件色大按钮，点击即记录该事件。
 */
class WidgetSingleConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var repo: EventRepository
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = EventRepository(this)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        binding.toolbar.title = getString(R.string.widget_single_bind_title)
        binding.tvHint.text = getString(R.string.widget_single_bind_hint)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updatePadding(top = top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        val events = repo.getEvents()
        binding.recyclerBind.layoutManager = LinearLayoutManager(this)
        binding.recyclerBind.adapter = Adapter(events)

        if (events.isEmpty()) {
            binding.tvHint.text = getString(R.string.widget_single_no_event)
        }
    }

    private fun onPick(eventId: Long) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        WidgetPrefs.saveSingleBind(this, widgetId, eventId)
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, WidgetSingleProvider.buildRemoteViews(this, widgetId))
        setResult(RESULT_OK)
        finish()
    }

    private inner class Adapter(private val events: List<TimestampEvent>) :
        RecyclerView.Adapter<Adapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemWidgetBindBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = events.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ev = events[position]
            holder.b.tvName.text = ev.name
            holder.b.tvSub.text = getString(R.string.event_count, repo.recordCount(ev.id))
            holder.b.viewDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ev.color)
            }
            holder.b.root.setOnClickListener { onPick(ev.id) }
        }

        inner class VH(val b: ItemWidgetBindBinding) : RecyclerView.ViewHolder(b.root)
    }
}
