# 2026-08-27-piper-warmup 任务清单

- [x] 1. 接线测试先行：`ScreenPalApplicationTest` 新增 Robolectric 测试（子类覆写 `warmUpTts` 断言 onCreate 调用）
  - 验收：当前代码无 warmUpTts 时测试红（编译失败或断言失败）
- [x] 2. 实现 `ScreenPalApplication.warmUpTts()`（internal open）+ `appScope`（SupervisorJob+IO）+ onCreate 调用 `appScope.launch { ttsManager.initialize() }`
  - 验收：接线测试绿；不阻塞主线程；initialize 失败不崩溃
- [x] 2b. 验收发现的同链路缺陷修复（scope 扩展，见 design「验收发现」）：① ModelDownloader.configUrl 远端名 404（改为 `.onnx.json`，本地名不变）+ 契约测试；② PiperTtsEngine.synthesize 输出张量维度强转崩溃（改为扁平 FloatBuffer 读取，兼容任意维度）
  - 验收：单测全绿；模拟器识别播报走 Piper 路径、结果卡片不再显示「播报不可用」
- [x] 3. `TtsManagerTest` 补 initialize 行为契约（幂等；Piper 初始化抛异常时不向外抛）
  - 验收：`gradle testDebugUnitTest` 全绿（含既有 41 项）
- [x] 4. 模拟器验收：模型落地（files/models/ 下 onnx 63MB + json）+ 识别播报 logcat 无降级 + AudioTrack 实际出声
  - 验收记录（21:37）：`AudioTrack: stop(16): called with 7680 frames delivered`（Piper 合成音频真实播放）；结果卡片不再显示「播报不可用」；模型下载受宿主网络（huggingface 被 DNS 污染）阻塞时由 hf-mirror 旁路装填，代码下载路径经 configUrl 修复后即恢复正常
