# 念念（ScreenPal）

念念（ScreenPal）是一款 Android 屏幕识别 + 语音播报 App。点击悬浮球框选屏幕区域，自动 OCR 识别文字并用 TTS 播报（Piper 神经语音优先，云端/系统兜底）。

## 功能说明

- **悬浮球**：授予悬浮窗权限后启动服务，退出 App 后悬浮球持续显示在桌面和任意应用上层；支持拖拽、点击触发截图。
- **框选识别**：点击悬浮球 → 自动截取当前屏幕 → 全屏截图上圈选区域——默认随手画套索，可在设置中切换长方形拖拽（最小 48dp；绘制时画面保持原亮度，确认后圈外变暗）→ 自动 OCR 识别 → 自动 TTS 播报。
- **OCR**：端侧 PaddleOCR（PP-OCRv4 ONNX）离线识别；可选 StepFun 视觉（step-3.7-flash）云端增强；混合模式在端侧置信度低于 0.75 时自动走云端。
- **TTS 播报**：
  - Piper（默认）：ONNX Runtime 端侧推理，中文女声 `zh_CN-huayan-medium`（约 15MB，首次使用自动从 HuggingFace 下载到应用私有目录，支持断点续传）；
  - StepFun 在线语音（云端）：stepaudio-2.5-tts 大模型音色，默认 tianmeinvsheng，需在设置中填写 StepFun API Key（platform.stepfun.com 获取）；
  - System：系统 TextToSpeech 兜底；
  - 任一引擎失败自动降级 Piper → System（从所选引擎顺延）。
- **结果操作**：识别完成后底部卡片展示文本与置信度，可停止播报、复制文本、重新框选。

## 权限说明

| 权限 | 用途 | 触发时机 |
|------|------|----------|
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 在其他应用上方显示悬浮球 | 主界面「去授权」跳转系统设置 |
| 通知（POST_NOTIFICATIONS） | Android 13+ 前台服务通知 | 首次启动前台服务 |
| 屏幕录制（MediaProjection） | 截取当前屏幕 | 每次点击悬浮球时系统弹窗授权 |
| 前台服务 | 悬浮窗保活与截图过程通知 | 随服务启动 |

MediaProjection 授权数据仅保存在内存中（Application 单例），App 进程结束后需重新授权。

## 使用教程

1. **安装**：`gradle assembleDebug` 构建，安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. **授权**：打开 App，在「权限状态」卡片依次授予悬浮窗权限与通知权限。
3. **开启悬浮窗**：点击「启动悬浮窗」按钮或开关，状态栏出现常驻通知即表示服务运行中。
4. **回到任意界面**：按 Home 键退出 App，悬浮球保持在屏幕右侧。
5. **识别播报**：点击悬浮球 → 系统弹出屏幕录制授权确认 → 随手圈出要朗读的文字区域 → 松手后自动识别并播报。
6. **调整配置**：回到 App 可切换 OCR 模式、TTS 引擎、语速（0.5x–2.0x）、音调（0.5–2.0），并在「StepFun 云服务」中填写 API Key 与音色（一把 Key 包办在线语音 / 云 OCR / AI 转译）。

## 已知限制

- 不支持视频画面逐帧识别（单次静态截图）。
- APK 不内置 Piper 模型以控制体积；首次使用 Piper 需网络下载模型（约 15MB）。
- Piper 音素映射为简化实现（直接查 config 的 phoneme_id_map），未集成 espeak-ng 分词归一化，长难句发音可能不自然——可切换 System TTS 作为替代。
- StepFun 云服务（在线语音 / 视觉 OCR / 转译）为付费服务（有免费额度），需在 platform.stepfun.com 创建 API Key；无凭据或调用失败自动降级 Piper / 端侧。
- OEM 后台清理可能杀掉悬浮窗服务，可在系统设置中将 App 加入自启动白名单。

## 构建说明

```bash
# Debug 构建
gradle assembleDebug

# 单元测试
gradle testDebugUnitTest

# Lint
gradle lint
```

- 要求 JDK 21、Android SDK 35（compileSdk）/ 24（minSdk）。
- Gradle wrapper 当前指向的发行版镜像不可用（404），项目使用本机 Gradle 9.7.1 直接构建；如需恢复 wrapper，请将 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改为可达地址。
- ABI 过滤为 armeabi-v7a + arm64-v8a，输出 APK 约 <50MB（不含模型）。

## 常见问题

**Q：小米/华为手机上悬浮窗开启后不显示？**
A：MIUI/EMUI 还需在「安全中心 → 权限管理」中允许「后台弹出界面」。App 已内置厂商检测（PermissionHelper.getOemSpecialIntent），后续版本将提供一键直达。

**Q：点击悬浮球没反应？**
A：多半是 MediaProjection 授权被拒绝或已失效，重新点击一次即可再次弹出授权；部分 ROM 会静默拒绝，请检查通知栏错误提示。

**Q：Piper 模型下载失败？**
A：HuggingFace 在部分网络不可达。代理环境下重试即可；或先在设置中切换到 SYSTEM 引擎继续使用。

**Q：识别出来的文字发音奇怪？**
A：受限于简化音素映射，建议切换到 SYSTEM 引擎对比效果。

## OpenSpec 变更

| # | Change | 说明 | 状态 |
|---|--------|------|------|
| 1 | `project-scaffold` | Gradle 骨架 + 基础设施 | ✅ 已实现 |
| 2 | `screen-capture-service` | MediaProjection 截图服务 | ✅ 已实现 |
| 3 | `floating-window-and-selection` | 悬浮窗 + 框选 Activity | ✅ 已实现 |
| 4 | `ocr-engine` | OCR 统一接口 + ML Kit + 云端 | ✅ 已实现 |
| 5 | `tts-engine-and-main-interface` | Piper TTS + Cloud TTS + 降级 | ✅ 已实现 |
| 6 | `main-interface-and-integration` | 主界面 + 端到端集成 | ✅ 已实现 |

## 技术栈

- Kotlin + Jetpack Compose（主界面）/ 传统 View（悬浮球与框选层）
- Jetpack WindowManager + TYPE_APPLICATION_OVERLAY（悬浮窗）
- MediaProjection + VirtualDisplay + ImageReader（屏幕截取）
- FileProvider content:// Uri 传递截图（规避 Binder 1MB 限制）
- PaddleOCR PP-OCRv4（端侧 ONNX）/ StepFun step-3.7-flash（云端视觉）
- ONNX Runtime + Piper VITS（端侧神经 TTS）/ StepFun stepaudio-2.5-tts（云端）/ 系统 TTS（兜底）
- DataStore Preferences（配置持久化）+ Ktor Client（网络）
