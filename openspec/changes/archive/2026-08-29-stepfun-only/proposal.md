# 2026-08-29-stepfun-only

## Why

产品决策收敛：在线服务商**只保留 StepFun**，把云能力配置做得简单纯粹。豆包（火山引擎）三件套与双服务商路由层是按早期方案建的，实际验收中 StepFun 单 Key 体验更好（一把 Key 管三件事），豆包侧从未配置过真实凭据——删除它们消除一半的设置面与维护面。

## What Changes

- **删除**：DoubaoTtsEngine、DoubaoTranslateClient、CloudOcrProvider（豆包视觉）、VendorRouter、火山语音三键（volcanoSpeechAppId/Token/ttsVoice）、方舟键（cloudApiKey）、cloudVendor 键及服务商选择 UI
- **保留**：三个协议接口（TtsEngine / OcrEngine / TranslateService）——抽象层不动，将来加第二家只需新实现类
- **直连**：三个挂点（Application TTS 工厂 / resolveOcrEngine 云侧 / 转译管道）直接构造 StepfunTtsEngine / StepfunOcrProvider / StepfunTranslateClient（凭据 = stepfunApiKey/stepfunVoice）
- 设置 UI：「在线服务商」卡 → 「StepFun 云服务」卡（API Key + 音色两项）；TTS/OCR 卡文案固定为 StepFun（不再动态切换）
- 被否选项：保留 VendorRouter 空壳（用户拍板要纯粹；协议层已是扩展点）

## 成功标准

- 全仓 grep 无 Doubao/VendorRouter/volcano 残留；构建 + 全部单测绿
- 设置页只有 StepFun 一组凭据；模拟器识别链路（StepFun OCR→转译→TTS）回归通过
- 降级语义不变：缺 Key/失败 → 端侧 Paddle / Piper / 系统

## 优先级

- P1：其他三个 change 的设置面都受它影响，先做避免二次返工。
