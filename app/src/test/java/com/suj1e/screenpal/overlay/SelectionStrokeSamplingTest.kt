package com.suj1e.screenpal.overlay

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the lasso stroke sampling filter (8dp minimum distance).
 * Pure-function tests: no Activity instance, no rendering involved.
 */
@RunWith(RobolectricTestRunner::class)
class SelectionStrokeSamplingTest {

    private val minDistancePx = SelectionOverlayActivity.MIN_SAMPLE_DISTANCE_DP * 2f // density=2 -> 16px

    @Test
    fun min_sample_distance_constant_is_8dp() {
        assertEquals(8f, SelectionOverlayActivity.MIN_SAMPLE_DISTANCE_DP, 0.001f)
    }

    @Test
    fun first_point_on_empty_stroke_is_always_accepted() {
        val candidate = PointF(100f, 100f)

        val result = SelectionOverlayActivity.filterStrokePoints(emptyList(), candidate, minDistancePx)

        assertEquals(1, result.size)
        assertEquals(100f, result[0].x, 0.001f)
        assertEquals(100f, result[0].y, 0.001f)
    }

    @Test
    fun point_closer_than_8dp_to_last_point_is_discarded() {
        val stroke = listOf(PointF(100f, 100f))
        // 10px away < 16px (8dp @ density 2)
        val candidate = PointF(106f, 108f)

        val result = SelectionOverlayActivity.filterStrokePoints(stroke, candidate, minDistancePx)

        assertEquals(1, result.size)
        assertEquals(stroke[0], result[0])
    }

    @Test
    fun point_at_exactly_8dp_is_accepted() {
        val stroke = listOf(PointF(100f, 100f))
        // exactly 16px away (>= semantics)
        val candidate = PointF(116f, 100f)

        val result = SelectionOverlayActivity.filterStrokePoints(stroke, candidate, minDistancePx)

        assertEquals(2, result.size)
        assertEquals(candidate, result[1])
    }

    @Test
    fun point_beyond_8dp_is_appended() {
        val stroke = listOf(PointF(0f, 0f))
        val candidate = PointF(50f, 50f)

        val result = SelectionOverlayActivity.filterStrokePoints(stroke, candidate, minDistancePx)

        assertEquals(2, result.size)
        assertTrue(result.last() === candidate || (result.last().x == 50f && result.last().y == 50f))
    }

    @Test
    fun long_jittery_chain_keeps_only_points_far_enough_apart() {
        // Simulate MOVE events with sub-8dp jitter around a moving finger.
        var stroke: List<PointF> = emptyList()
        val minPx = minDistancePx
        val moves = listOf(
            0f to 0f,      // accepted (first)
            8f to 6f,      // 10px away -> discarded
            16f to 0f,     // 16px from last accepted -> accepted
            20f to 2f,     // ~5.7px from last accepted -> discarded
            32f to 0f,     // 16px from last accepted -> accepted
            32.1f to 0.1f, // discarded
            48f to 0f      // accepted
        )
        for ((x, y) in moves) {
            stroke = SelectionOverlayActivity.filterStrokePoints(stroke, PointF(x, y), minPx)
        }

        assertEquals(4, stroke.size)
        assertEquals(0f, stroke[0].x, 0.001f)
        assertEquals(16f, stroke[1].x, 0.001f)
        assertEquals(32f, stroke[2].x, 0.001f)
        assertEquals(48f, stroke[3].x, 0.001f)
    }
}
