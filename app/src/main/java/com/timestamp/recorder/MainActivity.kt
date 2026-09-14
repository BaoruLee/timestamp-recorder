package com.timestamp.recorder

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.timestamp.recorder.databinding.ItemTimelineMonthBinding
import com.timestamp.recorder.databinding.ItemTimelineRecordBinding
import java.util.Calendar
import java.util.Collections
import java.util.Locale

/**
 * 主页：底部「事件 / 时间线」双 Tab（参考 Last Time 布局）。
 * - 事件 Tab：事件卡片列表 + 快捷记录 + 拖拽排序
 * - 时间线 Tab：全部记录按月份分组（事件色圆点 + 事件名 + 具体时间 + 相对时间）
 * - 右下「＋」上移至 Tab 栏上方，位置可在设置页切换（左/中/右）
 */
class MainActivity : BaseActivity() {

    companion object {
        private const val TAB_EVENTS = 0
        private const val TAB_TIMELINE = 1
        private const val TYPE_MONTH = 0
        private const val TYPE_RECORD = 1
        /** 外部（如详情页菜单）指定直接打开时间线 Tab */
        const val EXTRA_OPEN_TIMELINE = "extra_open_timeline"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: EventRepository
    private val adapter = EventAdapter()
    private val timelineAdapter = TimelineAdapter()
    private var dragEnabled = false
    private var currentTab = TAB_EVENTS

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

        binding.recyclerTimeline.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimeline.adapter = timelineAdapter

        binding.fabAdd.setOnClickListener { showEditDialog(null) }
        binding.tabEvents.setOnClickListener { selectTab(TAB_EVENTS) }
        binding.tabTimeline.setOnClickListener { selectTab(TAB_TIMELINE) }
        applyFabPosition()
        styleTab()
        setupBottomBar()
        // 若从详情页菜单直达时间线 Tab，需在渲染后生效
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMELINE, false)) {
            selectTab(TAB_TIMELINE)
        }
    }

    /**
     * 底部 Tab 栏（酷安式毛玻璃 + 彻底沉浸）：
     * 1. 材质 = 截取 Tab 栏上方内容实时高斯模糊（滚动节流刷新），半透明罩统一
     *    明暗、顶部 1dp 高光细线——玻璃质感，非廉价液态玻璃；
     * 2. 沉浸 = root 底部 padding 强制为 0（覆盖 BaseActivity 默认），bottomBar
     *    高度动态 = 64dp + 导航栏 inset，直接延伸到屏幕底，玻璃背景覆盖手势条
     *    区域（系统手势条绘制在玻璃之上，自然融合）；Tab 内容层按导航栏高度
     *    加底部 padding 避开手势条，绝不遮挡；
     * 3. 全部标准 API + 关闭导航栏对比度强制（BaseActivity），跨品牌一致。
     */
    private fun setupBottomBar() {
        applyGlassStyle()
        // 沉浸：root 底部 padding 恒为 0，底部安全区交给 bottomBar 动态高度
        val immersive = object : androidx.core.view.OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(
                v: android.view.View,
                insets: androidx.core.view.WindowInsetsCompat
            ): androidx.core.view.WindowInsetsCompat {
                val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
                val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                v.setPadding(0, top, 0, 0)
                applyBarLayout(nav)
                return insets
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root, immersive)
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
        // 双保险：post 后 insets 已就绪，手动触发一次（小米 ROM insets 分发时序不可靠）
        binding.root.post {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            if (insets != null) {
                immersive.onApplyWindowInsets(binding.root, insets)
            } else {
                binding.root.setPadding(0, 0, 0, 0)
                applyBarLayout(0)
            }
        }
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) updateGlassBackdrop()
            }
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // 滚动中节流更新，实现实时模糊
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastGlassUpdate > 60L) {
                    lastGlassUpdate = now
                    updateGlassBackdrop()
                }
            }
        }
        binding.recyclerEvents.addOnScrollListener(scrollListener)
        binding.recyclerTimeline.addOnScrollListener(scrollListener)
        updateGlassBackdrop()
    }

    /** 动态栏高 = 内容高 + 导航栏 inset；内容层上移避开手势条 */
    private fun applyBarLayout(nav: Int) {
        val barH = resources.getDimensionPixelSize(R.dimen.tab_bar_height) + nav
        binding.bottomBar.layoutParams.height = barH
        binding.tabContent.setPadding(0, 0, 0, nav)
        updateGlassBackdrop()
    }

    /** 高斯模糊高级材质：半透明玻璃底 + RenderEffect 模糊；明暗色代码控制 */
    private fun applyGlassStyle() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        binding.bgGlass.setBackgroundColor(
            if (night) Color.argb(0xE6, 0x14, 0x12, 0x18)
            else Color.argb(0xCC, 0xFF, 0xFF, 0xFF))
        binding.bgDim.setBackgroundColor(
            if (night) Color.argb(0x59, 0x14, 0x12, 0x18)
            else Color.argb(0x40, 0xFF, 0xFF, 0xFF))
        // 高斯模糊作用于玻璃层自身（RenderEffect 只影响本层，Tab 文字在兄弟层不受影响）
        BlurHelper.applyBlur(binding.bgGlass, 26f)
    }

    private var lastGlassUpdate = 0L

    /**
     * 实时毛玻璃背景：隐藏 bottomBar 后 root.draw 同步截取其背后内容（dispatchDraw
     * 会跳过 INVISIBLE 的 View，截取到的是纯背后内容，位置精确、无自身/FAB 残影），
     * 高斯模糊后回填为玻璃背景；滚动中节流刷新实现实时模糊。
     */
    private fun updateGlassBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val bar = binding.bottomBar
        if (bar.width <= 0 || bar.height <= 0) {
            bar.post { updateGlassBackdrop() }
            return
        }
        val root = binding.root
        val w = bar.width
        val h = bar.height
        val x = bar.left.coerceIn(0, maxOf(0, root.width - w))
        val y = bar.top.coerceIn(0, maxOf(0, root.height - h))
        val prev = bar.visibility
        bar.visibility = View.INVISIBLE
        try {
            val rootBmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(rootBmp))
            bar.visibility = prev
            val crop = Bitmap.createBitmap(rootBmp, x, y, w, h)
            rootBmp.recycle()
            binding.bgGlass.setBackground(BitmapDrawable(resources, blurBitmap(crop)))
            crop.recycle()
        } catch (_: Exception) {
            bar.visibility = prev
        }
    }

    /** 缩放降采样 + 平滑放大，等效中等强度高斯模糊（内容可辨、玻璃质感） */
    private fun blurBitmap(src: Bitmap): Bitmap {
        val scale = 4
        val sw = maxOf(1, src.width / scale)
        val sh = maxOf(1, src.height / scale)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val result = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        small.recycle()
        return result
    }

    override fun onResume() {
        super.onResume()
        refresh()
        applyFabPosition()
        WidgetRecordHelper.refreshAll(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 手势条（小白条）融入玻璃：深色模式下强制深色 pill（融入深色玻璃，彻底沉浸）
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                val appear = if (night) {
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else 0
                window.insetsController?.setSystemBarsAppearance(
                    appear,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
            // 窗口 insets 在此后才完全就绪：强制应用沉浸布局 + 刷新模糊背景
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            if (insets != null) {
                val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
                val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                binding.root.setPadding(0, top, 0, 0)
                applyBarLayout(nav)
            }
            updateGlassBackdrop()
        }
    }

    /** 快捷按钮位置：左 / 中 / 右（设置页可切换，立即生效）；统一上移至底部 Tab 栏上方 */
    private fun applyFabPosition() {
        val pos = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_FAB_POS, SettingsActivity.FAB_END) ?: SettingsActivity.FAB_END
        val lp = binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams
        lp.gravity = when (pos) {
            SettingsActivity.FAB_START -> Gravity.START or Gravity.BOTTOM
            SettingsActivity.FAB_CENTER -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            else -> Gravity.END or Gravity.BOTTOM
        }
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val bottom = resources.getDimensionPixelSize(R.dimen.fab_bottom_margin)
        lp.setMargins(m, m, m, bottom)
        binding.fabAdd.layoutParams = lp
    }

    private fun currentSortMode(): String =
        getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_SORT_MODE, SettingsActivity.SORT_MANUAL) ?: SettingsActivity.SORT_MANUAL

    // ---------- 底部 Tab 切换 ----------

    private val tabPrimary: Int by lazy {
        com.google.android.material.color.MaterialColors.getColor(
            binding.tabEvents, com.google.android.material.R.attr.colorPrimary)
    }
    private val tabOnSurfaceVariant: Int by lazy {
        com.google.android.material.color.MaterialColors.getColor(
            binding.tabEvents, com.google.android.material.R.attr.colorOnSurfaceVariant)
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        styleTab()
        updateVisibility()
        updateGlassBackdrop()
    }

    /** 选中 Tab = 事件色胶囊高亮 + 主题色加粗；未选中 = 透明 + 次要色 */
    private fun styleTab() {
        val eventsSelected = currentTab == TAB_EVENTS
        setTabLook(binding.tabEvents, eventsSelected)
        setTabLook(binding.tabTimeline, !eventsSelected)
    }

    private fun setTabLook(tv: TextView, selected: Boolean) {
        if (selected) {
            val r = resources.displayMetrics.density
            tv.background = GradientDrawable().apply {
                cornerRadius = resources.getDimensionPixelSize(R.dimen.radius_lg).toFloat()
                setColor((tabPrimary and 0x00FFFFFF) or 0x26000000.toInt())
                setStroke((1 * r).toInt(), (tabPrimary and 0x00FFFFFF) or 0x3D000000.toInt())
            }
            tv.setTextColor(tabPrimary)
            tv.typeface = Typeface.DEFAULT_BOLD
        } else {
            tv.background = null
            tv.setTextColor(tabOnSurfaceVariant)
            tv.typeface = Typeface.DEFAULT
        }
    }

    /** 统一管理两个列表与各自空状态的可见性 */
    private fun updateVisibility() {
        val events = currentTab == TAB_EVENTS
        binding.recyclerEvents.visibility = if (events) View.VISIBLE else View.GONE
        binding.recyclerTimeline.visibility = if (events) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (events && adapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.tvEmptyTimeline.visibility =
            if (!events && timelineAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    // ---------- 菜单（设置 + 统计 + 教程；时间线走底部 Tab） ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, R.string.menu_settings)
        menu.add(Menu.NONE, 2, 0, R.string.menu_stats)
        menu.add(Menu.NONE, 3, 0, R.string.menu_tutorial)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            2 -> { startActivity(Intent(this, StatsActivity::class.java)); true }
            3 -> { openExternalUrl(Links.TUTORIAL); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        dragEnabled = currentSortMode() == SettingsActivity.SORT_MANUAL
        val events = if (dragEnabled) repo.getEventsManualOrder() else repo.getEventsByRecent()
        adapter.submit(events)
        timelineAdapter.submit(repo.getAllRecords())
        updateVisibility()
        updateGlassBackdrop()
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

    // ---------- 时间线适配器（月份分组标题 + 记录行） ----------

    /** 月份分组键（按本地时区的年 + 月） */
    private data class MonthKey(val year: Int, val month: Int) {
        val label: String get() = String.format(Locale.getDefault(), "%d年%d月", year, month)

        companion object {
            fun of(millis: Long): MonthKey {
                val cal = Calendar.getInstance()
                cal.timeInMillis = millis
                return MonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            }
        }
    }

    private sealed class TimelineItem {
        data class Month(val key: MonthKey, val count: Int) : TimelineItem()
        data class Record(val rec: TimelineRecord) : TimelineItem()
    }

    private inner class TimelineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<TimelineItem>()

        fun submit(records: List<TimelineRecord>) {
            items.clear()
            // 记录已按时间倒序；LinkedHashMap 保持「新月份在前」的插入顺序
            val monthMap = LinkedHashMap<MonthKey, MutableList<TimelineRecord>>()
            for (r in records) {
                val key = MonthKey.of(r.millis)
                monthMap.getOrPut(key) { mutableListOf() }.add(r)
            }
            for ((key, list) in monthMap) {
                items.add(TimelineItem.Month(key, list.size))
                items.addAll(list.map { TimelineItem.Record(it) })
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int =
            if (items[position] is TimelineItem.Month) TYPE_MONTH else TYPE_RECORD

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_MONTH) {
                MonthVH(ItemTimelineMonthBinding.inflate(inflater, parent, false))
            } else {
                RecordVH(ItemTimelineRecordBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TimelineItem.Month -> (holder as MonthVH).bind(item)
                is TimelineItem.Record -> (holder as RecordVH).bind(item.rec)
            }
        }

        inner class MonthVH(private val b: ItemTimelineMonthBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TimelineItem.Month) {
                b.tvMonth.text = item.key.label
                b.tvMonthCount.text = getString(R.string.timeline_month_count, item.count)
                // 月份行：线 / 刻度用次要色，弱于事件色的记录行
                val dim = com.google.android.material.color.MaterialColors.getColor(
                    b.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
                b.vLine.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resources.displayMetrics.density
                    setColor((dim and 0x00FFFFFF) or 0x38000000.toInt())
                }
                b.vDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dim)
                }
            }
        }

        inner class RecordVH(private val b: ItemTimelineRecordBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rec: TimelineRecord) {
                b.tvEventName.text = rec.eventName
                b.tvTime.text = TimeFormat.short(rec.millis)
                b.tvRelative.text = TimeFormat.relative(this@MainActivity, rec.millis)
                // 事件色染色：竖线 + 节点都跟随该记录所属事件的颜色
                b.vLine.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resources.displayMetrics.density
                    setColor(rec.eventColor)
                }
                b.vDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(rec.eventColor)
                }
                b.root.setOnClickListener { openDetail(repo.getEvent(rec.eventId) ?: return@setOnClickListener) }
            }
        }
    }
}
