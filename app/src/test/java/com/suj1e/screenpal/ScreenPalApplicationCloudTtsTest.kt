package com.suj1e.screenpal

import android.content.Context
import com.suj1e.screenpal.tts.StepfunTtsEngine
import com.suj1e.screenpal.util.UserSettings
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTS CLOUD 槽位直连 StepFun（2026-08-29-stepfun-only）：
 * StepFun API Key 非空 → [StepfunTtsEngine]（音色空回落默认音色）；Key 缺失 → null，
 * 由 TtsManager 落 Piper → 系统兜底（降级语义不变）。
 */
class ScreenPalApplicationCloudTtsTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun withStepFunKey_returnsStepfunTtsEngine() {
        val engine = ScreenPalApplication.cloudTtsEngine(
            UserSettings(stepfunApiKey = "sk-step-1", stepfunVoice = "wenying"),
            context
        )
        assertTrue(engine is StepfunTtsEngine)
    }

    @Test
    fun missingKey_returnsNull() {
        assertNull(ScreenPalApplication.cloudTtsEngine(UserSettings(stepfunApiKey = ""), context))
        assertNull(ScreenPalApplication.cloudTtsEngine(UserSettings(stepfunApiKey = "   "), context))
    }

    @Test
    fun blankVoice_fallsBackToDefaultVoice() {
        val engine = ScreenPalApplication.cloudTtsEngine(
            UserSettings(stepfunApiKey = "sk-step-1", stepfunVoice = "  "),
            context
        ) as StepfunTtsEngine
        val body = Json.parseToJsonElement(engine.buildRequestBody("你好", 1.0f)).jsonObject
        assertEquals(StepfunTtsEngine.DEFAULT_VOICE, body["voice"]!!.jsonPrimitive.content)
    }

    @Test
    fun explicitVoice_isUsedInRequest() {
        val engine = ScreenPalApplication.cloudTtsEngine(
            UserSettings(stepfunApiKey = "sk-step-1", stepfunVoice = "xiaochen"),
            context
        ) as StepfunTtsEngine
        val body = Json.parseToJsonElement(engine.buildRequestBody("你好", 1.0f)).jsonObject
        assertEquals("xiaochen", body["voice"]!!.jsonPrimitive.content)
    }
}
