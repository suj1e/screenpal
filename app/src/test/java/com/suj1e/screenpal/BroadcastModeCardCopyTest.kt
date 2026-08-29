package com.suj1e.screenpal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「播报模式」card contract (2026-08-29-broadcast-mode): two radio options
 * (翻译朗读 TRANSLATE / AI 讲解 EXPLAIN), the card sits directly after the
 *「中文播报」card, wiring flows through MainViewModel::setBroadcastMode, and
 * both cards clarify the switch/mode relationship (switch only governs the
 * translate mode; explain mode always goes to AI).
 */
class BroadcastModeCardCopyTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    @Test
    fun card_hasTitleAndTwoOptions() {
        assertTrue("Card title「播报模式」must exist", mainActivitySrc.contains("播报模式"))
        assertTrue("TRANSLATE option copy「翻译朗读」must exist", mainActivitySrc.contains("翻译朗读"))
        assertTrue("EXPLAIN option copy「AI 讲解」must exist", mainActivitySrc.contains("AI 讲解"))
        assertTrue(
            "TRANSLATE option must describe 外文转中文原样朗读",
            mainActivitySrc.contains("外文转中文原样朗读")
        )
        assertTrue(
            "EXPLAIN option must describe 问 AI 这是什么",
            mainActivitySrc.contains("问 AI 这是什么")
        )
    }

    @Test
    fun card_isPlacedAfterChineseBroadcastCard() {
        val mainScreenBody = mainActivitySrc.substringAfter("fun MainScreen(")
        val toggleCardPos = mainScreenBody.indexOf("ToggleCard(")
        val broadcastPos = mainScreenBody.indexOf("BroadcastModeCard(")
        assertTrue("ToggleCard(中文播报) must render before BroadcastModeCard", toggleCardPos in 0 until broadcastPos)
    }

    @Test
    fun card_wiredToViewModelSetter_withStorageValues() {
        assertTrue(
            "BroadcastModeCard must persist via viewModel::setBroadcastMode",
            mainActivitySrc.contains("viewModel::setBroadcastMode")
        )
        assertTrue(
            "TRANSLATE storage value must be wired as an option",
            mainActivitySrc.contains("\"TRANSLATE\"")
        )
        assertTrue(
            "EXPLAIN storage value must be wired as an option",
            mainActivitySrc.contains("\"EXPLAIN\"")
        )
    }

    @Test
    fun card_descriptionsClarifySwitchScope() {
        assertTrue(
            "播报模式 card description must state switch governs translate mode",
            mainActivitySrc.contains("翻译模式受「中文播报」开关控制")
        )
        assertTrue(
            "播报模式 card description must state explain mode always goes to AI",
            mainActivitySrc.contains("讲解模式总是走 AI")
        )
        assertTrue(
            "中文播报 card description must clarify it only governs translate mode",
            mainActivitySrc.contains("仅作用于翻译朗读模式")
        )
    }
}
