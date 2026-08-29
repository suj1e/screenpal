# 2026-08-29-broadcast-mode

## Why

产品从"翻译器"升级为"讲解员"：现有管道只做语言转换（外文→中文直读，中文直读跳过），用户圈选中文菜单项、生僻词、缩写时得不到任何帮助。新增「AI 讲解」播报模式——问 AI"这是什么"，用简体中文口语化解释圈选内容。翻译 change 的 design 已预留此扩展点（"仅改 system prompt"）。用户拍板（默认值按推荐）：默认翻译朗读、设置卡切换、简明讲解 ≤80 字。

## What Changes

- 新增 `BroadcastMode` 枚举（TRANSLATE / EXPLAIN）+ DataStore 键 `broadcastMode`（默认 TRANSLATE，脏值回退 TRANSLATE）+ 「播报模式」设置卡两单选
- `TranslateService` 接口新增 `suspend fun explain(text: String): String`；StepfunTranslateClient 实现（EXPLAIN system prompt：口语化解释是什么/有什么用，≤80 字适合朗读，不逐字翻译；temperature=0；max_tokens 4096 复用）
- `ChineseBroadcastPipeline.broadcast` 增加 mode 分支：
  - TRANSLATE（现状零改动）：启发式直读 / 翻译 / 降级
  - **EXPLAIN：跳过中文启发式，任何语言都走一次 AI 调用**（外文隐含翻译）→ 朗读讲解 → `EXPLAINED`；失败/超时 → 播原文 → `FallbackOriginal`（降级语义复用）
- 结果卡：EXPLAIN 成功 → 讲解主显 + 原文小字（截断 120）+ 标注「AI 讲解」；失败 → 原文 + 「AI 讲解不可用」
- 「中文播报」开关语义澄清：仅作用于 TRANSLATE 模式；EXPLAIN 是用户显式意图不受其限制
- 被否选项：先读原文再讲解（时长翻倍）；圈外遮罩页快捷切换（保持设置单入口，与框选方式形态一致）

## 成功标准

- EXPLAIN 模式：圈中文"勿扰模式"或英文"Screen Time"都能听到简明中文讲解（真 Key 验收）
- TRANSLATE 模式行为与现状逐字节一致（零回归）
- AI 失败/超时 → 播原文 + 「AI 讲解不可用」标注，不崩溃
- 单测全绿（枚举/持久化/管道分支/prompt 契约）

## 优先级

- P1：产品升级核心，架构成本极低（扩展点已预留）。

## 依赖

- 前置:openspec/changes/2026-08-29-stepfun-only/（StepfunTranslateClient 与挂点直连，已 merge）
