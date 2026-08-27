# 2026-08-27-chinese-translation-broadcast 设计

## 方案设计

**图示**：[diagrams/translation-broadcast-flow.svg](diagrams/translation-broadcast-flow.svg)

1. **语言启发式**：`ChineseHeuristic.isMostlyChinese(text)` = `CJK 统一表意文字（\u4E00-\u9FFF）字符数 / 非空白字符数 ≥ 0.5`。纯数字/符号视为中文路径（无需翻译，数字 TTS 可读）。
2. **BaiduTranslateClient**：GET `api.fanyi.baidu.com/api/trans/vip/translate`（q, from=auto, to=zh, appid, salt, sign=MD5(appid+q+salt+key)）；响应 `trans_result[].dst` 拼接。**待确认**：标准版免费额度与 QPS=1 限流以开放平台现行政策为准；超限/错误码（54003 等）映射 `TranslationException`。
3. **ChineseBroadcastPipeline**：
   ```
   suspend fun broadcast(ocrText, tts): BroadcastOutcome =
     if (!enabled || isMostlyChinese(ocrText)) tts.speak(ocrText); Direct
     else runCatching { withTimeout(3s) { translate(ocrText) } }
       .onSuccess { tts.speak(it); Translated }
       .onFailure { tts.speak(ocrText); FallbackOriginal }
   ```
4. **卡片**：主 TextView 显译文（翻译发生时）或原文；`resultMeta` 追加原文（≤2 行省略）或「翻译不可用」标注。
5. **设置**：`translationEnabled: Boolean`（默认 true）、`translateAppId`、`translateSecret`（DataStore 三键 + 主界面「中文播报」卡片：开关 + 两个输入框）。

## 接口 / 数据契约

- `interface TranslateService { suspend fun translate(text: String): String }`
- `BroadcastOutcome { Translated, Direct, FallbackOriginal }` 供 UI 标注与测试断言

## 实施步骤

1. ChineseHeuristic + 单测（纯中/纯英/混合/数字符号四用例）
2. BaiduTranslateClient（签名/解析/错误码）+ 单测（mock engine）
3. ChineseBroadcastPipeline + 降级矩阵单测（开/关/有Key/无Key/超时）
4. 卡片双语 UI + 设置三键 + 输入项
5. 模拟器验收：英文页面框选（听中文 + 看双语卡片）、中文页面直读、关开关回归

## 性能优化点

中文路径零网络；翻译 3s 超时上限保证播报不悬置；q 自动截断至 6000 字节（百度单次上限）防超长框选。

## 设计模式建议

管道模式（pipeline 串联 OCR→翻译→TTS），各段独立可测；翻译服务策略接口化，未来可换 DeepL/腾讯。

## 风险与 Trade-off

- **风险：机器翻译准确度**——通用领域基准可接受；错误翻译直接入耳，开关提供逃生门
- **风险：QPS=1 限流**（标准版）——单用户手动框选节奏远低于限流；连续重框选场景失败降级原文
- **风险：双语账号（智能云 + 翻译平台）**——设置项分组标注清晰；**待确认**翻译平台账号开通流程
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 启发式四用例、签名 MD5 正确性、响应解析（多段 trans_result）、降级矩阵（开关×Key×网络 5 用例） | JVM + MockK |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | 英文→中文播报+双语卡片；中文直读（logcat 无翻译请求）；关开关直读；无 Key 降级 | 截图 + logcat + 听感 |

边界/异常：空 OCR 文本直接返回；翻译结果为空视为失败降级；超长文本截断。
