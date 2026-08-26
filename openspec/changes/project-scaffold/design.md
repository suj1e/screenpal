# Design: ScreenPal 项目骨架与基础设施

## 架构概览

```
app/src/main/java/com/suj1e/screenpal/
├── MainActivity.kt                      # 主界面入口（Compose）
├── MainViewModel.kt                     # 主界面状态管理
├── ScreenPalApplication.kt              # Application 基类（初始化全局依赖）
├── service/
│   ├── FloatingWindowService.kt
│   ├── ScreenCaptureService.kt
│   └── TtsManager.kt
├── overlay/
│   ├── SelectionOverlayActivity.kt
│   └── SelectionViewModel.kt
├── ocr/
│   ├── OcrEngine.kt
│   ├── MlKitOcrProvider.kt
│   └── CloudOcrProvider.kt
├── tts/
│   ├── PiperTtsEngine.kt
│   └── CloudTtsProvider.kt
├── util/
│   ├── ImageCropper.kt
│   └── PermissionHelper.kt
└── ui/
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## 技术栈

| 组件 | 选型 | 理由 |
|------|------|------|
| 语言 | Kotlin | Android 官方推荐，协程支持好 |
| UI | Jetpack Compose + Material 3 | 声明式 UI，框选 Canvas 交互方便 |
| 构建 | Gradle 8.x + AGP 8.x + Kotlin DSL | 现代 Android 构建标准 |
| 最低 SDK | API 24 (Android 7.0) | 覆盖 99%+ 活跃设备 |
| 目标 SDK | API 35 | 最新平台特性 + 合规要求 |
| 配置存储 | DataStore (Jetpack Preferences) | 类型安全，支持 Flow |
| 依赖注入 | 无（手动单例 + Application 管理） | 首版保持轻量，避免 Hilt 额外复杂度 |

## Gradle 配置要点

### Project-level build.gradle.kts
- 使用 `plugins` block 配置 `com.android.application` 和 `org.jetbrains.kotlin.android`
- 配置 `repositories`（google()、mavenCentral()、maven { url "https://jitpack.io" }）

### App-level build.gradle.kts
- `compileSdk = 35`
- `defaultConfig { minSdk = 24; targetSdk = 35 }`
- Compose BOM 管理 Compose 依赖版本
- 关键依赖：
  - `androidx.core:core-ktx`
  - `androidx.lifecycle:lifecycle-viewmodel-compose`
  - `androidx.activity:activity-compose`
  - `androidx.window:window-manager`（悬浮窗辅助）
  - `androidx.datastore:datastore-preferences`（配置存储）
  - `com.google.android.gms:play-services-mlkit-text-recognition`（OCR）
  - `com.microsoft.onnxruntime:onnxruntime-android`（Piper TTS 推理，直接集成 ONNX Runtime）
  - `io.ktor:ktor-client-android`（网络请求）
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android`

### Piper 库选型说明

**注意**：`com.github.Humanoid2064:piper-android-onnx` 可能未发布到 Maven Central。本方案采用直接集成 ONNX Runtime 的方式：
- 依赖 `com.microsoft.onnxruntime:onnxruntime-android:1.17.0`（已发布到 Maven Central）
- 手动封装 Piper VITS 模型的推理逻辑（加载 ONNX 模型 → 输入文本 → 输出音频）
- 这样不依赖第三方封装库，稳定可控

## AndroidManifest.xml 要点

```xml
<!-- 权限 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERNET" /> <!-- 云端 OCR/TTS -->

<!-- FileProvider for screenshot passing -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>

<!-- Application -->
<application
    android:name=".ScreenPalApplication"
    android:allowBackup="true"
    android:icon="@drawable/ic_launcher_foreground"
    android:roundIcon="@drawable/ic_launcher_foreground"
    android:theme="@style/Theme.ScreenPal">

    <!-- 主界面 -->
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:theme="@style/Theme.ScreenPal"
        android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- 框选 Activity（透明主题） -->
    <activity
        android:name=".overlay.SelectionOverlayActivity"
        android:exported="false"
        android:theme="@style/Theme.ScreenPal.Overlay"
        android:screenOrientation="portrait" />

    <!-- 截图临时服务（非持久，仅截图期间运行） -->
    <service
        android:name=".service.ScreenCaptureService"
        android:foregroundServiceType="mediaProjection"
        android:exported="false" />

    <!-- 悬浮窗前台服务（持久） -->
    <service
        android:name=".service.FloatingWindowService"
        android:foregroundServiceType="mediaProjection"
        android:exported="false" />
</application>
```

## 主题资源

### Theme 层级
- `Theme.ScreenPal`（主主题）— 紫色渐变主色 `#6366f1` / `#8b5cf6`
- `Theme.ScreenPal.Overlay`（框选主题）— 透明背景、无 ActionBar、全屏
- `Theme.ScreenPal.NoActionBar`（可选）— 隐藏 ActionBar 的主界面

### Color.kt
```kotlin
val Purple80 = Color(0xFF6366F1)
val PurpleGrey80 = Color(0xFF8B5CF6)
val PurpleAccent80 = Color(0xFFA78BFA)
val PurpleDark = Color(0xFF1a1a2e)
```

