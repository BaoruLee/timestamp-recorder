package com.timestamp.recorder

import android.content.Intent
import android.net.Uri
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

        binding.btnGithub.setOnClickListener { openUrl("https://github.com/BaoruLee/timestamp-recorder") }
        binding.btnFeedback.setOnClickListener { openUrl("https://github.com/BaoruLee/timestamp-recorder/issues") }
    }

    /** 用系统浏览器打开外链：App 自身不申请 INTERNET 权限，由系统浏览器负责联网 */
    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
