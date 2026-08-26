# Proposal: 主界面 + 全流程集成

## Summary

实现 MainActivity 主界面（Jetpack Compose，权限状态展示、悬浮窗开关、TTS/OCR 配置、启动按钮），MainViewModel 状态管理，SettingsRepository（DataStore 配置持久化），以及端到端联调：悬浮窗点击 → 截图 → 框选 → OCR → TTS 播报全流程。

## Motivation

- 用户需要一个统一入口配置 TTS 引擎、语速、音调、OCR 模式
- 主界面需要展示权限状态并提供快捷授权入口
- 端到端流程打通（悬浮窗 → 框选 → OCR → TTS）是 App 的核心价值
- README 文档帮助用户快速上手

## Goals

1. MainActivity 提供完整配置界面（权限状态、悬浮窗开关、TTS 设置、OCR 设置）
2. MainViewModel 管理 UI 状态，暴露 StateFlow
3. SettingsRepository 封装 DataStore 读写
4. 端到端流程打通：悬浮窗点击 → 截图 → 框选 → OCR → TTS 播报
5. 异常降级和错误提示完善
6. README 文档完整（功能说明、权限说明、使用教程）

## Non-goals

- 不实现多语言界面（首版仅中文）
- 不实现用户账号系统
- 不提供云端数据同步

## 依赖

- 前置：`project-scaffold`（需要基础配置、主题、PermissionHelper）
- 前置：`screen-capture-service`（需要截图能力）
- 前置：`floating-window-and-selection`（需要悬浮窗和框选）
- 前置：`ocr-engine`（需要 OCR 识别）
- 前置：`tts-engine`（需要 TTS 播报）
