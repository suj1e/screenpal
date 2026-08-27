# 2026-08-27-piper-warmup

## Why

用户反馈识别后没有声音。诊断（logcat + 系统状态实证）：TTS 三级降级链在模拟器上全部失效，其中 **Piper（默认引擎）是代码 bug**——`TtsManager.initialize()`（触发模型下载 + 创建 ONNX 会话的唯一入口）在整个 App 中没有任何调用方，引擎永远停在 PENDING，朗读时抛 `Piper engine not initialized` 降级，模型从未下载（应用私有目录无 .onnx，README 承诺的"首次使用自动下载"实际不会发生）。Cloud 因未配 API Key 跳过（预期），System 因模拟器 default 镜像无任何 TTS 引擎而失败（环境限制，真机一般自带）。

## What Changes

- `ScreenPalApplication.onCreate` 异步预热 TTS：新增 `internal open fun warmUpTts()`，在应用级协程作用域调用 `ttsManager.initialize()`（其内部已 runCatching，失败仅记日志，不影响主流程）
- TtsManager 补充 initialize 行为契约测试（幂等 / Piper 失败不抛出）
- **明确不做**：不改 ModelDownloader/下载逻辑（断点续传已具备，模型 URL 已验证可达）；不在模拟器上补装系统 TTS 引擎（环境配置，非代码问题）；不动降级链结构

## 成功标准

- 冷启动 App 后 Piper 引擎进入 READY（模型自动下载完成，~15MB，`hasModel()` 为真）
- 识别播报走 Piper 路径不再出现 `Piper engine not initialized` 降级
- 既有 41 项单测全绿，新增 warm-up 接线测试 + initialize 契约测试通过

## 优先级

- P1：默认播报引擎完全失效属功能性缺陷，直接影响产品核心价值。
