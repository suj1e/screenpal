package com.suj1e.screenpal.tts

import android.media.MediaPlayer
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class GoogleCloudTtsProvider(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }
) {
    private val endpoint = "https://texttospeech.googleapis.com/v1/text:synthesize"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun synthesize(text: String, rate: Float, pitch: Float): ByteArray {
        if (text.isBlank()) return ByteArray(0)

        val requestBody = """
            {
                "input": {"text": ${jsonString(text)}},
                "voice": {
                    "languageCode": "$LANGUAGE_CODE",
                    "name": "$VOICE_NAME",
                    "ssmlGender": "FEMALE"
                },
                "audioConfig": {
                    "audioEncoding": "MP3",
                    "speakingRate": ${rate.coerceIn(MIN_RATE, MAX_RATE)},
                    "pitch": ${pitch.coerceIn(MIN_PITCH, MAX_PITCH)}
                }
            }
        """.trimIndent()

        val response = withContext(Dispatchers.IO) {
            httpClient.post("$endpoint?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }

        val bodyText = response.bodyAsText()
        val audioContent = try {
            json.parseToJsonElement(bodyText).jsonObject["audioContent"]?.jsonPrimitive?.content
        } catch (_: IllegalArgumentException) {
            null
        } ?: throw TtsException("Cloud TTS response missing audioContent")

        return Base64.decode(audioContent, Base64.DEFAULT)
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    companion object {
        const val LANGUAGE_CODE = "cmn-CN"
        const val VOICE_NAME = "cmn-CN-Wavenet-A"
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4.0f
        const val MIN_PITCH = -20f
        const val MAX_PITCH = 20f
    }
}

/**
 * Plays cloud-synthesized MP3 bytes. Kept separate from the synthesis call so
 * tests can exercise request building without touching MediaPlayer.
 */
class CloudAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(audioBytes: ByteArray, cacheDir: File) {
        stop()
        val file = File(cacheDir, "tts_cloud_${System.currentTimeMillis()}.mp3")
        file.writeBytes(audioBytes)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
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
}
