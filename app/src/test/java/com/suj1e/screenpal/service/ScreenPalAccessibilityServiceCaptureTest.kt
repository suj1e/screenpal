package com.suj1e.screenpal.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * captureCurrentScreen 边界/异常契约（2026-08-29-a11y-screenshot）：
 * - 限流（INTERVAL_TIME_SHORT）→ onResult(null)（路由层 Toast 提示重试）；
 * - API < 30（无 takeScreenshot）→ 立即 onResult(null)；
 * - hardware buffer 为 null → onResult(null)，不抛异常；
 * - 回调经主线程 executor 触发且只回调一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenPalAccessibilityServiceCaptureTest {

    class DrivableService : ScreenPalAccessibilityService() {
        public override fun onServiceConnected() = super.onServiceConnected()
    }

    private fun connectedService(): ScreenPalAccessibilityService {
        val service = Robolectric.buildService(DrivableService::class.java).create().get()
        service.onServiceConnected()
        return service
    }

    private fun flushMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun capture_returnsNull_onIntervalTimeShortThrottle() {
        val service = connectedService()
        val shadow = shadowOf(service) as ShadowAccessibilityService
        shadow.setTakeScreenshotErrorCode(
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
        )

        var result: Bitmap? = NOT_CALLED
        var callbackCount = 0
        service.captureCurrentScreen {
            callbackCount++
            result = it
        }
        flushMainLooper()

        assertEquals("失败也必须且只回调一次", 1, callbackCount)
        assertNull("限流必须返回 null，由路由层 Toast 提示重试", result)
    }

    @Test
    @Config(sdk = [29])
    fun capture_returnsNullImmediately_belowApi30() {
        val service = Robolectric.buildService(DrivableService::class.java).create().get()
        service.onServiceConnected()

        var result: Bitmap? = NOT_CALLED
        var callbackCount = 0
        service.captureCurrentScreen {
            callbackCount++
            result = it
        }
        flushMainLooper()

        assertEquals(1, callbackCount)
        assertNull("API<30 无 takeScreenshot，必须立即失败回落", result)
    }

    @Test
    fun capture_callbackFiresOnce_evenWhenConversionYieldsNothing() {
        // shadow 成功路径构造 1x1 硬件 buffer；JVM 上 wrap/copy 可能为 null，
        // 契约：不得抛异常、回调恰好一次、结果可空。
        val service = connectedService()

        var callbackCount = 0
        var result: Bitmap? = NOT_CALLED
        service.captureCurrentScreen {
            callbackCount++
            result = it
        }
        flushMainLooper()

        assertEquals(1, callbackCount)
        assertTrue(result === null || result is Bitmap)
    }

    @Test
    fun softwareBitmap_nullBuffer_yieldsNullWithoutThrowing() {
        // buffer null 容错：构造合法 ScreenshotResult 后把 buffer 字段置空
        // （构造器本身禁止 null，防御性场景用反射模拟），软位图必须返回 null。
        val result = ReflectionHelpers.callConstructor(
            AccessibilityService.ScreenshotResult::class.java,
            ClassParameter.from(android.hardware.HardwareBuffer::class.java, NOT_CALLED_BUFFER),
            ClassParameter.from(ColorSpace::class.java, ColorSpace.get(ColorSpace.Named.SRGB)),
            ClassParameter.from(Long::class.java, 0L)
        )
        ReflectionHelpers.setField(result, "mHardwareBuffer", null)

        val service = connectedService()
        val converted = ReflectionHelpers.callInstanceMethod<Bitmap?>(
            service,
            "softwareBitmapFrom",
            ClassParameter.from(AccessibilityService.ScreenshotResult::class.java, result)
        )
        assertNull(converted)
    }

    companion object {
        /** 哨兵值：区分「回调未触发」与「回调给了 null」。 */
        private val NOT_CALLED = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        /** 构造 ScreenshotResult 用的合法占位 buffer（后续按用例改写或使用）。 */
        private val NOT_CALLED_BUFFER = android.hardware.HardwareBuffer.create(
            1, 1, android.hardware.HardwareBuffer.RGBA_8888, 1, 0
        )
    }
}
