package com.suj1e.screenpal.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

class PiperTtsEngine(
    private val context: Context,
    private val modelDownloader: ModelDownloader = ModelDownloader(context)
) : TtsEngine {

    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null
    private var config: PiperConfig? = null
    private var mediaPlayer: MediaPlayer? = null

    enum class State { PENDING, LOADING, READY, FAILED }

    var state: State = State.PENDING
        private set

    override val isInitialized: Boolean
        get() = state == State.READY && session != null

    override suspend fun initialize() {
        if (isInitialized) return

        state = State.LOADING
        try {
            withContext(Dispatchers.IO) {
                if (!modelDownloader.hasModel()) {
                    modelDownloader.downloadModel()
                }
                val configFile = modelDownloader.configFile()
                config = PiperConfig.fromJson(configFile.readText())

                environment = OrtEnvironment.getEnvironment()
                session = environment!!.createSession(
                    modelDownloader.modelFile().absolutePath,
                    OrtSession.SessionOptions()
                )
            }
            state = State.READY
        } catch (e: Exception) {
            state = State.FAILED
            throw TtsException("Piper initialization failed", e)
        }
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (text.isBlank()) return
        val ortSession = session ?: throw TtsException("Piper engine not initialized")
        val piperConfig = config ?: throw TtsException("Piper config not loaded")

        val audio = withContext(Dispatchers.Default) {
            synthesize(ortSession, piperConfig, text, rate)
        }

        val wavFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            writeWav(wavFile, audio, piperConfig.sampleRate)
        }
        play(wavFile)
    }

    /**
     * Simplified phonemization: look up each character in the config's phoneme_id_map.
     * Chars absent from the map are skipped. This avoids bundling espeak-ng data but
     * means pronunciation quality is lower than full espeak-based Piper frontends.
     */
    internal fun textToPhonemeIds(text: String, piperConfig: PiperConfig): LongArray {
        val ids = mutableListOf<Long>()
        ids.add(piperConfig.bosId)
        text.forEach { ch ->
            piperConfig.phonemeIdMap[ch.toString()]?.let { id ->
                ids.add(id)
                ids.add(piperConfig.blankId)
            }
        }
        ids.add(piperConfig.eosId)
        return ids.toLongArray()
    }

    private fun synthesize(
        ortSession: OrtSession,
        piperConfig: PiperConfig,
        text: String,
        rate: Float
    ): ShortArray {
        val env = environment ?: OrtEnvironment.getEnvironment()
        val phonemeIds = textToPhonemeIds(text, piperConfig)

        OnnxTensor.createTensor(env, arrayOf(phonemeIds)).use { inputTensor ->
            OnnxTensor.createTensor(env, longArrayOf(phonemeIds.size.toLong())).use { lengthsTensor ->
                OnnxTensor.createTensor(
                    env,
                    floatArrayOf(
                        piperConfig.noiseScale,
                        piperConfig.lengthScale / rate.coerceAtLeast(MIN_RATE),
                        piperConfig.noiseWarp
                    )
                ).use { scalesTensor ->
                    ortSession.run(
                        mapOf(
                            "input" to inputTensor,
                            "input_lengths" to lengthsTensor,
                            "scales" to scalesTensor
                        )
                    ).use { results ->
                        // Read the flat sample buffer instead of casting a fixed
                        // dimensionality: voice exports differ (this one emits
                        // [1,1,1,T], older piper models [1,1,T]).
                        val tensor = results[0] as OnnxTensor
                        val samples = tensor.floatBuffer
                        return ShortArray(samples.remaining()) { i ->
                            (samples.get(i) * 32767f).toInt().toShort()
                        }
                    }
                }
            }
        }
    }

    internal fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val byteCount = pcm.size * 2
        RandomAccessFile(file, "rw").use { raf ->
            // WAV header (44 bytes) + PCM16 mono data
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.writeIntLe(36 + byteCount)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLe(16)
            raf.writeShortLe(AUDIO_FORMAT_PCM)
            raf.writeShortLe(CHANNELS_MONO)
            raf.writeIntLe(sampleRate)
            raf.writeIntLe(sampleRate * CHANNELS_MONO * BYTES_PER_SAMPLE)
            raf.writeShortLe(CHANNELS_MONO * BYTES_PER_SAMPLE)
            raf.writeShortLe(BITS_PER_SAMPLE)
            raf.writeBytes("data")
            raf.writeIntLe(byteCount)
            pcm.forEach { raf.writeShortLe(it.toInt() and 0xFFFF) }
        }
    }

    private fun play(wavFile: File) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(wavFile.absolutePath)
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
        session?.close()
        session = null
        environment = null
        config = null
        state = State.PENDING
    }

    internal data class PiperConfig(
        val sampleRate: Int,
        val noiseScale: Float,
        val lengthScale: Float,
        val noiseWarp: Float,
        val phonemeIdMap: Map<String, Long>,
        val bosId: Long,
        val eosId: Long,
        val blankId: Long
    ) {
        companion object {
            const val DEFAULT_BOS = 1L
            const val DEFAULT_EOS = 2L
            const val DEFAULT_BLANK = 0L

            fun fromJson(jsonText: String): PiperConfig {
                val json = JSONObject(jsonText)
                val audio = json.optJSONObject("audio") ?: JSONObject()
                val inference = json.optJSONObject("inference") ?: JSONObject()

                val idMap = mutableMapOf<String, Long>()
                val rawMap = json.optJSONObject("phoneme_id_map") ?: JSONObject()
                rawMap.keys().forEach { symbol ->
                    val ids = rawMap.optJSONArray(symbol)
                    if (ids != null && ids.length() > 0) {
                        idMap[symbol] = ids.optLong(0)
                    }
                }

                return PiperConfig(
                    sampleRate = audio.optInt("sample_rate", 22050),
                    noiseScale = inference.optDouble("noise_scale", 0.667).toFloat(),
                    lengthScale = inference.optDouble("length_scale", 1.0).toFloat(),
                    noiseWarp = inference.optDouble("noise_w", 0.8).toFloat(),
                    phonemeIdMap = idMap,
                    bosId = DEFAULT_BOS,
                    eosId = DEFAULT_EOS,
                    blankId = DEFAULT_BLANK
                )
            }
        }
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val AUDIO_FORMAT_PCM = 1
        const val CHANNELS_MONO = 1
        const val BYTES_PER_SAMPLE = 2
        const val BITS_PER_SAMPLE = 16
    }
}

private fun RandomAccessFile.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}

private fun RandomAccessFile.writeShortLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}
