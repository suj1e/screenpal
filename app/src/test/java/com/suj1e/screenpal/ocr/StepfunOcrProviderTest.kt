package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * StepfunOcrProvider（StepFun 视觉）契约测试（Robolectric + Ktor MockEngine）：
 * 请求组装（system prompt 同豆包逐字 / user image data URL / model step-flash-3.7）、
 * 多行解析（TextBlock 切分/置信度 0.99/空矩形）、错误映射（401/429/5xx/空 content/畸形响应/网络异常）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepfunOcrProviderTest {

    private class RecordedEngine(
        private val status: HttpStatusCode = HttpStatusCode.OK,
        private val body: String = ""
    ) {
        var lastRequest: HttpRequestData? = null
            private set
        var lastBody: String = ""
            private set
        var callCount = 0
            private set

        val httpClient = HttpClient(MockEngine { request ->
            callCount++
            lastRequest = request
            lastBody = when (val content = request.body) {
                is TextContent -> content.text
                is ByteArrayContent -> content.bytes().decodeToString()
                else -> ""
            }
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
    }

    private fun provider(engine: RecordedEngine) =
        StepfunOcrProvider(apiKey = "sk-step-abc", httpClient = engine.httpClient)

    private fun bitmap(): Bitmap = mockk(relaxed = true)

    // ---------- 请求组装 ----------

    @Test
    fun recognize_postsToStepFunEndpoint_withBearerAuth() = runBlocking {
        val engine = RecordedEngine(body = SUCCESS_BODY)

        provider(engine).recognize(bitmap())

        val request = engine.lastRequest!!
        assertEquals("POST", request.method.value)
        assertEquals(StepfunOcrProvider.ENDPOINT, request.url.toString())
        assertEquals("Bearer sk-step-abc", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun recognize_requestBody_containsModelTemperatureSystemPromptAndImageDataUrl() = runBlocking {
        val engine = RecordedEngine(body = SUCCESS_BODY)

        provider(engine).recognize(bitmap())

        val obj = Json.parseToJsonElement(engine.lastBody).jsonObject
        assertEquals(StepfunOcrProvider.MODEL, obj["model"]!!.jsonPrimitive.content)
        assertEquals("step-flash-3.7", obj["model"]!!.jsonPrimitive.content)
        assertEquals(0.0, obj["temperature"]!!.jsonPrimitive.content.toDouble(), 1e-9)

        val messages = obj["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        val system = messages[0].jsonObject
        assertEquals("system", system["role"]!!.jsonPrimitive.content)
        // system prompt 与豆包云 OCR 逐字一致（同一 OCR 约束）
        assertEquals(CloudOcrProvider.SYSTEM_PROMPT, system["content"]!!.jsonPrimitive.content)

        val user = messages[1].jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        val parts = user["content"]!!.jsonArray
        assertEquals(1, parts.size)
        val part = parts[0].jsonObject
        assertEquals("image_url", part["type"]!!.jsonPrimitive.content)
        val url = part["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertTrue("data:image/jpeg;base64," in url)
    }

    // ---------- 解析 ----------

    @Test
    fun recognize_success_multiLineContent_splitsIntoBlocksInReadingOrder() = runBlocking {
        val engine = RecordedEngine(
            body = """{"choices":[{"message":{"role":"assistant","content":"第一行\n第二行\n\n第三行"}}]}"""
        )

        val result = provider(engine).recognize(bitmap())

        assertEquals("第一行\n第二行\n\n第三行", result.text)
        assertEquals(0.99f, result.confidence, 1e-6f)
        // 空行不产生文本块；三行按原顺序切分
        assertEquals(3, result.blocks.size)
        assertEquals("第一行", result.blocks[0].text)
        assertEquals("第二行", result.blocks[1].text)
        assertEquals("第三行", result.blocks[2].text)
        // 视觉大模型不回坐标：置信度固定 0.99，包围盒为空 Rect
        result.blocks.forEach { block ->
            assertEquals(0.99f, block.confidence, 1e-6f)
            assertEquals(Rect(), block.boundingBox)
            assertTrue(block.boundingBox.isEmpty)
        }
    }

    @Test
    fun recognize_success_singleLine_trimsAndKeepsOneBlock() = runBlocking {
        val engine = RecordedEngine(
            body = """{"choices":[{"message":{"role":"assistant","content":"  HELLO 世界  "}}]}"""
        )

        val result = provider(engine).recognize(bitmap())

        assertEquals("HELLO 世界", result.text)
        assertEquals(1, result.blocks.size)
        assertEquals("HELLO 世界", result.blocks[0].text)
    }

    // ---------- 错误 / 空响应映射 ----------

    @Test
    fun recognize_blankApiKey_throwsWithoutNetwork() = runBlocking {
        val engine = RecordedEngine(body = SUCCESS_BODY)
        val client = StepfunOcrProvider(apiKey = "   ", httpClient = engine.httpClient)

        try {
            client.recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals(0, engine.callCount)
        }
    }

    @Test
    fun recognize_http401_throwsIllegalStateWithCode() = runBlocking {
        val engine = RecordedEngine(status = HttpStatusCode.Unauthorized, body = """{"error":"bad key"}""")

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun recognize_http429_throwsIllegalStateWithCode() = runBlocking {
        val engine = RecordedEngine(status = HttpStatusCode.TooManyRequests, body = """{"error":"rate limited"}""")

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun recognize_http5xx_throwsIllegalStateWithCode() = runBlocking {
        val engine = RecordedEngine(status = HttpStatusCode.InternalServerError, body = """{"error":"boom"}""")

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun recognize_blankContent_throwsIllegalState() = runBlocking {
        val engine = RecordedEngine(
            body = """{"choices":[{"message":{"role":"assistant","content":"   "}}]}"""
        )

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // 空 content 视为失败，由上层降级端侧
        }
    }

    @Test
    fun recognize_missingChoices_throwsIllegalState() = runBlocking {
        val engine = RecordedEngine(body = """{"id":"resp-1"}""")

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun recognize_malformedBody_throwsIllegalState() = runBlocking {
        val engine = RecordedEngine(body = "not-json")

        try {
            provider(engine).recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun recognize_networkError_mapsToIllegalState() = runBlocking {
        val httpClient = HttpClient(MockEngine { throw IOException("network down") })
        val client = StepfunOcrProvider(apiKey = "sk-step-abc", httpClient = httpClient)

        try {
            client.recognize(bitmap())
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("请求失败"))
        }
    }

    private companion object {
        const val SUCCESS_BODY = """{"choices":[{"message":{"role":"assistant","content":"识别文本"}}]}"""
    }
}
