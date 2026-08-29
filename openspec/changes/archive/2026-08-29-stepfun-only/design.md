# 2026-08-29-stepfun-only 设计

## 现状分析

豆包侧共 4 个类 + VendorRouter + 5 个 DataStore 键 + 设置卡两组，全部服务于"双服务商并列"。实际用户只配了 StepFun（豆包从未拿到真实凭据），VendorRouter 的 DOUBAO 分支是永久死路。

## 方案设计

**图示**：[diagrams/simplified-stack.svg](diagrams/simplified-stack.svg)

1. **删除文件**：`tts/DoubaoTtsEngine.kt`、`translate/DoubaoTranslateClient.kt`、`ocr/CloudOcrProvider.kt`、`vendor/VendorRouter.kt` + 对应 4 个测试文件（DoubaoTtsEngineTest、DoubaoTranslateClientTest、CloudOcrProviderTest、VendorRouterTest、CloudOcrConfigTest）
2. **三个挂点直连**：
   - `ScreenPalApplication`：TTS 工厂 = `stepfunApiKey/stepfunVoice` 非空 → `StepfunTtsEngine(context, key, voice)`，否则 null
   - `SelectionOverlayActivity.resolveOcrEngine`：CLOUD/HYBRID 云侧 = key 非空 → `StepfunOcrProvider(StepfunConfig(key))`，否则 null/端侧
   - 转译管道：key 非空 → `StepfunTranslateClient(key)`，否则 null → 播原文
   - 需要新 `StepfunConfig`（对齐旧 CloudOcrConfig 位置，或直接单参构造）
3. **SettingsRepository**：删 5 键（cloudApiKey/cloudVendor/volcanoSpeechAppId/volcanoSpeechToken/ttsVoice）及其读写映射、MainUiState 字段、MainViewModel setter；DataStore 里遗留的旧键条目不迁移（DataStore 容忍未知键，读侧不再映射即视为删除）
4. **MainActivity**：「在线服务商」卡 → 「StepFun 云服务」（说明行改为"一把 API Key 包办：在线语音播报 · 云 OCR 增强 · AI 转译"；StepFun API Key + 音色两项常显）；TTS/OCR 卡文案固定 StepFun
5. **文案契约测试**更新（TtsSettingsCardCopyTest 等）

## 接口 / 数据契约

- 三个协议接口签名零改动
- DataStore 删 5 键（不迁移——旧值无真实用户数据）

## 实施步骤

1. 删 4 类 + 5 测试文件；三挂点直连改写
2. SettingsRepository/MainViewModel/MainUIState 键清理
3. MainActivity 设置卡重构为「StepFun 云服务」
4. 文案契约测试更新；全量测试绿
5. 模拟器回归：StepFun OCR→转译→TTS 链路 + 设置页视觉

## 性能优化点

无（删代码）。

## 设计模式建议

策略模式三协议保留——扩展点从"运行时路由"退化为"新增实现类 + 三个构造点各一行"，纯粹且足够。

## 风险与 Trade-off

- **风险：将来重新引入豆包**——需恢复实现类（git 历史可找回），可接受
- **风险：模拟器 DataStore 遗留旧键**——读侧不映射即无害
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 三挂点直连构造、缺 Key null/降级、grep 无 Doubao/VendorRouter/volcano 残留、设置键契约更新 | JVM |
| 构建门禁 | assembleDebug + 全量测试绿 | 本地 |
| 模拟器验收 | StepFun 链路回归 + 设置页只有一组凭据 | 截图 |

边界/异常：无（纯删减与直连）。
