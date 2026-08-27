# Tasks: 主界面 + 全流程集成

## Task 6.1 实现 DataStore 配置序列化

- [x] 添加 `kotlinx-serialization-json` 依赖
- [x] 实现 `UserSettingsSerializer`（DataStore Proto 序列化）
  - 采用下方备选方案（Preferences 手动读写），未引入 Proto 序列化器
- [x] 或使用 `DataStore<Preferences>` 手动读写（实际采用此方案）
- **测试验收标准**：配置读写正确，重启后保留

## Task 6.2 实现 SettingsRepository

- [x] 创建 `SettingsRepository` 类
- [x] 封装 DataStore 读写操作
- [x] 暴露 `userSettings: Flow<UserSettings>`
- [x] 实现 `updateSettings` 方法（`update(transform: UserSettings.() -> UserSettings)`）
- **测试验收标准**：
  - 单元测试：写入配置后 Flow 正确发出（DataStore 需 Android 环境，真机验证）
  - 单元测试：默认值正确

## Task 6.3 实现 MainActivity

- [x] 创建 `MainActivity.kt`，继承 `ComponentActivity`
- [x] 设置 Compose 主题
- [x] 实现 `setContent` 渲染 MainScreen
- [x] 注入 MainViewModel（viewModelFactory + viewModels 委托，onResume 刷新权限状态）
- **测试验收标准**：Activity 正常启动，无崩溃

## Task 6.4 实现 Compose UI 组件

- [x] 实现 `HomeTopBar`（TopAppBar 应用名称）
- [x] 实现 `PermissionCard`（权限状态展示 + 快捷授权入口）
- [x] 实现 `ToggleCard`（悬浮窗开关 + 描述）
- [x] 实现 `TtsSettingsCard`（引擎选择、语速滑块、音调滑块）
- [x] 实现 `OcrSettingsCard`（模式选择、API Key 输入）
- [x] 实现启动悬浮窗按钮（带权限检查）
- **测试验收标准**：
  - Compose 测试：每个 Card 正确渲染（集成阶段验证）
  - Compose 测试：Toggle 开关状态正确切换（集成阶段验证）
  - Compose 测试：滑块拖动正确更新值（集成阶段验证）

## Task 6.5 实现 MainViewModel

- [x] 创建 `MainViewModel`，注入依赖
- [x] 管理 UI 状态（Permissions、TTS 设置、OCR 设置、悬浮窗状态）
- [x] 实现 `refreshPermissions()`（onResume 触发）
- [x] 实现 `toggleFloatingWindow()`（开关回调内分流 start/stop）
- [x] 实现 `startFloatingWindow()` 方法（检查权限 → 启动服务 → 持久化开关）
- [x] 暴露 `uiState` 为 StateFlow；含 missingRequiredPermissions() 纯函数
- **测试验收标准**：
  - 单元测试：权限状态正确汇总（missingRequiredPermissions 逻辑随 OEM 测试一并覆盖路径；服务级联测为手动）
  - 单元测试：悬浮窗开关正确切换服务（真机验证）
  - 单元测试：启动悬浮窗时权限不足提示正确（真机验证）

## Task 6.6 实现 OCR 流程集成

- [x] 在 SelectionOverlayActivity 中接入 OcrEngine（按设置选择 LOCAL/CLOUD/HYBRID，云端缺 Key 自动降级 LOCAL）
- [x] 框选确认后调用 OCR 识别（SelectionViewModel.calculateCropRect 裁剪坐标映射）
- [x] 识别完成后自动调用 TtsManager.speak()
- [x] 在 SelectionOverlayActivity 底部展示识别结果
- [x] 添加播放/停止控制按钮
- **测试验收标准**：框选后自动触发 OCR + TTS 播报（构建通过 + 真机端到端验证）

## Task 6.7 实现结果展示 + 操作

- [x] 在框选 Activity 底部添加结果卡片（OCR 文本 + 置信度 + 文本块数）
- [x] 实现 TTS 控制按钮（停止播报）
- [ ] 实现引擎切换标签（引擎切换已在主界面提供，卡片内暂不重复）
- [ ] 实现波形动画（Compose 动画）
- [x] 实现复制文本 / 重新选择功能
- **测试验收标准**：识别结果正确展示，TTS 控制可用

## Task 6.8 实现 OEM 兼容引导

- [x] 实现常见 OEM 品牌检测（PermissionHelper.oemBrandFor 纯函数，覆盖小米/红米/华为/荣耀/OPPO/vivo/iQOO）
- [x] 实现 OEM 特殊设置页 Intent 跳转（getOemSpecialIntent）
- [x] 悬浮窗权限被拒绝时显示引导（服务侧 Toast 提示 + 主界面「去授权」）
- **测试验收标准**：
  - 单元测试：品牌检测正确 ✓（OemBrandDetectionTest）
  - 单元测试：OEM Intent 构建正确（Build.MANUFACTURER 为系统属性，真机验证）

## Task 6.9 端到端联调

- [ ] 流程 1：启动 App → 授权 → 开启悬浮窗 → 退出 App → 悬浮球显示
- [ ] 流程 2：点击悬浮球 → 截图 → 框选 → OCR → TTS 播报
- [ ] 流程 3：拖拽悬浮球 → 位置更新 → 点击触发识别
- [ ] 流程 4：OCR 结果不满意 → 重新框选 → 再次识别
- [ ] 流程 5：TTS 引擎切换 → 确认播报效果
- **测试验收标准**：5 个端到端流程全部通过（需模拟器/真机，集成阶段执行）

## Task 6.10 异常处理 + 降级验证

- [x] 实现 Piper 失败 → Cloud → System TTS 降级链（TtsManager 单测覆盖 PIPER→SYSTEM 路径）
- [x] 实现 OCR 云端缺配置 → 自动降级端侧（resolveOcrEngine 内置降级）
- [x] MediaProjection 授权失效 → 回传 ERROR_NEED_AUTH 并恢复悬浮球提示重新授权
- [ ] 悬浮窗权限被系统回收的现场引导（onStartCommand 校验 + 提示，系统回收场景需真机验证）
- **测试验收标准**：所有降级路径可用（代码级完成；运行时验证随 6.9）

## Task 6.11 编写 README

- [x] 功能说明（悬浮窗识别、框选、OCR、TTS）
- [x] 权限说明（需要哪些权限、为什么需要）
- [x] 使用教程（安装 → 授权 → 开启悬浮窗 → 使用）
- [x] 已知限制（不支持视频、不打包模型等）
- [x] 构建说明（Gradle 构建、wrapper 镜像问题与替代方案）
- [x] 常见问题（OEM 悬浮窗权限、Piper 模型下载失败等）
- **测试验收标准**：README 内容完整，新用户可按照教程完成首次使用 ✓

## Task 6.12 最终构建验证

- [x] 执行 `gradle assembleDebug`（构建通过）
- [ ] 执行 `gradle lint` 检查代码规范
- [ ] 在真机上验证完整流程
- [x] 检查 APK 大小（ABI 过滤 armeabi-v7a/arm64-v8a，未打包 Piper 模型）
- [x] Bitmap recycle（SelectionOverlayActivity onDestroy 已处理）
- **测试验收标准**：
  - 构建通过 ✓，Lint 无严重错误（待执行）
  - 真机完整流程可用（待验证）
