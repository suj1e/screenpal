# 2026-08-27-stepfun-vendor 设计

## 方案设计

**图示**：[diagrams/vendor-routing.svg](diagrams/vendor-routing.svg)

1. **凭据**：StepFun 仅一个 API Key（`stepfunApiKey`，OpenAI 兼容 Bearer）。音色 `stepfunVoice: String`（默认 wenying 女声；可选 xiaochen 男声等）。
2. **三实现**（全部 OpenAI 兼容协议，Ktor 复用）：
   - TTS：`/v1/audio/speech`（model=step-tts-2.5, voice, input, response_format=mp3）→ 二进制落盘 → MediaPlayer；语速经 input 自然语言控制指令或 V1 透传
   - 视觉 OCR：`/v1/chat/completions`（model=step-flash-3.7，image base64 data URL，system 同豆包 OCR prompt）
   - 转译：`/v1/chat/completions`（system 同豆包转译 prompt，temperature=0）
3. **路由层**：`VendorRouter` 按 `cloudVendor` 返回对应 TtsEngine 工厂/OcrEngine 工厂/TranslateService；TtsManager 在线分支经 router 取选定引擎。
4. **设置**：「在线服务商」RadioGroup（豆包/StepFun）；凭据卡片随选择切换；缺凭据 → 该 vendor 工厂返回 null → Piper 兜底。

## 接口 / 数据契约

- 新 DataStore 键：`cloudVendor`、`stepfunApiKey`、`stepfunVoice`
- 三个既有接口（TtsEngine/OcrEngine/TranslateService）不变——vendor 是工厂层扩展

## 实施步骤

1. StepfunTtsEngine + 单测
2. StepfunOcrProvider + StepfunTranslateClient + 单测
3. VendorRouter + 三处接线 + 路由矩阵单测
4. 设置 UI（服务商单选 + 凭据区切换）+ DataStore
5. 模拟器验收：StepFun 三路径出声/出结果 + 切回豆包回归

## 性能优化点

全部单请求往返；OpenAI 兼容无额外鉴权跳。

## 设计模式建议

抽象工厂（VendorRouter 按 vendor 提供 family of engines）；新增第三家只需新 factory，零改动上层。

## 风险与 Trade-off

- **风险：step-flash-3.7 视觉/转译质量**——验收实测，豆包随时可切回
- **风险：StepFun 免费额度/计费**（**待确认**现行政策）
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 三客户端请求/解析/错误映射；VendorRouter 路由矩阵；凭据缺失降级 | JVM + MockK + Ktor mock |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | StepFun 三路径 + 切回豆包回归 + 失败落 Piper | logcat + 听感 + 截图 |

边界/异常：凭据空 → 工厂 null → 兜底；超时映射；空响应视为失败。
