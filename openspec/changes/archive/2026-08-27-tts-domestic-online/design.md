# 2026-08-27-tts-domestic-online 设计

## 方案设计

**图示**：[diagrams/tts-fallback-chain.svg](diagrams/tts-fallback-chain.svg)

1. **`DoubaoTtsEngine : TtsEngine`**：POST `https://openspeech.bytedance.com/api/v1/tts`，JSON 体 `{app:{appid,token,cluster}, user:{uid}, audio:{voice_type, encoding:"mp3", speed_ratio, pitch_ratio}, request:{reqid, text, operation:"query"}}`，鉴权头 `Authorization: Bearer;{token}`（头格式**待确认**以火山文档为准）→ 响应 base64 data → MP3 落 cacheDir → MediaPlayer 播放。rate/pitch 映射 speed_ratio/pitch_ratio（0.2–3.0 浮点，clamp 到官方区间）。
2. **TtsManager 接线**：`cloudProviderFactory` 返回 `TtsEngine?`（接口统一后豆包引擎即 TtsEngine）；降级链代码不变；`TtsEngineType.CLOUD` 显示名改「豆包在线语音」。
3. **设置**：`volcanoSpeechAppId`、`volcanoSpeechToken`、`ttsVoice: String`（DataStore 三键）；主界面 TTS 卡新增 AppID/Token 输入与音色下拉。
4. **删除**：`GoogleCloudTtsProvider.kt` 及全部引用。

## 接口 / 数据契约

- `TtsEngine` 接口不变；`cloudProviderFactory: suspend () -> TtsEngine?`
- 新增 DataStore 键：`volcanoSpeechAppId` / `volcanoSpeechToken` / `ttsVoice: String`

## 实施步骤

1. DoubaoTtsEngine + 单测（请求 JSON 组装、rate/pitch 映射 clamp、base64→MP3 落盘、error 响应映射 TtsException；Ktor mock engine）
2. TtsManager 类型调整 + 降级矩阵单测（豆包失败→Piper→System）
3. 设置三键 + UI（文案/输入框/音色下拉）
4. 删除 GoogleCloudTtsProvider
5. 模拟器验收：在线播报出声 + 飞行模式降级 Piper 出声

## 性能优化点

合成 RTT 火山官方标称流式毫秒级、query 模式约 1s；MP3 直接播放。

## 设计模式建议

沿用 `TtsEngine` 策略模式与 TtsManager 降级链，仅换实现；音色为配置项。

## 风险与 Trade-off

- **风险：火山免费额度/计费**（**待确认**现行政策）——配额错误映射降级 Piper
- **风险：API 头格式/集群名（cluster）细节**以火山文档为准（**待确认**），实现处集中为常量便于校正
- **开放问题**：用户需在火山引擎控制台开通语音技术并创建应用

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 请求 JSON 组装/映射/头、base64→MP3、降级矩阵、Google 引擎零引用 | JVM + MockK + Ktor mock |
| 构建门禁 | assembleDebug；grep 无 GoogleCloudTtsProvider | 本地构建 |
| 模拟器验收 | 在线播报出声；飞行模式降级 Piper；设置文案/音色 | 截图 + logcat + 听感 |

边界/异常：空文本不请求；error_code 响应映射 TtsException；MediaPlayer error 回调走降级。
