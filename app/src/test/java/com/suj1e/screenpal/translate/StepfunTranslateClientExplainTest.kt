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
 * StepfunTranslateClient EXPLAIN contract tests (JVM + Ktor MockEngine),
 * 2026-08-29-broadcast-mode：讲解与转译同端点同 Key 同模型；system prompt 逐字
 * 契约；max_tokens 4096；6000 字节截断；解析与错误映射与 translate 同构。
 */
class StepfunTranslateClientExplainTest {

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
    fun buildExplainRequestJson_containsExplainPromptTemperatureZeroAndMaxTokens() {
        val client = StepfunTranslateClient(apiKey = "sk-step-x")

        val json = Json.parseToJsonElement(client.buildExplainRequestJson("勿扰模式"))

        val obj = json.jsonObject
        assertEquals(StepfunTranslateClient.MODEL, obj["model"]!!.jsonPrimitive.content)
        assertEquals(0.0, obj["temperature"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(4096, obj["max_tokens"]!!.jsonPrimitive.content.toInt())
        val messages = obj["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        // EXPLAIN system prompt 逐字契约（口语化解释 / ≤80 字 / 不逐字翻译 / 无前缀）
        assertEquals(
            "用户在屏幕上圈选了这段内容。请用简体中文口语化解释：这是什么、有什么用。" +
                "不超过80字，适合语音朗读，不要逐字翻译，不要任何前缀说明。",
            messages[0].jsonObject["content"]!!.jsonPrimitive.content
        )
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("勿扰模式", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun explain_postsToSameEndpoint_withBearerAuth() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[{"message":{"role":"assistant","content":"这是勿扰模式"}}]}""")
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        client.explain("勿扰模式")

        val request = engine.lastRequest!!
        assertEquals("POST", request.method.value)
        assertEquals(StepfunTranslateClient.ENDPOINT, request.url.toString())
        assertEquals("Bearer sk-step-abc", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun explain_truncatesInputTo6000Utf8Bytes() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        client.explain("a".repeat(6001))

        val bodyText = (engine.lastRequest!!.body as io.ktor.http.content.TextContent).text
        val userContent = Json.parseToJsonElement(bodyText).jsonObject["messages"]!!
            .jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertEquals(6000, userContent.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun explain_success_returnsTrimmedContent() = runBlocking {
        val engine = RecordedEngine(
            body = """{"choices":[{"message":{"role":"assistant","content":"  这是勿扰模式开关，\n打开后来电静音。  "}}]}"""
        )
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        val result = client.explain("勿扰模式")

        assertEquals("这是勿扰模式开关，\n打开后来电静音。", result)
    }

    // ---------- 错误 / 空响应映射（与 translate 同构）----------

    @Test
    fun explain_blankApiKey_throwsWithoutNetwork() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[]}""")
        val client = StepfunTranslateClient(apiKey = "  ", httpClient = engine.httpClient)

        try {
            client.explain("勿扰模式")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertEquals(0, engine.callCount)
        }
    }

    @Test
    fun explain_httpErrorCode_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(status = HttpStatusCode.Unauthorized, body = """{"error":"bad key"}""")
        val client = StepfunTranslateClient(apiKey = "sk-bad", httpClient = engine.httpClient)

        try {
            client.explain("勿扰模式")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun explain_missingChoices_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = """{"id":"resp-1"}""")
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        try {
            client.explain("勿扰模式")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }

    @Test
    fun explain_blankContent_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = """{"choices":[{"message":{"role":"assistant","content":"   "}}]}""")
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        try {
            client.explain("勿扰模式")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }

    @Test
    fun explain_malformedBody_mapsToTranslationException() = runBlocking {
        val engine = RecordedEngine(body = "not-json")
        val client = StepfunTranslateClient(apiKey = "sk-step-abc", httpClient = engine.httpClient)

        try {
            client.explain("勿扰模式")
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            // expected
        }
    }
}
