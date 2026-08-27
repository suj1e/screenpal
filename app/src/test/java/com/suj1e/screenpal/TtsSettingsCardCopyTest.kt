package com.suj1e.screenpal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TTS settings card copy contract for 2026-08-27-tts-domestic-online:
 * the CLOUD slot is now 豆包在线语音（火山引擎）; selecting it reveals
 * AppID / Token / 音色 inputs. Piper & System option copy stays unchanged,
 * and no Google Cloud TTS copy remains.
 */
class TtsSettingsCardCopyTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    @Test
    fun cloudOption_isDoubaoVolcanoCopy() {
        assertTrue(
            "CLOUD option must advertise 豆包在线语音（火山引擎）",
            mainActivitySrc.contains("豆包在线语音（火山引擎，需 AppID+Token）")
        )
        assertFalse(
            "Google Cloud TTS copy must be gone",
            mainActivitySrc.contains("Google Cloud TTS")
        )
    }

    @Test
    fun piperAndSystemCopy_unchanged() {
        assertTrue(mainActivitySrc.contains("Piper 离线（推荐，需下载模型）"))
        assertTrue(mainActivitySrc.contains("系统 TTS（兜底）"))
    }

    @Test
    fun cloudSelection_revealsAppIdTokenVoiceInputs() {
        assertTrue(
            "AppID input must be wired to volcanoAppId state",
            mainActivitySrc.contains("volcanoAppId")
        )
        assertTrue(
            "Token input must be wired to volcanoToken state",
            mainActivitySrc.contains("volcanoToken")
        )
        assertTrue(
            "Voice input must be wired to ttsVoice state",
            mainActivitySrc.contains("ttsVoice")
        )
    }
}
