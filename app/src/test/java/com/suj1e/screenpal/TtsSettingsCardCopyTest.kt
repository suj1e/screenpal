package com.suj1e.screenpal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings copy contract (StepFun-only, 2026-08-29-stepfun-only): the
 *「StepFun 云服务」card is the single credential section (API Key + voice always
 * visible), CLOUD TTS/OCR slots are fixed to StepFun copy, and no legacy vendor
 * copy (豆包/火山/方舟) remains anywhere in the settings UI.
 */
class TtsSettingsCardCopyTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    @Test
    fun stepfunCloudCard_isTheSingleCredentialSection() {
        assertTrue(
            "必须存在「StepFun 云服务」卡标题",
            mainActivitySrc.contains("StepFun 云服务")
        )
        assertTrue(
            "说明行必须声明一把 API Key 包办三项在线能力",
            mainActivitySrc.contains("一把 API Key 包办：在线语音播报 · 云 OCR 增强 · AI 转译")
        )
        assertTrue(
            "StepFun API Key 输入必须常显（无条件渲染）",
            mainActivitySrc.contains("StepFun API Key（TTS + 视觉 OCR + 转译共用）")
        )
        assertTrue(
            "音色输入必须常显（无条件渲染）",
            mainActivitySrc.contains("音色（voice，默认 tianmeinvsheng）")
        )
    }

    @Test
    fun cloudSlots_fixedToStepFunCopy() {
        assertTrue(
            "TTS CLOUD 槽位固定 StepFun 文案",
            mainActivitySrc.contains("在线语音（StepFun，凭据在「StepFun 云服务」）")
        )
        assertTrue(
            "OCR 云端选项固定 StepFun 文案",
            mainActivitySrc.contains("仅云端 StepFun 视觉（凭据在「StepFun 云服务」）")
        )
        assertTrue(
            "OCR 混合模式固定 StepFun 文案",
            mainActivitySrc.contains("混合模式：端侧优先，低置信度走云端 StepFun")
        )
        assertTrue(
            "「中文播报」描述固定 StepFun 转译语义",
            mainActivitySrc.contains("外文经 StepFun 转译为简体中文播报")
        )
    }

    @Test
    fun noLegacyVendorCopy_inSettingsUi() {
        assertFalse("豆包单选/凭据区必须删除", mainActivitySrc.contains("豆包"))
        assertFalse("火山凭据输入必须删除", mainActivitySrc.contains("火山"))
        assertFalse("OCR 卡不得再含方舟 Key 输入", mainActivitySrc.contains("方舟"))
        assertFalse("旧「在线服务商」卡标题必须删除", mainActivitySrc.contains("在线服务商"))
        assertFalse("Google Cloud TTS copy must remain absent", mainActivitySrc.contains("Google Cloud TTS"))
    }

    @Test
    fun piperAndSystemCopy_stillPresent() {
        assertTrue(mainActivitySrc.contains("Piper 离线"))
        assertTrue(mainActivitySrc.contains("系统 TTS（兜底）"))
    }
}
