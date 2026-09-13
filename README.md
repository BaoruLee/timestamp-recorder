# 时间戳记录器 · Timestamp Recorder

> 点一下，记下此刻。
> 零权限 · 无广告 · 完全离线的安卓时间戳记录应用。

![Version](https://img.shields.io/badge/version-2.2-blue)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen)
![Permissions](https://img.shields.io/badge/permissions-0-success)
![Offline](https://img.shields.io/badge/network-none-success)
![License](https://img.shields.io/badge/license-MIT-green)

---

## 特色

- **一键记录** —— 点一下记下当前时间，毫秒存储、秒级显示，同时给出 Unix 秒
- **多事件分类** —— 每个事件独立记录，12 色 Material 色板染色，一眼区分
- **桌面小组件** —— 两种形态：全部事件列表 / 单事件大按钮，桌面上直接点，不用打开 App
- **随手管理** —— 点击复制、长按删除、撤销上一条、一键清空
- **CSV 导出** —— UTF-8 带 BOM，Excel 打开不乱码
- **现代观感** —— Material 3 动态取色、毛玻璃质感、沉浸式状态栏、自动深色模式
- **隐私优先** —— 零权限、无网络、无第三方 SDK，数据只存在本机私有存储

---

## 安装

从 [**Releases**](../../releases) 页面下载最新 APK 直接安装。

## 快速上手

1. 打开 App → 右下角「+」新建事件（命名 + 选颜色）
2. 点事件卡片进入详情页 → 点大按钮记录
3. 长按卡片可编辑 / 删除
4. **添加小组件**：长按桌面空白处 → 小组件 → 「时间戳记录」，两种类型可选
5. 设置页可调整「+」按钮位置与小组件圆角

---

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin 1.9.22 |
| 构建 | Gradle 8.2 + AGP 8.2.2 |
| UI | Material Components 1.11（Material 3）+ ViewBinding + RecyclerView |
| 小组件 | AppWidget（RemoteViews + RemoteViewsService） |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 24 |
| JDK | 17 |

## 构建

```powershell
.\gradlew.bat assembleRelease
```

需要 **JDK 17** 与 **Android SDK 34**。注意不要用 JDK 25，AGP 8.x 会构建失败。

签名与发布可走一键脚本：`.\tools\release.ps1`（详见脚本内注释）。

---

## 版本记录

每个版本的**变更说明与安装包**都在 [**Releases**](../../releases) 页面。

## 许可

[MIT](LICENSE)

---

## 参考致谢

UI 与功能概念参考 [Android-SimpleTimeTracker](https://github.com/Razeeman/Android-SimpleTimeTracker)（GPL-3.0，仅作概念参考，未复制其代码）。
