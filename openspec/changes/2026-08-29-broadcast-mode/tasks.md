# 2026-08-29-broadcast-mode 任务清单

- [ ] 1. BroadcastMode 枚举（脏值回退 TRANSLATE）+ DataStore broadcastMode 键 + 「播报模式」设置卡 + 默认值/持久化单测
  - 验收：默认 TRANSLATE；脏值回退；往返持久化单测绿
- [ ] 2. TranslateService.explain + StepfunTranslateClient 实现（EXPLAIN prompt/max_tokens 4096/截断/解析）+ 单测
  - 验收：prompt 逐字契约；解析与错误映射绿
- [ ] 3. ChineseBroadcastPipeline EXPLAIN 分支 + 矩阵单测（EXPLAIN 成功/失败/超时/中文文本/空文本 + TRANSLATE 现状零回归）
  - 验收：矩阵单测绿；Outcome.EXPLAINED 语义正确
- [ ] 4. 卡片标注（AI 讲解/讲解不可用）+ 双语主显 + 挂点接线（mode 入参）+ 契约单测
  - 验收：metaAnnotation 三态正确；接线单测绿；全量测试零回归
- [ ] 5. 模拟器验收（主智能体执行）：EXPLAIN 圈中文/英文各一次 + TRANSLATE 回归
  - 验收：听感 + 截图
