package com.timestamp.recorder

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.timestamp.recorder.databinding.ActivityMainBinding
import com.timestamp.recorder.databinding.DialogEditEventBinding
import com.timestamp.recorder.databinding.ItemEventBinding

/** 主页：事件（分类）管理 + 染色 + 液态玻璃快捷按钮（位置可设）+ 小组件入口 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: EventRepository
    private val adapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        // 沉浸式：状态栏/导航栏透明（通杀各品牌，含小米 HyperOS 手势条）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = EventRepository(this)

        // 状态栏 inset：工具栏下沉到状态栏之下，背景渐变延伸至状态栏（无黑边）
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updatePadding(top = top)
            insets
        }
        // 导航栏 inset：底部内容上移，避开手势条/三键
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        binding.recyclerEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerEvents.adapter = adapter

        binding.fabAdd.setOnClickListener { showEditDialog(null) }
        applyFabPosition()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        applyFabPosition()
    }

    /** 快捷按钮位置：左 / 中 / 右（设置页可切换，立即生效） */
    private fun applyFabPosition() {
        val pos = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_FAB_POS, SettingsActivity.FAB_END) ?: SettingsActivity.FAB_END
        val lp = binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams
        lp.gravity = when (pos) {
            SettingsActivity.FAB_START -> Gravity.START or Gravity.BOTTOM
            SettingsActivity.FAB_CENTER -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            else -> Gravity.END or Gravity.BOTTOM
        }
        binding.fabAdd.layoutParams = lp
    }

    // ---------- 菜单（设置入口） ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, R.string.menu_settings)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        val events = repo.getEvents()
        adapter.submit(events)
        binding.tvEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(event: TimestampEvent?) {
        val dlg = DialogEditEventBinding.inflate(layoutInflater)
        val colorAdapter = ColorAdapter(event?.color ?: EventColors.random())
        dlg.recyclerColors.layoutManager = GridLayoutManager(this, 6)
        dlg.recyclerColors.adapter = colorAdapter
        if (event != null) dlg.editName.setText(event.name)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (event == null) R.string.dialog_add_event_title else R.string.dialog_edit_event_title)
            .setView(dlg.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        // 必须在 show() 之前设置：show() 内部同步触发 onShow，之后设置会错过回调
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dlg.editName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    dlg.inputName.error = getString(R.string.toast_name_required)
                    return@setOnClickListener
                }
                if (event == null) {
                    repo.addEvent(name, colorAdapter.selected)
                } else {
                    repo.updateEvent(event.id, name, colorAdapter.selected)
                }
                dialog.dismiss()
                refresh()
                WidgetRecordHelper.refreshAll(this@MainActivity)
            }
        }
        dialog.show()
    }

    private fun showEventMenu(event: TimestampEvent) {
        MaterialAlertDialogBuilder(this)
            .setTitle(event.name)
            .setItems(arrayOf(getString(R.string.menu_edit_event), getString(R.string.menu_delete_event))) { _, which ->
                when (which) {
                    0 -> showEditDialog(event)
                    1 -> confirmDeleteEvent(event)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteEvent(event: TimestampEvent) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_event_title)
            .setMessage(getString(R.string.delete_event_msg, event.name, repo.recordCount(event.id)))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteEvent(event.id)
                WidgetRecordHelper.refreshAll(this)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 事件列表适配器 ----------

    private inner class EventAdapter : RecyclerView.Adapter<EventAdapter.VH>() {

        private val items = mutableListOf<TimestampEvent>()

        fun submit(list: List<TimestampEvent>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(private val b: ItemEventBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(event: TimestampEvent) {
                b.tvEventName.text = event.name
                val count = repo.recordCount(event.id)
                val last = repo.lastRecord(event.id)
                b.tvEventInfo.text = if (last != null) {
                    getString(R.string.event_last, TimeFormat.hm(last))
                } else {
                    getString(R.string.event_no_record)
                }
                b.tvEventCount.text = count.toString()
                b.viewColorDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(event.color)
                }
                b.root.setOnClickListener { openDetail(event) }
                b.root.setOnLongClickListener {
                    showEventMenu(event)
                    true
                }
            }
        }
    }

    private fun openDetail(event: TimestampEvent) {
        startActivity(Intent(this, EventDetailActivity::class.java).putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id))
    }
}

