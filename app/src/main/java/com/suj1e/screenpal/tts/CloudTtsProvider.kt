package com.suj1e.screenpal.tts

class CloudTtsProvider : TtsEngine {
    override suspend fun speak(text: String) {}
    override fun stop() {}
    override fun shutdown() {}
}
