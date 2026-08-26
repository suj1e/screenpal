# Tasks: 主界面 + 全流程集成

## Task 6.1 实现 DataStore 配置序列化

- [ ] 添加 `kotlinx-serialization-json` 依赖
- [ ] 实现 `UserSettingsSerializer`（DataStore Proto 序列化）
- [ ] 或使用 `DataStore<Preferences>` 手动读写
- **测试验收标准**：配置读写正确，重启后保留

## Task 6.2 实现 SettingsRepository

- [ ] 创建 `SettingsRepository` 类
- [ ] 封装 DataStore 读写操作
- [ ] 暴露 `userSettings: Flow<UserSettings>`
- [ ] 实现 `updateSettings` 方法
- **测试验收标准**：
  - 单元测试：写入配置后 Flow 正确发出
  - 单元测试：默认值正确

## Task 6.3 实现 MainActivity

- [ ] 创建 `MainActivity.kt`，继承 `ComponentActivity`
- [ ] 设置 Compose 主题
- [ ] 实现 `setContent` 渲染 MainScreen
- [ ] 注入 MainViewModel
- **测试验收标准**：Activity 正常启动，无崩溃

## Task 6.4 实现 Compose UI 组件

- [ ] 实现 `HomeTopBar`（应用名称 + 设置按钮）
- [ ] 实现 `PermissionCard`（权限状态展示 + 快捷授权入口）
- [ ] 实现 `ToggleCard`（悬浮窗开关 + 描述）
- [ ] 实现 `TtsSettingsCard`（引擎选择、语速滑块、音调滑块）
- [ ] 实现 `OcrSettingsCard`（模式选择、API Key 输入）
- [ ] 实现启动悬浮窗按钮（带权限检查）
- **测试验收标准**：
  - Compose 测试：每个 Card 正确渲染
  - Compose 测试：Toggle 开关状态正确切换
  - Compose 测试：滑块拖动正确更新值

## Task 6.5 实现 MainViewModel

- [ ] 创建 `MainViewModel`，注入所有依赖
- [ ] 管理 UI 状态（Permissions、TTS 设置、OCR 设置、悬浮窗状态）
- [ ] 实现 `requestPermission()` 方法
- [ ] 实现 `toggleFloatingWindow()` 方法
- [ ] 实现 `startFloatingWindow()` 方法（检查权限 → 启动服务）
- [ ] 暴露 `uiState` 为 StateFlow
- **测试验收标准**：
  - 单元测试：权限状态正确汇总
  - 单元测试：悬浮窗开关正确切换服务
  - 单元测试：启动悬浮窗时权限不足提示正确

## Task 6.6 实现 OCR 流程集成

- [ ] 在 SelectionViewModel 中注入 OcrEngine
- [ ] 框选确认后调用 OCR 识别
- [ ] 识别完成后自动调用 TtsManager.speak()
- [ ] 在 SelectionOverlayActivity 底部展示识别结果
- [ ] 添加播放/暂停/停止按钮
- **测试验收标准**：框选后自动触发 OCR + TTS 播报

## Task 6.7 实现结果展示 + 操作

- [ ] 在框选 Activity 底部添加结果卡片（OCR 文本 + 置信度）
- [ ] 实现 TTS 控制按钮（播放/暂停/停止）
- [ ] 实现引擎切换标签（Piper / Cloud / System）
- [ ] 实现波形动画（Compose 动画）
- [ ] 实现复制文本 / 重新选择 / 分享功能
- **测试验收标准**：识别结果正确展示，TTS 控制可用

## Task 6.8 实现 OEM 兼容引导

- [ ] 实现常见 OEM 品牌检测
- [ ] 实现 OEM 特殊设置页 Intent 跳转
- [ ] 悬浮窗权限被拒绝时显示 OEM 特定引导页
- **测试验收标准**：
  - 单元测试：品牌检测正确
  - 单元测试：OEM Intent 构建正确

## Task 6.9 端到端联调

- [ ] 流程 1：启动 App → 授权 → 开启悬浮窗 → 退出 App → 悬浮球显示
- [ ] 流程 2：点击悬浮球 → 截图 → 框选 → OCR → TTS 播报
- [ ] 流程 3：拖拽悬浮球 → 位置更新 → 点击触发识别
- [ ] 流程 4：OCR 结果不满意 → 重新框选 → 再次识别
- [ ] 流程 5：TTS 引擎切换 → 确认播报效果
- **测试验收标准**：5 个端到端流程全部通过

## Task 6.10 异常处理 + 降级验证

- [ ] 测试 Piper 模型下载失败 → 降级到 System TTS
- [ ] 测试 OCR 云端调用超时 → 降级到端侧结果
- [ ] 测试 MediaProjection 授权失效 → 提示重新授权
- [ ] 测试悬浮窗权限被系统回收 → 引导用户重新开启
- **测试验收标准**：所有降级路径可用

## Task 6.11 编写 README

- [ ] 功能说明（悬浮窗识别、框选、OCR、TTS）
- [ ] 权限说明（需要哪些权限、为什么需要）
- [ ] 使用教程（安装 → 授权 → 开启悬浮窗 → 使用）
- [ ] 已知限制（不支持视频、不打包模型等）
- [ ] 构建说明（Gradle 构建、签名配置）
- [ ] 常见问题（OEM 悬浮窗权限、Piper 模型下载失败等）
- **测试验收标准**：README 内容完整，新用户可按照教程完成首次使用

## Task 6.12 最终构建验证

- [ ] 执行 `./gradlew assembleDebug`
- [ ] 执行 `./gradlew lint` 检查代码规范
- [ ] 在真机上验证完整流程
- [ ] 检查 APK 大小（确认未打包 Piper 模型）
- [ ] 检查内存泄漏（手动验证 Bitmap recycle）
- **测试验收标准**：
  - 构建通过，Lint 无严重错误
  - 真机完整流程可用
  - APK 大小 < 50MB（不含模型）
