package com.timestamp.recorder

import android.content.Context
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.DynamicColors
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
    }
}
