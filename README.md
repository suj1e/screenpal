# ScreenPal

Android 屏幕识别 + 语音播报 App。点击悬浮球框选屏幕区域，自动 OCR 识别文字并用 Piper 神经语音播报。

## 快速开始

### 原型预览

```bash
cd prototypes
node server.js
# 浏览器打开 http://localhost:8080/
```

### OpenSpec 变更

本项目使用 OpenSpec 管理需求，共 6 个 Change：

| # | Change | 说明 | 状态 |
|---|--------|------|------|
| 1 | `project-scaffold` | Gradle 骨架 + 基础设施 | 📝 待实现 |
| 2 | `screen-capture-service` | MediaProjection 截图服务 | 📝 待实现 |
| 3 | `floating-window-and-selection` | 悬浮窗 + 框选 Activity | 📝 待实现 |
| 4 | `ocr-engine` | OCR 统一接口 + ML Kit + 云端 | 📝 待实现 |
| 5 | `tts-engine` | Piper TTS + Cloud TTS + 降级 | 📝 待实现 |
| 6 | `main-interface-and-integration` | 主界面 + 端到端集成 | 📝 待实现 |

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- MediaProjection（屏幕截取）
- ML Kit Text Recognition（端侧 OCR）
- ONNX Runtime + Piper VITS（端侧神经 TTS）
- Google Cloud Vision API / Google Cloud TTS（云端增强）
- Gradle 8.x + AGP 8.x

## 依赖拓扑

```
project-scaffold
    ├── screen-capture-service
    │   └── floating-window-and-selection
    │       └── ocr-engine
    │           └── tts-engine
    │               └── main-interface-and-integration
    └── tts-engine
        └── main-interface-and-integration
```

Change 1-3 可串行，Change 4-5 在 Change 3 完成后可并行，Change 6 最后。
