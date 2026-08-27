# 2026-08-27-chinese-translation-broadcast 任务清单

- [ ] 1. ChineseHeuristic + 四用例单测
  - 验收：阈值 0.5 判定正确
- [ ] 2. DoubaoTranslateClient（机器翻译大模型请求/解析/错误码，端点凭据=语音线同源）+ 单测
  - 验收：mock 绿；多行译文拼接正确
- [ ] 3. ChineseBroadcastPipeline + 降级矩阵五用例
  - 验收：5s 超时降级原文；开关生效
- [ ] 4. 卡片双语 UI + 设置开关（方舟 Key 复用）
  - 验收：持久化往返；两种卡片呈现正确
- [ ] 5. SelectionOverlayActivity 接入 pipeline
  - 验收：既有测试适配全绿
- [ ] 6. 模拟器验收（主智能体执行）：英文→中文播报+双语卡；中文直读；关开关；无 Key 降级
  - 验收：logcat + 听感 + 截图
