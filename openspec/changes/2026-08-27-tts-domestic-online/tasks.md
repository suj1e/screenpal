# 2026-08-27-tts-domestic-online 任务清单

- [ ] 1. BaiduTtsEngine（合成+播放+stop）+ 单测（参数映射/落盘/失败映射）
  - 验收：mock 网络下单测绿；rate/pitch→spd/pit 映射正确
- [ ] 2. TtsManager 接线（cloudProviderFactory 返回 TtsEngine?）+ 降级矩阵单测
  - 验收：百度失败自动 Piper 再 System；既有 TtsManager 测试适配全绿
- [ ] 3. 设置扩展：引擎文案改「百度在线语音」+ 发音人下拉（ttsVoice 持久化）
  - 验收：设置往返持久化；主界面显示正确
- [ ] 4. 删除 GoogleCloudTtsProvider 及全部引用
  - 验收：grep 无残留；构建通过
- [ ] 5. 模拟器验收（主智能体执行）：在线播报出声 + 飞行模式降级 Piper 出声
  - 验收：两种路径各一次 AudioTrack 证据 + 听感
