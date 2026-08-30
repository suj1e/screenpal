package com.suj1e.screenpal.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * 静默截屏通道（2026-08-29-a11y-screenshot）。
 *
 * 系统绑定的无障碍服务：开启后（API 30+）可直接 takeScreenshot，
 * 免去 MediaProjection 授权弹窗。仅订阅 typeWindowsChanged（最小事件集），
 * 不消费任何无障碍事件；实例随系统连接/解绑增减，[isRunning] 与
 * 静态 [instance] 是 FloatingWindowService 截屏路由的回落依据。
 *
 * open 仅为单测驱动受保护的系统生命周期回调（DrivableService）。
 */
open class ScreenPalAccessibilityService(
    /** Injectable so tests can capture synchronously (delay = 0). */
    private val captureDelayMs: Long = CAPTURE_DELAY_MS
) : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * 静默截取当前屏幕（API 30+）。
     *
     * takeScreenshot(DEFAULT_DISPLAY) → hardware buffer → [Bitmap.wrapHardwareBuffer]
     * → 软位图拷贝 → buffer 立即 close。限流（约 333ms 间隔，回调
     * [AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT]）、失败、
     * buffer 异常一律 onResult(null)，由调用方 Toast 提示重试。
     * 回调固定在主线程（main executor）。
     */
    fun captureCurrentScreen(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }
        // The a11y screenshot grabs the LAST COMPOSITED frame. The floating
        // ball was removed on the UI thread moments ago; request the capture
        // one frame later so the removal is out of the frame (ball was baked
        // into real-device captures).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            captureNow(onResult)
        }, captureDelayMs)
    }

    private fun captureNow(onResult: (Bitmap?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        onResult(softwareBitmapFrom(screenshot))
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot failed errorCode=$errorCode")
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            // 服务断连等系统侧异常：按失败处理，不向调用方抛出。
            Log.e(TAG, "takeScreenshot threw", e)
            onResult(null)
        }
    }

    /** ScreenshotResult → 软位图；hardware buffer 用完即关，任何异常返回 null。 */
    private fun softwareBitmapFrom(screenshot: ScreenshotResult): Bitmap? {
        val buffer = screenshot.hardwareBuffer ?: return null
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                ?: return null
            return try {
                // 硬件位图仅渲染期有效，拷贝为软位图后再交上层落盘。
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardware.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "hardware buffer → bitmap failed", e)
            return null
        } finally {
            buffer.close()
        }
    }

    companion object {
        /** Delay before a11y capture: lets the ball-removal frame composite out. */
        const val CAPTURE_DELAY_MS = 150L
        const val TAG = "ScreenPalFlow"

        /** 当前被系统绑定的服务实例；未启用或被杀为 null。 */
        @Volatile
        var instance: ScreenPalAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
