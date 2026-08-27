package com.suj1e.screenpal.tts

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * Contract tests for DoubaoTtsEngine (2026-08-27-tts-domestic-online).
 *
 * Volcano Engine query API shape:
 *   POST https://openspeech.bytedance.com/api/v1/tts
 *   Authorization: Bearer;{token}   (NOTE: header format 待核 — pinned here so a
 *   correction to the constant must update this test deliberately)
 *   body: {app:{appid,token,cluster},user:{uid},audio:{voice_type,encoding,speed_ratio,pitch_ratio},request:{reqid,text,operation}}
 *   success: {data: <base64 mp3>} ; failure: {code|error_code, message}
 */
class DoubaoTtsEngineRatioMappingTest {

    @Test
    fun mapRatio_anchoredAtNormalIsIdentity() {
        // UI slider [0.5, 2.0] -> Volcano [0.2, 3.0], anchored: 1.0 stays 1.0
        assertEquals(0.2f, DoubaoTtsEngine.mapRatio(0.5f), 1e-4f)
        assertEquals(3.0f, DoubaoTtsEngine.mapRatio(2.0f), 1e-4f)
        assertEquals(1.0f, DoubaoTtsEngine.mapRatio(1.0f), 1e-4f)
        assertEquals(1.5f, DoubaoTtsEngine.mapRatio(1.25f), 1e-4f)
    }

    @Test
    fun mapRatio_clampsOutsideOfficialRange() {
        assertEquals(3.0f, DoubaoTtsEngine.mapRatio(99f), 1e-4f)
        assertEquals(0.2f, DoubaoTtsEngine.mapRatio(-1f), 1e-4f)
    }
}

class DoubaoTtsEngineRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun newEngine() = DoubaoTtsEngine(
        context = mockk(relaxed = true),
        appId = "app-123",
        token = "tok-456"
    )

    @Test
    fun buildRequestBody_matchesVolcanoQuerySchema() {
        val body = newEngine().buildRequestBody(
            text = "你好世界",
            rate = 1.25f,
            pitch = 0.5f,
            reqid = "req-1"
        )
        val obj = json.parseToJsonElement(body).jsonObject

        val app = obj["app"]!!.jsonObject
        assertEquals("app-123", app["appid"]!!.jsonPrimitive.content)
        assertEquals("tok-456", app["token"]!!.jsonPrimitive.content)
        assertEquals(DoubaoTtsEngine.CLUSTER, app["cluster"]!!.jsonPrimitive.content)

        assertEquals(DoubaoTtsEngine.USER_UID, obj["user"]!!.jsonObject["uid"]!!.jsonPrimitive.content)

        val audio = obj["audio"]!!.jsonObject
        assertEquals(DoubaoTtsEngine.DEFAULT_VOICE_TYPE, audio["voice_type"]!!.jsonPrimitive.content)
        assertEquals("mp3", audio["encoding"]!!.jsonPrimitive.content)
        assertEquals(1.5f, audio["speed_ratio"]!!.jsonPrimitive.content.toFloat(), 1e-4f)
        assertEquals(0.2f, audio["pitch_ratio"]!!.jsonPrimitive.content.toFloat(), 1e-4f)

        val request = obj["request"]!!.jsonObject
        assertEquals("req-1", request["reqid"]!!.jsonPrimitive.content)
        assertEquals("你好世界", request["text"]!!.jsonPrimitive.content)
        assertEquals(DoubaoTtsEngine.OPERATION, request["operation"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRequestBody_usesCustomVoiceType() {
        val engine = DoubaoTtsEngine(
            context = mockk(relaxed = true),
            appId = "a",
            token = "t",
            voiceType = "zh_female_custom"
        )
        val obj = json.parseToJsonElement(
            engine.buildRequestBody("hi", 1.0f, 1.0f, "r")
        ).jsonObject
        assertEquals(
            "zh_female_custom",
            obj["audio"]!!.jsonObject["voice_type"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun authHeader_usesBearerSemicolonScheme() {
        // 待核 constant: Volcano docs say "Bearer;{token}" (semicolon, no space).
        assertEquals("Bearer;tok-456", newEngine().buildAuthHeader())
    }

    @Test
    fun truncateUtf8_capsAtMaxBytesWithoutSplittingMultibyteChar() {
        // 600 CJK chars = 1800 bytes -> must truncate under 1024 bytes on a char boundary.
        val longText = "中".repeat(600)
        val truncated = DoubaoTtsEngine.truncateUtf8(longText, DoubaoTtsEngine.MAX_TEXT_BYTES)

        assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= DoubaoTtsEngine.MAX_TEXT_BYTES)
        assertTrue(truncated.length < longText.length)
        assertEquals(0, truncated.toByteArray(Charsets.UTF_8).size % 3) // no split multibyte
        // 341 full chars fit in 1023 bytes
        assertEquals(341, truncated.length)
    }

    @Test
    fun truncateUtf8_keepsShortTextUntouched() {
        assertEquals("abc你好", DoubaoTtsEngine.truncateUtf8("abc你好", DoubaoTtsEngine.MAX_TEXT_BYTES))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DoubaoTtsEngineNetworkTest {

    private val mp3Bytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x1F)

    private fun newEngine(handler: MockRequestHandler): Pair<DoubaoTtsEngine, MutableList<HttpRequestData>> {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests.add(request)
                    handler(request)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = DoubaoTtsEngine.REQUEST_TIMEOUT_MS
            }
        }
        val context = RuntimeEnvironment.getApplication()
        return DoubaoTtsEngine(context, "app-123", "tok-456", httpClient = client) to requests
    }

    private fun okHandler(payload: String): MockRequestHandler = { _: HttpRequestData ->
        respond(
            payload,
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Test
    fun synthesize_decodesBase64AndWritesMp3FileIntoCacheDir() = runBlocking<Unit> {
        val base64 = Base64.getEncoder().encodeToString(mp3Bytes)
        val (engine, _) = newEngine(okHandler("""{"reqid":"r","code":3000,"message":"success","data":"$base64"}"""))
        val cacheDir = RuntimeEnvironment.getApplication().cacheDir

        val file = engine.synthesizeToFile("你好", 1.0f, 1.0f)

        assertTrue("file must live in cacheDir", file.absolutePath.startsWith(cacheDir.absolutePath))
        assertTrue(file.name.endsWith(".mp3"))
        assertTrue(file.exists())
        assertTrue(mp3Bytes.contentEquals(file.readBytes()))
        file.delete()
    }

    @Test
    fun synthesize_sendsBearerSemicolonAuthHeaderAndJsonBody() = runBlocking<Unit> {
        val base64 = Base64.getEncoder().encodeToString(mp3Bytes)
        val (engine, requests) = newEngine(okHandler("""{"data":"$base64"}"""))

        engine.synthesizeToFile("hi", 1.0f, 1.0f)

        val request = requests.single()
        assertEquals("Bearer;tok-456", request.headers[HttpHeaders.Authorization])
        val body = request.body as? TextContent
        assertEquals(ContentType.Application.Json, body?.contentType)
        assertTrue(
            "request body must carry the volcano envelope",
            body?.text?.contains("\"appid\":\"app-123\"") == true
        )
    }

    @Test
    fun synthesize_errorCodeResponse_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine(okHandler("""{"code":3001,"message":"invalid appid"}"""))

        try {
            engine.synthesizeToFile("hi", 1.0f, 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("3001"))
            assertTrue(e.message!!.contains("invalid appid"))
        }
    }

    @Test
    fun synthesize_errorCodeAltShape_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine(okHandler("""{"error_code":3005,"message":"quota exceeded"}"""))

        try {
            engine.synthesizeToFile("hi", 1.0f, 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("3005"))
        }
    }

    @Test
    fun synthesize_emptyDataField_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine(okHandler("""{"code":3000,"data":""}"""))

        try {
            engine.synthesizeToFile("hi", 1.0f, 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertFalse(e.message.isNullOrBlank())
        }
    }

    @Test
    fun synthesize_malformedJsonBody_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine(okHandler("not-json at all"))

        try {
            engine.synthesizeToFile("hi", 1.0f, 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("response"))
        }
    }

    @Test
    fun synthesize_httpError_mapsToTtsException() = runBlocking<Unit> {
        val (engine, _) = newEngine { _: HttpRequestData ->
            respond("Server Error", HttpStatusCode.InternalServerError)
        }

        try {
            engine.synthesizeToFile("hi", 1.0f, 1.0f)
            fail("expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun speak_blankText_neverHitsNetwork() = runBlocking<Unit> {
        val (engine, requests) = newEngine(okHandler("""{"data":"aGk="}"""))

        engine.speak("   ", 1.0f, 1.0f)

        assertTrue(requests.isEmpty())
    }

    @Test
    fun stop_whenIdle_isSafeNoThrow() {
        val (engine, _) = newEngine(okHandler("""{"data":"aGk="}"""))

        engine.stop()
        engine.shutdown()

        // No exception means playback teardown is safe without an active player.
    }
}
