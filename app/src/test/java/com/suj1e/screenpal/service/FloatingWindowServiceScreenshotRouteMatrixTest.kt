package com.suj1e.screenpal.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 截屏路由矩阵（2026-08-29-a11y-screenshot，纯函数决策）：
 * - API < 30（无 takeScreenshot）→ MediaProjection 兜底；
 * - API ≥ 30 且未开启无障碍 → 引导对话框；
 * - API ≥ 30 且已开启且服务实例在 → 无障碍静默截屏；
 * - 系统显示已启用但实例被杀 → 回落 MediaProjection，不阻塞。
 */
class FloatingWindowServiceScreenshotRouteMatrixTest {

    private fun route(sdkInt: Int, enabled: Boolean, running: Boolean) =
        FloatingWindowService.resolveScreenshotRoute(sdkInt, enabled, running)

    @Test
    fun apiBelow30_routesToMediaProjection_regardlessOfA11yState() {
        assertEquals(
            ScreenshotRoute.MEDIA_PROJECTION,
            route(29, enabled = false, running = false)
        )
        assertEquals(
            ScreenshotRoute.MEDIA_PROJECTION,
            route(29, enabled = true, running = true)
        )
    }

    @Test
    fun api30Plus_a11yDisabled_routesToGuide() {
        assertEquals(
            ScreenshotRoute.GUIDE,
            route(30, enabled = false, running = false)
        )
        assertEquals(
            ScreenshotRoute.GUIDE,
            route(34, enabled = false, running = false)
        )
    }

    @Test
    fun api30Plus_enabledAndRunning_routesToAccessibility() {
        assertEquals(
            ScreenshotRoute.ACCESSIBILITY,
            route(30, enabled = true, running = true)
        )
        assertEquals(
            ScreenshotRoute.ACCESSIBILITY,
            route(34, enabled = true, running = true)
        )
    }

    @Test
    fun api30Plus_enabledButServiceKilled_fallsBackToMediaProjection() {
        // 设计风险条款：无障碍服务被系统/厂商杀 → 回落 MediaProjection，不阻塞。
        assertEquals(
            ScreenshotRoute.MEDIA_PROJECTION,
            route(34, enabled = true, running = false)
        )
    }
}
