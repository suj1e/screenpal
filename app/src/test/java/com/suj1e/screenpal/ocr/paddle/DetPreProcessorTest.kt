package com.suj1e.screenpal.ocr.paddle

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetPreProcessorTest {

    private val processor = DetPreProcessor()

    private fun bitmap(w: Int, h: Int, pixel: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(pixel)
        }

    @Test
    fun large_image_scaled_to_960_limit_and_multiple_of_32() {
        val size = DetPreProcessor.computeResize(1920, 1080)
        assertTrue("width ${size.first} > 960", size.first <= 960)
        assertTrue("height ${size.second} > 960", size.second <= 960)
        assertEquals(0, size.first % 32)
        assertEquals(0, size.second % 32)
    }

    @Test
    fun small_image_kept_under_limit_and_multiple_of_32() {
        val size = DetPreProcessor.computeResize(100, 50)
        assertTrue(size.first in 1..960)
        assertTrue(size.second in 1..960)
        assertEquals(0, size.first % 32)
        assertEquals(0, size.second % 32)
    }

    @Test
    fun orientation_preserved_wide_stays_wide() {
        val wide = DetPreProcessor.computeResize(1920, 1080)
        assertTrue(wide.first > wide.second)
        val tall = DetPreProcessor.computeResize(1080, 1920)
        assertTrue(tall.second > tall.first)
    }

    @Test
    fun output_tensor_layout_is_nchw_rgb_normalized() {
        // 32x32 image (det input must stay a multiple of 32): red pixel at
        // (0,0), green at (1,0), blue at (0,1), the rest white.
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        bmp.setPixel(0, 0, Color.rgb(255, 0, 0))
        bmp.setPixel(1, 0, Color.rgb(0, 255, 0))
        bmp.setPixel(0, 1, Color.rgb(0, 0, 255))

        val input = processor.process(bmp)

        assertEquals(32, input.width)
        assertEquals(32, input.height)
        assertEquals(32 * 32 * 3, input.data.size)

        fun channel(c: Int, x: Int, y: Int): Float =
            input.data[c * input.height * input.width + y * input.width + x]

        // Normalization: (v/255 - mean) / std with ImageNet stats.
        val red = (1f - DetPreProcessor.MEAN_R) / DetPreProcessor.STD_R
        val green = (1f - DetPreProcessor.MEAN_G) / DetPreProcessor.STD_G
        val blue = (1f - DetPreProcessor.MEAN_B) / DetPreProcessor.STD_B

        assertEquals(red, channel(0, 0, 0), 1e-4f)
        // Red pixel (0,0): G channel = (0/255 - meanG) / stdG
        assertEquals((0f - DetPreProcessor.MEAN_G) / DetPreProcessor.STD_G, channel(1, 0, 0), 1e-4f)
        // green pixel (1,0): R -> (0-0.485)/0.229, G -> red value, B -> (0-0.406)/0.225
        assertEquals(green, channel(1, 1, 0), 1e-4f)
        assertEquals(blue, channel(2, 0, 1), 1e-4f)
        assertEquals(red, channel(0, 1, 1), 1e-4f)
        assertEquals(green, channel(1, 1, 1), 1e-4f)
        assertEquals(blue, channel(2, 1, 1), 1e-4f)
    }

    @Test
    fun ratio_fields_reflect_resize() {
        val bmp = bitmap(100, 50, Color.WHITE)
        val input = processor.process(bmp)
        // ratio = scaled / original (multiply ratio to go original -> scaled)
        assertEquals(input.width.toFloat() / 100f, input.ratioX, 1e-3f)
        assertEquals(input.height.toFloat() / 50f, input.ratioY, 1e-3f)
    }
}
