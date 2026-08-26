# Proposal: TTS 引擎

## Summary

实现 Piper 端侧神经 TTS 引擎（ONNX Runtime 推理，离线可用）、云端 TTS 可选增强（Google Cloud TTS）、TtsManager 统一封装。

## Motivation

- 原生 Android TTS 语音生硬，用户对自然度要求高
- Piper 神经语音（VITS 架构）自然度远优于原生，完全离线，适合移动端
- 用户需要统一入口管理 TTS 引擎、语速、音调
- Piper 模型需按需下载，避免 APK 体积过大

## Goals

1. PiperTtsEngine 封装 ONNX Runtime 推理，支持中文神经语音
2. Piper 模型按需下载（首次使用时从 CDN 下载到 internal storage）
3. CloudTtsProvider 可选增强（Google Cloud TTS，官方稳定接口）
4. SystemTtsEngine 降级方案（原生 TextToSpeech）
5. TtsManager 统一封装 speak/stop/语速/音调
6. 自动降级：Piper 不可用时 fallback 到 System TTS

## Non-goals

- 不实现 TTS 音色选择（首版固定一个中文音色）
- 不实现 TTS 实时流式播报（整段文本生成后播报）
- 不实现多语言界面

## 依赖

- 前置：`project-scaffold`（需要 DataStore、Application 基类）
- 前置：`ocr-engine`（需要 OcrResult 定义）

## Piper 集成方案

**注意**：`piper-android-onnx` 库可能未发布到 Maven Central。本方案采用直接集成 ONNX Runtime：

- 依赖 `com.microsoft.onnxruntime:onnxruntime-android:1.17.0`（已发布到 Maven Central）
- 手动封装 Piper VITS 模型的推理逻辑：
  1. 加载 ONNX 模型（`.onnx` 文件）和配置文件（`.json`）
  2. 文本预处理：将中文文本转为音素序列
  3. ONNX 推理：输入音素 → 输出 mel 频谱
  4. 声码器：将 mel 频谱转为音频波形
  5. 输出为 WAV 文件，通过 MediaPlayer 播放

**选型理由**：
- 直接集成 ONNX Runtime 更稳定可控
- 避免依赖第三方未发布库
- ONNX Runtime 官方支持 Android，ABI 覆盖完整（armeabi-v7a, arm64-v8a）
