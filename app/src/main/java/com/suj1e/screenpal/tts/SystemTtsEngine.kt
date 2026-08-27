package com.suj1e.screenpal.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class SystemTtsEngine(private val context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null

    override var isInitialized: Boolean = false
        private set

    @Volatile
    private var initFailed = false

    override suspend fun initialize() {
        if (isInitialized) return
        if (initFailed) throw TtsException("System TTS previously failed to initialize")

        isInitialized = suspendCancellableCoroutine { cont ->
            var settled = false
            val engine = TextToSpeech(context) { status ->
                if (!settled) {
                    settled = true
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.setLanguage(Locale.CHINA)
                        cont.resume(true)
                    } else {
                        initFailed = true
                        cont.resume(false)
                    }
                }
            }
            tts = engine
            cont.invokeOnCancellation { engine.shutdown() }
        }

        if (!isInitialized) throw TtsException("System TTS initialization failed")
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        val engine = tts ?: throw TtsException("System TTS not initialized")
        engine.setSpeechRate(rate.coerceIn(MIN_RATE, MAX_RATE))
        engine.setPitch(pitch.coerceIn(MIN_PITCH, MAX_PITCH))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
        const val UTTERANCE_ID = "ScreenPal_Utterance"
    }
}
