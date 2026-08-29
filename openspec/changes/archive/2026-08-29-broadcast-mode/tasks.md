# 2026-08-29-broadcast-mode 任务清单

- [x] 1. BroadcastMode 枚举（脏值回退 TRANSLATE）+ DataStore broadcastMode 键 + 「播报模式」设置卡 + 默认值/持久化单测
  - 验收：默认 TRANSLATE；脏值回退；往返持久化单测绿
- [x] 2. TranslateService.explain + StepfunTranslateClient 实现（EXPLAIN prompt/max_tokens 4096/截断/解析）+ 单测
  - 验收：prompt 逐字契约；解析与错误映射绿
- [x] 3. ChineseBroadcastPipeline EXPLAIN 分支 + 矩阵单测（EXPLAIN 成功/失败/超时/中文文本/空文本 + TRANSLATE 现状零回归）
  - 验收：矩阵单测绿；Outcome.EXPLAINED 语义正确
- [x] 4. 卡片标注（AI 讲解/讲解不可用）+ 双语主显 + 挂点接线（mode 入参）+ 契约单测
  - 验收：metaAnnotation 三态正确；接线单测绿；全量测试零回归
- [x] 5. 模拟器验收：EXPLAIN 圈 "Google" → AI 讲解播报（「这是谷歌，是全球最大的搜索引擎…」，AudioTrack 279936 帧）+ 卡片「AI 讲解 · 原文」标注；TRANSLATE 回归（「谷歌 · AI 转译」）；模式切换持久化验证（TRANSLATE↔EXPLAIN）
