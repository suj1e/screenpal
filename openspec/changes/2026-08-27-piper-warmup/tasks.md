# 2026-08-27-piper-warmup 任务清单

- [x] 1. 接线测试先行：`ScreenPalApplicationTest` 新增 Robolectric 测试（子类覆写 `warmUpTts` 断言 onCreate 调用）
  - 验收：当前代码无 warmUpTts 时测试红（编译失败或断言失败）
- [x] 2. 实现 `ScreenPalApplication.warmUpTts()`（internal open）+ `appScope`（SupervisorJob+IO）+ onCreate 调用 `appScope.launch { ttsManager.initialize() }`
  - 验收：接线测试绿；不阻塞主线程；initialize 失败不崩溃
- [ ] 3. `TtsManagerTest` 补 initialize 行为契约（幂等；Piper 初始化抛异常时不向外抛）
  - 验收：`gradle testDebugUnitTest` 全绿（含既有 41 项）
- [ ] 4. 模拟器验收（主智能体执行）：冷启动后私有目录出现 >15MB `zh_CN-huayan-medium.onnx`；悬浮球识别播报 logcat 无 `Piper engine not initialized`、不再降级到 System
  - 验收：logcat 证据 + 模型文件确认
