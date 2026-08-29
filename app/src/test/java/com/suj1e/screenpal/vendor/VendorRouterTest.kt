package com.suj1e.screenpal.vendor

import android.content.Context
import com.suj1e.screenpal.ocr.CloudOcrProvider
import com.suj1e.screenpal.ocr.StepfunOcrProvider
import com.suj1e.screenpal.translate.DoubaoTranslateClient
import com.suj1e.screenpal.translate.StepfunTranslateClient
import com.suj1e.screenpal.tts.DoubaoTtsEngine
import com.suj1e.screenpal.tts.StepfunTtsEngine
import com.suj1e.screenpal.util.UserSettings
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VendorRouter 路由矩阵契约测试（2026-08-27-stepfun-vendor）：
 * DOUBAO / STEPFUN × TTS / OCR / 转译 全组合；缺凭据 → null（上层落 Piper / 端侧 / 原文兜底）；
 * 未知 vendor 值回落默认 DOUBAO。
 */
class VendorRouterTest {

    private val context: Context = mockk(relaxed = true)

    // ---------- vendor 归一化 ----------

    @Test
    fun normalizeVendor_doubaoAndStepFun() {
        assertEquals("DOUBAO", VendorRouter.normalizeVendor("DOUBAO"))
        assertEquals("STEPFUN", VendorRouter.normalizeVendor("STEPFUN"))
        assertEquals("STEPFUN", VendorRouter.normalizeVendor("stepfun"))
    }

    @Test
    fun normalizeVendor_unknownOrNull_blank_fallsBackToDoubao() {
        assertEquals("DOUBAO", VendorRouter.normalizeVendor("OPENAI"))
        assertEquals("DOUBAO", VendorRouter.normalizeVendor(null))
        assertEquals("DOUBAO", VendorRouter.normalizeVendor("  "))
    }

    // ---------- TTS 路由 ----------

    @Test
    fun tts_doubao_withAppIdAndToken_returnsDoubaoEngine() {
        val engine = VendorRouter.createTtsEngine(
            UserSettings(
                cloudVendor = "DOUBAO",
                volcanoSpeechAppId = "app-1",
                volcanoSpeechToken = "tok-1",
                ttsVoice = "BV001_streaming"
            ),
            context
        )
        assertTrue(engine is DoubaoTtsEngine)
    }

    @Test
    fun tts_doubao_missingEitherCredential_returnsNull() {
        assertNull(
            VendorRouter.createTtsEngine(
                UserSettings(cloudVendor = "DOUBAO", volcanoSpeechAppId = "", volcanoSpeechToken = "tok"),
                context
            )
        )
        assertNull(
            VendorRouter.createTtsEngine(
                UserSettings(cloudVendor = "DOUBAO", volcanoSpeechAppId = "app", volcanoSpeechToken = "  "),
                context
            )
        )
    }

    @Test
    fun tts_stepfun_withKey_returnsStepfunEngine() {
        val engine = VendorRouter.createTtsEngine(
            UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = "sk-1", stepfunVoice = "wenying"),
            context
        )
        assertTrue(engine is StepfunTtsEngine)
    }

    @Test
    fun tts_stepfun_missingKey_returnsNull() {
        assertNull(
            VendorRouter.createTtsEngine(
                UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = ""),
                context
            )
        )
    }

    @Test
    fun tts_unknownVendor_fallsBackToDoubaoRoute() {
        val engine = VendorRouter.createTtsEngine(
            UserSettings(
                cloudVendor = "SOMETHING_ELSE",
                volcanoSpeechAppId = "app-1",
                volcanoSpeechToken = "tok-1"
            ),
            context
        )
        assertTrue(engine is DoubaoTtsEngine)
    }

    // ---------- OCR 路由 ----------

    @Test
    fun ocr_doubao_withArkKey_returnsCloudOcrProvider() {
        val engine = VendorRouter.createOcrEngine(
            UserSettings(cloudVendor = "DOUBAO", cloudApiKey = "ark-1")
        )
        assertTrue(engine is CloudOcrProvider)
    }

    @Test
    fun ocr_doubao_missingKey_returnsNull() {
        assertNull(
            VendorRouter.createOcrEngine(UserSettings(cloudVendor = "DOUBAO", cloudApiKey = ""))
        )
    }

    @Test
    fun ocr_stepfun_withKey_returnsStepfunOcrProvider() {
        val engine = VendorRouter.createOcrEngine(
            UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = "sk-1")
        )
        assertTrue(engine is StepfunOcrProvider)
    }

    @Test
    fun ocr_stepfun_missingKey_returnsNull() {
        assertNull(
            VendorRouter.createOcrEngine(UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = " "))
        )
    }

    // ---------- 转译路由 ----------

    @Test
    fun translate_doubao_withArkKey_returnsDoubaoTranslateClient() {
        val client = VendorRouter.createTranslateClient(
            UserSettings(cloudVendor = "DOUBAO", cloudApiKey = "ark-1")
        )
        assertTrue(client is DoubaoTranslateClient)
    }

    @Test
    fun translate_stepfun_withKey_returnsStepfunTranslateClient() {
        val client = VendorRouter.createTranslateClient(
            UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = "sk-1")
        )
        assertTrue(client is StepfunTranslateClient)
    }

    @Test
    fun translate_missingCredentials_returnsNull() {
        assertNull(
            VendorRouter.createTranslateClient(UserSettings(cloudVendor = "DOUBAO", cloudApiKey = ""))
        )
        assertNull(
            VendorRouter.createTranslateClient(UserSettings(cloudVendor = "STEPFUN", stepfunApiKey = ""))
        )
    }

    // ---------- 默认值契约 ----------

    @Test
    fun userSettings_defaults_doubaoVendorAndEmptyStepfunCredentials() {
        val defaults = UserSettings()
        assertEquals("DOUBAO", defaults.cloudVendor)
        assertEquals("", defaults.stepfunApiKey)
        assertEquals("tianmeinvsheng", defaults.stepfunVoice)
    }
}
