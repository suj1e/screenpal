package com.suj1e.screenpal.overlay

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECT 拖拽归一化契约（2026-08-29-selection-mode）：任意方向拖拽都归一化为
 * min/max 包围矩形（Rect(start,end) 语义），单点退化为零尺寸矩形并被共享的
 * 48dp 门槛拒选；细长条与套索同语义（宽或高达标即有效）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionRectNormalizationTest {

    private val minSizePx = SelectionOverlayActivity.MIN_SELECTION_SIZE_DP * 2f // density=2 -> 96px

    @Test
    fun normalizeRect_topLeftToBottomRight_isIdentity() {
        assertEquals(
            RectF(100f, 100f, 250f, 300f),
            SelectionOverlayActivity.normalizeRect(100f, 100f, 250f, 300f)
        )
    }

    @Test
    fun normalizeRect_rightToLeft_dragSwapsHorizontally() {
        // 用户从右往左拖：起点 x > 终点 x。
        assertEquals(
            RectF(100f, 100f, 250f, 300f),
            SelectionOverlayActivity.normalizeRect(250f, 100f, 100f, 300f)
        )
    }

    @Test
    fun normalizeRect_bottomToTop_dragSwapsVertically() {
        // 用户从下往上拖：起点 y > 终点 y。
        assertEquals(
            RectF(100f, 100f, 250f, 300f),
            SelectionOverlayActivity.normalizeRect(100f, 300f, 250f, 100f)
        )
    }

    @Test
    fun normalizeRect_fullyReversed_dragSwapsBothAxes() {
        // 从右下往左上拖。
        assertEquals(
            RectF(100f, 100f, 250f, 300f),
            SelectionOverlayActivity.normalizeRect(250f, 300f, 100f, 100f)
        )
    }

    @Test
    fun normalizeRect_singlePoint_zeroSize() {
        val rect = SelectionOverlayActivity.normalizeRect(40f, 60f, 40f, 60f)

        assertEquals(RectF(40f, 60f, 40f, 60f), rect)
        assertEquals(0f, rect.width(), 0.001f)
        assertEquals(0f, rect.height(), 0.001f)
    }

    @Test
    fun gate_singlePoint_rect_isRejected() {
        val rect = SelectionOverlayActivity.normalizeRect(40f, 60f, 40f, 60f)

        assertFalse(SelectionOverlayActivity.isSelectionLargeEnough(rect, minSizePx))
    }

    @Test
    fun gate_smallRect_belowThreshold_isRejected() {
        // 40x40px (20dp @ density 2) < 48dp。
        val rect = SelectionOverlayActivity.normalizeRect(250f, 300f, 210f, 260f)

        assertFalse(SelectionOverlayActivity.isSelectionLargeEnough(rect, minSizePx))
    }

    @Test
    fun gate_thinRect_stripIsValid_likeLasso() {
        // 300x20px 细横条：与套索同语义，宽或高达标即有效。
        val rect = SelectionOverlayActivity.normalizeRect(10f, 30f, 310f, 10f)

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(rect, minSizePx))
    }

    @Test
    fun gate_exactlyMinSize_rectIsValid() {
        val rect = SelectionOverlayActivity.normalizeRect(0f, 96f, 96f, 0f)

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(rect, minSizePx))
    }
}
