package com.timestamp.recorder

import android.os.Bundle
import android.view.View
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

    protected fun setupChrome(
        toolbar: MaterialToolbar,
        appBar: View?,
        root: View,
        titleRes: Int,
        showBack: Boolean = false
    ) {
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(titleRes)
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.setNavigationOnClickListener { finish() }
        } else {
            toolbar.navigationIcon = null
        }

        // 状态栏 inset：工具栏下沉到状态栏之下，背景渐变延伸至状态栏（无黑边）
        appBar?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                v.updatePadding(top = top)
                insets
            }
        }
        // 导航栏 inset：底部内容上移，避开手势条 / 三键
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        // 全局应用 MiSans（无字体文件时自动回退系统字体，零风险）
        Fonts.applyTo(root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        // 沉浸式：状态栏 / 导航栏透明（通杀各品牌，含小米 HyperOS 手势条）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
    }
}
