# Tasks: TTS 引擎

## Task 5.1 集成 ONNX Runtime

- [ ] 在 app/build.gradle.kts 添加 ONNX Runtime 依赖
- [ ] 验证 ONNX 库在 Android 上的兼容性（ABI: armeabi-v7a, arm64-v8a）
- [ ] 配置 ABI 过滤（仅保留 arm64-v8a + armeabi-v7a）
- **测试验收标准**：依赖编译通过，APK 包含正确的原生库

## Task 5.2 实现 PiperTtsEngine

- [ ] 创建 `PiperTtsEngine.kt`
- [ ] 实现模型文件路径管理（internal storage/files/models/）
- [ ] 实现 ONNX Session 初始化
- [ ] 实现文本预处理（中文文本 → 音素序列）
- [ ] 实现 ONNX 推理（输入音素 → mel 频谱）
- [ ] 实现声码器（mel 频谱 → 音频波形，简化版：直接使用 ONNX 模型输出）
- [ ] 实现 WAV 文件写入
- [ ] 实现 MediaPlayer 播放
- [ ] 实现 `stop()` 和 `shutdown()` 生命周期管理
- [ ] 添加初始化状态机（Pending → Loading → Ready / Failed）
- **测试验收标准**：
  - 单元测试：模型路径正确生成
  - 集成测试：真实模型文件可加载并推理

## Task 5.3 实现 ModelDownloader

- [ ] 创建 `ModelDownloader` 类
- [ ] 使用 Ktor Client 下载模型文件
- [ ] 实现断点续传（支持大文件下载）
- [ ] 下载进度回调（Notification 显示进度）
- [ ] 文件完整性校验（文件大小检查）
- **测试验收标准**：
  - 单元测试：下载 URL 正确
  - 集成测试：真实下载模型文件（约 15MB）

## Task 5.4 实现 CloudTtsProvider（Google Cloud TTS）

- [ ] 创建 `CloudTtsProvider` 接口和实现类
- [ ] 实现 Google Cloud TTS API 调用
- [ ] 构建请求体（text + voice + audioConfig）
- [ ] 解析音频响应（Base64 audioContent）
- [ ] 添加 API Key 配置逻辑
- **测试验收标准**：
  - 单元测试：请求体格式正确
  - 集成测试：真实 API Key 调用成功

## Task 5.5 实现 SystemTtsEngine（降级方案）

- [ ] 创建 `SystemTtsEngine` 封装原生 TextToSpeech
- [ ] 实现初始化、speak、stop、shutdown
- [ ] 设置中文语言（Locale.CHINA）
- [ ] 处理 TTS 引擎不可用的情况
- **测试验收标准**：系统 TTS 可正常播放中文文本

## Task 5.6 实现 TtsManager

- [ ] 创建 `TtsManager` 单例
- [ ] 统一封装 Piper / Cloud / System 三种引擎
- [ ] 实现引擎自动降级（Piper 失败 → Cloud → System）
- [ ] 实现 `speak()` / `stop()` / `initialize()` 接口
- [ ] 添加状态监听（通过 SharedFlow 暴露播放状态）
- **测试验收标准**：
  - 单元测试：Piper 成功时使用 Piper
  - 单元测试：Piper 失败时降级到 System
  - 单元测试：空文本不调用引擎

## Task 5.7 构建验证

- [ ] 执行 `./gradlew assembleDebug`
- [ ] 验证 ONNX Runtime 原生库打包正确
- [ ] 在模拟器上验证 Piper 模型下载和推理
- [ ] 在模拟器上验证三种 TTS 引擎切换
- **测试验收标准**：构建通过，TTS 引擎可用
