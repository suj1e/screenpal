package com.suj1e.screenpal.translate

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * AI 转译客户端（StepFun / 阶跃星辰）：调用 StepFun chat/completions 把任意语言
 * 转写为简体中文。system 转译指令与豆包版（[DoubaoTranslateClient]）逐字一致。
 * 任何错误（无 Key / HTTP 错误码 / 网络异常 / 空、畸形响应）都抛 [TranslationException]，
 * 由上层管道决定是否降级播报原文。
 */
class StepfunTranslateClient(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient(Android)
) : TranslateService {

    override suspend fun translate(text: String): String {
        if (apiKey.isBlank()) throw TranslationException("翻译缺少 StepFun API Key")

        val q = truncateToUtf8Bytes(text, MAX_Q_UTF8_BYTES)
        val response = try {
            httpClient.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(buildRequestJson(q))
            }
        } catch (e: Exception) {
            throw TranslationException("翻译请求失败：${e.message}", e)
        }

        if (!response.status.isSuccess()) {
            throw TranslationException("翻译服务返回 HTTP ${response.status.value}")
        }

        val content = parseTranslationContent(response.bodyAsText())
            ?: throw TranslationException("翻译响应缺少 choices[0].message.content")
        if (content.isBlank()) throw TranslationException("翻译结果为空")
        return content
    }

    /** Serializes the StepFun chat/completions payload: system 转译指令 + 用户文本，温度 0。 */
    internal fun buildRequestJson(q: String): String = requestJson.encodeToString(
        ChatRequest.serializer(),
        ChatRequest(
            model = MODEL,
            messages = listOf(
                ChatMessage(role = "system", content = SYSTEM_PROMPT),
                ChatMessage(role = "user", content = q)
            ),
            temperature = TEMPERATURE
        )
    )

    /** Extracts choices[0].message.content (trimmed); null when the shape is unexpected. */
    internal fun parseTranslationContent(body: String): String? = try {
        val choices = responseJson.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
        val content = choices?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        content?.trim()
    } catch (e: Exception) {
        null
    }

    @Serializable
    internal data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double,
        // step-3.7-flash is a reasoning model: its thinking consumes the
        // completion budget before `content`, so the cap must be generous or
        // the answer comes back empty.
        val max_tokens: Int = MAX_TOKENS
    )

    @Serializable
    internal data class ChatMessage(val role: String, val content: String)

    companion object {
        const val ENDPOINT = "https://api.stepfun.com/step_plan/v1/chat/completions"

        /** StepFun 文本模型常量（主智能体待校准项：模型名可整期替换）。 */
        const val MODEL = "step-3.7-flash"

        const val TEMPERATURE = 0.0

        const val MAX_TOKENS = 4096

        /** q 的上限：按 UTF-8 字节截断，防止超长文本放大 token 消耗。 */
        const val MAX_Q_UTF8_BYTES = 6000

        /** 与豆包转译 prompt 逐字一致（见 StepfunTranslateClientTest 契约）。 */
        const val SYSTEM_PROMPT = DoubaoTranslateClient.SYSTEM_PROMPT

        private val requestJson = Json { encodeDefaults = true }
        private val responseJson = Json { ignoreUnknownKeys = true }

        /**
         * Longest UTF-8 prefix of [text] within [maxBytes] bytes; never splits a
         * multi-byte character (backs off to the previous character boundary).
         */
        internal fun truncateToUtf8Bytes(text: String, maxBytes: Int): String {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return text
            var end = maxBytes
            while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
            return bytes.decodeToString(0, end)
        }
    }
}
