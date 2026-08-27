# 2026-08-27-ocr-domestic-cloud 设计

## 方案设计

**图示**：[diagrams/hybrid-decision.svg](diagrams/hybrid-decision.svg)

1. **鉴权**：`BaiduAuthClient` 用 API Key + Secret Key 调 `oauth/2.0/token`（grant_type=client_credentials）取 access_token，内存缓存 + 过期前 60s 刷新（token 有效期 30 天，进程内缓存即可）。
2. **识别调用**：POST `https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic`（高精度版；标准版 `general_basic` 作为省流备选，配置常量切换）。请求 image=base64(JPEG 85)；响应 `words_result[].words` 拼接文本。百度 OCR 不回整体置信度——`OcrResult.confidence` 置 0.99（云结果视为可信，Hybrid 语义为"端侧不行就信云"）；`TextBlock.confidence` 同理，boundingBox 由 `location` 映射。
3. **复用与清理**：Ktor Client 复用（Piper 下载同款）；删 Google Vision 版 `CloudOcrProvider` 实现，类名保留（职责已是百度）。
4. **设置**：`UserSettings.cloudApiKey` 复用为百度 API Key，新增 `cloudApiSecret`（DataStore 加键，默认空）；主界面 OCR 设置卡新增 Secret 输入框。

## 接口 / 数据契约

- `CloudOcrConfig(apiKey, secretKey)`（扩字段）
- `OcrEngine` 接口不变；错误（401/配额）抛 `IllegalStateException` 由 Hybrid/上层按现状降级端侧

## 实施步骤

1. BaiduAuthClient + 单测（参数组装/token 缓存，Ktor engine mock）
2. CloudOcrProvider 重写 + 响应解析单测（样例 JSON）
3. 设置扩展 cloudApiSecret + UI 输入框
4. 移除 Google Vision 残留（imports/文案）
5. 模拟器验收：配置真实 Key 走 Hybrid 全链路（无 Key 降级路径回归）

## 性能优化点

token 进程内缓存避免每次鉴权；image 复用上层已压缩的 JPEG。

## 设计模式建议

沿用策略模式与既有 Ktor 栈；鉴权客户端独立小对象，不引入 SDK。

## 风险与 Trade-off

- **风险：免费额度限制**（以百度智能云现行政策为准，**待确认**具体额度）——配额错误已映射为降级端侧，不阻塞主流程
- **风险：accuracy 版本计费高于标准版**——默认标准版，高精度版留配置常量
- **开放问题**：用户需自行注册百度智能云并创建 OCR 应用拿 Key

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | token 请求参数/缓存、响应 JSON 解析（words_result/location→TextBlock）、无 secret 时的明确错误 | JVM + Ktor mock engine |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | 有 Key：Hybrid 触发云路径（logcat）；无 Key：降级端侧回归 | 截图 + logcat |

边界/异常：token 过期自动重取一次；响应含 error_code 时抛出明确异常文案。
