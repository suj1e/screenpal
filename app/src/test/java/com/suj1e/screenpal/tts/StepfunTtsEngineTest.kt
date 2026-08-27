package com.suj1e.screenpal.tts

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contract tests for StepfunTtsEngine (2026-08-27-stepfun-vendor).
 *
 * StepFun OpenAI-compatible speech API:
 *   POST https://api.stepfun.com/v1/audio/speech
 *   Authorization: Bearer {stepfunApiKey}
 *   body: {model, voice, input, response_format}
 *   success: binary MP3 bytes ; failure: HTTP error code / empty body
 */
class StepfunTtsEngineRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun newEngine() = StepfunTtsEngine(
        context = mockk(relaxed = true),
        apiKey = "sk-step-123"
    )

    @Test
    fun buildRequestBody_matchesStepFunSpeechSchema() {
        val body = newEngine().buildRequestBody(text = "你好世界", rate = 1.0f)
        val obj = json.parseToJsonElement(body).jsonObject

        assertEquals(StepfunTtsEngine.MODEL, obj["model"]!!.jsonPrimitive.content)
        assertEquals(StepfunTtsEngine.DEFAULT_VOICE, obj["voice"]!!.jsonPrimitive.content)
        assertEquals("你好世界", obj["input"]!!.jsonPrimitive.content)
        assertEquals("mp3", obj["response_format"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRequestBody_usesCustomVoice() {
        val engine = StepfunTtsEngine(
            context = mockk(relaxed = true),
            apiKey = "sk-1",
            voice = "xiaochen"
        )
        val obj = json.parseToJsonElement(engine.buildRequestBody("hi", 1.0f)).jsonObject
        assertEquals("xiaochen", obj["voice"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRequestBody_normalRate_noSpeedPrefix() {
        val body = newEngine().buildRequestBody(text = "你好", rate = 1.0f)
        val obj = json.parseToJsonElement(body).jsonObject
        assertEquals("你好", obj["input"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRequestBody_fasterRate_prependsNaturalLanguageInstruction() {
        val body = newEngine().buildRequestBody(text = "你好", rate = 1.5f)
        val obj = json.parseToJsonElement(body).jsonObject
        assertEquals("请以1.5倍语速朗读：你好", obj["input"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRequestBody_slowerRate_prependsNaturalLanguageInstruction() {
        val body = newEngine().buildRequestBody(text = "你好", rate = 0.5f)
        val obj = json.parseToJsonElement(body).jsonObject
        assertEquals("请以0.5倍语速朗读：你好", obj["input"]!!.jsonPrimitive.content)
    }

    @Test
    fun authHeader_usesPlainBearerScheme() {
        assertEquals("Bearer sk-step-123", newEngine().buildAuthHeader())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepfunTtsEngineNetworkTest {

    private val mp3Bytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x1F)

    private fun newEngine(handler: MockRequestHandler): Pair<StepfunTtsEngine, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests.add(request)
                    handler(request)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = StepfunTtsEngine.REQUEST_TIMEOUT_MS
            }
        }
        val context = RuntimeEnvironment.getApplication()
        return StepfunTtsEngine(context, "sk-step-123", httpClient = client) to requests
    }

    @Test
    fun synthesize_writesBinaryMp3IntoCacheDir() = runBlocking<Unit> {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond(
                mp3Bytes,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "audio/mpeg")
            )
        }
        val cacheDir = RuntimeEnvironment.getApplication().cacheDir

        val file = engine.synthesizeToFile("你好", 1.0f)

        assertTrue("file must live in cacheDir", file.absolutePath.startsWith(cacheDir.absolutePath))
        assertTrue(file.name.endsWith(".mp3"))
        assertTrue(file.exists())
        assertTrue(mp3Bytes.contentEquals(file.readBytes()))
        file.delete()
    }

    @Test
    fun synthesize_sendsBearerAuthAndSpeechEndpoint() = runBlocking<Unit> {
        val (engine, requests) = newEngine { _: HttpRequestData ->
            respond(mp3Bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "audio/mpeg"))
        }

        engine.synthesizeToFile("hi", 1.0f)

        val request = requests.single()
        assertEquals(StepfunTtsEngine.ENDPOINT, request.url.toString())
        assertEquals("Bearer sk-step-123", request.headers[HttpHeaders.Authorization])
        val body = request.body as? TextContent
        assertEquals(ContentType.Application.Json, body?.contentType)
        assertTrue(
            "request body must carry model/voice/input envelope",
            body?.text?.contains("\"model\":\"${StepfunTtsEngine.MODEL}\"") == true &&
                body.text.contains("\"voice\":\"${StepfunTtsEngine.DEFAULT_VOICE}\"") &&
                body.text.contains("\"input\":\"hi\"")
        )
    }

    @Test
    fun synthesize_httpError_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }

        try {
            engine.synthesizeToFile("hi", 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun synthesize_errorJsonBody_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond(
                """{"error":{"message":"invalid api key"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        try {
            engine.synthesizeToFile("hi", 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("400"))
        }
    }

    @Test
    fun synthesize_emptySuccessBody_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "audio/mpeg"))
        }

        try {
            engine.synthesizeToFile("hi", 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("empty", ignoreCase = true))
        }
    }

    @Test
    fun speak_blankText_neverHitsNetwork() = runBlocking<Unit> {
        val (engine, requests) = newEngine { _: HttpRequestData ->
            respond(mp3Bytes, HttpStatusCode.OK)
        }

        engine.speak("   ", 1.0f, 1.0f)

        assertTrue(requests.isEmpty())
    }

    @Test
    fun stop_whenIdle_isSafeNoThrow() {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond(mp3Bytes, HttpStatusCode.OK)
        }

        engine.stop()
        engine.shutdown()

        // No exception means playback teardown is safe without an active player.
    }
}
