# 时间戳记录器 (Timestamp Recorder)

> 安全 · 简洁 · 无广告 · 完全离线的安卓时间戳记录应用。
> 点一下，记录当前事件发生的时间（精确到秒，毫秒存储）。支持多事件分类、分类染色、桌面小组件一键记录。

**当前版本：v2.2**（versionCode 3 · 包名 `com.timestamp.recorder` · 零权限 · 无网络）

---

## ✨ 功能特性

### 核心记录
- **一键记录**：详情页大按钮点击即记录当前时间，显示 `yyyy-MM-dd HH:mm:ss` 与 Unix 秒
- **多事件管理**：创建多个事件（分类），每个事件独立记录时间戳
- **分类染色**：内置 12 色 Material 色板，每个事件可选一种颜色，一眼区分
- **记录管理**：点击复制（含 Unix 秒）、长按删除、撤销上一条、清空记录
- **CSV 导出**：通过系统文件选择器导出（UTF-8 带 BOM，Excel 可直接打开）

### 桌面小组件（两种独立类型）
| 类型 | 说明 |
|---|---|
| **全部事件（列表）** | 显示所有事件的彩色按钮列表，点击对应事件立即记录 |
| **单事件（大按钮）** | 创建时绑定一个事件，桌面显示事件色大按钮（含最近记录时间），点击即记录 |

- 尺寸可调：起步 1 格（110dp），可自由拉伸宽高，两种形态自适应
- 圆角可配置：设置页可切换小组件圆角档位（8 / 16 / 24 / 32dp），即时生效
- 完全离线：无轮询、无后台唤醒（`updatePeriodMillis=0`）

### 现代 UI 与适配
- **Material 3**：动态取色（Material You）、自动深色模式
- **毛玻璃 / 液态玻璃**：页面渐变背景 + 半透明玻璃卡片；「+」快捷按钮为液态玻璃质感
- **沉浸式适配（通杀）**：状态栏/导航栏透明，无黑边、手势条沉浸，适配小米 HyperOS 及各品牌
- **快捷按钮位置可调**：设置页可切换「+」按钮在底部左 / 中 / 右

### 安全设计
- **零权限**：不申请任何权限（无 INTERNET、无存储、无定位）
- **无广告、无第三方 SDK、无网络**，数据 100% 保存在应用私有存储（SharedPreferences + JSON）

---

## 🛠 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin 1.9.22 |
| 构建 | Gradle 8.2（wrapper）+ AGP 8.2.2 |
| UI | Material Components 1.11（Material 3）+ ViewBinding + RecyclerView |
| 小组件 | AppWidget（RemoteViews + RemoteViewsService，两种 provider） |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 24 |
| JDK | 17 |
| 依赖 | core-ktx / appcompat / material / constraintlayout / recyclerview / activity-ktx |

---

## 📂 目录结构

```
TimestampRecorder/
├── app/
│   ├── build.gradle                    # 模块构建配置（Kotlin + AGP + ViewBinding）
│   └── src/main/
│       ├── AndroidManifest.xml         # 零权限；4 Activity + 2 Widget Provider + 1 Service
│       ├── java/com/timestamp/recorder/
│       │   ├── EventRepository.kt      # 数据层：事件 CRUD + 记录 CRUD + JSON 存储
│       │   ├── ColorAdapter.kt         # 12 色选择器
│       │   ├── MainActivity.kt         # 主页：事件卡片 + 液态玻璃按钮 + 设置入口
│       │   ├── EventDetailActivity.kt  # 详情页：一键记录/撤销/复制/导出
│       │   ├── SettingsActivity.kt     # 设置页：按钮位置 + 小组件圆角
│       │   ├── TimestampWidgetProvider.kt      # 小组件①：全部事件列表 + 记录广播
│       │   ├── WidgetRemoteViewsService.kt     # 小组件①列表数据源
│       │   ├── WidgetSingleProvider.kt         # 小组件②：单事件大按钮
│       │   ├── WidgetSingleConfigureActivity.kt# 单事件绑定配置页
│       │   └── WidgetPrefs.kt          # 小组件配置：圆角档位 + 绑定存储
│       └── res/
│           ├── layout/                 # 8 个页面/条目布局 + 2 个小组件布局
│           ├── drawable/ + drawable-night/     # 毛玻璃/液态玻璃/圆角背景、图标
│           ├── values/ + values-night/ # Material3 色板 + 12 事件色 + 深色覆盖
│           ├── xml/                    # widget_all_info / widget_single_info
│           └── mipmap-*/               # 应用图标（5 档密度）
├── gradle/wrapper/                     # Gradle 8.2 wrapper
├── tools/                              # gen_icon.ps1 / verify_apk.ps1（辅助脚本）
├── gradlew.bat / build.gradle / settings.gradle / gradle.properties
├── release.keystore                  # ⚠️ 签名私钥：仅本机使用，严禁上传 Git
├── TimestampRecorder_v2.2.apk        # 已签名安装包（可直接安装）
├── README.md                         # 本文件（用户向）
└── DEVELOPMENT.md                    # 迭代说明文档（开发向，含踩坑记录）
```

---

## 🔨 构建

### 环境要求
- **JDK 17**（勿用 JDK 25，Android 构建不支持）
- **Android SDK**：platform 34 + build-tools 34.0.0
- 环境变量：`JAVA_HOME` 指向 JDK 17；`ANDROID_HOME` 指向 SDK

### 构建命令（Windows PowerShell）
```powershell
$env:JAVA_HOME = "你的 JDK17 路径"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleRelease
# 产物：app\build\outputs\apk\release\app-release-unsigned.apk
```

### 签名（可选，交付安装需要）
```powershell
$bt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0"
& "$bt\zipalign.exe" -f -p 4 app-release-unsigned.apk app-aligned.apk
& "$bt\apksigner.bat" sign --ks release.keystore --ks-key-alias timestamp `
  --ks-pass pass:你的密钥密码 --out 时间戳记录_v2.2.apk app-aligned.apk
```
> **⚠️ 重要安全提醒**：
> - `release.keystore`（alias=`timestamp`，密钥密码见 `DEVELOPMENT.md`）已包含在本项目包内，**仅限本机/信任环境使用**
> - **严禁将此密钥上传到任何 Git 仓库 / GitHub / 代码托管平台**（工程根目录 `.gitignore` 已排除 `release.keystore`，推送前务必检查）
> - 密钥泄露后任何人可冒名签名你的应用，请妥善保管

---

## 📲 安装

1. 直接安装 `TimestampRecorder_v2.2.apk`（已签名）
2. 首次使用：打开 App → 右下角「+」新建事件（命名 + 选颜色）
3. 长按事件卡片可编辑 / 删除；点击进入详情页一键记录
4. **添加小组件**：长按桌面空白处 → 小组件 → 找到「时间戳记录」，有两种类型：
   - 「全部事件」：直接添加，显示所有事件按钮
   - 「单事件」：添加时弹出绑定页，选一个事件后显示该事件大按钮
5. 设置页可调整「+」按钮位置与小组件圆角

---

## 📄 参考致谢

- UI / 功能概念参考 [Android-SimpleTimeTracker](https://github.com/Razeeman/Android-SimpleTimeTracker)（GPL-3.0，仅作概念参考，未复制其代码）

## 📜 License

MIT（本项目代码）
