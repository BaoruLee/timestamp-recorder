package com.timestamp.recorder

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

/**
 * 系统级「窗口背景模糊」= HyperOS 的**高级材质**（background blur）。
 *
 * 对应 `Window#setBackgroundBlurRadius`：只模糊**窗口自身范围**内的背后内容
 * （与 `setBlurBehindRadius` 不同，后者会把**整块屏幕**背后都糊掉，不能用于局部元素）。
 *
 * 本机门控已实测：`persist.sys.background_blur_supported=true`、
 * `ro.surface_flinger.supports_background_blur=1`、`advanced_visual_release=5`。
 *
 * ⚠️ 两个坑（都踩过）：
 * 1. 范围受限的 background blur **按「窗口背景」推导模糊区域** —— 窗口背景若为全透明，
 *    就没有模糊区域，系统什么都不画。所以必须给窗口一个**真正可见的半透明背景**（胶囊玻璃）。
 * 2. 窗口必须 **floating**：`windowIsFloating=false` 会让窗口铺满全屏（整屏糊 + 挡掉全部触摸）。
 *
 * ⚠️ 用户开关（重要）：AOSP 这条「App 显式请求」的路径，系统渲染时**只看 `mBlurEnabled`**，
 * 不读用户的高级材质开关 —— MIUI 原生界面才吃那个开关。所以我们要**主动读**
 * `Settings.Secure.background_blur_enable`，用户关掉高级材质时就不做模糊（尊重其省电选择）。
 *
 * ⚠️ 关于「材质风格」（柔光玻璃 / 轻透磨砂，`Settings.Secure.material_style`）：
 * 实测把该键在 0/1 之间来回切，SurfaceFlinger 的模糊栈（`KawaseDualFilterV2`、`blurRegions`…）
 * **输出完全一致** → 风格差异不在系统合成器的模糊层，而是 MIUI 在自己组件内部实现的
 * （内部接口 `MaterialToken`，只有 Xposed 模块能反射调用）。
 * 结论：第三方 App **没有公开 API 能让窗口"按系统材质风格渲染"**，硬要跟就得自己编参数
 * （透明度/半径），与"用系统自己的东西"相悖，故**不做风格联动**，材质固定一档。
 */
object SystemBlur {

    /** 用户级「高级材质」总开关（HyperOS：设置 → 显示与亮度 → 高级材质） */
    private const val KEY_USER_BLUR = "background_blur_enable"

    /**
     * 是否**可用**：系统版本够 + 系统开着跨窗口模糊 + 用户自己开着高级材质。
     * 三者缺一 → 底栏退回「半透明静态胶囊」（不模糊）。
     */
    fun isUsable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val crossWindowOn = try {
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        } catch (_: Throwable) {
            false
        }
        return crossWindowOn && isUserEnabled(context)
    }

    /**
     * 用户是否开着「高级材质」。读 Secure 设置不需要任何权限；
     * 读不到（非小米 / 旧版本没有这个键）时按「开着」处理，避免误伤。
     */
    fun isUserEnabled(context: Context): Boolean = try {
        Settings.Secure.getInt(context.contentResolver, KEY_USER_BLUR, 1) != 0
    } catch (_: Throwable) {
        true
    }

    /**
     * 给窗口挂上「窗口背景模糊」。必须在 [Dialog.show] 之前调用。
     * @param bg 窗口背景（必须有可见内容，模糊区域由它推导）
     */
    fun attach(dialog: Dialog, radiusPx: Int, bg: Drawable?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val w = dialog.window ?: return false
        return try {
            if (bg != null) w.setBackgroundDrawable(bg)
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            // 注意：setBackgroundBlurRadius 是 Window 上的方法（不是 LayoutParams 上的）
            w.setBackgroundBlurRadius(radiusPx)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
