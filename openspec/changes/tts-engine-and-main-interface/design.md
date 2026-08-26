# Design: TTS 引擎

## 架构概览

```
TtsManager（单例，统一入口）
    │
    ├── PiperTtsEngine（端侧优先，ONNX Runtime）
    │   ├── ModelDownloader（模型下载）
    │   └── OnnxRuntimeSession（推理会话）
    │
    ├── CloudTtsProvider（Google Cloud TTS，可选）
    │   └── Ktor HttpClient
    │
    └── SystemTtsEngine（降级方案，原生 TextToSpeech）
```

## PiperTtsEngine 实现

### 集成方式

直接集成 ONNX Runtime，手动封装 Piper VITS 推理：

```kotlin
// build.gradle.kts
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
```

### 类结构
```kotlin
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader
) : TtsEngine {

    private var session: OrtSession? = null
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    override suspend fun initialize() {
        if (isInitialized) return

        // 1. 检查模型是否已下载
        val modelFile = context.getModelFile("zh_CN-huayan-medium.onnx")
        val configFile = context.getModelFile("zh_CN-huayan-medium.json")

        if (!modelFile.exists() || !configFile.exists()) {
            modelDownloader.downloadModel("zh_CN-huayan-medium")
        }

        // 2. 初始化 ONNX Runtime 会话
        val environment = OrtEnvironment.getEnvironment()
        session = environment.createSession(modelFile.absolutePath, SessionOptions())

        isInitialized = true
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (!isInitialized) awaitInitialization()

        // 1. 文本预处理：中文 → 音素序列
        val phonemes = textToPhonemes(text)

        // 2. ONNX 推理：生成 mel 频谱
        val melSpectrogram = synthesizeMel(session!!, phonemes)

        // 3. 声码器：mel → 音频波形
        val audioData = vocode(melSpectrogram)

        // 4. 写入 WAV 文件
        val wavFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
        writeWav(wavFile, audioData)

        // 5. MediaPlayer 播放
        mediaPlayer = MediaPlayer().apply {
            setDataSource(wavFile.absolutePath)
            prepare()
            start()
        }
    }

    override fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun shutdown() {
        session?.close()
        session = null
        stop()
        isInitialized = false
    }
}
```

### 模型下载策略

```kotlin
class ModelDownloader @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        // Piper  voices 托管在 HuggingFace
        const val MODEL_BASE_URL = "https://huggingface.co/datasets/rhasspy/piper-voices/resolve/main/zh/"
        const val MODEL_FILE = "zh_CN-huayan-medium.onnx"
        const val CONFIG_FILE = "zh_CN-huayan-medium.json"
        const val MODEL_SIZE_MB = 15L
    }

    suspend fun downloadModel(language: String) {
        val modelFile = context.getModelFile("$language.onnx")
        val configFile = context.getModelFile("$language.json")

        // 下载 ONNX 模型
        downloadFile("$MODEL_BASE_URL$MODEL_FILE", modelFile)
        // 下载配置文件
        downloadFile("$MODEL_BASE_URL$CONFIG_FILE", configFile)
    }

    private suspend fun downloadFile(url: String, dest: File) {
        // 使用 Ktor Client 下载，支持断点续传
        httpClient.get(url).bodyAsChannel().toFile(dest)
    }
}
```

### Piper 模型说明

| 属性 | 值 |
|------|-----|
| 模型 | `zh_CN-huayan-medium` |
| 语言 | 中文（普通话） |
| 音色 | 女声（Huayan 是中文女声音色） |
| 文件大小 | ONNX 模型约 15MB，配置文件约 1KB |
| 采样率 | 22050 Hz |
| 架构 | VITS（Variational Inference with adversarial learning for end-to-end Text-to-Speech） |

## CloudTtsProvider 实现（Google Cloud TTS）

