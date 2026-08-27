package com.suj1e.screenpal.overlay

import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for lasso UP judgment: bounding box of the sampled stroke and
 * the minimum-size gate (48dp wide OR tall; single taps are invalid).
 */
@RunWith(RobolectricTestRunner::class)
class SelectionLassoBoundsTest {

    private val minSizePx = SelectionOverlayActivity.MIN_SELECTION_SIZE_DP * 2f // density=2 -> 96px

    @Test
    fun min_selection_size_constant_is_48dp() {
        assertEquals(48f, SelectionOverlayActivity.MIN_SELECTION_SIZE_DP, 0.001f)
    }

    @Test
    fun computeBounds_L_shaped_stroke_returns_correct_bounding_box() {
        // L drawn as: down the left leg, then right along the bottom.
        val points = listOf(
            PointF(100f, 100f),
            PointF(100f, 200f),
            PointF(100f, 300f),
            PointF(220f, 300f),
            PointF(250f, 300f)
        )

        val bounds = SelectionOverlayActivity.computeBounds(points)

        assertEquals(RectF(100f, 100f, 250f, 300f), bounds)
    }

    @Test
    fun computeBounds_empty_stroke_returns_null() {
        assertNull(SelectionOverlayActivity.computeBounds(emptyList()))
    }

    @Test
    fun computeBounds_single_point_returns_zero_size_rect() {
        val bounds = SelectionOverlayActivity.computeBounds(listOf(PointF(40f, 60f)))

        assertEquals(RectF(40f, 60f, 40f, 60f), bounds)
        assertEquals(0f, bounds!!.width(), 0.001f)
        assertEquals(0f, bounds.height(), 0.001f)
    }

    @Test
    fun gate_single_point_is_invalid() {
        val bounds = SelectionOverlayActivity.computeBounds(listOf(PointF(40f, 60f)))!!

        assertFalse(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }

    @Test
    fun gate_small_blob_below_threshold_is_invalid() {
        // 40x40px (20dp x 20dp @ density 2) < 48dp
        val bounds = RectF(0f, 0f, 40f, 40f)

        assertFalse(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }

    @Test
    fun gate_thin_horizontal_strip_is_valid() {
        // 300x20px: width >= min, height < min -> still valid (细长条)
        val bounds = RectF(10f, 10f, 310f, 30f)

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }

    @Test
    fun gate_thin_vertical_strip_is_valid() {
        // 20x300px
        val bounds = RectF(10f, 10f, 30f, 310f)

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }

    @Test
    fun gate_exactly_min_size_is_valid() {
        val bounds = RectF(0f, 0f, 96f, 96f)

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }

    @Test
    fun gate_normal_circle_stroke_is_valid() {
        val bounds = SelectionOverlayActivity.computeBounds(
            listOf(PointF(0f, 50f), PointF(50f, 0f), PointF(100f, 50f), PointF(50f, 100f))
        )!!

        assertTrue(SelectionOverlayActivity.isSelectionLargeEnough(bounds, minSizePx))
    }
}
