package com.suj1e.screenpal.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FloatingWindowService 截屏路由接线契约（2026-08-29-a11y-screenshot）：
 * onBallClicked 按路由矩阵三分支——无障碍静默截屏（成功进框选/null 重试），
 * 未开启无障碍 → 引导对话框，API<30/实例被杀 → 原 MediaProjection 路径原样保留。
 */
class FloatingWindowServiceScreenshotWiringTest {

    private val serviceSrc =
        File("src/main/java/com/suj1e/screenpal/service/FloatingWindowService.kt").readText()

    @Test
    fun onBallClicked_resolvesRouteFromSystemSignals() {
        assertTrue(
            "onBallClicked 必须经 resolveScreenshotRoute 决策",
            serviceSrc.contains("resolveScreenshotRoute(")
        )
        assertTrue(
            "决策输入必须含 SDK 版本",
            serviceSrc.contains("sdkInt = Build.VERSION.SDK_INT")
        )
        assertTrue(
            "决策输入必须含无障碍启用态（系统实时查询）",
            serviceSrc.contains("a11yEnabled = AccessibilityHelper.isEnabled(this)")
        )
        assertTrue(
            "决策输入必须含服务实例存活态",
            serviceSrc.contains("a11yServiceRunning = ScreenPalAccessibilityService.isRunning()")
        )
    }

    @Test
    fun onBallClicked_branchesToThreeRoutes() {
        assertTrue(
            "ACCESSIBILITY 分支必须走无障碍截屏",
            serviceSrc.contains("ScreenshotRoute.ACCESSIBILITY -> captureViaAccessibility()")
        )
        assertTrue(
            "GUIDE 分支必须走引导对话框",
            serviceSrc.contains("ScreenshotRoute.GUIDE -> showAccessibilityGuide()")
        )
        assertTrue(
            "MEDIA_PROJECTION 分支必须保留原录屏路径",
            serviceSrc.contains("ScreenshotRoute.MEDIA_PROJECTION -> legacyCapture()")
        )
    }

    @Test
    fun accessibilityCapture_successLaunchesSelection_nullRestoresBall() {
        assertTrue(
            "无障碍截屏必须经静态实例（服务被杀时实例为 null）",
            serviceSrc.contains("ScreenPalAccessibilityService.instance")
        )
        assertTrue(
            "必须调用 captureCurrentScreen",
            serviceSrc.contains("captureCurrentScreen")
        )
        assertTrue(
            "失败/限流必须 Toast「截屏失败或太频繁，请重试」并恢复球",
            serviceSrc.contains("截屏失败或太频繁，请重试") &&
                serviceSrc.contains("restoreAfterFailure")
        )
        assertTrue(
            "成功必须 launchSelection(uri)",
            serviceSrc.contains("launchSelection(uri)")
        )
    }

    @Test
    fun accessibilityCapture_savesBitmapWithSameContractAsProjectionPath() {
        assertTrue(
            "位图落盘必须复用录屏路径同款 cache/screenshots 目录",
            serviceSrc.contains("ScreenCaptureService.CAPTURE_DIR")
        )
        assertTrue(
            "位图落盘必须复用同款 JPEG 质量",
            serviceSrc.contains("ScreenCaptureService.JPEG_QUALITY")
        )
        assertTrue(
            "位图 URI 必须经 FileProvider（框选页跨进程读取）",
            serviceSrc.contains("FileProvider.getUriForFile")
        )
    }

    @Test
    fun mediaProjectionLegacyPath_isPreserved() {
        assertTrue(
            "原 MediaProjection captureScreen 流程不得移除",
            serviceSrc.contains("ScreenCaptureService.start(this, receiver)")
        )
        assertTrue(
            "无授权仍走 CaptureConsentActivity 授权",
            serviceSrc.contains("CaptureConsentActivity")
        )
    }
}
