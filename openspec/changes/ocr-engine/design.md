# Design: OCR 引擎

## 架构概览

```
SelectionOverlayActivity（调用方）
    │
    ▼
OcrEngine（统一接口）
    │
    ├── MlKitOcrProvider（端侧，默认）
    │
    └── CloudOcrProvider（Google Cloud Vision API，可选）
```

## OcrEngine 统一接口

```kotlin
data class OcrResult(
    val text: String,
    val confidence: Float,
    val blocks: List<TextBlock>
)

data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect
)

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}

class HybridOcrEngine(
    private val mlKitProvider: MlKitOcrProvider,
    private val cloudProvider: CloudOcrProvider?,
    private val confidenceThreshold: Float = 0.75f
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        // 1. 优先端侧识别
        val mlKitResult = mlKitProvider.recognize(bitmap)

        // 2. 检查是否需要云端增强
        if (mlKitResult.confidence < confidenceThreshold && cloudProvider != null) {
            return cloudProvider.recognize(bitmap)
        }

        // 3. 返回端侧结果
        return mlKitResult
    }
}
```

## MlKitOcrProvider 实现

```kotlin
class MlKitOcrProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrEngine {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    override suspend fun recognize(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.map { block ->
                    TextBlock(
                        text = block.text,
                        confidence = block.confidence ?: 0f,
                        boundingBox = block.boundingBox ?: Rect()
                    )
                }
                val avgConfidence = if (blocks.isNotEmpty()) {
                    blocks.map { it.confidence }.average().toFloat()
                } else 0f

                cont.resume(OcrResult(
                    text = visionText.text,
                    confidence = avgConfidence,
                    blocks = blocks
                ))
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }
}
```

## CloudOcrProvider 实现（Google Cloud Vision API）

**选型说明**：选用 Google Cloud Vision API 的原因：
- 与 ML Kit 同属 Google 生态，接口风格一致
- 支持多语言（含中文），准确率高
- 文档完善，社区支持好
- 免费额度：每月前 1000 次请求免费

```kotlin
data class CloudOcrConfig(
    val apiKey: String,
    val timeoutMs: Long = 10000
)

class CloudOcrProvider(
    private val config: CloudOcrConfig,
    private val httpClient: HttpClient
) : OcrEngine {

    private val endpoint = "https://vision.googleapis.com/v1/images:annotate"

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        // 1. Bitmap 转 Base64（JPEG 压缩）
        val base64 = bitmap.toBase64(quality = 85)

        // 2. 构建 Google Cloud Vision 请求
        val requestBody = buildJsonObject {
            put("requests", buildJsonArray {
                addJsonObject {
                    put("image", buildJsonObject {
                        put("content", base64)
                    })
                    put("features", buildJsonArray {
                        addJsonObject {
                            put("type", "TEXT_DETECTION")
                            put("maxResults", 10)
                        }
                    })
                    put("imageContext", buildJsonObject {
                        put("languageHints", buildJsonArray {
                            add("zh")
                            add("en")
                        })
                    })
                }
            })
        }

        // 3. 发送 HTTP 请求
        val response = httpClient.post("$endpoint?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        // 4. 解析响应
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val responses = json["responses"]?.jsonArray?.get(0)?.jsonObject
        val textAnnotations = responses?.get("textAnnotations")?.jsonArray

        val fullText = textAnnotations?.firstOrNull()?.jsonObject?.get("description")?.jsonPrimitive?.contentOrNull ?: ""
        val blocks = textAnnotations?.drop(1)?.map { annotation ->
            val obj = annotation.jsonObject
            val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val boundingBox = obj["boundingPoly"?.let { /* parse vertices */ }]
            TextBlock(
                text = description,
                confidence = 0.95f, // Google Cloud Vision 不返回逐块置信度，使用默认高值
                boundingBox = boundingBox ?: Rect()
            )
        } ?: emptyList()

        return OcrResult(
            text = fullText,
            confidence = if (blocks.isNotEmpty()) 0.95f else 0f,
            blocks = blocks
        )
    }
}
```

## 混合识别策略

```kotlin
class OcrEngineFactory {
    fun create(
        mode: OcrMode,  // LOCAL / CLOUD / HYBRID
        cloudConfig: CloudOcrConfig?
    ): OcrEngine {
        return when (mode) {
            OcrMode.LOCAL -> MlKitOcrProvider(context)
            OcrMode.CLOUD -> CloudOcrProvider(cloudConfig!!, httpClient)
            OcrMode.HYBRID -> HybridOcrEngine(
                mlKitProvider = MlKitOcrProvider(context),
                cloudProvider = cloudConfig?.let { CloudOcrProvider(it, httpClient) },
                confidenceThreshold = 0.75f
            )
        }
    }
}
```

## 识别模式配置

| 模式 | 端侧 | 云端 | 适用场景 |
|------|------|------|----------|
| LOCAL | ✅ | ❌ | 离线场景，优先速度 |
| CLOUD | ❌ | ✅ | 复杂场景，优先准确率 |
| HYBRID | ✅（优先） | ✅（降级） | 综合最优，默认推荐 |

## 依赖

| 依赖 | 用途 |
|------|------|
| `com.google.android.gms:play-services-mlkit-text-recognition` | 端侧 OCR |
| `io.ktor:ktor-client-android` | 云端 HTTP 请求 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON 解析 |

## 异常处理

| 异常 | 处理 |
|------|------|
| ML Kit 识别超时 | 返回空结果 + 置信度 0 |
| 云端 API 超时/失败 | 降级返回端侧结果（即使置信度低） |
| 云端 API Key 无效 | 降级返回端侧结果，记录错误日志 |
| Bitmap 过大 | 缩放至最大 1920px 宽度后识别 |

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | OcrEngine 接口、Hybrid 策略、Bitmap 工具方法 | JUnit 4 + MockK | 90% |
| 集成测试 | ML Kit 识别准确性（模拟器/真机） | AndroidX Test | 手动验证 |
| 手动验证 | 混合模式切换、云端 fallback | 模拟器/真机 | 100% 流程通过 |

### 测试数据

- 模拟中文印刷体截图（不同字号、背景）
- 模拟英文截图
- 模拟低质量截图（模糊、低对比度）
- 模拟极端尺寸 Bitmap（超大/极小）

### 边界条件

- 空白图片识别（返回空文本 + 置信度 0）
- 极端比例图片（超宽/超高）
- 网络异常时云端 fallback
- API Key 无效时的降级行为
