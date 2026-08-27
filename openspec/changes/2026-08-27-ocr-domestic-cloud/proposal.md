# 2026-08-27-ocr-domestic-cloud

## Why

云端 OCR 增强路线去 Google 化：现有 `CloudOcrProvider` 走 Google Cloud Vision。按产品决策替换为百度智能云 OCR（与 PaddleOCR 同源技术，通用文字识别标准版有免费额度），与端侧 PaddleOCR 组成「端侧为主 + 云端增强」混合形态。

## What Changes

- 重写 `CloudOcrProvider`：调百度智能云通用文字识别（高精度版可选），REST + API Key/Secret Key 换 access_token（缓存复用）
- `CloudOcrConfig` 扩展 secretKey 字段；设置界面提示补 Secret Key 输入（主界面现有 cloudApiKey 框复用为 API Key，新增一项）
- 移除 Google Vision 相关代码；Hybrid 决策（置信度阈值 0.75）不变
- 被否选项：腾讯云 OCR（与百度 TTS/OCR 无法同账号，双账号管理成本高）

## 成功标准

- 配置 Key 后 Hybrid 模式下端侧低置信度自动走百度云并返回中文结果
- 无 Key 时静默降级端侧（现状行为保持）
- 单测全绿（鉴权参数组装/响应解析/降级）

## 优先级

- P2：增强路径，端侧可用时非必需。

## 依赖

- 前置:openspec/changes/2026-08-27-ocr-paddle-onnx/（Hybrid 的 LOCAL 侧替换为 Paddle 后联调语义才完整）
