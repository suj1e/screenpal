package com.suj1e.screenpal.overlay

import com.suj1e.screenpal.translate.BroadcastOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 结果卡 meta 标注映射：翻译发生 → 「AI 转译」；降级 → 「翻译不可用」；直读 → 不标注。
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
}
