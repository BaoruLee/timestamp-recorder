package com.timestamp.recorder

import android.os.Bundle
import com.timestamp.recorder.databinding.ActivityAboutBinding

/** 关于 / 开源引导页：版本信息、GitHub 入口、字体与许可出处 */
class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupChrome(binding.toolbar, binding.appBar, binding.root, R.string.about_title, showBack = true)

        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        binding.btnGithub.setOnClickListener { openExternalUrl(Links.REPO) }
        binding.btnFeedback.setOnClickListener { openExternalUrl(Links.ISSUES) }
    }
}
