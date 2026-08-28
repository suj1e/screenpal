package com.suj1e.screenpal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings copy contract (vendor-separation restructure): the CLOUD TTS slot
 * and cloud OCR slot are vendor-routed (豆包/StepFun), all vendor credentials
 * live in the「在线服务商」card (one section per vendor, never mixed), and no
 * Google copy remains anywhere.
 */
class TtsSettingsCardCopyTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    @Test
    fun cloudSlot_isVendorRoutedNotHardcodedDoubao() {
        assertTrue(
            "TTS CLOUD slot must be dynamic on the selected vendor",
            mainActivitySrc.contains("在线语音（豆包") && mainActivitySrc.contains("在线语音（StepFun")
        )
        assertFalse(
            "TTS slot must not hardcode the doubao vendor",
            mainActivitySrc.contains("豆包在线语音（火山引擎，需 AppID+Token）")
        )
        assertFalse(
            "Google Cloud TTS copy must be gone",
            mainActivitySrc.contains("Google Cloud TTS")
        )
    }

    @Test
    fun vendorCard_ownsAllCredentials_splitByVendor() {
        assertTrue(
            "Vendor card must state what the selected vendor covers",
            mainActivitySrc.contains("在线语音播报 · 云 OCR 增强 · AI 转译")
        )
        assertTrue(
            "Doubao credentials live in the vendor card",
            mainActivitySrc.contains("火山语音 AppID（在线 TTS）") &&
                mainActivitySrc.contains("火山方舟 API Key（视觉 OCR + AI 转译）")
        )
        assertTrue(
            "StepFun credentials live in the vendor card",
            mainActivitySrc.contains("StepFun API Key（TTS + 视觉 OCR + 转译共用）")
        )
        // The OCR settings card must NOT hold vendor credentials anymore.
        assertFalse(
            "Ark key input must not live in the OCR card",
            mainActivitySrc.contains("火山方舟 API Key（云 OCR + 转译共用）")
        )
    }

    @Test
    fun piperAndSystemCopy_stillPresent() {
        assertTrue(mainActivitySrc.contains("Piper 离线"))
        assertTrue(mainActivitySrc.contains("系统 TTS（兜底）"))
    }
}
