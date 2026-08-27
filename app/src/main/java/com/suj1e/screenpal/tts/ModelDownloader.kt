package com.suj1e.screenpal.tts

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class ModelDownloader(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS
        }
    }
) {
    fun modelsDir(): File = File(context.filesDir, MODEL_DIR_NAME).apply { mkdirs() }

    fun modelFile(): File = File(modelsDir(), MODEL_FILE_NAME)
    fun configFile(): File = File(modelsDir(), CONFIG_FILE_NAME)

    fun hasModel(): Boolean =
        modelFile().let { it.exists() && it.length() > MIN_MODEL_SIZE_BYTES } && configFile().exists()

    suspend fun downloadModel(onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            downloadFile(configUrl(), configFile())
            downloadFile(modelUrl(), modelFile(), onProgress)
        }
    }

    private suspend fun downloadFile(url: String, dest: File, onProgress: (Float) -> Unit = {}) {
        // Resume support: ask the server for the remaining bytes of a partial file.
        val existing = if (dest.exists()) dest.length() else 0L

        val response = httpClient.get(url) {
            if (existing > 0) header("Range", "bytes=$existing-")
        }

        val resuming = response.status == HttpStatusCode.PartialContent && existing > 0
        if (response.status != HttpStatusCode.OK && !resuming) {
            throw TtsException("Download failed: HTTP ${response.status.value}")
        }

        val bodyChannel = response.bodyAsChannel()
        val buffer = ByteArray(BUFFER_SIZE_BYTES)

        if (resuming) {
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(existing)
                while (!bodyChannel.isClosedForRead) {
                    val read = bodyChannel.readAvailable(java.nio.ByteBuffer.wrap(buffer))
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                }
            }
            onProgress(1f)
            return
        }

        dest.outputStream().use { out ->
            val total = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
            var written = 0L
            while (!bodyChannel.isClosedForRead) {
                val read = bodyChannel.readAvailable(java.nio.ByteBuffer.wrap(buffer))
                if (read == -1) break
                out.write(buffer, 0, read)
                written += read
                if (total > 0) onProgress(written.toFloat() / total)
            }
            onProgress(1f)
        }
    }

    companion object {
        const val MODEL_DIR_NAME = "models"
        const val MODEL_BASE_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/zh/zh_CN/huayan/medium/"
        const val MODEL_FILE_NAME = "zh_CN-huayan-medium.onnx"
        const val CONFIG_FILE_NAME = "zh_CN-huayan-medium.json"
        const val MIN_MODEL_SIZE_BYTES = 1L // sanity lower bound; real model is ~15MB
        const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
        const val BUFFER_SIZE_BYTES = 64 * 1024

        fun modelUrl(): String = MODEL_BASE_URL + MODEL_FILE_NAME

        // Upstream config is named "<model>.onnx.json"; the local copy keeps
        // CONFIG_FILE_NAME (without .onnx) so existing installs stay valid.
        fun configUrl(): String = MODEL_BASE_URL + MODEL_FILE_NAME + ".json"
    }
}
