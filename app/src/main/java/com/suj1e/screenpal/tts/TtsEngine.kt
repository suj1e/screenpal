package com.suj1e.screenpal.tts

interface TtsEngine {
    suspend fun speak(text: String)
    fun stop()
    fun shutdown()
}
