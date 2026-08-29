# 2026-08-29-broadcast-mode 设计

## 方案设计

**图示**：[diagrams/broadcast-modes.svg](diagrams/broadcast-modes.svg)

1. **BroadcastMode**（`translate/BroadcastMode.kt`）：`TRANSLATE` / `EXPLAIN`；`fromStorageValue` 脏值/空/null 一律回退 TRANSLATE（与 SelectionMode 同款防御）。
2. **设置**：DataStore `broadcastMode`（String 默认 "TRANSLATE"）；「播报模式」卡两单选（翻译朗读——外文转中文原样朗读 / AI 讲解——问 AI 这是什么），插在「中文播报」卡之后；MainUiState/MainViewModel 全链路（merge-on-persisted 写法，勿回退到 stale-state）。
3. **TranslateService 扩展**：接口加 `suspend fun explain(text: String): String`；StepfunTranslateClient 实现——同端点同 Key，`EXPLAIN_SYSTEM_PROMPT = "用户在屏幕上圈选了这段内容。请用简体中文口语化解释：这是什么、有什么用。不超过80字，适合语音朗读，不要逐字翻译，不要任何前缀说明。"`（temperature=0、max_tokens 4096、6000 字节截断复用现有实现），解析同 translate。Mock 测试同步。
4. **ChineseBroadcastPipeline**：`broadcast(text, tts, translationEnabled, mode)`：
   - TRANSLATE 分支 = 现状逐字保留（启发式直读/翻译/降级）
   - EXPLAIN 分支：`withTimeout(TRANSLATE_TIMEOUT_MS)` 调 explain → 成功 speak(讲解)、`lastSpokenText=讲解`、返回 `EXPLAINED`；TimeoutCancellationException/任何失败 → speak(原文)、返回 `FallbackOriginal`；CancellationException 重抛；空文本直返 Direct
   - Outcome 枚举加 `EXPLAINED`
5. **卡片**：`metaAnnotation` 加 `EXPLAINED -> " · AI 讲解"`；讲解≠原文时主显讲解 + 原文小字（复用现有双语卡逻辑，条件从 `spoken != result.text` 天然覆盖）
6. **挂点**：SelectionOverlayActivity 读 `settings.broadcastMode` 传入 pipeline。

## 接口 / 数据契约

- `TranslateService` + `explain`；`BroadcastOutcome` + `EXPLAINED`；DataStore + `broadcastMode`
- EXPLAIN 模式不受 `translationEnabled` 开关限制（用户显式选择讲解即为意图）；该开关仅作用于 TRANSLATE 模式——design 语义澄清写入卡片 description

## 实施步骤

1. BroadcastMode + DataStore + 设置卡 + 默认值/持久化单测
2. TranslateService.explain + Stepfun 实现 + prompt/解析/错误单测
3. Pipeline EXPLAIN 分支 + 矩阵单测（EXPLAIN 成功/失败降级/中文文本也讲解/TRANSLATE 零回归）
4. 卡片标注 + 挂点接线 + 契约单测
5. 模拟器验收（主智能体执行）：EXPLAIN 圈中文与英文各一次 + TRANSLATE 回归

## 性能优化点

EXPLAIN 单次 AI 调用（外文隐含翻译，不二跳）；超时复用 15s（推理模型已验证）。

## 设计模式建议

模式分支在管道内单点分发；prompt 即策略——将来加"详细讲解/操作建议"只是新 prompt 常量。

## 风险与 Trade-off

- **风险：讲解质量/幻觉**——system prompt 强约束 + ≤80 字；讲解错误直接入耳，用户可切回翻译朗读
- **风险：中文内容也走网络**（EXPLAIN 模式）——用户显式选择的模式，预期内
- **风险：推理模型偶发超时** → 15s + 降级播原文
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | BroadcastMode 脏值回退；持久化往返；explain 请求/prompt 逐字/解析；管道矩阵（EXPLAIN 成功/失败/超时/中文文本/空文本；TRANSLATE 现状零回归断言）；卡片标注三态 | JVM + MockK + Ktor mock |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器验收 | EXPLAIN 圈中文/英文各一次（真 Key）+ TRANSLATE 回归 | 听感 + 截图 |

边界/异常：空文本直返；讲解空视为失败；截断 6000 字节。
