package com.suj1e.screenpal.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

object TtsManager {
    private var tts: TextToSpeech? = null
    var isInitialized: Boolean = false
        private set

    fun init(context: Context) {
        if (isInitialized) return
        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
        }
        tts?.setLanguage(Locale.CHINA)
    }

    fun speak(text: String) {
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ScreenPal_Utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
