<div align="center">

# 🕐 时间戳记录器

**Timestamp Recorder**

### 点一下，记下此刻

[![Version](https://img.shields.io/badge/版本-2.2-4C8BF5?style=flat-square)](../../releases)
[![Platform](https://img.shields.io/badge/Android-7.0+-3DDC84?style=flat-square)](../../releases)
[![Permissions](https://img.shields.io/badge/权限-0-97CA00?style=flat-square)](#-隐私优先)
[![License](https://img.shields.io/badge/license-MIT-orange?style=flat-square)](LICENSE)

</div>

---

<div align="center">
  <img src="docs/images/main.png" alt="主界面截图" width="220" />
  <br />
  <sub>多事件 · 分类染色 · 一键记录</sub>
</div>

---

## ✨ 特色

| 功能 | 说明 |
|:---|:---|
| 🎯 **一键记录** | 点一下记下当前时间，毫秒存储、秒级显示，同时给出 Unix 秒 |
| 🗂 **多事件分类** | 每个事件独立记录，12 色 Material 色板染色，一眼区分 |
| 🧩 **桌面小组件** | 两种形态，桌面上直接点，完全不用打开 App |
| ✂️ **随手管理** | 点击复制、长按删除、撤销上一条、一键清空 |
| 📤 **CSV 导出** | UTF-8 带 BOM，Excel 打开不乱码 |
| 🎨 **现代观感** | Material 3 动态取色、毛玻璃质感、沉浸式状态栏、自动深色模式 |
| 🔒 **隐私优先** | 零权限 · 无网络 · 无广告 · 无第三方 SDK |

## 🧩 桌面小组件

| 类型 | 说明 |
|:---|:---|
| **全部事件** | 彩色按钮列表，点击对应事件立即记录 |
| **单事件** | 绑定一个事件，桌面显示事件色大按钮，附上最近记录时间 |

两种都支持自由拉伸尺寸，圆角可在设置页切换（8 / 16 / 24 / 32dp）。

## 📦 安装

从 [**Releases**](../../releases) 下载最新 APK 直接安装，Android 7.0 及以上可用。

## 🚀 快速上手

1. 打开 App → 右下角「+」新建事件（命名 + 选颜色）
2. 点事件卡片进入详情页 → 点大按钮记录
3. 长按卡片可编辑 / 删除
4. **添加小组件**：长按桌面空白处 → 小组件 → 「时间戳记录」
5. 设置页可调整「+」按钮位置与小组件圆角

## 🔒 隐私优先

不申请任何权限 —— 没有 INTERNET，没有存储、定位权限。

数据 100% 保存在应用私有存储（SharedPreferences），不上传、不联网、无后台唤醒。

## 🛠 技术栈

| 项 | 值 |
|:---|:---|
| 语言 | Kotlin 1.9.22 |
| 构建 | Gradle 8.2 + AGP 8.2.2 |
| UI | Material Components 1.11（Material 3）+ ViewBinding + RecyclerView |
| 小组件 | AppWidget（RemoteViews + RemoteViewsService） |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 24 |
| JDK | 17 |

## 🔨 构建

```powershell
.\gradlew.bat assembleRelease
```

需要 **JDK 17** 与 **Android SDK 34**（用 JDK 25 会导致 AGP 构建失败）。

一键构建 + 签名 + 发布：`.\tools\release.ps1`。

## 📋 版本记录

每个版本的**新特性中文介绍与安装包**都在 [**Releases**](../../releases) 页面。

## 📄 许可

[MIT](LICENSE)

UI 与功能概念参考 [Android-SimpleTimeTracker](https://github.com/Razeeman/Android-SimpleTimeTracker)（GPL-3.0，仅作概念参考，未复制其代码）。

---

<div align="center">
<sub>安全 · 简洁 · 无广告 · 完全离线</sub>
</div>
