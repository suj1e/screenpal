package com.suj1e.screenpal.tts

enum class TtsEngineType {
    PIPER,
    CLOUD,
    SYSTEM;

    companion object {
        fun from(raw: String?): TtsEngineType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PIPER
    }
}
