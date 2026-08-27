package com.suj1e.screenpal.ocr.paddle

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClsProcessorTest {

    private val processor = ClsProcessor()

    @Test
    fun upright_text_not_rotated() {
        assertFalse(processor.needsRotation(floatArrayOf(0.9f, 0.1f)))
    }

    @Test
    fun upside_down_text_rotated() {
        assertTrue(processor.needsRotation(floatArrayOf(0.2f, 0.8f)))
    }

    @Test
    fun tie_breaks_to_no_rotation() {
        assertFalse(processor.needsRotation(floatArrayOf(0.5f, 0.5f)))
    }

    @Test
    fun rotate180_swaps_pixels() {
        val bmp = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.RED)
        bmp.setPixel(1, 0, Color.BLUE)

        val rotated = processor.rotate180(bmp)

        assertEquals(2, rotated.width)
        assertEquals(1, rotated.height)
        assertEquals(Color.BLUE, rotated.getPixel(0, 0))
        assertEquals(Color.RED, rotated.getPixel(1, 0))
    }
}
