package com.suj1e.screenpal.tts

interface TtsEngine {
    val isInitialized: Boolean

    suspend fun initialize()

    suspend fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f)

    fun stop()

    fun shutdown()
}