```kotlin
interface CloudTtsProvider {
    suspend fun synthesize(text: String, voice: String, rate: Float, pitch: Float): ByteArray
}

class GoogleCloudTtsProvider(
    private val httpClient: HttpClient,
    private val config: CloudTtsConfig
) : CloudTtsProvider {

    private val endpoint = "https://texttospeech.googleapis.com/v1/text:synthesize"

    override suspend fun synthesize(text: String, voice: String, rate: Float, pitch: Float): ByteArray {
        val requestBody = buildJsonObject {
            put("input", buildJsonObject {
                put("text", text)
            })
            put("voice", buildJsonObject {
                put("languageCode", "cmn-CN")
                put("name", "cmn-CN-Wavenet-A") // 中文女声 WaveNet
                put("ssmlGender", "FEMALE")
            })
            put("audioConfig", buildJsonObject {
                put("audioEncoding", "MP3")
                put("speakingRate", rate)
                put("pitch", pitch)
            })
        }

        val response = httpClient.post("$endpoint?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val audioContent = json["audioContent"]?.jsonPrimitive?.contentOrNull
            ?: throw TtsException("Cloud TTS response missing audioContent")

        return Base64.decode(audioContent, Base64.DEFAULT)
    }
}
```

## SystemTtsEngine 实现（降级方案）

```kotlin
class SystemTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    override suspend fun initialize() {
        if (isInitialized) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
            }
        }.apply {
            setLanguage(Locale.CHINA)
        }
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (!isInitialized) awaitInitialization()

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, rate)
            putFloat(TextToSpeech.Engine.KEY_PARAM_PITCH, pitch)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ScreenPal_Utterance")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
```

## TtsManager 统一封装

```kotlin
class TtsManager @Inject constructor(
    private val piperEngine: PiperTtsEngine,
    private val cloudProvider: CloudTtsProvider?,
    private val settingsRepository: SettingsRepository
) : TtsEngine {

    private var currentEngine: TtsEngine = piperEngine
    private var currentText: String = ""
    private var isSpeaking = false

    override suspend fun speak(text: String) {
        currentText = text
        isSpeaking = true

        try {
            val settings = settingsRepository.userSettings.first()
            val rate = settings.ttsRate
            val pitch = settings.ttsPitch

            currentEngine = when (settings.ttsEngine) {
                TtsEngineType.PIPER -> piperEngine
                TtsEngineType.CLOUD -> cloudProvider ?: piperEngine
                TtsEngineType.SYSTEM -> SystemTtsEngine(context)
            }

            currentEngine.speak(text, rate, pitch)
        } catch (e: Exception) {
            // Piper 失败时降级到系统 TTS
            if (currentEngine == piperEngine) {
                try {
                    SystemTtsEngine(context).apply {
                        initialize()
                        speak(text, 1.0f, 1.0f)
                    }
                } catch (e2: Exception) {
                    // 彻底失败，记录错误
                    Log.e("TtsManager", "All TTS engines failed", e2)
                }
            }
        }
    }

    override fun stop() {
        currentEngine.stop()
        isSpeaking = false
    }

    override suspend fun initialize() {
        piperEngine.initialize()
    }
}
```

## 数据模型

```kotlin
enum class TtsEngineType {
    PIPER,    // 端侧 Piper 神经语音
    CLOUD,    // 云端 Google Cloud TTS
    SYSTEM    // 原生系统 TTS
}

data class TtsSettings(
    val engine: TtsEngineType = TtsEngineType.PIPER,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f
)
```

## 降级策略

| 组件 | 正常路径 | 降级路径 |
|------|----------|----------|
| Piper TTS | Piper 神经语音（ONNX） | 降级到 Google Cloud TTS → 再降级到 System TTS |
| 模型下载 | 从 HuggingFace CDN 下载 | 提示用户手动下载（后续版本） |

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | TtsManager 降级逻辑、SystemTtsEngine 封装、CloudTtsProvider 请求构建 | JUnit 4 + MockK | 85% |
| 集成测试 | Piper 模型下载 + 推理（需真实模型文件） | AndroidX Test | 手动验证 |
| 手动验证 | Piper / Cloud / System 三种引擎切换播放 | 模拟器/真机 | 100% 流程通过 |

### 测试数据

- 模拟短文本（10-50 字）和长文本（500+ 字）
- 模拟极端语速/音调（0.5x / 2.0x）
- 模拟 Piper 初始化失败场景

### 边界条件

- Piper 模型文件损坏 → 降级到 Cloud/System
- 网络不可用 → Cloud TTS 不可用，自动降级
- TTS 引擎初始化超时 → fallback
- 空文本输入 → 不调用引擎
