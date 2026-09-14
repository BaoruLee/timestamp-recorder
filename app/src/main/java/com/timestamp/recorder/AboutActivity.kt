package com.timestamp.recorder

import android.os.Bundle
import com.timestamp.recorder.databinding.ActivityAboutBinding

/** 关于 / 开源引导页：版本信息、GitHub 入口、字体与许可出处 */
class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupChrome(binding.toolbar, binding.appBar, binding.root, R.string.about_title, showBack = true, scrollContent = binding.scrollContent)
        // 液态玻璃顶栏（与主页同源）：内容滚动时从玻璃底下穿过实时折射
        installLiquidTopGlass(binding.topGlass, binding.appBar, binding.scrollContent)

        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        binding.btnGithub.setOnClickListener { openExternalUrl(Links.REPO) }
        binding.btnFeedback.setOnClickListener { openExternalUrl(Links.ISSUES) }
    }
}
