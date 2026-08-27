package com.suj1e.screenpal.tts

import android.content.Context
import android.media.MediaPlayer
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * StepFun（阶跃星辰）online TTS engine, used for the CLOUD slot of the fallback
 * chain (StepFun -> Piper -> System) when the selected vendor is STEPFUN.
 *
 * OpenAI-compatible speech API: POST a JSON envelope, receive binary MP3 bytes,
 * write them to cacheDir and play via MediaPlayer (same stop semantics as
 * [DoubaoTtsEngine]).
 *
 * Protocol constants live in [Companion] — change them there and the contract
 * tests in StepfunTtsEngineTest will flag any deliberate divergence.
 */
class StepfunTtsEngine(
    private val context: Context,
    private val apiKey: String,
    private val voice: String = DEFAULT_VOICE,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }
) : TtsEngine {

    private var mediaPlayer: MediaPlayer? = null

    /** Set by stop() so a synthesis started before the stop finishes mute. */
    @Volatile
    private var cancelled = false

    // Stateless network engine: nothing to load ahead of time, always ready.
    override val isInitialized: Boolean
        get() = true

    override suspend fun initialize() {
        // No model/session to prepare; kept for TtsEngine symmetry.
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (text.isBlank()) return
        cancelled = false
        val file = synthesizeToFile(text, rate)
        if (cancelled) {
            file.delete()
            return
        }
        playFile(file)
    }

    /** Synthesizes and writes the MP3 to cacheDir; playback-free for testability. */
    internal suspend fun synthesizeToFile(text: String, rate: Float): File {
        val audio = withContext(Dispatchers.IO) { synthesize(text, rate) }
        val file = File(context.cacheDir, "tts_stepfun_${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) { file.writeBytes(audio) }
        return file
    }

    private suspend fun synthesize(text: String, rate: Float): ByteArray {
        val response = httpClient.post(ENDPOINT) {
            headers { append(AUTH_HEADER_NAME, buildAuthHeader()) }
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(text, rate))
        }
        if (!response.status.isSuccess()) {
            throw TtsException("StepFun TTS HTTP ${response.status.value}")
        }
        val bytes = response.readBytes()
        if (bytes.isEmpty()) {
            throw TtsException("StepFun TTS response body is empty")
        }
        return bytes
    }

    internal fun buildRequestBody(text: String, rate: Float): String = buildJsonObject {
        put("model", MODEL)
        put("voice", voice)
        put("input", buildSpeechInput(text, rate))
        put("response_format", RESPONSE_FORMAT)
    }.toString()

    internal fun buildAuthHeader(): String = "$AUTH_HEADER_SCHEME$apiKey"

    private fun playFile(file: File) {
        stop()
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            // Completion releases the player and cleans the cached MP3.
            player.setOnCompletionListener {
                it.release()
                file.delete()
                if (mediaPlayer === it) mediaPlayer = null
            }
            player.prepare()
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
            file.delete()
            throw e
        }
    }

    override fun stop() {
        cancelled = true
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
        const val TAG = "StepfunTtsEngine"
        const val ENDPOINT = "https://api.stepfun.com/v1/audio/speech"

        const val AUTH_HEADER_NAME = "Authorization"
        const val AUTH_HEADER_SCHEME = "Bearer "

        /** StepFun TTS 模型常量（主智能体待校准项：模型名可整期替换）。 */
        const val MODEL = "step-tts-2.5"

        /** 默认音色：闻莺女声（可选小宸男声等，设置页可改）。 */
        const val DEFAULT_VOICE = "wenying"

        const val RESPONSE_FORMAT = "mp3"
        const val REQUEST_TIMEOUT_MS = 10_000L

        /**
         * 语速经 input 前置自然语言指令控制（StepFun 支持自然语言风格控制）；
         * rate=1.0（UI 正常态）不加指令，保持原文。
         */
        fun buildSpeechInput(text: String, rate: Float): String =
            if (rate == 1.0f) {
                text
            } else {
                String.format(java.util.Locale.US, "请以%.1f倍语速朗读：%s", rate, text)
            }
    }
}
