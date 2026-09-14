package com.timestamp.recorder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.timestamp.recorder.databinding.ActivityEventDetailBinding
import com.timestamp.recorder.databinding.DialogEditEventBinding
import com.timestamp.recorder.databinding.ItemRecordBinding
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

/** 事件详情页：一键记录 + 记录列表管理（含批量勾选）+ 导出 */
class EventDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    private lateinit var binding: ActivityEventDetailBinding
    private lateinit var repo: EventRepository
    private var eventId: Long = -1L
    private val adapter = RecordAdapter()

    /** 批量管理模式 */
    private var selectionMode = false
    private val selected = mutableSetOf<Long>()

    private val exportLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                exportTo(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupChrome(binding.toolbar, binding.appBar, binding.root, R.string.app_name, showBack = true)
        repo = EventRepository(this)
        eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)

        binding.recyclerRecords.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecords.adapter = adapter

        binding.btnRecord.setOnClickListener { record() }
        binding.btnUndo.setOnClickListener { undo() }

        // 批量管理模式下的底部操作栏
        binding.btnSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnDeleteSelected.setOnClickListener { deleteSelected() }
        binding.btnCancelSelect.setOnClickListener { exitSelectionMode() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun currentEvent(): TimestampEvent? = repo.getEvent(eventId)

    private fun refresh() {
        val event = currentEvent()
        if (event == null) {
            finish()
            return
        }
        binding.toolbar.title = event.name
        binding.btnRecord.iconTint = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(event.color)
        val records = repo.getRecords(eventId)
        adapter.submit(records)
        val last = records.firstOrNull()
        binding.tvStats.text = getString(
            R.string.detail_stats,
            records.size,
            last?.let { TimeFormat.hm(it) } ?: getString(R.string.event_no_record)
        )
        binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        // 列表变化后，若仍处于选择模式则同步计数（如刚删除完）
        if (selectionMode) updateSelectionUI()
    }

    private fun record() {
        val millis = repo.addRecord(eventId)
        refresh()
        Snackbar.make(binding.root, getString(R.string.toast_recorded, TimeFormat.full(millis)), Snackbar.LENGTH_SHORT).show()
        WidgetRecordHelper.refreshAll(this)
    }

    private fun undo() {
        if (repo.undoLast(eventId)) {
            refresh()
            WidgetRecordHelper.refreshAll(this)
        } else {
            Snackbar.make(binding.root, R.string.toast_no_undo, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copy(millis: Long) {
        val text = TimeFormat.full(millis) + "  (Unix 秒: " + (millis / 1000) + ")"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("timestamp", text))
        Snackbar.make(binding.root, getString(R.string.toast_copied, text), Snackbar.LENGTH_LONG).show()
    }

    private fun confirmDelete(position: Int) {
        val millis = adapter.items[position]
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_record_title)
            .setMessage(getString(R.string.delete_record_msg, TimeFormat.full(millis)))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteRecord(eventId, position)
                refresh()
                WidgetRecordHelper.refreshAll(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 批量管理 ----------

    private fun enterSelectionMode() {
        if (adapter.items.isEmpty()) {
            Snackbar.make(binding.root, R.string.toast_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        selectionMode = true
        selected.clear()
        binding.selectionBar.visibility = View.VISIBLE
        // 给底部操作栏留出空间，避免最后几条被遮挡
        binding.recyclerRecords.updatePadding(bottom = 140)
        adapter.notifyDataSetChanged()
        updateSelectionUI()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selected.clear()
        binding.selectionBar.visibility = View.GONE
        binding.recyclerRecords.updatePadding(bottom = 24)
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(millis: Long) {
        if (selected.contains(millis)) selected.remove(millis) else selected.add(millis)
        adapter.notifyDataSetChanged()
        updateSelectionUI()
    }

    private fun toggleSelectAll() {
        if (selected.size == adapter.items.size) {
            selected.clear()
        } else {
            selected.addAll(adapter.items)
        }
        adapter.notifyDataSetChanged()
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val n = selected.size
        binding.tvSelectedCount.text = getString(R.string.batch_selected_count, n)
        binding.btnDeleteSelected.text = getString(R.string.batch_delete_with_count, n)
        binding.btnSelectAll.setText(
            if (n == adapter.items.size && adapter.items.isNotEmpty()) {
                R.string.batch_deselect_all
            } else {
                R.string.batch_select_all
            }
        )
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Snackbar.make(binding.root, R.string.batch_none_selected, Snackbar.LENGTH_SHORT).show()
            return
        }
        val n = selected.size
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_delete_title)
            .setMessage(getString(R.string.batch_delete_msg, n))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteRecords(eventId, selected.toSet())
                exitSelectionMode()
                refresh()
                WidgetRecordHelper.refreshAll(this)
                Snackbar.make(binding.root, getString(R.string.batch_deleted, n), Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 菜单 ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, R.string.menu_edit_event)
        menu.add(Menu.NONE, 2, 0, R.string.export_records)
        menu.add(Menu.NONE, 3, 0, R.string.menu_clear_records)
        menu.add(Menu.NONE, 4, 0, R.string.menu_delete_event)
        val batchItem = menu.add(Menu.NONE, 5, 0, R.string.menu_batch)
        batchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        batchItem.setIcon(R.drawable.ic_check_box)
        menu.add(Menu.NONE, 6, 0, R.string.menu_stats)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> showEditDialog()
            2 -> startExport()
            3 -> confirmClear()
            4 -> confirmDeleteEvent()
            5 -> enterSelectionMode()
            6 -> startActivity(Intent(this, StatsActivity::class.java)
                .putExtra(StatsActivity.EXTRA_FOCUS_EVENT_ID, eventId))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showEditDialog() {
        val event = currentEvent() ?: return
        val dlg = DialogEditEventBinding.inflate(layoutInflater)
        val colorAdapter = ColorAdapter(event.color)
        dlg.recyclerColors.layoutManager = GridLayoutManager(this, 6)
        dlg.recyclerColors.adapter = colorAdapter
        dlg.editName.setText(event.name)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_edit_event_title)
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
                repo.updateEvent(event.id, name, colorAdapter.selected)
                dialog.dismiss()
                refresh()
                WidgetRecordHelper.refreshAll(this@EventDetailActivity)
            }
        }
        dialog.show()
    }

    private fun confirmClear() {
        val count = repo.recordCount(eventId)
        if (count == 0) {
            Snackbar.make(binding.root, R.string.toast_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_records_title)
            .setMessage(getString(R.string.clear_records_msg, count))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.clearRecords(eventId)
                refresh()
                WidgetRecordHelper.refreshAll(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteEvent() {
        val event = currentEvent() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_event_title)
            .setMessage(getString(R.string.delete_event_msg, event.name, repo.recordCount(event.id)))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.deleteEvent(eventId)
                WidgetRecordHelper.refreshAll(this)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 导出 ----------

    private fun startExport() {
        val count = repo.recordCount(eventId)
        if (count == 0) {
            Snackbar.make(binding.root, R.string.toast_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val now = TimeFormat.full(System.currentTimeMillis()).replace(':', '-').replace(' ', '_')
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/comma-separated-values"
            putExtra(Intent.EXTRA_TITLE, "时间戳记录_$now.csv")
        }
        exportLauncher.launch(intent)
    }

    private fun exportTo(uri: Uri) {
        try {
            val os = contentResolver.openOutputStream(uri) ?: return
            val writer = OutputStreamWriter(os, StandardCharsets.UTF_8)
            val records = repo.getRecords(eventId)
            val total = records.size
            // CSV：事件名, 记录时间, Unix秒（新记录在前）
            writer.write("\uFEFF事件,记录时间,Unix秒\n")
            records.forEachIndexed { i, millis ->
                val name = currentEvent()?.name?.replace(",", "，").orEmpty()
                writer.write("$name,${TimeFormat.full(millis)},${millis / 1000}\n")
            }
            writer.flush()
            writer.close()
            Snackbar.make(binding.root, getString(R.string.toast_exported, total), Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, R.string.toast_export_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    // ---------- 记录列表 ----------

    private inner class RecordAdapter : RecyclerView.Adapter<RecordAdapter.VH>() {

        val items = mutableListOf<Long>()

        fun submit(list: List<Long>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val millis = items[position]
            val inSelection = this@EventDetailActivity.selectionMode
            holder.b.tvIndex.text = String.format(Locale.getDefault(), "#%d", items.size - position)
            holder.b.tvTime.text = TimeFormat.full(millis)
            holder.b.tvUnix.text = getString(R.string.unix_label, millis / 1000)
            holder.b.tvCopy.visibility = if (inSelection) View.GONE else View.VISIBLE
            holder.b.cbSelect.visibility = if (inSelection) View.VISIBLE else View.GONE
            holder.b.cbSelect.isChecked = this@EventDetailActivity.selected.contains(millis)
            if (inSelection) {
                holder.b.root.setOnClickListener { toggleSelection(millis) }
                holder.b.root.setOnLongClickListener { toggleSelection(millis); true }
            } else {
                holder.b.tvCopy.setOnClickListener { copy(millis) }
                holder.b.root.setOnClickListener { copy(millis) }
                holder.b.root.setOnLongClickListener {
                    confirmDelete(position)
                    true
                }
            }
        }

        inner class VH(val b: ItemRecordBinding) : RecyclerView.ViewHolder(b.root)
    }
}
