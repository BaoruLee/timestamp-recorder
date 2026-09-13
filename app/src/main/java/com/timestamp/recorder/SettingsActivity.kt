package com.timestamp.recorder

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.timestamp.recorder.databinding.ActivitySettingsBinding

/** 设置页：快捷按钮（+）位置左/中/右切换 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "tsr_settings"
        const val KEY_FAB_POS = "fab_pos"
        const val FAB_START = "start"
        const val FAB_CENTER = "center"
        const val FAB_END = "end"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updatePadding(top = top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        when (prefs.getString(KEY_FAB_POS, FAB_END)) {
            FAB_START -> binding.rbStart.isChecked = true
            FAB_CENTER -> binding.rbCenter.isChecked = true
            else -> binding.rbEnd.isChecked = true
        }

        binding.radioFabPos.setOnCheckedChangeListener { _, checkedId ->
            val pos = when (checkedId) {
                com.timestamp.recorder.R.id.rbStart -> FAB_START
                com.timestamp.recorder.R.id.rbCenter -> FAB_CENTER
                else -> FAB_END
            }
            prefs.edit().putString(KEY_FAB_POS, pos).apply()
        }

        // 小组件圆角档位
        when (WidgetPrefs.corner(this)) {
            8 -> binding.rbCorner8.isChecked = true
            24 -> binding.rbCorner24.isChecked = true
            32 -> binding.rbCorner32.isChecked = true
            else -> binding.rbCorner16.isChecked = true
        }
        binding.radioCorner.setOnCheckedChangeListener { _, checkedId ->
            val corner = when (checkedId) {
                com.timestamp.recorder.R.id.rbCorner8 -> 8
                com.timestamp.recorder.R.id.rbCorner24 -> 24
                com.timestamp.recorder.R.id.rbCorner32 -> 32
                else -> 16
            }
            WidgetPrefs.setCorner(this, corner)
            WidgetRecordHelper.refreshAll(this)
            Snackbar.make(binding.root, R.string.corner_applied, Snackbar.LENGTH_SHORT).show()
        }

        // 一键添加到桌面（标准 API：支持的 ROM 弹确认框钉到桌面；不支持的 ROM 退回手动添加指引）
        binding.btnPinAll.setOnClickListener { pinWidget(TimestampWidgetProvider::class.java) }
        binding.btnPinSingle.setOnClickListener { pinWidget(WidgetSingleProvider::class.java) }
    }

    /**
     * 一键申请把小组件钉到桌面（Android 8.0+）。
     *
     * 系统小部件列表里各 App 的排序由启动器决定，开发者无法通过任何 Manifest 属性干预，
     * 所以用这个官方 API 让用户不必去列表里翻找。
     * 返回 false 表示当前桌面不支持（部分 ROM 如此），此时提示用户手动长按桌面添加。
     */
    private fun pinWidget(provider: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(this)
            if (manager.requestPinAppWidget(ComponentName(this, provider), null, null)) {
                Toast.makeText(this, R.string.toast_pin_ok, Toast.LENGTH_LONG).show()
                return
            }
        }
        // 桌面未开放该能力（小米 / MIUI 实测返回 false）：退回手动添加指引
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pin_guide_title)
            .setMessage(R.string.pin_guide_msg)
            .setPositiveButton(R.string.i_know, null)
            .show()
    }
}
