package com.suj1e.screenpal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「框选方式」card contract (2026-08-29-selection-mode): two radio options
 * (随手画 LASSO / 长方形 RECT), the card sits directly after the「StepFun 云服务」
 * card, and the wiring flows through MainViewModel::setSelectionMode.
 */
class SelectionModeCardCopyTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    @Test
    fun card_hasTitleAndTwoOptions() {
        assertTrue("Card title「框选方式」must exist", mainActivitySrc.contains("框选方式"))
        assertTrue("LASSO option copy「随手画」must exist", mainActivitySrc.contains("随手画"))
        assertTrue("RECT option copy「长方形」must exist", mainActivitySrc.contains("长方形"))
    }

    @Test
    fun card_isPlacedAfterStepfunCard_andBeforeTtsCard() {
        val mainScreenBody = mainActivitySrc.substringAfter("fun MainScreen(")
        val stepfunPos = mainScreenBody.indexOf("StepfunCloudSettingsCard(")
        val selectionPos = mainScreenBody.indexOf("SelectionModeCard(")
        val ttsPos = mainScreenBody.indexOf("TtsSettingsCard(")
        assertTrue("StepfunCloudSettingsCard must render before SelectionModeCard", stepfunPos in 0 until selectionPos)
        assertTrue("SelectionModeCard must render before TtsSettingsCard", selectionPos in 0 until ttsPos)
    }

    @Test
    fun card_wiredToViewModelSetter() {
        assertTrue(
            "SelectionModeCard must persist via viewModel::setSelectionMode",
            mainActivitySrc.contains("viewModel::setSelectionMode")
        )
        assertTrue(
            "LASSO storage value must be wired as an option",
            mainActivitySrc.contains("\"LASSO\"")
        )
        assertTrue(
            "RECT storage value must be wired as an option",
            mainActivitySrc.contains("\"RECT\"")
        )
    }
}
