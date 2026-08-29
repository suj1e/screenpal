package com.suj1e.screenpal.overlay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 播报模式挂点接线契约（2026-08-29-broadcast-mode tasks 4）：
 * - Activity 在播报前从设置解析 [com.suj1e.screenpal.translate.BroadcastMode]
 *   （脏值回退由 fromStorageValue 承担）并作为 mode 入参传入管道；
 * - EXPLAIN 失败降级标注「AI 讲解不可用」，TRANSLATE 降级维持「翻译不可用」；
 * - 缺凭据（pipeline 为 null）时 EXPLAIN 一律视为需要 AI（讲解不可用），
 *   TRANSLATE 维持原语义（开关关或本就中文 → 直读无标注）；
 * - 双语主显复用既有 lastSpokenText 条件逻辑（模式无关）：讲解 ≠ 原文时
 *   主显讲解 + 原文小字。
 */
class SelectionOverlayBroadcastModeWiringTest {

    private val overlaySrc =
        File("src/main/java/com/suj1e/screenpal/overlay/SelectionOverlayActivity.kt").readText()

    private val broadcastBlock: String by lazy {
        val start = overlaySrc.indexOf("val outcome = if (pipeline != null)")
        val end = overlaySrc.indexOf("metaAnnotation(outcome", start)
        if (start == -1 || end == -1) overlaySrc else overlaySrc.substring(start, end)
    }

    // ---- mode 入参接线 ----

    @Test
    fun broadcast_modeResolvedFromSettings_viaFromStorageValue() {
        assertTrue(
            "播报模式必须经 BroadcastMode.fromStorageValue 从设置解析（脏值回退 TRANSLATE）",
            overlaySrc.contains("BroadcastMode.fromStorageValue(settings.broadcastMode)")
        )
    }

    @Test
    fun broadcast_callPassesModeToPipeline() {
        assertTrue(
            "pipeline.broadcast 必须传 translationEnabled 与 mode 两个具名入参",
            broadcastBlock.contains("translationEnabled = settings.translationEnabled,") &&
                broadcastBlock.contains("mode = mode")
        )
    }

    @Test
    fun broadcast_metaAnnotationReceivesMode() {
        assertTrue(
            "结果卡标注必须按 mode 区分语义：metaAnnotation(outcome, mode)",
            overlaySrc.contains("metaAnnotation(outcome, mode)")
        )
    }

    // ---- 缺凭据路径（pipeline == null）----

    @Test
    fun missingCredentials_explainMode_alwaysCountsAsNeedingAi() {
        assertTrue(
            "缺凭据时 EXPLAIN 模式必须一律落 FallbackOriginal（总是走 AI）",
            broadcastBlock.contains("mode == BroadcastMode.EXPLAIN ||")
        )
    }

    @Test
    fun missingCredentials_translateMode_keepsDirectSemantics() {
        assertTrue(
            "缺凭据时 TRANSLATE 模式必须保留「开关关或本就中文 → 直读」判断",
            broadcastBlock.contains("settings.translationEnabled &&") &&
                broadcastBlock.contains("ChineseHeuristic.isMostlyChinese(result.text)")
        )
    }

    // ---- 讲解失败标注语义 ----

    @Test
    fun explainFallbackAnnotation_copyExistsInSource() {
        assertTrue(
            "「AI 讲解不可用」标注文案必须存在于 metaAnnotation 映射",
            overlaySrc.contains("AI 讲解不可用")
        )
        assertTrue(
            "metaAnnotation FallbackOriginal 必须按 mode 分支",
            overlaySrc.contains("if (mode == BroadcastMode.EXPLAIN)")
        )
    }

    // ---- 双语主显（模式无关，既有逻辑护栏）----

    @Test
    fun bilingualMainDisplay_reusesLastSpokenText_contract() {
        assertTrue(
            "主显必须复用 pipeline.lastSpokenText 条件逻辑",
            overlaySrc.contains("pipeline?.lastSpokenText?.let { spoken ->") &&
                overlaySrc.contains("if (spoken != result.text)")
        )
        assertTrue(
            "原文小字必须以「原文：」前缀附在 meta（截断至 120 字符）",
            overlaySrc.contains("\" · 原文：\"") &&
                overlaySrc.contains("result.text.take(120)")
        )
    }
}
