# Tasks: 项目骨架与基础设施

## Task 1.1 创建 Gradle 项目骨架

- [x] 创建项目级 `build.gradle.kts`（repositories + plugins 声明）
- [x] 创建项目级 `settings.gradle.kts`（pluginManagement + dependencyResolutionManagement）
- [x] 创建 `gradle.properties`（org.gradle.jvmargs、android.useAndroidX、kotlin.code.style）
- [x] 创建 `gradle/wrapper/gradle-wrapper.properties`
- [x] 执行 `gradle wrapper` 生成 wrapper 文件
- [x] 验证 `gradle tasks` 正常输出
- **测试验收标准**：`./gradlew tasks` 输出无错误，包含 `assembleDebug` 任务

## Task 1.2 创建 app 模块与包目录

- [x] 创建 `app/build.gradle.kts`（compose + dependencies）
- [x] 创建 `app/src/main/` 目录结构
- [x] 创建包目录：`com.suj1e.screenpal`、`service`、`overlay`、`ocr`、`tts`、`util`、`ui.theme`
- [x] 配置 Compose BOM 版本
- **测试验收标准**：项目结构符合设计文档架构图，包目录完整

## Task 1.3 编写 AndroidManifest.xml

- [x] 声明所有权限（POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PROJECTION、WAKE_LOCK、SYSTEM_ALERT_WINDOW、INTERNET）
- [x] 注册 FileProvider（authorities = `${applicationId}.fileprovider`）
- [x] 注册 MainActivity（LAUNCHER）
- [x] 注册 SelectionOverlayActivity（透明主题，portrait）
- [x] 注册 ScreenCaptureService（mediaProjection foregroundServiceType）
- [x] 注册 FloatingWindowService（mediaProjection foregroundServiceType）
- **测试验收标准**：Manifest 无 lint 错误，所有组件声明正确

## Task 1.4 创建主题资源

- [x] 创建 `res/values/colors.xml`（紫色主题色系）
- [x] 创建 `res/values/themes.xml`（Theme.ScreenPal、Theme.ScreenPal.Overlay、Theme.ScreenPal.NoActionBar）
- [x] 创建 `res/values/strings.xml`（应用名称、权限说明文案）
- [x] 创建 `res/drawable/ic_launcher_foreground.xml`（自适应图标前景层）
- [x] 创建 `res/mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml`（自适应图标）
- [x] 创建 `res/xml/file_paths.xml`（FileProvider 路径配置）
- **测试验收标准**：资源编译通过，App 图标正确显示

## Task 1.5 实现 ScreenPalApplication 基类

- [x] 继承 `Application`
- [x] 初始化 DataStore 配置仓库（SettingsRepository）
- [x] 注册 ActivityLifecycleCallbacks（用于统计前台状态，判断是否显示悬浮窗）
- **测试验收标准**：Application 启动无崩溃，DataStore 初始化成功

## Task 1.6 实现 PermissionHelper 工具类

- [x] 实现 `hasOverlayPermission()`（Settings.canDrawOverlays）
- [x] 实现 `hasNotificationPermission()`（NotificationManagerCompat.areNotificationsEnabled + API 33 运行时检查）
- [x] 实现 `requestOverlayPermission()`（跳转 Settings.ACTION_MANAGE_OVERLAY_PERMISSION）
- [x] 实现 `requestNotificationPermission()`（Activity Result API 请求 POST_NOTIFICATIONS）
- [x] 实现 `getAllPermissionStatus()` 汇总方法
- [x] 实现 `getOemSpecialIntent()` 返回常见 OEM 特殊设置页 Intent
- [x] 为小米/华为/OPPO/vivo 添加 OEM 检测和设置入口
- **测试验收标准**：
  - 单元测试：覆盖 hasOverlayPermission / hasNotificationPermission 的 granted/denied 分支
  - 单元测试：验证 OEM 品牌检测逻辑返回正确的 Intent
  - 集成测试：在模拟器上验证权限请求流程

## Task 1.7 实现 ImageCropper 工具类

- [x] 实现 `crop(bitmap, rect, screenWidth, screenHeight)` 方法
- [x] 坐标映射：将屏幕坐标转换为 Bitmap 像素坐标
- [x] 边界处理：矩形超出 Bitmap 边界时做 clamp
- [x] 处理坐标缩放比（VirtualDisplay 尺寸 vs 屏幕物理尺寸）
- **测试验收标准**：
  - 单元测试：标准坐标映射（1080×2400 截图，选区 100,100,500,500 → 正确裁剪）
  - 单元测试：边界溢出 clamp（选区超出截图范围 → 裁剪到有效区域）
  - 单元测试：极端比例（VirtualDisplay 非屏幕物理尺寸时坐标映射正确）

## Task 1.8 创建 Compose 主题文件

- [x] `ui/theme/Color.kt` — ColorScheme 定义
- [x] `ui/theme/Type.kt` — Typography 定义（标题、正文、按钮等字体规格）
- [x] `ui/theme/Theme.kt` — ScreenPalTheme Composable，包含 darkTheme 支持
- **测试验收标准**：Compose 预览可正常渲染，主题色正确应用

## Task 1.9 创建基础占位文件

- [x] 为 service 目录下的所有 Service 创建空类文件
- [x] 为 overlay 目录下的 Activity/ViewModel 创建空类文件
- [x] 为 ocr/tts 目录下的所有类创建空类文件
- [x] 创建 SettingsRepository（DataStore 封装）
- [x] 创建 UserSettings 数据类
- [x] 确保项目可编译通过（无引用错误）
- **测试验收标准**：`./gradlew assembleDebug` 构建成功

## Task 1.10 验证构建

- [x] 执行 `./gradlew assembleDebug`，确认构建成功
- [x] 检查 APK 大小和基本信息
- [x] 确认无 Lint 错误
- **测试验收标准**：构建输出无错误，APK 大小 < 5MB（不含原生库）
