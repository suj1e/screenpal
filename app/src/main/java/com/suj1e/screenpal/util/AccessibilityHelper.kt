package com.suj1e.screenpal.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.suj1e.screenpal.service.ScreenPalAccessibilityService

/**
 * 无障碍启用判定与系统设置跳转（2026-08-29-a11y-screenshot）。
 * 以系统实时回显的「已启用无障碍服务列表」为准，无 DataStore 缓存状态；
 * 供截屏路由与设置页权限卡片共用。
 */
object AccessibilityHelper {

    /** 系统无障碍设置页 Intent（引导对话框「去开启」出口）。 */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** 本应用截屏服务是否已被系统启用。 */
    fun isEnabled(context: Context): Boolean {
        // 首选：Settings.Secure 的启用服务字符串（全 ROM 可靠——部分 ROM 的
        // 快捷开关/临时授权不回显在 AccessibilityManager 列表里）。
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabled?.contains("${context.packageName}") == true) return true

        // 兜底：AccessibilityManager 实时列表（处理相对类名回显）。
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
        val expected = ComponentName(context, ScreenPalAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                val actual = ComponentName(serviceInfo.packageName, serviceInfo.name)
                actual == expected ||
                    actual.flattenToShortString() == expected.flattenToShortString()
            }
    }
}
