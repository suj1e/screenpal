package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrEngineTest {

    @Test
    fun ocrResult_dataClass_creation() {
        val result = OcrResult(
            text = "Hello",
            confidence = 0.9f,
            blocks = listOf(TextBlock("Hello", 0.9f, android.graphics.Rect(0, 0, 100, 50)))
        )

        assertEquals("Hello", result.text)
        assertEquals(0.9f, result.confidence, 0.01f)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun textBlock_dataClass_creation() {
        val block = TextBlock("Test", 0.8f, android.graphics.Rect(10, 20, 110, 70))
        assertEquals("Test", block.text)
        assertEquals(0.8f, block.confidence, 0.01f)
        assertEquals(android.graphics.Rect(10, 20, 110, 70), block.boundingBox)
    }
}
