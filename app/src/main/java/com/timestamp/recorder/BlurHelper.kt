package com.timestamp.recorder

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * 液态玻璃 / 高斯模糊工具。
 *
 * API 31+（Android 12）用 RenderEffect 对视图做背景模糊，营造液态玻璃质感；
 * 低版本静默降级（保持透明玻璃质感，仅无模糊），不影响功能。
 */
object BlurHelper {
    fun applyBlur(view: View, radiusPx: Float = 16f): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(
                    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                )
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(null)
            } catch (_: Exception) {
            }
        }
    }
}
