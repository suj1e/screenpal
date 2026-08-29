package com.suj1e.screenpal.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.suj1e.screenpal.service.ScreenPalAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * 无障碍启用判定契约（2026-08-29-a11y-screenshot）：
 * [AccessibilityHelper.isEnabled] 以 AccessibilityManager 回显的「已启用服务列表」
 * 是否包含本应用 ScreenPalAccessibilityService 为准；settingsIntent 直跳系统无障碍设置页。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityHelperTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    private val shadowManager: android.view.accessibility.AccessibilityManager =
        app.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private fun enabledList(vararg infos: AccessibilityServiceInfo) {
        shadowOf(shadowManager).setEnabledAccessibilityServiceList(infos.toList())
    }

    /** 系统 getEnabledAccessibilityServiceList 回显的真实形态：resolveInfo.serviceInfo 指向组件。 */
    private fun serviceInfo(pkg: String, cls: String): AccessibilityServiceInfo {
        val info = AccessibilityServiceInfo()
        ReflectionHelpers.setField(
            info,
            "mResolveInfo",
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = pkg
                    name = cls
                }
            }
        )
        return info
    }

    private fun ourServiceInfo(className: String) =
        serviceInfo(app.packageName, className)

    private fun foreignServiceInfo() = serviceInfo(
        "com.google.android.marvin.talkback",
        "com.google.android.marvin.talkback.TalkBackService"
    )

    @Test
    fun isEnabled_false_whenEnabledListEmpty() {
        enabledList()
        assertFalse(AccessibilityHelper.isEnabled(app))
    }

    @Test
    fun isEnabled_true_whenOurServiceEnabled() {
        enabledList(
            foreignServiceInfo(),
            ourServiceInfo("com.suj1e.screenpal.service.ScreenPalAccessibilityService")
        )
        assertTrue(AccessibilityHelper.isEnabled(app))
    }

    @Test
    fun isEnabled_true_whenOurServiceEnabledWithRelativeClassName() {
        // 部分 ROM 回显未展开的相对类名（.service.X），判定不得漏报。
        enabledList(ourServiceInfo(".service.ScreenPalAccessibilityService"))
        assertTrue(AccessibilityHelper.isEnabled(app))
    }

    @Test
    fun isEnabled_false_whenOnlyForeignServicesEnabled() {
        enabledList(foreignServiceInfo())
        assertFalse(AccessibilityHelper.isEnabled(app))
    }

    @Test
    fun isEnabled_false_whenResolveInfoMissingServiceInfo() {
        val info = AccessibilityServiceInfo()
        ReflectionHelpers.setField(info, "mResolveInfo", ResolveInfo())
        enabledList(info)
        assertFalse(AccessibilityHelper.isEnabled(app))
    }

    @Test
    fun settingsIntent_targetsSystemAccessibilitySettings() {
        val intent = AccessibilityHelper.settingsIntent()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }
}

