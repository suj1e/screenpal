# 2026-08-27-piper-warmup 设计

## 现状分析

- `TtsManager.initialize()`（TtsManager.kt:38，内部 `runCatching { piperEngine.initialize() }`）是 Piper 模型下载与 ONNX 会话创建的唯一入口，**全仓库零调用方**。
- `PiperTtsEngine.speak()` 在 `session == null` 时直接抛 `TtsException("Piper engine not initialized")`，`TtsManager.speakWithFallback` 捕获后降级——模拟器 logcat 实证每次朗读都走这条路。
- `ModelDownloader` 具备断点续传与 `hasModel()` 幂等检查，模型 URL（HuggingFace zh_CN-huayan-medium，onnx+json）经 curl 验证可达（302/307 → CDN）。
- 模拟器 default 镜像无系统 TTS 引擎（`tts_default_synth` 为 null），System 兜底失败属环境限制，本 change 不处理。

## 方案设计

**图示**：[diagrams/warmup-flow.svg](diagrams/warmup-flow.svg)

1. `ScreenPalApplication` 新增应用级作用域与预热函数：
   ```kotlin
   private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
   override fun onCreate() {
       ...
       warmUpTts()
   }
   internal open fun warmUpTts() {
       appScope.launch { ttsManager.initialize() }
   }
   ```
   - 作用域挂 Application 生命周期（进程存活即存活），**不用 viewModelScope**——模型下载 15MB，ViewModel.onCleared 取消会导致下载反复截断（虽有续传，体验差）。
   - `initialize()` 内部已 runCatching，网络失败仅记日志，不崩溃、不阻塞主线程。
2. **可测性**：`warmUpTts` 声明为 `internal open`，测试用 Robolectric 子类覆写记录调用，验证 `onCreate` 接线。
3. **TtsManager 契约测试**（TtsManagerTest 补充，mockk 注入 piperEngine，模式同现有用例）：
   - `initialize` 幂等：Piper 初始化成功后再次调用不重复初始化
   - Piper 初始化抛异常时 `initialize` 不向外抛（失败安全）
   - 更正（review B1）：TtsManager.initialize 为无条件单次委托，真正的幂等守卫在 PiperTtsEngine 的 `isInitialized` 早退，**该守卫当前无单测覆盖**，由任务 4 模拟器验收兜底（piperEngine.initialize 的 src/main 调用方仅 warmUpTts 一处，每进程单次，双初始化竞态现实不存在）

**取舍记录**：
- 拒绝把预热放 `MainViewModel.init`：viewModelScope 生命周期与下载时长不匹配（见上）。
- 拒绝为接线测试注入 mock TtsManager：Application 的 `ttsManager` 为 `private set`，改造注入面扩大改动；`warmUpTts` 覆写法以最小侵入获得同等验证力。
- 模拟器 System TTS 缺失不做代码补偿（装 TTS 引擎 APK 属环境配置，真机无此问题）。

## 接口 / 数据契约

无对外接口变化。`ScreenPalApplication` 新增 `internal open fun warmUpTts()`（Kotlin internal 对测试源集可见）。

## 实施步骤

1. `ScreenPalApplicationTest` 新增 Robolectric 接线测试（TestApplication 覆写 warmUpTts 记录调用）→ 红当前代码无 warmUpTts，编译失败即红。
2. 实现 `warmUpTts` + `appScope` + onCreate 调用 → 绿。
3. `TtsManagerTest` 补 initialize 幂等/失败安全契约测试 → 绿（行为已满足，属回归保护）。
4. `gradle testDebugUnitTest` 全绿 + `assembleDebug`。
5. 模拟器验收（主智能体执行）：冷启动 → 私有目录出现 >15MB .onnx → 悬浮球识别 → logcat 无 `Piper engine not initialized`、播报走 Piper 路径。

## 性能优化点

预热在 IO 派发器后台执行，不阻塞启动；模型下载仅在 `hasModel()` 为假时进行，二次启动零开销。

## 设计模式建议

不适用（生命周期接线修复）。`warmUpTts` 的 open + 覆写是为可测性做的最小模板方法，无扩散风险。

## 风险与 Trade-off

- **风险：HuggingFace 不可达网络下载失败**——失败仅记日志不崩溃，下次启动 hasModel 为假自动重试（断点续传）；README 已有 FAQ。
- **风险：下载占用用户流量**——模型 ~15MB 且为一次性成本，README 已披露；可接受。
- **风险：`initialize` 与 `speak` 并发**（用户秒点悬浮球）：Piper 引擎内部 state 由 LOADING→READY 串行迁移，speak 在未 READY 时抛异常走降级，与现状一致，无新增竞态。
- **风险备忘（review S1，当前不修）**：`runCatching` 会把 CancellationException 当普通失败记日志；今日 appScope 无任何取消触发点属死路径，未来若引入作用域取消机制，须先改为对 CE 重新抛出的封装。
- **开放问题**：无。

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | ① onCreate 接线：Robolectric `setupApplication(子类)` 覆写 `warmUpTts` 断言被调用；② TtsManager.initialize 幂等；③ Piper 初始化失败时 initialize 不抛 | JUnit + Robolectric + MockK，`gradle testDebugUnitTest` |
| 构建门禁 | assembleDebug 通过 | 本地构建 |
| 模拟器验收 | 模型下载落地（.onnx >15MB）+ 朗读不再降级（logcat）+ 状态为 READY | 主智能体执行，真听声音以 logcat Piper 路径为准 |

边界/异常：下载失败（无网/404）路径由"失败安全不抛出"契约测试 + 现有 ModelDownloader 测试覆盖；并发预热（onCreate 多次？Application onCreate 单次）不适用。
