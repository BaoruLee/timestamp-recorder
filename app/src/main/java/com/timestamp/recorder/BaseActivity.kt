package com.timestamp.recorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors

/**
 * 所有页面的公共基座：
 * - 动态取色（M3 Dynamic Colors）
 * - 沉浸式状态栏 / 导航栏（通杀各品牌，含小米 HyperOS 手势条）
 * - 统一的工具栏（返回键 + 标题）与 WindowInsets 处理
 * - 全局应用 MiSans 字体（若已放入 assets/fonts，否则回退系统字体）
 *
 * 子类约定：onCreate 中第一行调用 super.onCreate(savedInstanceState)，
 * 随后 setContentView，最后调用 [setupChrome]。
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * @param scrollContent 本页的滚动容器（NestedScrollView / RecyclerView）。
     *   传入后「导航栏高度」会加到它自己的底部留白上，于是内容能一路滚到小白条底下 ——
     *   这才是真正的沉浸；不传则退回「根布局吃导航栏内边距」的保守做法（内容不跑到手势条下）。
     */
    protected fun setupChrome(
        toolbar: MaterialToolbar,
        appBar: View?,
        root: View,
        titleRes: Int,
        showBack: Boolean = false,
        scrollContent: View? = null
    ) {
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(titleRes)
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationOnClickListener { finish() }
        } else {
            toolbar.navigationIcon = null
        }

        // 顶部：状态栏内边距只加在 AppBar 上（没有 AppBar 的页面退化为工具栏本身），
        // 让工具栏底色一路铺到屏幕顶端 —— 既不留白、状态栏也被罩住。
        // ⚠️ 顶部内边距只能在这一处加：根布局若再加一次，就会出现「双倍留白」。
        val topHost = appBar ?: toolbar
        ViewCompat.setOnApplyWindowInsetsListener(topHost) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (v.paddingTop != top) v.updatePadding(top = top)
            insets
        }

        // 底部（沉浸的关键）：
        // - 有 scrollContent 时，根布局不吃内边距，改把导航栏高度加进滚动容器自己的
        //   底部留白 —— 内容可以滚到小白条下面，最后一项也仍然完整可见
        //   （各页滚动容器都带 clipToPadding=false）。
        // - 没有时退回保守做法：根布局吃导航栏内边距，内容不铺到手势条下，但也不会被遮住。
        // 所有分支都「值变了才写」，避免在 inset 回调里反复 requestLayout 引起布局死循环。
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val sc = scrollContent
            if (sc != null) {
                if (scrollBaseBottom < 0) scrollBaseBottom = sc.paddingBottom
                val want = scrollBaseBottom + nav
                if (sc.paddingBottom != want) sc.updatePadding(bottom = want)
                if (v.paddingTop != 0 || v.paddingBottom != 0) v.setPadding(0, 0, 0, 0)
            } else {
                if (v.paddingTop != 0 || v.paddingBottom != nav) v.setPadding(0, 0, 0, nav)
            }
            insets
        }

        // 全局应用 MiSans（无字体文件时自动回退系统字体，零风险）
        Fonts.applyTo(root)
    }

    /** 滚动容器最初的底部留白（-1 = 尚未采样） */
    private var scrollBaseBottom = -1

    /**
     * 用系统浏览器打开外链。
     *
     * App 自身不申请 INTERNET 权限，联网由系统浏览器负责——保持「零权限」。
     * 设备上没有浏览器（或链接被系统策略拦截）时给个提示，不至于点了没反应。
     */
    protected fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        // 沉浸式：状态栏 / 导航栏透明（通杀各品牌，含小米 HyperOS 手势条）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        // 关闭导航栏对比度强制（ColorOS / MIUI 等 ROM 默认会给透明导航栏加一层
        // 半透明 scrim，导致底部出现「小白条」、手势条区域不沉浸）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                window.isNavigationBarContrastEnforced = false
            } catch (_: Exception) {
            }
        }
    }
}
