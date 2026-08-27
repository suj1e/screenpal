# 2026-08-27-tts-domestic-online 任务清单

- [x] 1. DoubaoTtsEngine（请求组装/合成/播放/stop）+ 单测（JSON 组装、rate/pitch clamp、base64 落盘、error 映射）
  - 验收：mock 网络单测绿
- [x] 2. TtsManager 接线（cloudProviderFactory 返回 TtsEngine?）+ 降级矩阵单测
  - 验收：豆包失败自动 Piper 再 System；既有测试适配全绿
- [ ] 3. 设置三键（AppID/Token/音色）+ UI（文案改「豆包在线语音」+ 下拉）
  - 验收：持久化往返；主界面显示正确
- [ ] 4. 删除 GoogleCloudTtsProvider 及全部引用
  - 验收：grep 无残留；构建通过
- [ ] 5. 模拟器验收（主智能体执行）：在线播报出声 + 飞行模式降级 Piper
  - 验收：两路径 AudioTrack 证据 + 听感
