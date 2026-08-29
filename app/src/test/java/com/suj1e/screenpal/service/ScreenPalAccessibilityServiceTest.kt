package com.suj1e.screenpal.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

/**
 * 静默截屏无障碍服务契约（2026-08-29-a11y-screenshot）：
 * 1) 服务必须在 Manifest 以 BIND_ACCESSIBILITY_SERVICE + accessibilityservice
 *    intent-filter + meta-data 配置注册（系统才可启用）；
 * 2) 静态实例随系统连接/解绑增减（isRunning 与路由回落依据）；
 * 3) 配置 XML 必须开启 canTakeScreenshot（takeScreenshot 的前提）并最小订阅 typeWindowsChanged。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenPalAccessibilityServiceTest {

    /** 暴露受保护的系统生命周期回调，供 JVM 驱动（真实时机由系统绑定决定）。 */
    class DrivableService : ScreenPalAccessibilityService() {
        public override fun onServiceConnected() = super.onServiceConnected()
        public override fun onUnbind(intent: Intent?): Boolean = super.onUnbind(intent)
        public override fun onDestroy() = super.onDestroy()
    }

    @After
    fun tearDown() {
        // 静态实例是进程级单例，防止跨用例泄漏。
        Robolectric.buildService(DrivableService::class.java)
            .create()
            .get()
            .let { svc ->
                svc.onUnbind(Intent())
                svc.onDestroy()
            }
    }

    // region Manifest / 配置注册契约

    @Test
    fun service_declaredInManifest_withBindAccessibilityPermission() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getServiceInfo(
            ComponentName(app, ScreenPalAccessibilityService::class.java),
            PackageManager.GET_META_DATA
        )
        assertEquals(
            "截屏服务必须受系统签名权限保护，仅允许系统绑定",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            info.permission
        )
        assertNotNull("必须挂 accessibilityservice 配置 meta-data", info.metaData)
        assertTrue(
            "meta-data 必须指向配置 XML",
            info.metaData.containsKey("android.accessibilityservice")
        )
    }

    @Test
    fun configXml_enablesScreenshot_withMinimalWindowChangedSubscription() {
        val xml = File("src/main/res/xml/accessibility_service_config.xml").readText()
        assertTrue(
            "canTakeScreenshot=true 是 takeScreenshot 的硬前提",
            xml.contains("""android:canTakeScreenshot="true"""")
        )
        assertTrue(
            "最小订阅 typeWindowsChanged，服务可常驻但不做事件处理",
            xml.contains("""android:accessibilityEventTypes="typeWindowsChanged"""")
        )
    }

    @Test
    fun configXml_parsedFromMergedManifest_grantsTakeScreenshotCapability() {
        // 以系统真实路径（ResolveInfo + 配置 XML）构造服务信息，端到端钉住 canTakeScreenshot 生效。
        val app = RuntimeEnvironment.getApplication()
        val svcInfo = app.packageManager.getServiceInfo(
            ComponentName(app, ScreenPalAccessibilityService::class.java),
            PackageManager.GET_META_DATA
        )
        val resolveInfo = android.content.pm.ResolveInfo().apply { serviceInfo = svcInfo }
        val parsed = ReflectionHelpers.callConstructor(
            AccessibilityServiceInfo::class.java,
            ReflectionHelpers.ClassParameter.from(android.content.pm.ResolveInfo::class.java, resolveInfo),
            ReflectionHelpers.ClassParameter.from(Context::class.java, app)
        )
        assertTrue(
            "配置 XML 必须授予 CAPABILITY_CAN_TAKE_SCREENSHOT",
            parsed.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
        )
        assertEquals(
            "事件订阅必须是最小集 typeWindowsChanged",
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            parsed.eventTypes
        )
    }

    // endregion

    // region 静态实例生命周期

    @Test
    fun isRunning_false_beforeSystemConnects() {
        Robolectric.buildService(DrivableService::class.java).create()
        assertFalse("系统尚未连接，不得视为运行中", ScreenPalAccessibilityService.isRunning())
    }

    @Test
    fun instance_setOnServiceConnected() {
        val controller = Robolectric.buildService(DrivableService::class.java).create()
        assertFalse(ScreenPalAccessibilityService.isRunning())

        controller.get().onServiceConnected()

        assertTrue(ScreenPalAccessibilityService.isRunning())
        assertTrue(
            "instance 必须指向当前被绑定的服务",
            ScreenPalAccessibilityService.instance is DrivableService
        )
    }

    @Test
    fun instance_clearedOnUnbind() {
        val controller = Robolectric.buildService(DrivableService::class.java).create()
        controller.get().onServiceConnected()
        assertTrue(ScreenPalAccessibilityService.isRunning())

        controller.get().onUnbind(Intent())

        assertFalse("解绑后必须置空，路由据此回落 MediaProjection", ScreenPalAccessibilityService.isRunning())
    }

    @Test
    fun instance_clearedOnDestroy() {
        val controller = Robolectric.buildService(DrivableService::class.java).create()
        controller.get().onServiceConnected()
        controller.get().onDestroy()
        assertFalse(ScreenPalAccessibilityService.isRunning())
    }

    // endregion
}
