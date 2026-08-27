package com.suj1e.screenpal.overlay

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests pinning the view->bitmap coordinate mapping used by the
 * lasso confirmation flow. The lasso rewrite keeps SelectionViewModel's
 * calculateCropRect signature and math unchanged; these tests lock that
 * contract in.
 */
@RunWith(RobolectricTestRunner::class)
class SelectionCropRectMappingTest {

    private val viewModel = SelectionViewModel()

    @Test
    fun identity_scale_maps_rect_unchanged() {
        val selection = Rect(10, 20, 110, 220)

        val crop = viewModel.calculateCropRect(
            selection, bitmapWidth = 400, bitmapHeight = 800, screenWidth = 400, screenHeight = 800
        )

        assertEquals(Rect(10, 20, 110, 220), crop)
    }

    @Test
    fun uniform_scale_multiplies_both_axes() {
        // bitmap is 2x the view on both axes
        val crop = viewModel.calculateCropRect(
            Rect(10, 20, 30, 40), bitmapWidth = 200, bitmapHeight = 400, screenWidth = 100, screenHeight = 200
        )

        assertEquals(Rect(20, 40, 60, 80), crop)
    }

    @Test
    fun non_uniform_scale_uses_independent_axis_scales() {
        // scaleX = 1080/1080 = 1, scaleY = 2400/600 = 4
        val crop = viewModel.calculateCropRect(
            Rect(100, 50, 200, 100), bitmapWidth = 1080, bitmapHeight = 2400, screenWidth = 1080, screenHeight = 600
        )

        assertEquals(Rect(100, 200, 200, 400), crop)
    }

    @Test
    fun negative_coordinates_clamp_to_zero() {
        // Lasso strokes are clamped to the view, but guard the contract anyway.
        val crop = viewModel.calculateCropRect(
            Rect(-10, -20, 50, 60), bitmapWidth = 200, bitmapHeight = 200, screenWidth = 100, screenHeight = 100
        )

        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(100, crop.right)
        assertEquals(120, crop.bottom)
    }

    @Test
    fun oversized_selection_clamps_to_bitmap_bounds() {
        val crop = viewModel.calculateCropRect(
            Rect(0, 0, 500, 500), bitmapWidth = 200, bitmapHeight = 300, screenWidth = 100, screenHeight = 100
        )

        assertEquals(Rect(0, 0, 200, 300), crop)
    }

    @Test
    fun full_screen_selection_covers_whole_bitmap() {
        val crop = viewModel.calculateCropRect(
            Rect(0, 0, 1080, 2400), bitmapWidth = 1080, bitmapHeight = 2400, screenWidth = 1080, screenHeight = 2400
        )

        assertEquals(Rect(0, 0, 1080, 2400), crop)
    }
}
