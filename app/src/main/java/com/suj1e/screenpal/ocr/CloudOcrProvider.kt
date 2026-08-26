package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

data class CloudOcrConfig(
    val apiKey: String,
    val timeoutMs: Long = 10000
)

class CloudOcrProvider(
    private val config: CloudOcrConfig,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) : OcrEngine {

    private val endpoint = "https://vision.googleapis.com/v1/images:annotate"

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val base64 = bitmap.toBase64(quality = 85)
        val requestBody = """
            {
                "requests": [
                    {
                        "image": {
                            "content": "$base64"
                        },
                        "features": [
                            {
                                "type": "TEXT_DETECTION",
                                "maxResults": 10
                            }
                        ],
                        "imageContext": {
                            "languageHints": ["zh", "en"]
                        }
                    }
                ]
            }
        """.trimIndent()

        httpClient.post("$endpoint?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        return OcrResult(
            text = "",
            confidence = 0f,
            blocks = emptyList()
        )
    }

    private fun Bitmap.toBase64(quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
