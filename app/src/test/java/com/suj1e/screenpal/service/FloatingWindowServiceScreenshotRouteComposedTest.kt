package com.suj1e.screenpal.service

import android.content.Intent
import android.view.accessibility.AccessibilityManager
import com.suj1e.screenpal.util.AccessibilityHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 路由决策与真实系统信号（Build.VERSION.SDK_INT / AccessibilityHelper /
 * ScreenPalAccessibilityService 实例状态）的组合矩阵：
 * Robolectric @Config 控 SDK，shadow 无障碍服务列表控启用态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FloatingWindowServiceScreenshotRouteComposedTest {

    class DrivableA11yService : ScreenPalAccessibilityService() {
        public override fun onServiceConnected() = super.onServiceConnected()
        public override fun onUnbind(intent: Intent?): Boolean = super.onUnbind(intent)
    }

    private val app = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        Robolectric.buildService(DrivableA11yService::class.java)
            .create().get()
            .let { svc ->
                svc.onUnbind(Intent())
                svc.onDestroy()
            }
    }

    private fun enableOurService(enabled: Boolean) {
        val manager = app.getSystemService(AccessibilityManager::class.java)
        if (enabled) {
            val info = android.accessibilityservice.AccessibilityServiceInfo()
            org.robolectric.util.ReflectionHelpers.setField(
                info,
                "mResolveInfo",
                android.content.pm.ResolveInfo().apply {
                    serviceInfo = android.content.pm.ServiceInfo().apply {
                        packageName = app.packageName
                        name = "com.suj1e.screenpal.service.ScreenPalAccessibilityService"
                    }
                }
            )
            shadowOf(manager).setEnabledAccessibilityServiceList(listOf(info))
        } else {
            shadowOf(manager).setEnabledAccessibilityServiceList(emptyList())
        }
    }

    private fun resolveRoute() = FloatingWindowService.resolveScreenshotRoute(
        sdkInt = android.os.Build.VERSION.SDK_INT,
        a11yEnabled = AccessibilityHelper.isEnabled(app),
        a11yServiceRunning = ScreenPalAccessibilityService.isRunning()
    )

    @Test
    fun api34_enabledButInstanceDead_routesToMediaProjection() {
        enableOurService(enabled = true)
        // 服务未绑定（实例 null）：系统列表已启用但进程内实例被杀。
        assertEquals(
            ScreenshotRoute.MEDIA_PROJECTION,
            resolveRoute()
        )
    }

    @Test
    fun api34_enabledAndBound_routesToAccessibility() {
        enableOurService(enabled = true)
        Robolectric.buildService(DrivableA11yService::class.java).create().get()
            .onServiceConnected()
        assertEquals(
            ScreenshotRoute.ACCESSIBILITY,
            resolveRoute()
        )
    }

    @Test
    fun api34_notEnabled_routesToGuide() {
        enableOurService(enabled = false)
        assertEquals(
            ScreenshotRoute.GUIDE,
            resolveRoute()
        )
    }

    @Test
    @Config(sdk = [29])
    fun api29_evenIfEnabled_routesToMediaProjection() {
        enableOurService(enabled = true)
        assertEquals(
            ScreenshotRoute.MEDIA_PROJECTION,
            resolveRoute()
        )
    }

    @Test
    fun route_isDeterministic_forSameSystemSignals() {
        enableOurService(enabled = true)
        Robolectric.buildService(DrivableA11yService::class.java).create().get()
            .onServiceConnected()
        assertEquals(resolveRoute(), resolveRoute())
        assertEquals(
            ScreenshotRoute.ACCESSIBILITY,
            resolveRoute()
        )
    }
}
