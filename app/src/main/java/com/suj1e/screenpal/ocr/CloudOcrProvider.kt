package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

data class CloudOcrConfig(
    val apiKey: String,
    val timeoutMs: Long = 10000
)

/**
 * 云 OCR：调用火山方舟（Volcano Ark）chat/completions 的豆包视觉模型，
 * 与 AI 转译（[com.suj1e.screenpal.translate.DoubaoTranslateClient]）共用同一把方舟
 * API Key（Bearer 鉴权），但 prompt 与解析独立。
 *
 * 视觉大模型不返回精确坐标：[TextBlock.boundingBox] 恒为空 [Rect]，UI 不消费坐标。
 * 错误（无 Key / HTTP 错误码 / 网络异常 / 空、畸形响应）都抛 [IllegalStateException]，
 * 由 Hybrid/上层降级端侧。
 */
class CloudOcrProvider(
    private val config: CloudOcrConfig,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        if (config.apiKey.isBlank()) throw IllegalStateException("云 OCR 缺少火山方舟 API Key")

        val response = try {
            httpClient.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(buildRequestJson(bitmap.toJpegDataUrl()))
            }
        } catch (e: Exception) {
            throw IllegalStateException("云 OCR 请求失败：${e.message}", e)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("云 OCR 服务返回 HTTP ${response.status.value}")
        }

        val content = parseContent(response.bodyAsText())
            ?: throw IllegalStateException("云 OCR 响应缺少 choices[0].message.content")
        if (content.isBlank()) throw IllegalStateException("云 OCR 未识别到文字（content 为空）")

        val blocks = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { TextBlock(it, CONFIDENCE, Rect()) }
        return OcrResult(text = content, confidence = CONFIDENCE, blocks = blocks)
    }

    /** Serializes the ark vision payload: system OCR 指令 + user 图片（base64 data URL），温度 0。 */
    internal fun buildRequestJson(imageDataUrl: String): String = buildJsonObject {
        put("model", MODEL)
        put("temperature", TEMPERATURE)
        put("max_tokens", MAX_TOKENS)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", imageDataUrl) })
                    })
                })
            })
        })
    }.toString()

    /** Extracts choices[0].message.content (trimmed); null when the shape is unexpected. */
    internal fun parseContent(body: String): String? = try {
        val choices = responseJson.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
        val content = choices?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        content?.trim()
    } catch (e: Exception) {
        null
    }

    private fun Bitmap.toJpegDataUrl(quality: Int = JPEG_QUALITY): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    companion object {
        const val ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

        /** 方舟视觉模型常量（主智能体待校准项：模型名可整期替换）。 */
        const val MODEL = "doubao-seed-1-6"

        const val TEMPERATURE = 0.0

        /** 输出 token 上限：OCR 文本输出有限，防止失控长输出放大按 token 计费成本。 */
        const val MAX_TOKENS = 2048

        /** 与上层 JPEG 压缩质量约定对齐（85）。 */
        const val JPEG_QUALITY = 85

        /** 视觉大模型不回逐块置信度，用固定高置信近似。 */
        const val CONFIDENCE = 0.99f

        const val SYSTEM_PROMPT =
            "你是 OCR 引擎。只输出图片中的文字，按阅读顺序（先上后下、先左后右），" +
                "不要任何解释、标注或额外符号。"

        private val responseJson = Json { ignoreUnknownKeys = true }
    }
}
