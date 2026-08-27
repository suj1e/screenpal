package com.suj1e.screenpal.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.graphics.Rect

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaddleOcrProviderTest {

    @Test
    fun get_instance_returns_lazy_singleton() {
        val context = RuntimeEnvironment.getApplication()
        val first = PaddleOcrProvider.getInstance(context)
        val second = PaddleOcrProvider.getInstance(context)
        assertSame(first, second)
    }

    @Test
    fun detects_inclusive_box_maps_to_exclusive_rect() {
        // DetectedBox uses inclusive right/bottom; android.graphics.Rect is
        // exclusive, so the provider must expand by 1px on both edges.
        val rect = PaddleOcrProvider.toRect(DbPostProcessor.DetectedBox(10, 5, 29, 14))
        assertEquals(Rect(10, 5, 30, 15), rect)
    }
}
