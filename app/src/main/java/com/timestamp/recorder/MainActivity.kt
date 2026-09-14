package com.timestamp.recorder

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.timestamp.recorder.databinding.ActivityMainBinding
import com.timestamp.recorder.databinding.DialogEditEventBinding
import com.timestamp.recorder.databinding.ItemEventBinding
import java.util.Collections

/** 主页：事件（分类）管理 + 染色 + 快捷记录 + 拖拽排序 + 统计入口 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: EventRepository
    private val adapter = EventAdapter()
    private var dragEnabled = false

    private val touchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.move(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
            override fun isItemViewSwipeEnabled() = false
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                adapter.persistOrder()
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupChrome(binding.toolbar, binding.appBar, binding.root, R.string.main_title, showBack = false)
        repo = EventRepository(this)

        binding.recyclerEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerEvents.adapter = adapter
        touchHelper.attachToRecyclerView(binding.recyclerEvents)

        binding.fabAdd.setOnClickListener { showEditDialog(null) }
        applyFabPosition()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        applyFabPosition()
        WidgetRecordHelper.refreshAll(this)
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

    private fun currentSortMode(): String =
        getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_SORT_MODE, SettingsActivity.SORT_MANUAL) ?: SettingsActivity.SORT_MANUAL

    // ---------- 菜单（设置 + 统计入口） ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, R.string.menu_settings)
        menu.add(Menu.NONE, 2, 0, R.string.menu_stats)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            2 -> { startActivity(Intent(this, StatsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        dragEnabled = currentSortMode() == SettingsActivity.SORT_MANUAL
        val events = if (dragEnabled) repo.getEventsManualOrder() else repo.getEventsByRecent()
        adapter.submit(events)
        binding.tvEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun quickRecord(event: TimestampEvent) {
        repo.addRecord(event.id)
        Toast.makeText(this, getString(R.string.toast_recorded, event.name), Toast.LENGTH_SHORT).show()
        refresh()
        WidgetRecordHelper.refreshAll(this)
    }

    private fun showEditDialog(event: TimestampEvent?) {
        val dlg = DialogEditEventBinding.inflate(layoutInflater)
        val colorAdapter = ColorAdapter(event?.color ?: EventColors.random())
        dlg.recyclerColors.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 6)
        dlg.recyclerColors.adapter = colorAdapter
        if (event != null) dlg.editName.setText(event.name)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (event == null) R.string.dialog_add_event_title else R.string.dialog_edit_event_title)
            .setView(dlg.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dlg.editName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    dlg.inputName.error = getString(R.string.toast_name_required)
                    return@setOnClickListener
                }
                if (event == null) repo.addEvent(name, colorAdapter.selected)
                else repo.updateEvent(event.id, name, colorAdapter.selected)
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

    private fun openDetail(event: TimestampEvent) {
        startActivity(Intent(this, EventDetailActivity::class.java)
            .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id))
    }

    // ---------- 事件列表适配器（支持拖拽重排） ----------

    private inner class EventAdapter : RecyclerView.Adapter<EventAdapter.VH>() {

        val items = mutableListOf<TimestampEvent>()

        fun submit(list: List<TimestampEvent>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun move(from: Int, to: Int) {
            if (from == to || from !in items.indices || to !in items.indices) return
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        fun persistOrder() {
            repo.setEventsOrder(items.map { it.id })
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
                b.btnQuickRecord.backgroundTintList = ColorStateList.valueOf(event.color)
                b.btnQuickRecord.setOnClickListener { quickRecord(event) }
                b.root.setOnClickListener { openDetail(event) }
                b.btnMenu.setOnClickListener { showEventMenu(event) }

                if (dragEnabled) {
                    b.btnDrag.visibility = View.VISIBLE
                    b.btnDrag.setOnTouchListener { _, e ->
                        if (e.action == MotionEvent.ACTION_DOWN) touchHelper.startDrag(this)
                        false
                    }
                } else {
                    b.btnDrag.visibility = View.GONE
                }
            }
        }
    }
}
