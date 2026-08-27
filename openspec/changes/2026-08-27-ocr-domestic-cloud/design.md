# 2026-08-27-ocr-domestic-cloud 设计

## 方案设计

**图示**：[diagrams/hybrid-decision.svg](diagrams/hybrid-decision.svg)

1. **调用**：POST `https://ark.cn-beijing.volces.com/api/v3/chat/completions`，`Authorization: Bearer {ARK_API_KEY}`；messages=[system:"你是 OCR 引擎，只输出图片中的文字，按阅读顺序，不要任何解释", user: image_url(base64 data URL JPEG)]；`model` 常量默认 `doubao-seed-1-6-vision` 级别（**待确认**当前可用型号与定价）。
2. **解析**：`choices[0].message.content` 按行切分为 `TextBlock`（无坐标——视觉大模型不回精确框；`boundingBox` 置空 Rect，UI 不消费坐标）、`confidence=0.99`。
3. **成本控制**：JPEG 复用上层压缩（85）；max_tokens 上限；模型 id 常量可换 lite 级。
4. **清理**：删除百度/Google 两代旧实现残留；`cloudApiKey` 设置标签改「火山方舟 API Key（仅视觉 OCR 增强）」。

## 接口 / 数据契约

- `CloudOcrConfig(arkApiKey)`（收敛为单字段）；`OcrEngine` 接口不变
- 错误（401/429/超限）抛 `IllegalStateException`，由 Hybrid/上层降级端侧

## 实施步骤

1. CloudOcrProvider 重写（方舟 vision）+ 请求/解析单测（mock engine）
2. 设置标签与 CloudOcrConfig 收敛
3. Google/百度残留清理
4. 模拟器验收：有 Key 云路径触发（logcat）；无 Key 降级回归

## 性能优化点

低频触发 + 单请求往返；无 token 换取环节（较百度方案少一跳）。

## 设计模式建议

沿用 `OcrEngine` 策略模式；REST 薄客户端。

## 风险与 Trade-off

- **风险：大模型幻觉**（多输出/改写文字）→ prompt 强约束"只输出图中文字"；system+低温度
- **风险：按 token 计费成本**→ 低频触发 + lite 级模型可选（**待确认**型号定价）
- **风险：延迟高于传统 OCR**（2–4s）→ 仅低置信度触发，可接受
- **开放问题**：用户需开通火山方舟并创建 API Key

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 请求体组装（base64 data URL/模型/prompt）、响应解析（多行切分/confidence）、错误映射 | JVM + Ktor mock |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | 有 Key 云路径 + 无 Key 降级 | logcat 两路径 |

边界/异常：content 为空视为失败降级；429/401 明确异常文案。
