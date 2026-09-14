package com.timestamp.recorder

/**
 * 项目相关外链的集中定义。
 *
 * 改仓库地址 / 域名时只改这里，避免散落在各 Activity 里对不上。
 * App 自身不申请 INTERNET 权限，这些链接一律交给系统浏览器打开。
 */
object Links {

    /**
     * 仓库主页。
     *
     * 注意用**当前**的账号名：GitHub 账号从 `BaoruLee` 改名为 `baoru0908` 之后，
     * 旧地址只是靠重定向撑着，一旦旧用户名被他人注册就会失效。
     */
    const val REPO = "https://github.com/baoru0908/timestamp-recorder"

    /** 问题反馈 / 建议 */
    const val ISSUES = "$REPO/issues"

    /**
     * README 的「使用教程」章节。
     *
     * 用显式的命名锚点 `#tutorial`（在 README 里由 `<a name="tutorial"></a>` 定义），
     * 而不是 GitHub 自动生成的标题锚点——后者会随标题文字（尤其表情符号）变化而失效。
     */
    const val TUTORIAL = "$REPO#tutorial"
}