## FileProvider 配置

**res/xml/file_paths.xml**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="screenshots" path="screenshots/" />
    <files-path name="models" path="models/" />
</paths>
```

截图保存流程：
1. ScreenCaptureService 截图后写入 `cacheDir/screenshots/screenshot_<timestamp>.jpg`
2. 通过 `FileProvider.getUriForFile()` 获取 content Uri
3. 通过 Intent 传递 Uri + `FLAG_GRANT_READ_URI_PERMISSION`
4. SelectionOverlayActivity 从 Uri 解码为 Bitmap
5. 使用完毕后删除临时文件

## PermissionHelper 设计

提供以下静态方法：
- `hasOverlayPermission(context): Boolean` — 检查 `SYSTEM_ALERT_WINDOW`
- `hasNotificationPermission(context): Boolean` — API 33+ 检查
- `requestOverlayPermission(activity: Activity)` — 跳转设置页
- `requestNotificationPermission(activity: Activity)` — 运行时请求
- `getAllPermissionStatus(context): Map<String, Boolean>` — 汇总所有权限状态
- `getOemSpecialIntent(): Intent?` — 返回常见 OEM 的特殊设置页 Intent

### OEM 兼容

| 品牌 | 检测方式 | 特殊设置页 |
|------|----------|-----------|
| 小米 | `Build.MANUFACTURER.contains("Xiaomi")` | `miui.intent.action.APP_PERM_EDITOR`（悬浮窗） |
| 华为 | `Build.MANUFACTURER.contains("Huawei")` | `com.huawei.systemmanager.optimize.process.ProtectActivity` |
| OPPO | `Build.MANUFACTURER.contains("OPPO")` | `oppo.intent.action.OPPO_PERMISSION` |
| vivo | `Build.MANUFACTURER.contains("vivo")` | `permission.intent.action.softdetail` |

## ImageCropper 设计

```kotlin
object ImageCropper {
    fun crop(bitmap: Bitmap, rect: Rect, screenWidth: Int, screenHeight: Int): Bitmap?
}
```
- `rect` 是用户在框选 Activity 中绘制的矩形坐标（基于屏幕尺寸）
- 需要将屏幕坐标映射到 Bitmap 的实际像素坐标（考虑 VirtualDisplay 尺寸与屏幕物理尺寸的差异）
- 返回裁剪后的 Bitmap，边界溢出时做 clamp

## ScreenPalApplication 设计

```kotlin
class ScreenPalApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
```

- 不在此处初始化 TTS 引擎（按需初始化，避免启动延迟）
- 不在此处初始化 MediaProjection（按需授权）
- 仅初始化 DataStore 配置仓库

## 配置策略

- 使用 DataStore（Jetpack Preferences）存储配置
- 敏感配置（如云端 API Key）不硬编码，通过 DataStore 管理
- 首版不实现加密存储，后续可升级为 `EncryptedDataStore`
- Piper 模型文件不随 APK 打包（避免超过 100MB 限制），首次使用时从 CDN 下载到 `filesDir/models/`

## 事件流设计

```
┌──────────────────┐      Intent/Result       ┌──────────────────────┐
│ FloatingWindow   │ ────────────────────────▶ │ SelectionOverlay     │
│ Service          │ ◀──────────────────────── │ Activity             │
│ (点击悬浮球)      │  返回 Rect + Uri         │ (框选确认)           │
└────────┬─────────┘                          └──────────┬───────────┘
         │                                                  │
         │ startService(Intent)                             │ crop + OCR
         ▼                                                  ▼
┌──────────────────┐                              ┌──────────────────┐
│ ScreenCapture    │                              │ OcrEngine        │
│ Service          │                              │ (识别文字)        │
│ (截图)           │                              └────────┬─────────┘
└──────────────────┘                                       │
                                                            ▼
                                                 ┌──────────────────┐
                                                 │ TtsManager       │
                                                 │ (语音播报)        │
                                                 └──────────────────┘
```

关键通信机制：
- **FloatingWindow → SelectionOverlay**：`startActivityForResult` + Intent 传递截图 Uri
- **SelectionOverlay → FloatingWindow**：`setResult` + Intent 返回 Rect 坐标
- **FloatingWindow → ScreenCapture**：`startService` + `MediaProjection` 授权数据
- **ScreenCapture → FloatingWindow**：通过 `ResultReceiver` 或 `LocalBroadcastManager` 回传截图 Uri
- **SelectionOverlay → OCR/TTS**：直接调用（同进程内）

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | PermissionHelper、ImageCropper、DataStore 工具 | JUnit 4 + MockK | 90% |
| 集成测试 | 权限请求流程、DataStore 读写 | AndroidX Test | 70% |
| 手动验证 | 项目可编译、资源无缺失 | Gradle build | 100% 构建通过 |

### 测试数据

- 模拟截图 Bitmap（通过 Bitmap.createBitmap 创建测试用图片）
- 模拟屏幕尺寸（1080×2400、720×1600 等）
- 模拟权限状态（ granted / denied）

### 边界条件

- Bitmap 尺寸为 0 或极端值
- 屏幕旋转（ portrait / landscape 坐标映射）
- 不同 API Level 的权限行为差异
