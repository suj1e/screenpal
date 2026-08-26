# Tasks: OCR 引擎

## Task 4.1 创建 OCR 统一接口

- [ ] 创建 `OcrEngine` 接口（`suspend fun recognize(bitmap: Bitmap): OcrResult`）
- [ ] 创建 `OcrResult` 数据类（text、confidence、blocks）
- [ ] 创建 `TextBlock` 数据类（text、confidence、boundingBox）
- [ ] 定义 `OcrMode` 枚举（LOCAL / CLOUD / HYBRID）
- **测试验收标准**：接口定义完整，可被 Mock 和实现

## Task 4.2 实现 MlKitOcrProvider

- [ ] 创建 `MlKitOcrProvider` 类
- [ ] 初始化 `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`
- [ ] 实现 `recognize(bitmap)` 方法，将 Bitmap 转为 `InputImage`
- [ ] 处理识别结果，提取 text、confidence、blocks
- [ ] 使用 `suspendCancellableCoroutine` 封装回调为挂起函数
- [ ] 添加异常处理（识别失败返回空结果）
- **测试验收标准**：
  - 单元测试：模拟识别成功，返回正确 text 和 confidence
  - 单元测试：模拟识别失败，抛出异常
  - 集成测试：真实截图识别中文文本

## Task 4.3 实现 CloudOcrProvider（Google Cloud Vision API）

- [ ] 创建 `CloudOcrConfig` 数据类（apiKey、timeoutMs）
- [ ] 创建 `CloudOcrProvider` 类
- [ ] 实现 `recognize(bitmap)` 方法
- [ ] Bitmap 转 Base64（JPEG 压缩）
- [ ] 构建 Google Cloud Vision API 请求体（TEXT_DETECTION）
- [ ] 使用 Ktor Client 发送 HTTP POST 请求
- [ ] 解析 JSON 响应为 `OcrResult`
- [ ] 添加超时和错误处理
- **测试验收标准**：
  - 单元测试：构建请求体格式正确
  - 单元测试：解析模拟响应为 OcrResult
  - 集成测试：真实 API Key 调用云端识别

## Task 4.4 实现 HybridOcrEngine

- [ ] 创建 `HybridOcrEngine` 类，接收端侧和云端 Provider
- [ ] 实现混合策略：先调用端侧，根据置信度决定是否调用云端
- [ ] 可配置置信度阈值（默认 0.75）
- [ ] 云端调用失败时降级返回端侧结果
- [ ] 云端 Provider 为 null 时直接返回端侧结果
- **测试验收标准**：
  - 单元测试：端侧置信度高于阈值 → 直接返回端侧结果
  - 单元测试：端侧置信度低于阈值 → 调用云端
  - 单元测试：云端失败 → 降级返回端侧结果

## Task 4.5 实现 OcrEngineFactory

- [ ] 创建工厂类，根据 `OcrMode` 创建对应引擎实例
- [ ] LOCAL 模式：仅 MlKitOcrProvider
- [ ] CLOUD 模式：仅 CloudOcrProvider
- [ ] HYBRID 模式：HybridOcrEngine
- [ ] 处理 cloudConfig 为 null 的情况（HYBRID 降级为 LOCAL）
- **测试验收标准**：三种模式创建正确引擎实例

## Task 4.6 实现 Bitmap 工具方法

- [ ] 实现 `Bitmap.toBase64(quality: Int): String`（JPEG 压缩 + Base64 编码）
- [ ] 实现 `Bitmap.scaleTo(maxWidth: Int): Bitmap`（保持宽高比缩放）
- [ ] 添加 OOM 保护（限制最大尺寸 1920px）
- **测试验收标准**：
  - 单元测试：Base64 编码正确（可解码还原）
  - 单元测试：缩放后宽高比保持
  - 单元测试：极端尺寸处理（> 1920px 自动缩放）

## Task 4.7 添加网络依赖

- [ ] 在 app/build.gradle.kts 添加 Ktor Client 依赖
- [ ] 配置 Android 平台 Ktor Client（OkHttp 引擎）
- [ ] 配置网络权限（INTERNET）
- **测试验收标准**：依赖编译通过，网络请求可正常发送

## Task 4.8 集成到 OCR 流程

- [ ] 在 SelectionViewModel 中注入 OcrEngine
- [ ] 框选确认后调用 `ocrEngine.recognize(croppedBitmap)`
- [ ] 处理识别结果（展示文本、计算置信度）
- [ ] 识别完成后自动调用 TtsManager.speak()（Change 5）
- **测试验收标准**：框选后自动触发 OCR，结果正确展示

## Task 4.9 构建验证

- [ ] 执行 `./gradlew assembleDebug`
- [ ] 在模拟器上测试 ML Kit 识别
- [ ] 测试混合模式（模拟低置信度场景触发云端）
- [ ] 测试网络异常降级
- **测试验收标准**：构建通过，三种模式均可用
