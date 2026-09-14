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
        /** 底栏形态：布局内的静态胶囊 / 独立窗口 + 系统级背后模糊 */
        private const val MODE_STATIC = 0
        private const val MODE_WINDOW_BLUR = 1
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
        applyFabPosition()
        setupBottomBar()
        styleTab()
        // 若从详情页菜单直达时间线 Tab，需在渲染后生效
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMELINE, false)) {
            selectTab(TAB_TIMELINE)
        }
    }

    /**
     * 底部导航栏（**静态玻璃** + 彻底沉浸）。
     *
     * ⚠️ 这里刻意**不做实时模糊**。Android 没有「模糊身后内容」的公开 API，
     * 想看到背后内容只能自己「截屏 → 模糊 → 回填」；而那条路必然要重绘整棵视图树，
     * 且截到的永远是**上一帧** —— 结果就是延迟肉眼可见 + 滚动掉帧，无论怎么加压都追不上
     * 内容（已实测两版，均如此）。所以整块放弃，改用静态半透明玻璃：零延迟、零额外开销。
     *
     * 沉浸做法：
     * - 根布局四周 padding 恒为 0（顶部内边距由 BaseActivity 加在 AppBar 上）；
     * - bottomBar 高度 = 64dp + 导航栏 inset，一直铺到屏幕最底，系统手势条浮在玻璃之上；
     * - Tab 内容层按导航栏高度加底部 padding，文字绝不被手势条遮挡。
     */
    private fun setupBottomBar() {
        val immersive = object : androidx.core.view.OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(
                v: android.view.View,
                insets: androidx.core.view.WindowInsetsCompat
            ): androidx.core.view.WindowInsetsCompat {
                // 顶部内边距由 BaseActivity 加在 AppBar 上（状态栏被工具栏罩住）；
                // 这里只把「导航栏高度」交给底部栏，根布局四周 padding 保持 0。
                val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                v.setPadding(0, 0, 0, 0)
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
        syncBarMode()
    }

    /**
     * 底栏形态：系统能给模糊就给（胶囊岛放进独立窗口，模糊交给系统合成器，App 端零开销、
     * 真·实时）；给不了就是布局内的**半透明静态胶囊**（完全不模糊）。
     *
     * ⚠️ 刻意**不做**两件事：
     * 1. App 自己截屏 / 着色器算模糊的兜底 —— 用户关掉高级材质（多半为了省电、流畅）时
     *    还硬糊一层，等于无视系统级的显示偏好；
     * 2. 跟随「材质风格」（柔光玻璃 / 轻透磨砂）—— 实测该差异不在系统模糊层
     *    （SurfaceFlinger 输出逐字节相同），是 MIUI 内部 `MaterialToken` 实现的，
     *    第三方 App 没有公开接口；硬跟只能自己编透明度/半径，反而离"系统自己的渲染"更远。
     *
     * 因为「高级材质」是**系统设置**，用户可能在我们退到后台时改，所以 onResume 会再调一次。
     */
    private fun syncBarMode() {
        val want = if (SystemBlur.isUsable(this)) MODE_WINDOW_BLUR else MODE_STATIC
        if (want != barMode) {
            barMode = want
            // 换形态前先收掉旧窗口，避免窗口残留 / 旧引用被继续使用
            glassDialog?.dismiss()
            glassDialog = null
            islandW = 0
            islandH = 0
            if (want == MODE_WINDOW_BLUR) {
                binding.bottomBar.visibility = View.GONE
                buildIslandWindow()
            } else {
                binding.bottomBar.visibility = View.VISIBLE
                tabEventsRef = binding.tabEvents
                tabTimelineRef = binding.tabTimeline
            }
            tabEventsRef?.setOnClickListener { selectTab(TAB_EVENTS) }
            tabTimelineRef?.setOnClickListener { selectTab(TAB_TIMELINE) }
        }
        applyBarLayout(navInsetPx)
        styleTab()
    }

    /** 底栏当前形态（-1 = 尚未定过，首次必然进入初始化分支） */
    private var barMode = -1

    /**
     * 胶囊岛独立窗口 + 系统级「窗口背景模糊」（HyperOS 高级材质）。
     * 关键差异：窗口背景是**半透明胶囊玻璃**（`bg_bottom_nav` 原样使用，不做运行时改色）
     * 而不是全透明 —— 范围受限的模糊区域靠它推导。模糊半径用固定档
     * `glass_blur_radius`（材质固定一档，不跟随系统「材质风格」）。
     */
    private fun buildIslandWindow() {
        val dlg = android.app.Dialog(this, R.style.Theme_Timestamp_GlassBar)
        val content = layoutInflater.inflate(R.layout.view_bottom_nav, null)
        dlg.setContentView(content)
        dlg.setCancelable(false)
        dlg.setCanceledOnTouchOutside(false)

        tabEventsRef = content.findViewById(R.id.tabEvents)
        tabTimelineRef = content.findViewById(R.id.tabTimeline)
        tabEventsRef?.setOnClickListener { selectTab(TAB_EVENTS) }
        tabTimelineRef?.setOnClickListener { selectTab(TAB_TIMELINE) }

        dlg.window?.let { w ->
            w.setDimAmount(0f)
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // 不吃焦点、不拦截窗口外的触摸（岛外的滚动 / 点击照常传给下面的列表）
            w.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            w.setGravity(Gravity.BOTTOM)
            SystemBlur.attach(
                dlg,
                resources.getDimensionPixelSize(R.dimen.glass_blur_radius),
                resources.getDrawable(R.drawable.bg_bottom_nav, theme)
            )
        }
        dlg.show()
        glassDialog = dlg
        islandW = 0
        islandH = 0
        applyIslandWindowLayout()
        styleTab()
    }

    /** 让窗口与「布局内那颗胶囊」的位置、尺寸完全一致 */
    private fun applyIslandWindowLayout() {
        val w = glassDialog?.window ?: return
        val wantW = resources.displayMetrics.widthPixels -
                2 * resources.getDimensionPixelSize(R.dimen.space_4)
        val wantH = resources.getDimensionPixelSize(R.dimen.tab_bar_height)
        if (islandW == wantW && islandH == wantH) return
        val lp = w.attributes
        lp.width = wantW
        lp.height = wantH
        lp.gravity = Gravity.BOTTOM
        lp.y = resources.getDimensionPixelSize(R.dimen.island_bottom_gap)
        w.attributes = lp
        islandW = wantW
        islandH = wantH
    }

    private var glassDialog: android.app.Dialog? = null
    private var islandW = 0
    private var islandH = 0

    /**
     * 胶囊岛：高度固定，只调整「离底部多远」= 导航栏高度 + 12dp 呼吸感。
     * ⚠️ 直接改 layoutParams 字段不会触发重新布局，必须整体写回；而 inset 回调里无条件写回
     * 又会引起布局死循环，所以统一「数值变了才写」。
     */
    private fun applyBarLayout(nav: Int) {
        navInsetPx = nav
        barHeightPx = resources.getDimensionPixelSize(R.dimen.tab_bar_height)
        if (glassDialog != null) {
            applyIslandWindowLayout()
        } else {
            // 胶囊岛：高度固定，只调整「离底部多远」= 导航栏高度 + 12dp 呼吸感
            val lp = binding.bottomBar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val want = nav + resources.getDimensionPixelSize(R.dimen.island_bottom_gap)
            if (lp.bottomMargin != want) {
                lp.bottomMargin = want
                binding.bottomBar.layoutParams = lp
            }
        }
        applyFabPosition()
    }

    private var navInsetPx = 0

    /** 当前生效的两个 Tab 文本视图 */
    private var tabEventsRef: TextView? = null
    private var tabTimelineRef: TextView? = null

    /** 当前底部栏实测高度（= 内容高 + 导航栏 inset），FAB 据此上移 */
    private var barHeightPx = 0

    override fun onResume() {
        super.onResume()
        refresh()
        // 用户可能在系统里改了「高级材质」开关或「材质风格」，回到前台时重新对齐
        syncBarMode()
        applyFabPosition()
        WidgetRecordHelper.refreshAll(this)
    }

    override fun onDestroy() {
        // 独立窗口要收掉，避免窗口泄漏
        glassDialog?.dismiss()
        glassDialog = null
        super.onDestroy()
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
            // 窗口 insets 在此后才完全就绪：强制应用一次沉浸布局
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            if (insets != null) {
                val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                binding.root.setPadding(0, 0, 0, 0)
                applyBarLayout(nav)
            }
        }
    }

    /** 快捷按钮位置：左 / 中 / 右（设置页可切换）；底边距跟随底部栏实测高度，始终悬在栏上方 */
    private fun applyFabPosition() {
        val pos = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_FAB_POS, SettingsActivity.FAB_END) ?: SettingsActivity.FAB_END
        val gravity = when (pos) {
            SettingsActivity.FAB_START -> Gravity.START or Gravity.BOTTOM
            SettingsActivity.FAB_CENTER -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            else -> Gravity.END or Gravity.BOTTOM
        }
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        // FAB 永远悬在胶囊岛上方：岛离底距离 + 岛高 + 一点呼吸感
        val bottom = if (barHeightPx > 0) {
            navInsetPx + resources.getDimensionPixelSize(R.dimen.island_bottom_gap) +
                    barHeightPx + resources.getDimensionPixelSize(R.dimen.space_3)
        } else {
            resources.getDimensionPixelSize(R.dimen.fab_bottom_margin)
        }
        val lp = binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams
        // 值没变就不写回：避免在 inset 回调里反复 requestLayout
        if (lp.gravity == gravity && lp.leftMargin == m && lp.topMargin == m &&
            lp.rightMargin == m && lp.bottomMargin == bottom) return
        lp.gravity = gravity
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
    }

    /** 选中 Tab = 主题色胶囊高亮 + 加粗；未选中 = 透明 + 次要色。
     *  底栏可能在布局内、也可能在独立窗口里，这里统一取当前生效的那个。 */
    private fun styleTab() {
        val eventsSelected = currentTab == TAB_EVENTS
        setTabLook(tabEventsRef ?: binding.tabEvents, eventsSelected)
        setTabLook(tabTimelineRef ?: binding.tabTimeline, !eventsSelected)
    }

    /**
     * 选中 Tab = 一块**大圆角玻璃胶囊**（只在玻璃上加一档白微光，不做彩色填充）
     * + 近白文字；未选中 = 纯文字、次要色。对齐参考图那种「玻璃里有块玻璃」的观感。
     * 胶囊左右各内缩 8dp，视觉上不贴边。
     */
    private fun setTabLook(tv: TextView, selected: Boolean) {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        if (selected) {
            // 岛内的选中态 = 一块内嵌胶囊（左右 4dp、上下 8dp 内缩，半径 20dp）
            val insetH = resources.getDimensionPixelSize(R.dimen.space_1)
            val insetV = resources.getDimensionPixelSize(R.dimen.space_2)
            val fill = if (night) 0x24FFFFFF else 0x18000000
            tv.background = android.graphics.drawable.InsetDrawable(
                GradientDrawable().apply {
                    cornerRadius = resources.getDimensionPixelSize(R.dimen.radius_lg).toFloat()
                    setColor(fill)
                }, insetH, insetV, insetH, insetV
            )
            tv.setTextColor(if (night) 0xFFEDEAF3.toInt() else 0xFF16181C.toInt())
        } else {
            tv.background = null
            tv.setTextColor(tabOnSurfaceVariant)
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
        // 「＋」是用来新建事件的，只在「事件」Tab 下出现；「时间线」Tab 下隐藏
        binding.fabAdd.visibility = if (events) View.VISIBLE else View.GONE
        // 时间线底轨：只在「时间线」Tab 显示（贯穿屏幕上下那条淡线）
        binding.timelineTrack.visibility = if (events) View.GONE else View.VISIBLE
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

        /**
         * 每个列表项的「代表色」，与 [items] 一一对应：
         * 记录 = 该记录事件的颜色；月份标题 = **该月首条记录**的颜色
         * （这样月份行和它下面的记录同色，不会在月份处突然换个颜色）。
         */
        private var colors = IntArray(0)

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
            // 从后往前推：月份取紧随其后那条记录的颜色
            colors = IntArray(items.size)
            for (i in items.indices.reversed()) {
                colors[i] = when (val it = items[i]) {
                    is TimelineItem.Record -> it.rec.eventColor
                    is TimelineItem.Month -> if (i + 1 < items.size) colors[i + 1] else 0
                }
            }
            notifyDataSetChanged()
        }

        /** 上一条的颜色（用于渐变过渡）；没有上一条 → null（顶部淡入） */
        private fun prevColor(position: Int): Int? =
            if (position > 0 && colors[position - 1] != 0) colors[position - 1] else null

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
                is TimelineItem.Month -> (holder as MonthVH).bind(item, position)
                is TimelineItem.Record -> (holder as RecordVH).bind(item.rec, position)
            }
        }

        inner class MonthVH(private val b: ItemTimelineMonthBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TimelineItem.Month, position: Int) {
                b.tvMonth.text = item.key.label
                b.tvMonthCount.text = getString(R.string.timeline_month_count, item.count)
                // 竖线：与上下相邻记录之间做颜色渐变（月份行不再是灰色，而是接住该月的颜色）
                val own = colors[position]
                b.vLine.background = TimelineLine.gradient(prevColor(position), own)
                // 刻度点：该月颜色（半透明，弱于记录节点）；无颜色时退回次要色
                val dim = com.google.android.material.color.MaterialColors.getColor(
                    b.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
                b.vDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (own != 0) TimelineLine.withAlpha(own, 0x99) else dim)
                }
            }
        }

        inner class RecordVH(private val b: ItemTimelineRecordBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rec: TimelineRecord, position: Int) {
                b.tvEventName.text = rec.eventName
                b.tvTime.text = TimeFormat.short(rec.millis)
                b.tvRelative.text = TimeFormat.relative(this@MainActivity, rec.millis)
                // 竖线：从上一条的颜色渐变到本条的，条与条之间不再硬切
                b.vLine.background = TimelineLine.gradient(prevColor(position), rec.eventColor)
                // 节点：本记录的事件色（实心，作为「这一刻」的标记）
                b.vDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(rec.eventColor)
                }
                b.root.setOnClickListener { openDetail(repo.getEvent(rec.eventId) ?: return@setOnClickListener) }
            }
        }
    }
}
