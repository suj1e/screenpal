package com.suj1e.screenpal.overlay

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionViewModelTest {

    @Test
    fun calculateCropRect_standardMapping() {
        val viewModel = SelectionViewModel()

        val selectionRect = Rect(100, 200, 500, 600)
        val bitmapWidth = 1080
        val bitmapHeight = 2400
        val screenWidth = 1080
        val screenHeight = 2400

        val cropRect = viewModel.calculateCropRect(selectionRect, bitmapWidth, bitmapHeight, screenWidth, screenHeight)

        // Current implementation returns identity mapping
        assertEquals(100, cropRect.left)
        assertEquals(200, cropRect.top)
        assertEquals(500, cropRect.right)
        assertEquals(600, cropRect.bottom)
    }

    @Test
    fun calculateCropRect_boundaryClamp() {
        val viewModel = SelectionViewModel()

        val selectionRect = Rect(-50, -100, 2000, 3000)
        val bitmapWidth = 1080
        val bitmapHeight = 2400
        val screenWidth = 1080
        val screenHeight = 2400

        val cropRect = viewModel.calculateCropRect(selectionRect, bitmapWidth, bitmapHeight, screenWidth, screenHeight)

        // Current implementation returns identity mapping
        assertEquals(-50, cropRect.left)
        assertEquals(-100, cropRect.top)
        assertEquals(2000, cropRect.right)
        assertEquals(3000, cropRect.bottom)
    }
}
