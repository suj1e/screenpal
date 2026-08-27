# 2026-08-27-chinese-translation-broadcast 设计

## 方案设计

**图示**：[diagrams/translation-broadcast-flow.svg](diagrams/translation-broadcast-flow.svg)

1. **语言启发式**：`ChineseHeuristic.isMostlyChinese(text)` = CJK 表意字符 / 非空白字符 ≥ 0.5；纯数字/符号走中文路径。
2. **DoubaoTranslateClient**：POST 方舟 `chat/completions`，`Authorization: Bearer {ARK_API_KEY}`（与云 OCR 共用设置键）；system="你是翻译引擎。将用户内容翻译为简体中文，只输出译文，不解释。"，temperature=0，model 常量（doubao-pro/lite，**待确认**型号）；q 截断 6000 字节。
3. **ChineseBroadcastPipeline**：`broadcast(ocrText, tts)` → 开关关/中文 → Direct；否则 5s 超时翻译，成功 `tts.speak(译文)`→Translated，失败 `tts.speak(原文)`→FallbackOriginal。
4. **卡片**：主显译文（翻译发生时）/原文；meta 追加原文小字或「翻译不可用」。
5. **设置**：`translationEnabled`（默认 true）+ 复用 `cloudApiKey`（方舟）。

## 接口 / 数据契约

- `interface TranslateService { suspend fun translate(text: String): String }`
- `BroadcastOutcome { Translated, Direct, FallbackOriginal }`

## 实施步骤

1. ChineseHeuristic + 四用例单测
2. DoubaoTranslateClient + 请求/解析/错误码单测（mock）
3. ChineseBroadcastPipeline + 降级矩阵五用例
4. 卡片双语 UI + 设置开关
5. SelectionOverlayActivity 接入
6. 模拟器验收四路径

## 性能优化点

中文路径零网络；大模型翻译短文本 RTT 约 1–2s，5s 超时上限；q 截断防超长。

## 设计模式建议

管道模式；翻译策略接口化（未来可换 DeepL 等）。

## 风险与 Trade-off

- **风险：大模型翻译"再创作"**——system 强约束 + temperature 0；关键场景开关逃生
- **风险：按 token 计费**——低频（手动框选）+ 短文本，成本可控；**待确认**方舟定价
- **风险：延迟 1–2s 感知**——卡片先出 OCR 文本，翻译完成后更新主显（异步不阻塞卡片）
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 启发式四用例、请求组装（system/温度/截断）、解析、降级矩阵五用例 | JVM + MockK + Ktor mock |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | 英文→中文播报+双语卡；中文直读；关开关；无 Key 降级 | logcat + 听感 + 截图 |

边界/异常：空文本直返；译文空视为失败；超长截断。
