package com.suj1e.screenpal.translate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * DoubaoTranslateClient contract tests (JVM + Ktor MockEngine):
 * 请求组装（system/温度0/截断/鉴权头/端点）、多行译文解析、错误映射。
 */
class DoubaoTranslateClientTest {

    private class RecordedEngine(
        private val status: HttpStatusCode = HttpStatusCode.OK,
        private val body: String = ""
    ) {
        var lastRequest: HttpRequestData? = null
            private set
        var callCount = 0
            private set

        val httpClient = HttpClient(MockEngine { request ->
            callCount++
            lastRequest = request
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
    }

    // ---------- 请求组装 ----------

    @Test
    fun buildRequestJson_containsSystemPromptTemperatureZeroAndModel() {
        val client = DoubaoTranslateClient(apiKey = "key-x")

        val json = Json.parseToJsonElement(client.buildRequestJson("hello"))

        val obj = json.jsonObject
        assertEquals(DoubaoTranslateClient.MODEL, obj["model"]!!.jsonPrimitive.content)
        assertEquals(0.0, obj["temperature"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        val messages = obj["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(DoubaoTranslateClient.SYSTEM_PROMPT, messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun translate_postsToArkEndpoint_withBearerAuth() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[{"message":{"role":"assistant","content":"你好"}}]}""")
        val client = DoubaoTranslateClient(apiKey = "key-abc", httpClient = engine.httpClient)

        client.translate("hello")

        val request = engine.lastRequest!!
        assertEquals("POST", request.method.value)
        assertEquals(DoubaoTranslateClient.ENDPOINT, request.url.toString())
        assertEquals("Bearer key-abc", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun truncateToUtf8Bytes_shortText_unchanged() {
        assertEquals("hello 你好", DoubaoTranslateClient.truncateToUtf8Bytes("hello 你好", 6000))
    }

    @Test
    fun truncateToUtf8Bytes_overLimit_cutAtAsciiBoundary() {
        val text = "a".repeat(6001)
        val truncated = DoubaoTranslateClient.truncateToUtf8Bytes(text, 6000)
        assertEquals(6000, truncated.toByteArray(Charsets.UTF_8).size)
        assertEquals("a".repeat(6000), truncated)
    }

    @Test
    fun truncateToUtf8Bytes_overLimit_neverSplitsMultibyteChar() {
        // "a"*5999 + "中" = 5999 + 3 bytes；在 6000 处会切开"中"，须回退到 5999。
        val text = "a".repeat(5999) + "中"
        val truncated = DoubaoTranslateClient.truncateToUtf8Bytes(text, 6000)
        assertEquals("a".repeat(5999), truncated)
        assertEquals(5999, truncated.toByteArray(Charsets.UTF_8).size)
    }

    // ---------- 解析 ----------

    @Test
    fun translate_success_multiLineContent_fullyPreserved() = runBlocking {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"  第一行译文\n第二行译文  "}}]}
        """.trimIndent()
        val engine = RecordedEngine(body = body)
        val client = DoubaoTranslateClient(apiKey = "key-abc", httpClient = engine.httpClient)

        val result = client.translate("hello world")

        // 多行译文完整拼接保留（仅去首尾空白）
        assertEquals("第一行译文\n第二行译文", result)
    }

    // ---------- 错误 / 空响应映射 ----------

    @Test
    fun translate_blankApiKey_throwsWithoutNetwork() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[]}""")
        val client = DoubaoTranslateClient(apiKey = "  ", httpClient = engine.httpClient)

        try {
            client.translate("hello")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertEquals(0, engine.callCount)
        }
    }

    @Test
    fun translate_httpErrorCode_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(status = HttpStatusCode.Unauthorized, body = """{"error":"bad key"}""")
        val client = DoubaoTranslateClient(apiKey = "bad-key", httpClient = engine.httpClient)

        try {
            client.translate("hello")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun translate_missingChoices_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = """{"id":"resp-1"}""")
        val client = DoubaoTranslateClient(apiKey = "key-abc", httpClient = engine.httpClient)

        try {
            client.translate("hello")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }

    @Test
    fun translate_blankContent_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[{"message":{"role":"assistant","content":"   "}}]}""")
        val client = DoubaoTranslateClient(apiKey = "key-abc", httpClient = engine.httpClient)

        try {
            client.translate("hello")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }

    @Test
    fun translate_malformedBody_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = "not-json")
        val client = DoubaoTranslateClient(apiKey = "key-abc", httpClient = engine.httpClient)

        try {
            client.translate("hello")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }
}
