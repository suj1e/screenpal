# 2026-08-27-chinese-translation-broadcast

## Why

产品核心诉求「无论框选的是什么语言，都要播报中文」：在 OCR 与 TTS 之间插入翻译层。翻译实现随用户选型改为**豆包机器翻译大模型（火山引擎语音技术产品线）**——与豆包 TTS 同一控制台同一凭据（AppID/Token 或语音 API Key），一套凭据管 TTS + 翻译两件事；方舟 Key 仅留给视觉 OCR。结果卡片双语呈现（译文主显、原文小字）。

## What Changes

- 新增 `TranslateService` 接口与 `DoubaoTranslateClient`（方舟 `chat/completions`，system prompt 约束"只输出简体中文译文"，温度 0）
- 新增 `ChineseBroadcastPipeline`：语言启发式（CJK 占比 ≥50% 直读，零网络）→ 非中文调翻译 → 译文交 `TtsManager.speak`
- `SelectionOverlayActivity` 接入：播报译文；卡片译文主显 + 原文小字
- 设置新增「自动翻译播报」开关（默认开）；翻译凭据复用豆包语音的 AppID/Token（与 TTS 同源，不新增键）
- 失败降级：无 Key/网络/超时 5s → 播报原文 + 卡片「翻译不可用」
- 被否选项：百度翻译（用户否）、本地离线翻译模型（体积质量不匹配 V1）

## 成功标准

- 框选英文 → 播报中文译文，卡片双语
- 框选中文 → 零网络直读（logcat 证据）
- 开关关 → 直读；无 Key/断网 → 播报原文不崩溃
- 单测全绿（启发式/请求/解析/降级矩阵）

## 优先级

- P1：产品核心诉求的直接实现。

## 依赖

- 前置:openspec/changes/2026-08-27-ocr-paddle-onnx/（OCR 文本来源切换，接口不变故弱依赖）
