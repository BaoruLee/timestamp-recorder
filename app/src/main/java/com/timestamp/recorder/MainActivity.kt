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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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
import com.timestamp.recorder.databinding.ItemTimelineCapBinding
import com.timestamp.recorder.databinding.ItemTimelineMonthBinding
import com.timestamp.recorder.databinding.ItemOverflowRowBinding
import com.timestamp.recorder.databinding.ItemTimelineRecordBinding
import com.timestamp.recorder.databinding.ViewOverflowMenuBinding
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
        private const val TYPE_CAP = 2
        /** 底栏形态：布局内的静态胶囊 / 独立窗口 + 系统级背后模糊 */
        private const val MODE_STATIC = 0
        private const val MODE_WINDOW_BLUR = 1
        /** 外部（如详情页菜单）指定直接打开时间线 Tab */
        const val EXTRA_OPEN_TIMELINE = "extra_open_timeline"

        /** 「⋮」菜单的动作 id（options menu 与顶部玻璃栏的 PopupMenu 共用） */
        private const val MENU_SETTINGS = 1
        private const val MENU_STATS = 2
        private const val MENU_TUTORIAL = 3
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
        // 时间线列表不留底部留白：「收笔」那一段自己负责盖住底部（否则收到最后一屏会有一截没上色）
        binding.recyclerTimeline.setPadding(
            binding.recyclerTimeline.paddingLeft,
            binding.recyclerTimeline.paddingTop,
            binding.recyclerTimeline.paddingRight,
            0
        )
        // 记下布局里原本的留白：顶部玻璃栏开启 / 关闭时要来回切换
        origListTopPadding = binding.recyclerEvents.paddingTop
        origEmptyTopMargin =
            (binding.tvEmpty.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
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
        syncTopBar()
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
                tabEventsRef = layoutTabEvents
                tabTimelineRef = layoutTabTimeline
            }
            tabEventsRef?.root?.setOnClickListener { selectTab(TAB_EVENTS) }
            tabTimelineRef?.root?.setOnClickListener { selectTab(TAB_TIMELINE) }
        }
        applyBarLayout(navInsetPx)
        styleTab()
    }

    /** 底栏当前形态（-1 = 尚未定过，首次必然进入初始化分支） */
    private var barMode = -1

    // ---------------- 顶部玻璃栏（独立窗口 + 系统级模糊） ----------------

    /**
     * 顶部工具栏也做成「模糊的半透材质」。
     *
     * 难点：系统只能模糊**窗口背后**的内容，而列表和工具栏在同一个窗口里 —— 同窗内的内容
     * 系统没法替我们糊。所以和底部胶囊岛一样，把这一栏搬进一个**独立窗口**（浮在内容之上），
     * 模糊交给系统合成器；同时把布局里的 AppBar 收起来，列表于是会一直铺到屏幕顶端，
     * 滚动时内容就从玻璃栏底下穿过去。
     *
     * 附带影响：右上角「⋮」（设置 / 统计 / 教程）跟着搬进这个窗口 —— 菜单本身也是一扇
     * 独立的玻璃窗口（见 [showOverflowMenu]），动作仍走 [handleMenuAction]，
     * 与原来的 options menu 共用一套逻辑。
     */
    private fun syncTopBar() {
        // 顶栏形态：系统能给模糊就搬进独立窗口（真·实时模糊，且能罩住状态栏）；
        // 给不了就用布局内的 AppBar（静态半透明玻璃）。
        //
        // ⚠️ 已知代价（主人权衡后选择保留模糊）：这扇独立窗口会变成「最上层应用窗口」，
        //    状态栏图标按它上色，而它必须 NOT_FOCUSABLE → appearance 设不进去
        //    （InsetsController / LayoutParams.systemUiVisibility / decorView.systemUiVisibility
        //     三种写法全试过都无效），于是浅色模式下状态栏时间是白色的。
        //    实测：有这扇窗时间区深色 0%，没有 39%。**状态栏与模糊在这台 ROM 上互斥**，
        //    目前按主人要求优先保留模糊。
        val want = if (SystemBlur.isUsable(this)) MODE_WINDOW_BLUR else MODE_STATIC
        if (want != topBarMode) {
            topBarMode = want
            dismissTopBar()
            if (want == MODE_WINDOW_BLUR) {
                binding.appBar.visibility = View.GONE
                buildTopBarWindow()
            } else {
                binding.appBar.visibility = View.VISIBLE
                // 布局内形态也要有「⋮」—— 而且同样弹我们那块玻璃菜单
                binding.btnMore.setOnClickListener { showOverflowMenu(it) }
            }
        }
        applyTopBarInsets()
    }

    private fun buildTopBarWindow() {
        val dlg = android.app.Dialog(this, R.style.Theme_Timestamp_GlassBar)
        val content = layoutInflater.inflate(R.layout.view_top_bar, null)
        dlg.setContentView(content)
        dlg.setCancelable(false)
        dlg.setCanceledOnTouchOutside(false)
        topBarRoot = content
        content.findViewById<View>(R.id.btnMore)?.setOnClickListener { showOverflowMenu(it) }

        dlg.window?.let { w ->
            w.setDimAmount(0f)
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // ⚠️ 试过改成 TYPE_APPLICATION_PANEL（子窗口不参与系统栏配色，能让状态栏黑化），
            //    但 PANEL 是**子窗口**，必须带 Activity 的 window token，Dialog 拿不到 →
            //    直接抛 BadTokenException，App 打不开。只能维持 TYPE_APPLICATION。
            // 不吃焦点、不拦截窗口外的触摸；要让这扇窗真正顶到屏幕最上（含状态栏），
            // 只靠 LAYOUT_IN_SCREEN 不够 —— 系统仍会把 TYPE_APPLICATION 摆到「应用可用区」里
            // （实测 frame=[0,169]…，状态栏那 169px 罩不住），必须再给 LAYOUT_NO_LIMITS。
            // ⚠️ NO_LIMITS 只在**显式给了宽高 + floating** 时才安全：否则窗口会退化成整屏
            //    （既整屏被模糊、又挡掉所有触摸，踩过）。
            // LAYOUT_IN_SCREEN + NO_LIMITS 缺一不可：少了它们窗口会被摆在「应用可用区」里，
            // 顶不到 y=0，状态栏那一条就罩不住（栏会掉到状态栏下面，观感退步）。
            w.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            w.setGravity(Gravity.TOP)
            val lp = w.attributes
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
            // ⚠️ 关键：不能让窗口被系统栏再挤一次。默认会按 statusBars 内缩，
            // 于是玻璃栏掉到状态栏之下（原 AppBar 是罩着状态栏的，观感会退步）。
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                lp.fitInsetsTypes = 0
                lp.fitInsetsSides = 0
            }
            // 状态栏图标配色「多管齐下」（不同 ROM 听不同的 API，全写上，谁认谁生效）：
            // lp.systemUiVisibility（部分 ROM 按 LayoutParams 上色）+ post 里的
            // InsetsController 和 decorView.systemUiVisibility。HyperOS 上都不认（已知取舍），
            // 但原生 / 其他 ROM 认 LayoutParams 这条路 —— 写上不吃亏。
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            if (!night) {
                @Suppress("DEPRECATION")
                lp.systemUiVisibility =
                    lp.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            w.attributes = lp
            SystemBlur.attach(
                dlg,
                resources.getDimensionPixelSize(R.dimen.glass_blur_radius),
                resources.getDrawable(R.drawable.bg_top_bar, theme)
            )
        }
        dlg.show()
        topBarDialog = dlg
        // ⚠️ 这扇窗盖住了状态栏，于是**状态栏图标按"它的" appearance 来画**，
        //    主 Activity 那边的设置会被它盖掉（实测：把这扇窗去掉后状态栏立刻正常黑化，
        //    开着它就变全白 —— 时间文字与背景同为 250，等于隐形）。
        //    所以这里也必须点亮，而且要等窗口真正 attach 之后（show() 刚返回时设不算数）。
        dlg.window?.let { w ->
            w.statusBarColor = Color.TRANSPARENT
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            w.decorView.post {
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                    ?.isAppearanceLightStatusBars = !night
                if (!night) {
                    // 旧 API 兜底：直接写 decorView 的 flag，绕过 InsetsController
                    @Suppress("DEPRECATION")
                    w.decorView.systemUiVisibility =
                        w.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }
        // 布局完成后再算实际高度（那一刻 insets / 测量才可靠）
        dlg.window?.decorView?.post { applyTopBarInsets() }
        // 每次布局完都对一次：show() 刚返回时窗口还没测量（量到的高度偏小），列表留白会算少，
        // 首个事件就被压在玻璃底下。等布局稳定后再量一次就能对上（值不变时不会重复写回）。
        val obs = dlg.window?.decorView?.viewTreeObserver
        if (obs != null && obs.isAlive) {
            val l = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() = applyTopBarInsets()
            }
            topBarLayoutListener = l
            obs.addOnGlobalLayoutListener(l)
        }
    }

    /** 收掉顶部玻璃栏窗口（连同布局监听，避免泄漏） */
    private fun dismissTopBar() {
        topBarLayoutListener?.let { l ->
            try {
                val obs = topBarDialog?.window?.decorView?.viewTreeObserver
                if (obs != null && obs.isAlive) obs.removeOnGlobalLayoutListener(l)
            } catch (_: Throwable) {
                // 窗口已销毁，忽略
            }
        }
        topBarLayoutListener = null
        topBarDialog?.dismiss()
        topBarDialog = null
        topBarRoot = null
    }

    /**
     * 玻璃顶栏盖住了状态栏 + 标题栏，所以：
     * - 窗口内加「状态栏高度」的上内边距（标题落到状态栏之下）；
     * - 事件列表按**玻璃栏下沿在屏幕上的位置**留白（首条不被压在玻璃下）；
     * - 时间线列表**不留白**：让「起笔」那一段从屏幕最顶端开始，彩色竖线才能从玻璃底下顶上来。
     */
    private fun applyTopBarInsets() {
        if (topBarMode != MODE_WINDOW_BLUR || topBarRoot == null) {
            restoreTopPadding()
            return
        }
        val root = topBarRoot ?: return
        val statusTop = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0

        // 窗口到底盖没盖住状态栏，不能靠猜：直接看它的真实屏幕位置。
        // - 盖住了（窗口顶端 == 0）→ 标题要往下让出状态栏，内边距 = 状态栏高度；
        // - 没盖住（窗口顶端 == 状态栏高度）→ 已经让出来了，内边距 = 0。
        // 这样无论 ROM 怎么摆这个窗口，栏高和列表留白都是对的（不会白多一条状态栏的高度）。
        val loc = IntArray(2)
        topBarDialog?.window?.decorView?.getLocationOnScreen(loc)
        val winTop = loc[1].coerceIn(0, statusTop)
        val pad = statusTop - winTop
        if (root.paddingTop != pad) {
            root.setPadding(root.paddingLeft, pad, root.paddingRight, root.paddingBottom)
        }

        val winH = measureTopBarHeight()
        // ⚠️ 列表要的留白 = 玻璃栏**下沿**在屏幕上的位置，也就是「窗口顶端 + 窗口高」。
        // 之前只算了「内容高」（winH - pad），等于漏掉了被罩住的那条状态栏 ——
        // 留白少了一整个状态栏高度，于是第一个事件直接被额头压住。
        // 再兜一层底线（状态栏 + 一栏工具栏）：万一某帧量到的窗口位置还不准，也不会压到内容。
        val minCover = statusTop + resources.getDimensionPixelSize(R.dimen.top_bar_height)
        val covered = (winTop + winH).coerceAtLeast(minCover)
        // 留白要相对「内容区原点」算，而不是假设列表恰好从 y=0 开始 —— 少一层假设，少一处坑。
        // 用 binding.root 而不是列表本身：列表在另一个 Tab 下是 GONE 的，GONE 的 view 量不到位置。
        val hostLoc = IntArray(2)
        binding.root.getLocationOnScreen(hostLoc)
        val gap = resources.getDimensionPixelSize(R.dimen.space_2)
        val base = (covered - hostLoc[1]).coerceAtLeast(0)
        if (winH > 0 && covered > 0) {
            binding.recyclerEvents.applyTopPadding(base + gap)
            binding.recyclerTimeline.applyTopPadding(0)
            applyEmptyTopPadding(base + gap * 3)
        }
    }

    /** 退出玻璃顶栏（如系统关掉高级材质）时，把列表留白还回布局里原本的值。
     *  ⚠️ 时间线的顶部留白**永远为 0**：起笔（Cap）负责把彩色线顶到「当前顶端」，
     *  留白会在顶端留出一截没颜色的空档（两种顶栏形态下都一样）。 */
    private fun restoreTopPadding() {
        binding.recyclerEvents.applyTopPadding(origListTopPadding)
        binding.recyclerTimeline.applyTopPadding(0)
        applyEmptyTopPadding(origEmptyTopMargin)
    }

    /**
     * 顶栏窗口的实际高度。
     * ⚠️ 不能用 `decorView.height` 一把梭：窗口刚 show() 时它还是 0，
     * 那样列表留白就永远补不上、首条会被压在玻璃底下。测不到就自己量一次。
     */
    private fun measureTopBarHeight(): Int {
        val v = topBarRoot ?: return 0
        if (v.height > 0) return v.height
        val w = binding.root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return v.measuredHeight
    }

    private fun applyEmptyTopPadding(px: Int) {
        for (v in listOf(binding.tvEmpty, binding.tvEmptyTimeline)) {
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.topMargin != px) {
                    lp.topMargin = px
                    v.layoutParams = lp
                }
            }
        }
    }

    private fun View.applyTopPadding(px: Int) {
        if (paddingTop != px) setPadding(paddingLeft, px, paddingRight, paddingBottom)
    }

    /**
     * 右上角「⋮」的菜单。
     *
     * ⚠️ 刻意**不用**系统 PopupMenu：
     * 1. 它的位置是按「锚点所在窗口」推算的，而这里的锚点在一扇 NO_LIMITS 的独立窗口里 ——
     *    实测菜单会整个甩到屏幕外面去；
     * 2. 系统那套白底 / 直角 / 无图标的样式，跟我们这套玻璃语言根本不是一个东西。
     *
     * 所以自己来：一块同样跑在**独立窗口**里的玻璃卡片（模糊照样交给系统合成器，App 端零开销），
     * 位置按「按钮在屏幕上的真实坐标」算并**夹在屏幕内**，从按钮那一角缩放淡入。
     * 动作仍走 [handleMenuAction]，和 options menu 共用一套逻辑。
     */
    private fun showOverflowMenu(anchor: View) {
        dismissOverflowMenu()
        val dlg = android.app.Dialog(this, R.style.Theme_Timestamp_GlassBar)
        val b = ViewOverflowMenuBinding.inflate(layoutInflater)
        dlg.setContentView(b.root)
        dlg.setCancelable(true)
        dlg.setCanceledOnTouchOutside(true)
        dlg.setOnDismissListener { tintMenuAnchor(anchor, false) }

        bindMenuRow(b.rowSettings, R.drawable.ic_menu_settings, R.string.menu_settings, MENU_SETTINGS)
        bindMenuRow(b.rowStats, R.drawable.ic_menu_stats, R.string.menu_stats, MENU_STATS)
        bindMenuRow(b.rowTutorial, R.drawable.ic_menu_tutorial, R.string.menu_tutorial, MENU_TUTORIAL)

        // 位置：右边缘贴着按钮、整体夹在屏幕内（8dp 安全边距），绝不顶出画面
        val m = resources.getDimensionPixelSize(R.dimen.space_2)
        val menuW = resources.getDimensionPixelSize(R.dimen.overflow_menu_width)
        val maxX = (resources.displayMetrics.widthPixels - menuW - m).coerceAtLeast(m)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val x = (loc[0] + anchor.width - menuW + m).coerceIn(m, maxX)
        // 上沿就贴在「⋮」下沿下面 6dp —— 视觉上是从按钮里长出来的（缩放原点也在右上角）。
        // 之前挂在「玻璃栏下沿」，中间还隔着一行副标题的落差，看着就远了；
        // 现在 ⋮ 垂直居中于整块栏，它的下沿离栏底只剩几 dp，所以直接锚按钮就又近又不压字。
        val y = (loc[1] + anchor.height +
                resources.getDimensionPixelSize(R.dimen.overflow_menu_gap)).coerceAtLeast(m)

        dlg.window?.let { w ->
            w.setDimAmount(0f)
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // 不吃焦点、不拦窗口外的触摸；WATCH_OUTSIDE_TOUCH 让「点空白处」也能收起菜单
            // ⚠️ LAYOUT_IN_SCREEN 不能少：少了它，窗口被限制在「应用可用区」里，
            //    于是 lp.y 被当成「内容区坐标」—— 设 y=365，实际却被摆到 534
            //    （正好多一条 169px 的状态栏），菜单就凭空往下掉了一截。
            //    顶部玻璃栏那扇窗一直有这个 flag，所以只有菜单踩到。
            w.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            // 模糊区域由「窗口背景」推导，所以这块可见的玻璃必须给到窗口上（内容本身不带背景）
            val bg = resources.getDrawable(R.drawable.bg_overflow_menu, theme)
            if (SystemBlur.isUsable(this)) {
                SystemBlur.attach(
                    dlg, resources.getDimensionPixelSize(R.dimen.glass_blur_radius), bg)
            } else {
                w.setBackgroundDrawable(bg)
            }
            w.setGravity(Gravity.TOP or Gravity.START)
            val lp = w.attributes
            // ⚠️ 宽度必须显式给像素，不能靠布局里的 layout_width：
            //    `Dialog.setContentView(View)` 会把这个 View 挂到 decor 的 FrameLayout 下，
            //    LinearLayout 的 LayoutParams 不兼容 → 被换成 wrap_content，188dp 直接失效
            //    （实测菜单只有 144dp 宽）。
            lp.width = menuW
            lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
            lp.x = x
            lp.y = y
            // ⚠️ 和顶部玻璃栏同一个坑：浮动窗口默认会按状态栏再内缩一次 ——
            //    设了 y=365，实际 frame 却被推到 534（差的正是 169px 状态栏高度），
            //    于是菜单凭空往下掉了一整条状态栏，看着就跟按钮"离得太远"。
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                lp.fitInsetsTypes = 0
                lp.fitInsetsSides = 0
            }
            w.attributes = lp
        }

        // 从右上角那颗按钮的方向展开（宽度已知，不必等测量）
        b.root.pivotX = menuW.toFloat()
        b.root.pivotY = 0f
        b.root.alpha = 0f
        b.root.scaleX = 0.88f
        b.root.scaleY = 0.88f
        dlg.show()
        b.root.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        overflowDialog = dlg
        tintMenuAnchor(anchor, true)
    }

    private fun bindMenuRow(
        row: ItemOverflowRowBinding,
        icon: Int,
        label: Int,
        action: Int
    ) {
        row.rowIcon.setImageResource(icon)
        row.rowText.setText(label)
        row.rowRoot.setOnClickListener {
            dismissOverflowMenu()
            handleMenuAction(action)
        }
    }

    /** 菜单展开时把「⋮」点亮（主题色），收起后回到常规颜色 —— 让人知道菜单是从哪冒出来的 */
    private fun tintMenuAnchor(anchor: View, open: Boolean) {
        (anchor as? android.widget.ImageView)?.imageTintList =
            ColorStateList.valueOf(if (open) menuAccent else menuIconTint)
    }

    private fun dismissOverflowMenu() {
        overflowDialog?.dismiss()
        overflowDialog = null
    }

    private var overflowDialog: android.app.Dialog? = null

    private val menuIconTint: Int by lazy {
        com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurface)
    }
    private val menuAccent: Int by lazy {
        com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary)
    }

    private fun handleMenuAction(id: Int): Boolean = when (id) {
        MENU_SETTINGS -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        MENU_STATS -> { startActivity(Intent(this, StatsActivity::class.java)); true }
        MENU_TUTORIAL -> { openExternalUrl(Links.TUTORIAL); true }
        else -> false
    }

    private var topBarDialog: android.app.Dialog? = null
    private var topBarRoot: View? = null
    private var topBarLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** 顶部玻璃栏是否生效（-1 = 尚未定过） */
    private var topBarMode = -1

    // 布局里原本的留白，退出玻璃顶栏（如系统关掉高级材质）时要还原
    private var origListTopPadding = 0
    private var origEmptyTopMargin = 0

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

        tabEventsRef = TabViews(
            content.findViewById(R.id.tabEvents),
            content.findViewById(R.id.iconEvents),
            content.findViewById(R.id.textEvents)
        )
        tabTimelineRef = TabViews(
            content.findViewById(R.id.tabTimeline),
            content.findViewById(R.id.iconTimeline),
            content.findViewById(R.id.textTimeline)
        )
        tabEventsRef?.root?.setOnClickListener { selectTab(TAB_EVENTS) }
        tabTimelineRef?.root?.setOnClickListener { selectTab(TAB_TIMELINE) }

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
            // 这扇窗是「最上层应用窗口」，状态栏图标按它上色 —— 所以这里也要点亮，
            // 否则状态栏时间会变纯白、在浅色顶栏上完全看不见。
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            if (!night) {
                val lp = w.attributes
                @Suppress("DEPRECATION")
                lp.systemUiVisibility =
                    lp.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                w.attributes = lp
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                    ?.isAppearanceLightStatusBars = true
            }
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

    /** 一个 Tab 的三件套：容器（承载选中态的玻璃胶囊）、图标、文字 */
    private class TabViews(val root: View, val icon: ImageView, val text: TextView)

    /** 布局内（静态底栏）的两个 Tab —— 从玻璃窗口切回来时要还原成它们 */
    private val layoutTabEvents: TabViews by lazy {
        TabViews(binding.tabEvents, binding.iconEvents, binding.textEvents)
    }
    private val layoutTabTimeline: TabViews by lazy {
        TabViews(binding.tabTimeline, binding.iconTimeline, binding.textTimeline)
    }

    /** 当前生效的两个 Tab（可能在布局里，也可能在玻璃窗口里） */
    private var tabEventsRef: TabViews? = null
    private var tabTimelineRef: TabViews? = null

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

    override fun onPause() {
        // 「⋮」菜单是个独立窗口，Activity 退到后台时它不会被自动收掉，这里手动关
        dismissOverflowMenu()
        super.onPause()
    }

    override fun onDestroy() {
        // 独立窗口要收掉，避免窗口泄漏
        dismissOverflowMenu()
        glassDialog?.dismiss()
        glassDialog = null
        dismissTopBar()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 系统栏图标配色，按当前深浅色**强制**定死，不吃 ROM 默认值：
            // - 状态栏：顶栏是浅色玻璃，浅色模式下图标必须转深（否则白图标糊在浅玻璃上看不见）；
            // - 手势条：深色模式下强制深色 pill，融入深色玻璃，彻底沉浸。
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                val navAppear = if (night) {
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else 0
                val statusAppear = if (night) {
                    0
                } else {
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                }
                window.insetsController?.setSystemBarsAppearance(
                    navAppear or statusAppear,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        or android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            }
            // 窗口 insets 在此后才完全就绪：强制应用一次沉浸布局
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
            if (insets != null) {
                val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                binding.root.setPadding(0, 0, 0, 0)
                applyBarLayout(nav)
                // 状态栏高度此时才是准的：顶部玻璃栏的上内边距 / 列表留白要重算
                applyTopBarInsets()
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

    /** 选中 Tab = 玻璃胶囊高亮；未选中 = 透明 + 次要色。
     *  底栏可能在布局内、也可能在独立窗口里，这里统一取当前生效的那个。 */
    private fun styleTab() {
        val eventsSelected = currentTab == TAB_EVENTS
        val ev = tabEventsRef ?: layoutTabEvents
        val tl = tabTimelineRef ?: layoutTabTimeline
        setTabLook(ev, eventsSelected)
        setTabLook(tl, !eventsSelected)
    }

    /**
     * 选中 Tab = 一块**内嵌玻璃胶囊**（半透明填充 + 一圈描边，把"轮廓"交代清楚）
     * + 高对比文字与图标；未选中 = 透明底 + 次要色。
     * 胶囊左右各内缩 4dp、上下 8dp，视觉上不贴边。
     */
    private fun setTabLook(tab: TabViews, selected: Boolean) {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        if (selected) {
            // 岛内的选中态 = 一块内嵌胶囊。白天在浅玻璃上「白上加白」几乎看不出边界，
            // 所以补一圈淡淡的冷色描边（夜间用白描边），轮廓一眼可辨。
            val insetH = resources.getDimensionPixelSize(R.dimen.space_1)
            val insetV = resources.getDimensionPixelSize(R.dimen.space_2)
            val fill = if (night) 0x2EFFFFFF else 0x1F000000
            val stroke = if (night) 0x33FFFFFF else 0x2E5B6B8C
            tab.root.background = android.graphics.drawable.InsetDrawable(
                GradientDrawable().apply {
                    cornerRadius = resources.getDimensionPixelSize(R.dimen.radius_lg).toFloat()
                    setColor(fill)
                    setStroke(resources.getDimensionPixelSize(R.dimen.space_1) / 4, stroke)
                }, insetH, insetV, insetH, insetV
            )
            val content = if (night) 0xFFEDEAF3.toInt() else 0xFF16181C.toInt()
            tab.text.setTextColor(content)
            tab.icon.imageTintList = ColorStateList.valueOf(content)
        } else {
            tab.root.background = null
            tab.text.setTextColor(tabOnSurfaceVariant)
            tab.icon.imageTintList = ColorStateList.valueOf(tabOnSurfaceVariant)
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
    // ⚠️ 不再注册 options menu：那会在工具栏上多出一颗系统样式的「⋮」（跟自定义按钮重复，
    //    关掉高级材质时两个 ⋮ 并排出现）。统一走 showOverflowMenu() 的玻璃菜单。

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

        /** 起笔 / 收笔：列表最上、最下那一段「有颜色的空行」，让时间线的两头不至于没颜色 */
        data class Cap(val color: Int, val head: Boolean) : TimelineItem()
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
            var cols = IntArray(items.size)
            for (i in items.indices.reversed()) {
                cols[i] = when (val it = items[i]) {
                    is TimelineItem.Record -> it.rec.eventColor
                    is TimelineItem.Month -> if (i + 1 < items.size) cols[i + 1] else 0
                    is TimelineItem.Cap -> it.color
                }
            }
            // 两头各补一段起笔 / 收笔：颜色沿用「最新那条」与「最旧那条」
            if (cols.isNotEmpty()) {
                items.add(0, TimelineItem.Cap(cols.first(), head = true))
                items.add(TimelineItem.Cap(cols.last(), head = false))
                cols = IntArray(cols.size + 2).also {
                    it[0] = cols.first()
                    System.arraycopy(cols, 0, it, 1, cols.size)
                    it[it.size - 1] = cols.last()
                }
            }
            colors = cols
            notifyDataSetChanged()
        }

        /** 上一条的颜色（用于渐变过渡）；没有上一条 → null（顶部淡入） */
        private fun prevColor(position: Int): Int? =
            if (position > 0 && colors[position - 1] != 0) colors[position - 1] else null

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is TimelineItem.Month -> TYPE_MONTH
            is TimelineItem.Record -> TYPE_RECORD
            is TimelineItem.Cap -> TYPE_CAP
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_MONTH -> MonthVH(ItemTimelineMonthBinding.inflate(inflater, parent, false))
                TYPE_CAP -> CapVH(ItemTimelineCapBinding.inflate(inflater, parent, false))
                else -> RecordVH(ItemTimelineRecordBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TimelineItem.Month -> (holder as MonthVH).bind(item, position)
                is TimelineItem.Record -> (holder as RecordVH).bind(item.rec, position)
                is TimelineItem.Cap -> (holder as CapVH).bind(item, position)
            }
        }

        inner class CapVH(private val b: ItemTimelineCapBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TimelineItem.Cap, position: Int) {
                b.root.layoutParams = b.root.layoutParams.apply {
                    // 起笔高度跟顶栏形态走：独立玻璃窗口罩着屏幕顶端时要够高才能从玻璃下穿出来；
                    // 静态 AppBar 那种情况 AppBar 自己已占着顶端，起笔只需一小段把线接上。
                    height = resources.getDimensionPixelSize(
                        when {
                            !item.head -> R.dimen.timeline_tail_height
                            topBarMode == MODE_WINDOW_BLUR -> R.dimen.timeline_head_height
                            else -> R.dimen.timeline_head_height_static
                        }
                    )
                }
                b.vLine.setLine(
                    prevColor(position),
                    if (item.color != 0) item.color else colors.getOrElse(position) { 0 },
                    if (item.head) TimelineLineView.Mode.HEAD else TimelineLineView.Mode.TAIL
                )
            }
        }

        inner class MonthVH(private val b: ItemTimelineMonthBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TimelineItem.Month, position: Int) {
                b.tvMonth.text = item.key.label
                b.tvMonthCount.text = getString(R.string.timeline_month_count, item.count)
                // 竖线：顶部一小段内从上一条的颜色过渡到自己的（月份行也不再是灰色）
                val own = colors[position]
                b.vLine.setLine(prevColor(position), own)
                // 刻度点：该月颜色（半透明，弱于记录节点）；无颜色时退回次要色
                val dim = com.google.android.material.color.MaterialColors.getColor(
                    b.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
                b.vDot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (own != 0) withAlpha(own, 0x99) else dim)
                }
            }
        }

        inner class RecordVH(private val b: ItemTimelineRecordBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rec: TimelineRecord, position: Int) {
                b.tvEventName.text = rec.eventName
                b.tvTime.text = TimeFormat.short(rec.millis)
                b.tvRelative.text = TimeFormat.relative(this@MainActivity, rec.millis)
                // 竖线：顶部一小段内从上一条的颜色过渡到本条的，条与条之间不再硬切
                b.vLine.setLine(prevColor(position), rec.eventColor)
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
