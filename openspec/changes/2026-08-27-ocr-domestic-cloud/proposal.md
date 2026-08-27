# 2026-08-27-ocr-domestic-cloud

## Why

云端 OCR 增强去 Google 化，且随用户选型由百度改为**豆包视觉大模型（火山方舟 Doubao Vision）**：框选是低频操作，云侧只在端侧置信度不足时触发，用视觉大模型换更高精度完全成立；且方舟 API Key 与翻译共用（同 Key 两用），鉴权为简单 Bearer，比传统 OCR 服务的签名/token 机制更简。

## What Changes

- 重写 `CloudOcrProvider`：调火山方舟 `chat/completions`（doubao-vision 模型，image 为 base64 data URL，prompt 要求按阅读顺序输出全部文字）
- `CloudOcrConfig` 收敛为 `arkApiKey` + 模型 id（常量，默认 doubao-vision-lite 级别以控成本，**待确认**可选型号）
- `OcrResult.confidence` 置 0.99（云结果视为可信，Hybrid 语义不变）
- 设置键 `cloudApiKey` 语义改为「火山方舟 API Key」（仅视觉 OCR；翻译已随语音线凭据走）
- 移除 Google Vision 与（先前方案中的）百度 OCR 设定；Hybrid 决策阈值 0.75 不变
- 被否选项：火山传统 OCR API（鉴权签名复杂、能力同质）、百度 OCR（用户否）

## 成功标准

- 配置方舟 Key 后 Hybrid 模式端侧低置信度自动走豆包视觉并返回正确中文文本
- 无 Key 静默降级端侧（现状行为保持）；单测全绿（请求组装/解析/降级）

## 优先级

- P2：增强路径。

## 依赖

- 前置:openspec/changes/2026-08-27-ocr-paddle-onnx/（Hybrid LOCAL 侧为 Paddle 后联调语义完整）
