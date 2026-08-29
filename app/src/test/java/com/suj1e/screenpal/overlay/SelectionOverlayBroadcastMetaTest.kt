package com.suj1e.screenpal.overlay

import com.suj1e.screenpal.translate.BroadcastMode
import com.suj1e.screenpal.translate.BroadcastOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 结果卡 meta 标注映射：翻译发生 → 「AI 转译」；降级 → 「翻译不可用」；直读 → 不标注。
 * 2026-08-29-broadcast-mode 起 FallbackOriginal 按播报模式区分语义：TRANSLATE 降级
 * 是「翻译不可用」，EXPLAIN 降级是「AI 讲解不可用」——同一 Outcome、两种失败含义。
 */
class SelectionOverlayBroadcastMetaTest {

    @Test
    fun translated_annotatesAiTranslation() {
        assertEquals(" · AI 转译", SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Translated))
    }

    @Test
    fun fallbackOriginal_annotatesTranslationUnavailable() {
        assertEquals(" · 翻译不可用", SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.FallbackOriginal))
    }

    @Test
    fun direct_annotatesNothing() {
        assertNull(SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Direct))
    }

    // ---- 播报模式矩阵（2026-08-29-broadcast-mode tasks 4）----

    @Test
    fun matrix_translated_annotatesAiTranslation_inBothModes() {
        assertEquals(
            " · AI 转译",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Translated, BroadcastMode.TRANSLATE)
        )
        assertEquals(
            " · AI 转译",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Translated, BroadcastMode.EXPLAIN)
        )
    }

    @Test
    fun matrix_explained_annotatesAiExplain_inBothModes() {
        assertEquals(
            " · AI 讲解",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.EXPLAINED, BroadcastMode.EXPLAIN)
        )
        assertEquals(
            " · AI 讲解",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.EXPLAINED, BroadcastMode.TRANSLATE)
        )
    }

    @Test
    fun matrix_fallbackOriginal_semanticsSplitByMode() {
        assertEquals(
            "TRANSLATE 降级必须是「翻译不可用」",
            " · 翻译不可用",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.FallbackOriginal, BroadcastMode.TRANSLATE)
        )
        assertEquals(
            "EXPLAIN 降级必须是「AI 讲解不可用」（讲解失败 ≠ 翻译失败）",
            " · AI 讲解不可用",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.FallbackOriginal, BroadcastMode.EXPLAIN)
        )
    }

    @Test
    fun matrix_direct_annotatesNothing_inBothModes() {
        assertNull(SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Direct, BroadcastMode.TRANSLATE))
        assertNull(SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.Direct, BroadcastMode.EXPLAIN))
    }

    @Test
    fun defaultMode_isTranslate_backCompat() {
        assertEquals(
            "缺省 mode 必须保持 TRANSLATE 语义（既有调用点零改动）",
            " · 翻译不可用",
            SelectionOverlayActivity.metaAnnotation(BroadcastOutcome.FallbackOriginal)
        )
    }
}
