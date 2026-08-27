package com.suj1e.screenpal.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * Volcano Engine (火山引擎 / 豆包) online TTS engine, used for the CLOUD slot of
 * the fallback chain (Doubao -> Piper -> System).
 *
 * Query-mode synthesis: POST a JSON envelope, receive `{data: <base64 mp3>}`,
 * decode to cacheDir and play via MediaPlayer (same pattern as PiperTtsEngine).
 *
 * All protocol details that the main agent may need to correct after checking
 * the official docs (auth header format, cluster, ratios) live in [Companion]
 * constants — change them there and the contract tests in DoubaoTtsEngineTest
 * will flag any deliberate divergence.
 */
class DoubaoTtsEngine(
    private val context: Context,
    private val appId: String,
    private val token: String,
    private val voiceType: String = DEFAULT_VOICE_TYPE,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }
) : TtsEngine {

    private val json = Json { ignoreUnknownKeys = true }

    private var mediaPlayer: MediaPlayer? = null

    // Stateless network engine: nothing to load ahead of time, always ready.
    override val isInitialized: Boolean
        get() = true

    override suspend fun initialize() {
        // No model/session to prepare; kept for TtsEngine symmetry.
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (text.isBlank()) return
        val file = synthesizeToFile(text, rate, pitch)
        playFile(file)
    }

    /** Synthesizes and writes the MP3 to cacheDir; playback-free for testability. */
    internal suspend fun synthesizeToFile(text: String, rate: Float, pitch: Float): File {
        val audio = withContext(Dispatchers.IO) { synthesize(text, rate, pitch) }
        val file = File(context.cacheDir, "tts_doubao_${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) { file.writeBytes(audio) }
        return file
    }

    private suspend fun synthesize(text: String, rate: Float, pitch: Float): ByteArray {
        val body = buildRequestBody(
            text = truncateUtf8(text, MAX_TEXT_BYTES),
            rate = rate,
            pitch = pitch,
            reqid = UUID.randomUUID().toString()
        )
        val response = httpClient.post(ENDPOINT) {
            headers { append(AUTH_HEADER_NAME, buildAuthHeader()) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw TtsException("Doubao TTS HTTP ${response.status.value}")
        }
        return parseAudioBytes(response.bodyAsText())
    }

    internal fun buildRequestBody(text: String, rate: Float, pitch: Float, reqid: String): String =
        buildJsonObject {
            put("app", buildJsonObject {
                put("appid", appId)
                put("token", token)
                put("cluster", CLUSTER)
            })
            put("user", buildJsonObject { put("uid", USER_UID) })
            put("audio", buildJsonObject {
                put("voice_type", voiceType)
                put("encoding", AUDIO_ENCODING)
                put("speed_ratio", mapRatio(rate))
                put("pitch_ratio", mapRatio(pitch))
            })
            put("request", buildJsonObject {
                put("reqid", reqid)
                put("text", text)
                put("operation", OPERATION)
            })
        }.toString()

    internal fun buildAuthHeader(): String = "$AUTH_SCHEME$token"

    /**
     * Success: `{data: <base64>}`. Failure: `{code, message}` (or `{error_code,
     * message}` shape) — both map to [TtsException] so TtsManager can degrade.
     */
    internal fun parseAudioBytes(bodyText: String): ByteArray {
        val obj = runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
            ?: throw TtsException("Doubao TTS malformed response body")

        val data = obj["data"]?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
        if (!data.isNullOrEmpty()) {
            return runCatching { Base64.getMimeDecoder().decode(data) }
                .getOrElse { throw TtsException("Doubao TTS audio payload is not valid base64", it) }
        }

        val code = obj["code"]?.let { el -> runCatching { el.jsonPrimitive.int }.getOrNull() }
            ?: obj["error_code"]?.let { el -> runCatching { el.jsonPrimitive.int }.getOrNull() }
        val message = obj["message"]?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
            ?: "unknown error"
        throw TtsException("Doubao TTS error code=${code ?: -1} message=$message")
    }

    private fun playFile(file: File) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    override fun stop() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            } catch (_: IllegalStateException) {
                // player already released
            }
        }
        mediaPlayer = null
    }

    override fun shutdown() {
        stop()
    }

    companion object {
        const val TAG = "DoubaoTtsEngine"
        const val ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts"

        // NOTE(待核): Volcano docs specify "Authorization: Bearer;{token}"
        // (semicolon, no space). Centralized here for one-line correction.
        const val AUTH_HEADER_NAME = "Authorization"
        const val AUTH_SCHEME = "Bearer;"

        const val CLUSTER = "volcano_tts"
        const val USER_UID = "screenpal"
        const val AUDIO_ENCODING = "mp3"
        const val OPERATION = "query"
        const val DEFAULT_VOICE_TYPE = "BV001_streaming"
        const val REQUEST_TIMEOUT_MS = 10_000L

        // Volcano rejects text above 1024 UTF-8 bytes in query mode: truncate + log.
        const val MAX_TEXT_BYTES = 1024

        // UI sliders expose [0.5, 2.0]; Volcano accepts ratios in [0.2, 3.0].
        // UI range is linearly expanded onto the official range, then clamped.
        const val UI_MIN_RATIO = 0.5f
        const val UI_MAX_RATIO = 2.0f
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 3.0f

        fun mapRatio(value: Float): Float {
            val expanded = MIN_RATIO +
                (value - UI_MIN_RATIO) * (MAX_RATIO - MIN_RATIO) / (UI_MAX_RATIO - UI_MIN_RATIO)
            return expanded.coerceIn(MIN_RATIO, MAX_RATIO)
        }

        fun truncateUtf8(text: String, maxBytes: Int): String {
            if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
            val sb = StringBuilder()
            var bytes = 0
            var i = 0
            while (i < text.length) {
                val chars = String(Character.toChars(Character.codePointAt(text, i)))
                val len = chars.toByteArray(Charsets.UTF_8).size
                if (bytes + len > maxBytes) break
                sb.append(chars)
                bytes += len
                i += chars.length
            }
            Log.w(TAG, "Doubao TTS text exceeds $maxBytes bytes; truncated")
            return sb.toString()
        }
    }
}
