package com.suj1e.screenpal.overlay

import com.suj1e.screenpal.ocr.StepfunOcrProvider
import com.suj1e.screenpal.translate.StepfunTranslateClient
import com.suj1e.screenpal.util.UserSettings
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云侧挂点直连 StepFun（2026-08-29-stepfun-only）：
 * OCR 的 CLOUD 模式 / HYBRID 云侧与「中文播报」的 AI 转译客户端，按 StepFun Key
 * 是否非空直连构造；缺 Key 返回 null（OCR 落端侧 Paddle / 转译播原文，降级语义不变）。
 */
class SelectionOverlayStepfunWiringTest {

    // ---------- 云 OCR ----------

    @Test
    fun cloudOcr_withKey_returnsStepfunOcrProvider() {
        val engine = SelectionOverlayActivity.cloudOcrEngine(UserSettings(stepfunApiKey = "sk-step-1"))
        assertTrue(engine is StepfunOcrProvider)
    }

    @Test
    fun cloudOcr_missingKey_returnsNull() {
        assertNull(SelectionOverlayActivity.cloudOcrEngine(UserSettings(stepfunApiKey = "")))
        assertNull(SelectionOverlayActivity.cloudOcrEngine(UserSettings(stepfunApiKey = " ")))
    }

    // ---------- AI 转译 ----------

    @Test
    fun translate_withKey_returnsStepfunTranslateClient() {
        val client = SelectionOverlayActivity.translateClient(UserSettings(stepfunApiKey = "sk-step-1"))
        assertTrue(client is StepfunTranslateClient)
    }

    @Test
    fun translate_missingKey_returnsNull() {
        assertNull(SelectionOverlayActivity.translateClient(UserSettings(stepfunApiKey = "")))
        assertNull(SelectionOverlayActivity.translateClient(UserSettings(stepfunApiKey = " ")))
    }
}
