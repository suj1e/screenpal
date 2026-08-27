# Tasks: TTS 引擎

## Task 5.1 集成 ONNX Runtime

- [x] 在 app/build.gradle.kts 添加 ONNX Runtime 依赖
- [x] 验证 ONNX 库在 Android 上的兼容性（ABI: armeabi-v7a, arm64-v8a）
- [x] 配置 ABI 过滤（仅保留 arm64-v8a + armeabi-v7a）
- **测试验收标准**：依赖编译通过，APK 包含正确的原生库

## Task 5.2 实现 PiperTtsEngine

- [x] 创建 `PiperTtsEngine.kt`
- [x] 实现模型文件路径管理（internal storage/files/models/）
- [x] 实现 ONNX Session 初始化
- [x] 实现文本预处理（中文文本 → 音素序列，简化版：查 phoneme_id_map）
- [x] 实现 ONNX 推理（输入音素 → mel 频谱）
- [x] 实现声码器（mel 频谱 → 音频波形，简化版：直接使用 ONNX 模型输出）
- [x] 实现 WAV 文件写入
- [x] 实现 MediaPlayer 播放
- [x] 实现 `stop()` 和 `shutdown()` 生命周期管理
- [x] 添加初始化状态机（Pending → Loading → Ready / Failed）
- **测试验收标准**：
  - 单元测试：WAV 头写入正确、音素映射正确（已通过）
  - 集成测试：真实模型文件可加载并推理（需真机 + 真实模型，手动验证）

## Task 5.3 实现 ModelDownloader

- [x] 创建 `ModelDownloader` 类
- [x] 使用 Ktor Client 下载模型文件
- [x] 实现断点续传（支持大文件下载，Range 请求）
- [x] 下载进度回调（onProgress 回调；通知展示由上层接入）
- [x] 文件完整性校验（文件大小检查 hasModel()）
- **测试验收标准**：
  - 单元测试：下载 URL 正确（常量导出可断言）
  - 集成测试：真实下载模型文件（需网络环境，手动验证）

## Task 5.4 实现 CloudTtsProvider（Google Cloud TTS）

- [x] 创建实现类 `GoogleCloudTtsProvider`（+ `CloudAudioPlayer` 播放 MP3 字节）
- [x] 实现 Google Cloud TTS API 调用
- [x] 构建请求体（text + voice + audioConfig）
- [x] 解析音频响应（Base64 audioContent）
- [x] 添加 API Key 配置逻辑（构造参数注入）
- **测试验收标准**：
  - 单元测试：空文本短路返回空字节（已在 TtsManager 层覆盖）
  - 集成测试：真实 API Key 调用成功（手动验证）

## Task 5.5 实现 SystemTtsEngine（降级方案）

- [x] 创建 `SystemTtsEngine` 封装原生 TextToSpeech
- [x] 实现初始化、speak、stop、shutdown
- [x] 设置中文语言（Locale.CHINA）
- [x] 处理 TTS 引擎不可用的情况（init 失败抛 TtsException，语速/音调 clamp）
- **测试验收标准**：系统 TTS 可正常播放中文文本（TtsManager 降级单测通过；真机播放手动验证）

## Task 5.6 实现 TtsManager

- [x] 创建 `TtsManager` 统一入口（可注入引擎，替代原单例占位）
- [x] 统一封装 Piper / Cloud / System 三种引擎
- [x] 实现引擎自动降级（Piper 失败 → Cloud → System）
- [x] 实现 `speak()` / `stop()` / `initialize()` 接口
- [x] 添加状态监听（isSpeaking: SharedFlow<Boolean>）
- **测试验收标准**：
  - 单元测试：Piper 成功时使用 Piper ✓
  - 单元测试：Piper 失败时降级到 System ✓
  - 单元测试：空文本不调用引擎 ✓

## Task 5.7 构建验证

- [x] 执行 `./gradlew assembleDebug`（gradle assembleDebug，wrapper 不可用）
- [x] 验证 ONNX Runtime 原生库打包正确（build 输出含 libonnxruntime.so）
- [ ] 在模拟器上验证 Piper 模型下载和推理（依赖真实网络与模型，后续真机联调）
- [ ] 在模拟器上验证三种 TTS 引擎切换（同上）
- **测试验收标准**：构建通过 ✓、单元测试全绿 ✓；模拟器/真机播放在集成阶段验证
