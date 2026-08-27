# 2026-08-27-tts-domestic-online 设计

## 方案设计

**图示**：[diagrams/tts-fallback-chain.svg](diagrams/tts-fallback-chain.svg)

1. **`BaiduTtsEngine : TtsEngine`**：`initialize` 轻量（校验 Key 存在，BaiduAuthClient 就绪）；`speak(text, rate, pitch)`：POST `rest/2.0/tts/v1`（tex=URL 编码文本, per=发音人, spd/pit 由 rate/pitch 映射 0-15, aue=3 mp3）→ MP3 写 cacheDir → MediaPlayer 播放（复用 Piper 的播放封装思路）；`stop/shutdown` 同 Piper 语义。
2. **TtsManager 接线**：构造参数 `cloudProviderFactory` 返回类型改为 `TtsEngine?`（百度引擎也是 TtsEngine，接口已统一）；降级链代码不变（CLOUD 分支语义即百度）。`TtsEngineType.CLOUD` 显示名改「百度在线语音」。
3. **发音人配置**：`UserSettings.ttsVoice`（DataStore，默认 `0` 度小雯女声；枚举：0/1/3/4/4115 等百度公开 per 值），设置界面下拉。**待确认**：per 合法值以百度云 TTS API 当前文档为准。
4. **删除**：`GoogleCloudTtsProvider.kt`、其 import 与设置文案。

## 接口 / 数据契约

- `TtsEngine` 接口不变；`cloudProviderFactory: suspend () -> TtsEngine?`
- 新增 `UserSettings.ttsVoice: Int`、`cloudApiSecret`（与 ocr-domestic-cloud 共用键）

## 实施步骤

1. BaiduTtsEngine + 单测（请求参数映射 rate/pitch→spd/pit、MP3 落盘路径、stop 语义；网络 mock）
2. TtsManager 类型调整 + 降级矩阵单测（百度成功/失败→Piper→System 三段）
3. 设置 UI（引擎文案 + 发音人下拉）+ DataStore 键
4. 删除 GoogleCloudTtsProvider
5. 模拟器验收：百度在线播报出声 + 断网降级 Piper 出声

## 性能优化点

短文本合成 RTT 百度官方 <1s（局域网外视网络）；MP3 直接播不复解码为 PCM。

## 设计模式建议

沿用 `TtsEngine` 策略模式与 TtsManager 降级链（模板不变，仅换实现）；发音人选择为纯配置项。

## 风险与 Trade-off

- **风险：免费额度**（百度 TTS 个人免费额度有限，**待确认**现行政策）——超配额异常映射为降级 Piper，主流程不断
- **风险：长文本截断**（百度短文本接口 ≤60K 字节 UTF-8）——框选文本实际远小于此，V1 不做分段
- **开放问题**：用户需在百度智能云创建 TTS 应用（与 OCR 同账号）

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 请求参数映射、降级矩阵（百度失败→Piper 兜底→System 兜底）、Google 引擎删除后无引用 | JVM + MockK |
| 构建门禁 | assembleDebug；全仓 grep 无 GoogleCloudTtsProvider | 本地构建 |
| 模拟器验收 | 在线播报出声；飞行模式降级 Piper 出声；设置项文案 | 截图 + logcat + 听感 |

边界/异常：空文本不请求；合成失败 MediaPlayer error 回调映射 TtsException 走降级。
