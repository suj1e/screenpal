# 2026-08-27-stepfun-vendor

## Why

产品决策：在线引擎**并列可选**而非单一供应商——用户在设置里选「豆包」或「StepFun」，选定谁用谁（失败仍落 Piper → 系统兜底）。StepFun（阶跃星辰）接入形态最友好：OpenAI 兼容协议 + 单一 API Key 管全部三件事——TTS（step-tts-2.5，Step-Audio 2.5 旗舰语音生成，中英双语、音色丰富、支持自然语言风格控制）、视觉（step-flash-3.7）、文本转译。

## What Changes

- 新增 `StepfunVendor` 实现（豆包侧三件套的等价物）：
  - `StepfunTtsEngine : TtsEngine`（POST `https://api.stepfun.com/v1/audio/speech`，model=step-tts-2.5，voice 音色，response_format=mp3 → MediaPlayer）
  - `StepfunOcrProvider`（chat/completions + image base64，按阅读顺序输出文字）
  - `StepfunTranslateClient`（step 文本模型 AI 转译，system 约束同豆包版）
- 引入**服务商选择器**：`UserSettings.cloudVendor: String`（DOUBAO / STEPFUN，默认 DOUBAO）；`TtsEngineType` 扩展 STEPFUN；设置界面「在线服务商」单选 + 凭据区随所选切换（豆包=方舟 Key+语音 AppID/Token；StepFun=一个 API Key+音色）
- 降级语义：选定服务商失败 → Piper → 系统（Piper 退为兜底而非链中间位）
- 被否选项：把 StepFun 做成豆包的降级链下一级（用户明确：并列可选，非降级）

## 成功标准

- 设置切到 StepFun：配置其 API Key 后，TTS/云 OCR 增强/翻译三路径全走 StepFun 且出声/出结果
- 切回豆包：路径全走豆包（回归）
- 选定服务商失败 → 自动 Piper 兜底；路由矩阵单测覆盖
- 既有全部测试无回归

## 优先级

- P2：并列引擎扩充，依赖豆包侧三 change 先落地（接口形态稳定）。

## 依赖

- 前置:openspec/changes/2026-08-27-tts-domestic-online/（TtsManager 引擎路由与 TtsEngineType 扩展点）
- 前置:openspec/changes/2026-08-27-ocr-domestic-cloud/（云 OCR vendor 路由）
- 前置:openspec/changes/2026-08-27-chinese-translation-broadcast/（TranslateService 路由）
